package com.mj.yata.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Animated star/favorite toggle — pops in with a spring when turned on. Shared across
 * Projects/People card rows; entity-specific label text lives in the caller. */
@Composable
fun StarToggleButton(
    starred: Boolean,
    onToggle: () -> Unit,
    starredColor: Color,
    modifier: Modifier = Modifier,
    unstarredColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    starredContentDescription: String = "Unstar",
    unstarredContentDescription: String = "Star"
) {
    val starScale = remember { Animatable(1f) }
    LaunchedEffect(starred) {
        if (starred) {
            starScale.snapTo(0.4f)
            starScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }
    IconButton(onClick = onToggle, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = if (starred) starredContentDescription else unstarredContentDescription,
            tint = if (starred) starredColor else unstarredColor,
            modifier = Modifier
                .size(20.dp)
                .scale(starScale.value)
        )
    }
}
