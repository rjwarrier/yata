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

    private var isListeningActive = false
    private var accumulatedText = ""
    private var lastLanguage = "default"
    private var lastStrictOffline = true

    fun startListening(useStrictOffline: Boolean = true, language: String = "default") {
        isListeningActive = true
        lastLanguage = language
        lastStrictOffline = useStrictOffline
        restartInternal()
    }

    private fun restartInternal() {
        stopRecognizerOnly()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("Voice recognition service not available on this device")
            return
        }

        mainHandler.post {
            if (!isListeningActive) return@post
            try {
                val recognizer = if (lastStrictOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (isListeningActive) {
                            if (accumulatedText.isNotBlank()) {
                                _state.value = VoiceState.FinalResult(accumulatedText)
                            } else {
                                _state.value = VoiceState.Listening
                            }
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        if (isListeningActive) {
                            _state.value = VoiceState.Speaking(partialText = accumulatedText, rmsDb = 0f)
                        }
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
                        if (!isListeningActive) return

                        // Fallback once if strict offline engine is unavailable
                        if (lastStrictOffline && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NETWORK)) {
                            lastStrictOffline = false
                            restartInternal()
                            return
                        }

                        // For transient pause/timeout errors, automatically restart continuous listening!
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        ) {
                            mainHandler.postDelayed({
                                if (isListeningActive) {
                                    restartInternal()
                                }
                            }, 200)
                            return
                        }

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Please check mic permissions."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for online speech engine."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
                            SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                            else -> "Voice recognition error (code $error)"
                        }

                        _state.value = VoiceState.Error(message)
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isListeningActive) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            accumulatedText = if (accumulatedText.isNotBlank()) "$accumulatedText $text" else text
                        }
                        if (accumulatedText.isNotBlank()) {
                            _state.value = VoiceState.FinalResult(accumulatedText)
                        } else {
                            _state.value = VoiceState.Listening
                        }

                        // Continuously restart listening until user clicks "Create Task" or dismisses overlay
                        mainHandler.postDelayed({
                            if (isListeningActive) {
                                restartInternal()
                            }
                        }, 150)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isListeningActive) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim() ?: ""
                        if (partial.isNotBlank()) {
                            val combined = if (accumulatedText.isNotBlank()) "$accumulatedText $partial" else partial
                            _state.value = VoiceState.Speaking(partialText = combined, rmsDb = _rmsDb.value)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    val langTag = if (lastLanguage != "default") lastLanguage else Locale.getDefault().toLanguageTag()
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    if (lastStrictOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                recognizer.startListening(intent)
            } catch (e: Exception) {
                if (lastStrictOffline) {
                    lastStrictOffline = false
                    restartInternal()
                } else {
                    _state.value = VoiceState.Error(e.localizedMessage ?: "Failed to start speech recognizer")
                }
            }
        }
    }

    private fun stopRecognizerOnly() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun stopListening() {
        isListeningActive = false
        stopRecognizerOnly()
    }

    fun reset() {
        stopListening()
        accumulatedText = ""
        _state.value = VoiceState.Idle
        _rmsDb.value = 0f
    }
}
