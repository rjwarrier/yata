package com.mj.yata.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.mj.yata.domain.model.MotionMode

/**
 * The system-wide "Animator duration scale" (Developer Options > Animation off, or the equivalent
 * accessibility toggle some OEMs expose). Compose doesn't consult this on its own, so a user who
 * turned system animations off got full YATA motion anyway - previously checked only by
 * [com.mj.yata.ui.widgets.ConfettiOverlay]; [MainActivity][com.mj.yata.MainActivity] now folds it
 * into [YataDur.applyMotionMode] so it governs every animation in the app the same way, not just
 * confetti.
 */
fun systemAnimatorScaleIsZero(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Centralized easings/durations mirroring handoff m3-widgets.jsx EASE/DUR. */
object YataEase {
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}


/** Snapshot state so [applyMotionMode] registers as a state change app-wide. */
object YataDur {
    private const val defaultNav = 380
    private const val defaultPop = 200
    private const val defaultSheet = 340
    private const val defaultFade = 200
    private const val defaultMicro = 140

    var nav by mutableIntStateOf(defaultNav)
        private set
    var pop by mutableIntStateOf(defaultPop)
        private set
    var sheet by mutableIntStateOf(defaultSheet)
        private set
    var fade by mutableIntStateOf(defaultFade)
        private set
    var micro by mutableIntStateOf(defaultMicro)
        private set

    var modeState by mutableStateOf(MotionMode.FULL)
        private set

    fun applyReduceMotion(enabled: Boolean) {
        applyMotionMode(if (enabled) MotionMode.REDUCED else MotionMode.FULL)
    }

    fun applyMotionMode(mode: MotionMode) {
        modeState = mode
        when (mode) {
            MotionMode.FULL -> {
                nav = defaultNav
                pop = defaultPop
                sheet = defaultSheet
                fade = defaultFade
                micro = defaultMicro
            }
            MotionMode.REDUCED -> {
                nav = defaultNav / 3
                pop = defaultPop / 3
                sheet = defaultSheet / 3
                fade = defaultFade / 3
                micro = defaultMicro / 3
            }
            MotionMode.OFF -> {
                nav = 0
                pop = 0
                sheet = 0
                fade = 0
                micro = 0
            }
        }
    }
}

val yataItemPlacement: FiniteAnimationSpec<IntOffset>
    get() = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)

val yataItemFade: FiniteAnimationSpec<Float>
    get() = tween(durationMillis = YataDur.fade, easing = YataEase.emphasized)

/**
 * Motion-aware replacement for `rememberInfiniteTransition().animateFloat(...)`, for a loop that is
 * purely decorative (a pulse, a shimmer sweep, a wandering wave) rather than communicating a state
 * change. Gates on [LocalReduceMotion] — true for both [MotionMode.REDUCED] and [MotionMode.OFF] —
 * matching the convention [ProgressRing]'s wave already established: decorative motion should stop
 * outright under Reduce Motion, not just play faster, which is what [YataDur]'s duration scaling is
 * for instead. Every direct call to `rememberInfiniteTransition` bypassed that and looped forever
 * regardless of the setting. Callers keep their own [animationSpec] (repeat mode, easing, duration
 * all vary per use) - this only decides whether the transition runs at all.
 */
@Composable
fun rememberMotionAwareInfiniteFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String = "motionAwareInfiniteFloat",
    /** The value to hold when motion is off. Defaults to [initialValue], right for a sweep that
     * starts/rests at one end (shimmer, a wave's phase); a Reverse pulse oscillating around a
     * midpoint should pass that midpoint explicitly instead. */
    restValue: Float = initialValue
): State<Float> {
    if (LocalReduceMotion.current) {
        return remember(restValue) { mutableFloatStateOf(restValue) }
    }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label
    )
}
