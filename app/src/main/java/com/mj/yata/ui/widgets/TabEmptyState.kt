package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Illustrated empty state — icon badge + title + subtitle — shared across tabs so an empty
 * list reads consistently everywhere instead of some tabs getting a plain gray line of text. */
@Composable
fun TabEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isEnhancedM3 = com.mj.yata.ui.theme.LocalEnhancedM3Theming.current
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "emptyPulse")
    val pulseScale = if (isEnhancedM3) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "emptyScale"
        ).value
    } else 1.0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
