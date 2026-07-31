package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The leading tile for a tag: its first letter(s) in the tag's accent colour, instead of the
 * generic label icon every tag used to share. Two initials for a multi-word name, one otherwise —
 * a truncated pair reads worse than a single letter at these sizes.
 */
fun tagMonogramFor(name: String): String =
    com.mj.yata.util.initialsFor(name).takeIf { it != "?" } ?: "#"

@Composable
fun TagMonogram(
    name: String,
    tagColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape
) {
    val monogram = remember(name) { tagMonogramFor(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tagColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monogram,
            color = tagColor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    size >= 56.dp -> 22.sp
                    size >= 40.dp -> 16.sp
                    else -> 12.sp
                }
            )
        )
    }
}
