package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

/** "PENDING (3)" / "COMPLETED (2)" header shared by every task-list screen that splits tasks
 * into the two sections — callers only render this at all when the section is non-empty, and
 * skip both sections entirely while completed tasks are hidden. Carries its own horizontal=20dp
 * margin matching TaskRow's hardcoded internal padding, so the header lines up with the rows
 * below it regardless of whether the enclosing LazyColumn's contentPadding has horizontal insets. */
@Composable
fun TaskSectionHeader(title: String, count: Int, modifier: Modifier = Modifier, horizontalPadding: Dp = 20.dp) {
    Row(
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title (",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val spec = tween<IntOffset>(durationMillis = YataDur.micro, easing = YataEase.emphasized)
                val fadeSpec = tween<Float>(durationMillis = YataDur.micro, easing = YataEase.emphasized)
                (slideInVertically(spec) { height -> height } + fadeIn(fadeSpec)).togetherWith(
                    slideOutVertically(spec) { height -> -height } + fadeOut(fadeSpec)
                )
            },
            label = "headerCountAnim"
        ) { targetCount ->
            Text(
                text = "$targetCount",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = ")",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
