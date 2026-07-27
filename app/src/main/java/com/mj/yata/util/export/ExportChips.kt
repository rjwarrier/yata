package com.mj.yata.util.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExportPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        ),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExportTagPills(
    chips: List<ExportTagChip>,
    modifier: Modifier = Modifier,
    maxItems: Int = Int.MAX_VALUE,
    fontSize: TextUnit = 9.sp
) {
    val visible = chips.take(maxItems)
    val hiddenCount = (chips.size - visible.size).coerceAtLeast(0)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { chip ->
            ExportPill(text = chip.name, color = chip.color, fontSize = fontSize)
        }
        if (hiddenCount > 0) {
            ExportPill(
                text = "+$hiddenCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = fontSize
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExportNamePills(
    names: List<String>,
    modifier: Modifier = Modifier,
    maxItems: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.tertiary,
    prefix: String = "@",
    fontSize: TextUnit = 9.sp
) {
    val visible = names.take(maxItems)
    val hiddenCount = (names.size - visible.size).coerceAtLeast(0)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { name ->
            ExportPill(text = "$prefix$name", color = color, fontSize = fontSize)
        }
        if (hiddenCount > 0) {
            ExportPill(
                text = "+$hiddenCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = fontSize
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExportLabeledPills(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(7.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}
