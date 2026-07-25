package com.mj.yata.ui.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Lightweight completion sound effect utility that plays a crisp high-register chime
 * when tasks are completed, without requiring external audio asset files.
 */
object CompletionSoundPlayer {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    fun playCompletionChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
            // Silence gracefully if audio hardware/stream is busy
        }
    }
}
