package com.mj.yata.ui.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Lightweight completion sound effect utility that plays a crisp high-register chime
 * when tasks are completed, lazily instantiating and recovering ToneGenerator if stale.
 */
object CompletionSoundPlayer {
    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    private fun getToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            } catch (_: Exception) {
                try {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 85)
                } catch (_: Exception) {
                    toneGenerator = null
                }
            }
        }
        return toneGenerator
    }

    @Synchronized
    fun playCompletionChime() {
        try {
            val generator = getToneGenerator()
            generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
            // Re-create generator instance on next call if current became stale/released by OS
            try {
                toneGenerator?.release()
            } catch (_: Exception) {}
            toneGenerator = null
        }
    }
}
