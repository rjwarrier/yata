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

object YataDur {
    const val nav = 380
    const val sheet = 340
    const val fade = 200
    const val micro = 140
}
