package com.mj.yata.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun PressableScaleBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleAmount: Float = 0.96f,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    // This used to ignore LocalReduceMotion entirely - every one of its 13 call sites kept the
    // full press-shrink animation under Reduced or Off. Snapping straight to 1f (no shrink at
    // all) rather than shortening it matches ProgressRing's convention: purely decorative motion
    // stops outright.
    val reduceMotion = com.mj.yata.ui.theme.LocalReduceMotion.current

    LaunchedEffect(isPressed, enabled, reduceMotion) {
        when {
            reduceMotion -> scale.snapTo(1f)
            isPressed && enabled -> scale.animateTo(scaleAmount, spring(stiffness = 900f))
            else -> scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f))
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled = com.mj.yata.ui.theme.LocalHapticsEnabled.current

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple
                enabled = enabled,
                onClick = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                    onClick()
                }
            ),
        content = content
    )
}
