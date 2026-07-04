package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mj.yata.ui.theme.LocalYataAccents

@Composable
fun PriorityBars(
    priority: String, // "none" | "low" | "med" | "high"
    modifier: Modifier = Modifier
) {
    if (priority == "none") return

    val fillCount = when (priority) {
        "low" -> 1
        "med" -> 2
        "high" -> 3
        else -> 0
    }

    val accents = LocalYataAccents.current
    val barColor = when (priority) {
        "low" -> accents.accentE
        "med" -> accents.accentD
        "high" -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }

    val inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Row(
        modifier = modifier.height(14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..3) {
            val isFilled = i <= fillCount
            val barHeight = when (i) {
                1 -> 6.dp
                2 -> 10.dp
                else -> 14.dp
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .background(if (isFilled) barColor else inactiveColor)
            )
        }
    }
}
