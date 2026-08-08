package com.mj.yata.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.mj.yata.domain.model.MotionMode

/** Centralized easings/durations mirroring handoff m3-widgets.jsx EASE/DUR. */
object YataEase {
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/** Expressive spring specs for Material 3 responsive micro-animations */
object YataSpring {
    val bouncy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val press: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    val checkmark: AnimationSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessLow
    )
}

/** Snapshot state so [applyMotionMode] registers as a state change app-wide. */
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
                sheet = defaultSheet
                fade = defaultFade
                micro = defaultMicro
            }
            MotionMode.REDUCED -> {
                nav = defaultNav / 3
                sheet = defaultSheet / 3
                fade = defaultFade / 3
                micro = defaultMicro / 3
            }
            MotionMode.OFF -> {
                nav = 0
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
 * Reusable M3 Expressive touch feedback modifier that shrinks slightly on press (0.96f)
 * with bouncy spring recovery upon release, automatically respecting current [MotionMode].
 */
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        !enabled || YataDur.modeState == MotionMode.OFF -> 1f
        isPressed && YataDur.modeState == MotionMode.REDUCED -> 0.99f
        isPressed -> pressedScale
        else -> 1f
    }

    val animSpec: AnimationSpec<Float> = when (YataDur.modeState) {
        MotionMode.FULL -> YataSpring.press
        MotionMode.REDUCED -> tween(durationMillis = YataDur.micro)
        MotionMode.OFF -> snap()
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = animSpec,
        label = "bounce-scale"
    )

    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

private fun <T> snap(): AnimationSpec<T> = tween(0)
