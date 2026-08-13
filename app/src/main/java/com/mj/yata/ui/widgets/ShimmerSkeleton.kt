package com.mj.yata.ui.widgets

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val translateAnim = com.mj.yata.ui.theme.rememberMotionAwareInfiniteFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

/**
 * Placeholder rows for a tab's list content, shown while [com.mj.yata.ui.screen.main.MainViewModel.initialDataLoaded]
 * is still false — the gap between the ViewModel existing and Room's first query landing, during
 * which every list is `emptyList()` indistinguishable from a real empty state. Sized to roughly
 * match a comfortable-density [TaskRow]/list-item card so the layout doesn't visibly jump once
 * real content replaces it.
 */
@Composable
fun ListRowsShimmer(rowCount: Int = 6, modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(rowCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun TaskDetailShimmer() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar space
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Box(modifier = Modifier.width(120.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Task title placeholder
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(32.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Spacer(modifier = Modifier.height(16.dp))

        // Task meta row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.width(80.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(brush))
            Box(modifier = Modifier.width(100.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(brush))
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Tab chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Box(modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Content placeholder blocks
        repeat(3) {
            Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ListDetailShimmer() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Title and description
        Box(modifier = Modifier.fillMaxWidth(0.5f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Spacer(modifier = Modifier.height(32.dp))

        // Task rows
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(brush))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
            }
        }
    }
}
