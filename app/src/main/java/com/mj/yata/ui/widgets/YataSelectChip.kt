package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.theme.UiShape

/**
 * Tinted pill selector: accent@18% bg + accent text + check when selected.
 * Mirrors handoff sheets.jsx / m3-widgets.jsx pill-chip language.
 */
@Composable
fun YataSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    dotColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    showCheck: Boolean = true,
    height: Dp = 34.dp
) {
    val bg = if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) tint else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .clip(UiShape.pill)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leading != null) {
            leading()
        } else if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        }
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
        if (selected && showCheck) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Dashed "create new" pill — handoff pattern for pickers (New tag / New project / Create new person). */
@Composable
fun YataDashedAddChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp
) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .clip(UiShape.pill)
            .dashedBorder(color)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        )
    }
}

private fun Modifier.dashedBorder(color: Color, strokeWidth: Dp = 1.5.dp, radius: Dp = 999.dp): Modifier =
    this.then(
        drawWithCache {
            val stroke = strokeWidth.toPx()
            val cornerPx = radius.toPx().coerceAtMost(size.minDimension / 2)
            onDrawBehind {
                drawRoundRect(
                    color = color,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    ),
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            }
        }
    )
