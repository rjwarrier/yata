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
import androidx.compose.ui.draw.scale

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

    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            scale.animateTo(scaleAmount, spring(stiffness = 900f))
        } else {
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f))
        }
    }

    Box(
        modifier = modifier
            .scale(scale.value)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}
