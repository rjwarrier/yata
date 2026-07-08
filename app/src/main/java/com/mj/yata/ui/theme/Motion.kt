package com.mj.yata.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Centralized easings/durations mirroring handoff m3-widgets.jsx EASE/DUR. */
object YataEase {
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/** Plain `var`s (not `const val`) so [applyReduceMotion] can mutate them at runtime — every
 * consumer just reads `YataDur.nav` etc. fresh each call, so toggling Reduce Motion in Settings
 * takes effect app-wide with no changes needed at any of those call sites. */
object YataDur {
    private const val defaultNav = 380
    private const val defaultSheet = 340
    private const val defaultFade = 200
    private const val defaultMicro = 140

    var nav = defaultNav
    var sheet = defaultSheet
    var fade = defaultFade
    var micro = defaultMicro

    fun applyReduceMotion(enabled: Boolean) {
        val scale = if (enabled) 3 else 1
        nav = defaultNav / scale
        sheet = defaultSheet / scale
        fade = defaultFade / scale
        micro = defaultMicro / scale
    }
}
