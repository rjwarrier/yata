package com.mj.yata.ui.widgets

import com.mj.yata.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mj.yata.domain.model.MotionMode
import com.mj.yata.ui.theme.YataDur

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpringyCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 24.dp
) {
    val scale = remember { Animatable(if (checked) 1f else 1f) }
    val iconScale = remember { Animatable(if (checked) 1f else 0f) }
    val rippleScale = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }
    val soundEnabled = com.mj.yata.ui.theme.LocalCompletionSoundEnabled.current
    var initialCheckedState by remember { mutableStateOf(checked) }

    LaunchedEffect(checked) {
        if (checked == initialCheckedState) {
            return@LaunchedEffect
        }
        initialCheckedState = checked

        if (YataDur.modeState == MotionMode.OFF) {
            scale.snapTo(1f)
            iconScale.snapTo(if (checked) 1f else 0f)
            rippleAlpha.snapTo(0f)
            return@LaunchedEffect
        }

        if (checked) {
            if (soundEnabled) {
                com.mj.yata.ui.util.CompletionSoundPlayer.playCompletionChime()
            }
            rippleScale.snapTo(1f)
            rippleAlpha.snapTo(0.45f)
            scale.snapTo(0.4f)
            iconScale.snapTo(0.3f)
            launch {
                rippleScale.animateTo(2.2f, spring(stiffness = Spring.StiffnessLow))
            }
            launch {
                rippleAlpha.animateTo(0f, androidx.compose.animation.core.tween(300))
            }
            iconScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        } else {
            scale.snapTo(0.85f)
            iconScale.snapTo(0f)
            scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled = com.mj.yata.ui.theme.LocalHapticsEnabled.current

    // Outer container expands the touch target to at least 48x48dp
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
                if (!checked && soundEnabled) {
                    com.mj.yata.ui.util.CompletionSoundPlayer.playCompletionChime()
                }
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.Center
    ) {
        if (rippleAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = rippleScale.value
                        scaleY = rippleScale.value
                        alpha = rippleAlpha.value
                    }
                    .background(color, CircleShape)
            )
        }
        // Inner checkbox maintains visual size
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .border(
                    width = if (checked) 0.dp else 2.dp,
                    color = if (checked) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                    shape = CircleShape
                )
                .background(
                    color = if (checked) color else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked || iconScale.value > 0f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.springy_check_check),
                    tint = Color.White,
                    modifier = Modifier
                        .size(size * 0.65f)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        }
                )
            }
        }
    }
}
