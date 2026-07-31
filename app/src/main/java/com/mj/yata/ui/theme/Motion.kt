package com.mj.yata.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/** Centralized easings/durations mirroring handoff m3-widgets.jsx EASE/DUR. */
object YataEase {
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/** Snapshot state (not plain `var`s) so [applyReduceMotion] mutating them registers as a state
 * change — a composable that already read `YataDur.nav` etc. recomposes with the new value
 * instead of keeping whatever spec it started with until it happens to recompose for another
 * reason. Every consumer still just reads `YataDur.nav` fresh each call, so toggling Reduce
 * Motion in Settings takes effect app-wide with no changes needed at any of those call sites. */
object YataDur {
    private const val defaultNav = 380
    private const val defaultSheet = 340
    private const val defaultFade = 200
    private const val defaultMicro = 140

    var nav by mutableIntStateOf(defaultNav)
        private set
    var sheet by mutableIntStateOf(defaultSheet)
        private set
    var fade by mutableIntStateOf(defaultFade)
        private set
    var micro by mutableIntStateOf(defaultMicro)
        private set

    fun applyReduceMotion(enabled: Boolean) {
        val scale = if (enabled) 3 else 1
        nav = defaultNav / scale
        sheet = defaultSheet / scale
        fade = defaultFade / scale
        micro = defaultMicro / scale
    }
}

/**
 * Specs for `Modifier.animateItem` in the task lists — how a row slides when the rows above it
 * change, and how one fades in and out as it arrives or leaves.
 *
 * Every list already passed a tokenised `placementSpec` and left the fades to take their default,
 * which is a framework spring. So with Reduce Motion on a row's *movement* shortened threefold
 * while its *fade* carried on at full length — the one setting whose whole job is to be applied
 * uniformly. Reading [YataDur] at call time (hence `get()`, not a stored value) is what keeps them
 * in step, matching how every other consumer of these tokens works.
 */
val yataItemPlacement: FiniteAnimationSpec<IntOffset>
    get() = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)

val yataItemFade: FiniteAnimationSpec<Float>
    get() = tween(durationMillis = YataDur.fade, easing = YataEase.emphasized)
