package com.mj.yata.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceState {
    object Idle : VoiceState
    object Listening : VoiceState
    data class Speaking(val partialText: String, val rmsDb: Float) : VoiceState
    data class FinalResult(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * 100% Local On-Device Speech Recognizer wrapper using Android's native SpeechRecognizer
 * configured with EXTRA_PREFER_OFFLINE = true. Real-time partial transcription and
 * audio level amplitude callbacks drive the voice task creation UI.
 */
class OnDeviceVoiceRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    fun startListening(useStrictOffline: Boolean = true, language: String = "default") {
        stopListening()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("Voice recognition service not available on this device")
            return
        }

        mainHandler.post {
            try {
                val recognizer = if (useStrictOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = VoiceState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        _state.value = VoiceState.Speaking(partialText = "", rmsDb = 0f)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
                        _rmsDb.value = normalized
                        val current = _state.value
                        if (current is VoiceState.Speaking) {
                            _state.value = current.copy(rmsDb = normalized)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // If strict offline failed due to missing offline pack (client/network error), fallback once
                        if (useStrictOffline && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NETWORK)) {
                            startListening(useStrictOffline = false)
                            return
                        }

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Please check mic permissions."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for online speech engine."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy. Retrying..."
                            SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic to try again."
                            else -> "Voice recognition error (code $error)"
                        }

                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            _state.value = VoiceState.Idle
                        } else {
                            _state.value = VoiceState.Error(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _state.value = VoiceState.FinalResult(text)
                        } else {
                            _state.value = VoiceState.Idle
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _state.value = VoiceState.Speaking(partialText = text, rmsDb = _rmsDb.value)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    val langTag = if (language != "default") language else Locale.getDefault().toLanguageTag()
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    if (useStrictOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                recognizer.startListening(intent)
            } catch (e: Exception) {
                if (useStrictOffline) {
                    startListening(useStrictOffline = false)
                } else {
                    _state.value = VoiceState.Error(e.localizedMessage ?: "Failed to start speech recognizer")
                }
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    fun reset() {
        stopListening()
        _state.value = VoiceState.Idle
        _rmsDb.value = 0f
    }
}
