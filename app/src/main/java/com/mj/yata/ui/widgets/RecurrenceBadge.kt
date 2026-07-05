package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.util.RecurrenceEvaluator

@Composable
fun RecurrenceBadge(
    recurrence: Recurrence?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (recurrence == null) return

    val color = MaterialTheme.colorScheme.tertiary
    val text = RecurrenceEvaluator.recurrenceSummary(recurrence)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = "Repeats",
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        if (!compact) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp
                )
            )
        }
    }
}
