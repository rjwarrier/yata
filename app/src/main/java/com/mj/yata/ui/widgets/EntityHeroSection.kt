package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared hero header for the entity-detail screens (Person/Project/List/Tag) — a leading
 * visual (avatar or icon tile), a primary stat line, an optional name/secondary content block,
 * a trailing progress ring, and a row of overdue/high-priority/due-today stats below. Each
 * screen still computes its own counts (this widget only renders, matching how ProgressRing/
 * AssigneeStack are already used as pure presentational widgets elsewhere). */
@Composable
fun EntityHeroSection(
    accentColor: Color,
    progress: Float,
    primaryText: String,
    overdueCount: Int,
    highPriorityCount: Int,
    dueTodayCount: Int,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    nameText: String? = null,
    secondaryContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingExtra: (@Composable () -> Unit)? = null,
    ringSize: Dp = 56.dp,
    ringStrokeWidth: Dp = 5.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(accentColor.copy(alpha = 0.16f))
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingContent()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (nameText != null) {
                    Text(
                        text = nameText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                secondaryContent?.invoke(this)
            }
            if (trailingExtra != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingExtra()
            }
            Spacer(modifier = Modifier.width(12.dp))
            ProgressRing(
                progress = progress,
                size = ringSize,
                strokeWidth = ringStrokeWidth,
                activeColor = accentColor,
                showLabel = false
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeroStatCell(
                label = "Overdue",
                value = overdueCount,
                valueColor = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            HeroStatCell(label = "High priority", value = highPriorityCount)
            HeroStatCell(label = "Due today", value = dueTodayCount)
        }
    }
}

@Composable
private fun HeroStatCell(
    label: String,
    value: Int,
    valueColor: Color = Color.Unspecified
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
