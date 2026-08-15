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
 * Android SpeechRecognizer wrapper that prefers on-device recognition when available.
 * Real-time partial transcription and audio level callbacks drive the voice task creation UI.
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
    private var transientRestartCount = 0
    private var activeSessionId = 0
    private var lastCommittedSegment = ""

    fun startListening(useStrictOffline: Boolean = true, language: String = "default") {
        isListeningActive = true
        accumulatedText = ""
        lastCommittedSegment = ""
        _rmsDb.value = 0f
        _state.value = VoiceState.Listening
        lastLanguage = language
        lastStrictOffline = useStrictOffline && language !in offlineUnavailableLanguages
        transientRestartCount = 0
        restartInternal()
    }

    private fun restartInternal() {
        mainHandler.removeCallbacksAndMessages(null)
        val sessionId = ++activeSessionId
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
                        if (!isCurrentSession(sessionId)) return
                        if (isListeningActive) {
                            transientRestartCount = 0
                            if (accumulatedText.isNotBlank()) {
                                _state.value = VoiceState.FinalResult(accumulatedText)
                            } else {
                                _state.value = VoiceState.Listening
                            }
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        if (!isCurrentSession(sessionId)) return
                        if (isListeningActive) {
                            transientRestartCount = 0
                            _state.value = VoiceState.Speaking(partialText = accumulatedText, rmsDb = 0f)
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (!isCurrentSession(sessionId)) return
                        val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
                        _rmsDb.value = normalized
                        val current = _state.value
                        if (current is VoiceState.Speaking) {
                            _state.value = current.copy(rmsDb = normalized)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (!isCurrentSession(sessionId)) return
                    }

                    override fun onError(error: Int) {
                        if (!isCurrentSession(sessionId)) return
                        if (!isListeningActive) return

                        // Fallback once if strict/offline recognition cannot handle the device,
                        // selected language, or local model. Several engines report these as
                        // server/support errors rather than the older client/network pair.
                        if (lastStrictOffline && shouldRetryOnline(error)) {
                            offlineUnavailableLanguages += lastLanguage
                            lastStrictOffline = false
                            restartInternal()
                            return
                        }

                        if (shouldRestartAfterError(error)) {
                            scheduleRestartAfterError(error)
                            return
                        }

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Please check mic permissions."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Selected voice language is not supported on this device."
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Selected voice language is not available on this device."
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for online speech engine."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
                            SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Speech service disconnected."
                            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech service is busy. Please try again."
                            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Could not check speech support on this device."
                            else -> "Voice recognition error (code $error)"
                        }

                        _state.value = VoiceState.Error(message)
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isCurrentSession(sessionId)) return
                        if (!isListeningActive) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches.bestTranscript()
                        if (text.isNotBlank()) {
                            appendRecognizedSegment(text)
                            transientRestartCount = 0
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
                        }, 450)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isCurrentSession(sessionId)) return
                        if (!isListeningActive) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches.bestTranscript()
                        if (partial.isNotBlank()) {
                            transientRestartCount = 0
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
                    offlineUnavailableLanguages += lastLanguage
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
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun scheduleRestartAfterError(error: Int) {
        transientRestartCount += 1
        if (transientRestartCount > MAX_TRANSIENT_RESTARTS && accumulatedText.isBlank()) {
            _state.value = VoiceState.Error("Voice recognition paused. Tap speak again.")
            return
        }
        if (accumulatedText.isNotBlank()) {
            _state.value = VoiceState.FinalResult(accumulatedText)
        } else {
            _state.value = VoiceState.Listening
        }
        mainHandler.postDelayed({
            if (isListeningActive) {
                restartInternal()
            }
        }, restartDelayMillis(error))
    }

    private fun restartDelayMillis(error: Int): Long {
        return when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 500L + (transientRestartCount * 150L).coerceAtMost(900L)
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> 1200L + (transientRestartCount * 300L).coerceAtMost(1800L)
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> 650L + (transientRestartCount * 150L).coerceAtMost(900L)
            else -> 250L
        }
    }

    private fun appendRecognizedSegment(segment: String) {
        val normalizedSegment = segment.normalizedForComparison()
        if (normalizedSegment.isBlank()) return
        if (normalizedSegment == lastCommittedSegment.normalizedForComparison()) return
        val normalizedAccumulated = accumulatedText.normalizedForComparison()
        if (normalizedAccumulated.endsWith(normalizedSegment)) return
        if (normalizedSegment.startsWith(normalizedAccumulated) && normalizedAccumulated.isNotBlank()) {
            accumulatedText = segment.trim()
            lastCommittedSegment = segment
            return
        }

        val merged = mergeWithTokenOverlap(accumulatedText, segment)
        if (merged != null) {
            accumulatedText = merged
            lastCommittedSegment = segment
            return
        }

        accumulatedText = if (accumulatedText.isNotBlank()) "$accumulatedText $segment" else segment
        lastCommittedSegment = segment
    }

    private fun List<String>?.bestTranscript(): String =
        orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .maxByOrNull { it.normalizedForComparison().length }
            .orEmpty()

    private fun mergeWithTokenOverlap(existing: String, incoming: String): String? {
        val existingTokens = existing.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val incomingTokens = incoming.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (existingTokens.isEmpty() || incomingTokens.isEmpty()) return null
        val maxOverlap = minOf(existingTokens.size, incomingTokens.size)
        for (size in maxOverlap downTo 1) {
            val existingTail = existingTokens.takeLast(size).joinToString(" ").normalizedForComparison()
            val incomingHead = incomingTokens.take(size).joinToString(" ").normalizedForComparison()
            if (existingTail == incomingHead) {
                return (existingTokens + incomingTokens.drop(size)).joinToString(" ")
            }
        }
        return null
    }

    private fun String.normalizedForComparison(): String {
        return lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun shouldRestartAfterError(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
    }

    private fun isCurrentSession(sessionId: Int): Boolean {
        return isListeningActive && sessionId == activeSessionId
    }

    private fun shouldRetryOnline(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            error == SpeechRecognizer.ERROR_SERVER ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
            error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
    }

    fun stopListening() {
        isListeningActive = false
        activeSessionId += 1
        mainHandler.removeCallbacksAndMessages(null)
        stopRecognizerOnly()
    }

    fun reset() {
        stopListening()
        accumulatedText = ""
        lastCommittedSegment = ""
        _state.value = VoiceState.Idle
        _rmsDb.value = 0f
    }

    private companion object {
        const val MAX_TRANSIENT_RESTARTS = 8
        val offlineUnavailableLanguages = mutableSetOf<String>()
    }
}
