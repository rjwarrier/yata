package com.mj.yata.ui.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.theme.UiShape
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

/**
 * Pill stepper: [−] value [+] on a surfaceContainerHigh track, matching handoff sheets.jsx Stepper.
 */
@Composable
fun YataStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, UiShape.pill)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StepperButton(symbol = "−", enabled = value > min) { onChange((value - 1).coerceAtLeast(min)) }
        
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> -height } + fadeOut())
                } else {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> height } + fadeOut())
                }.using(
                    SizeTransform(clip = false)
                )
            },
            label = "stepperValueAnimation"
        ) { targetValue ->
            Text(
                text = "$targetValue",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(30.dp)
            )
        }

        StepperButton(symbol = "+", enabled = value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val iconColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "stepperIconColor"
    )

    PressableScaleBox(
        onClick = onClick,
        enabled = enabled,
        scaleAmount = 0.92f
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = iconColor
            )
        }
    }
}
