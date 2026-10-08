package com.mj.yata.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.util.AppFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val CELL = 12.dp
private val GAP = 3.dp
private const val MAX_WEEKS = 26

/**
 * GitHub-style grid of the days a recurring series was completed: one column per week, one row
 * per weekday, oldest week on the left, today's week on the right. Fits as many whole weeks as
 * the width allows (capped at [MAX_WEEKS]) — about four months on a phone. The summary line
 * above it carries the same information as text, so the grid itself is decorative.
 */
@Composable
fun CompletionHeatmap(
    completedDays: Set<LocalDate>,
    today: LocalDate,
    startOfWeekSunday: Boolean,
    modifier: Modifier = Modifier
) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        val weeks = ((maxWidth + GAP) / (CELL + GAP)).toInt().coerceIn(1, MAX_WEEKS)
        val firstDayOfWeek = if (startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(weeks - 1L)
        val count = completedDays.count { !it.isBefore(start) && !it.isAfter(today) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = pluralStringResource(
                    R.plurals.task_detail_heatmap_summary, count, count, start.format(AppFormats.dayMonthFormatter())
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(Modifier.fillMaxWidth().height(CELL * 7 + GAP * 6)) {
                val cell = CELL.toPx()
                val step = cell + GAP.toPx()
                val radius = CornerRadius(3.dp.toPx())
                for (week in 0 until weeks) {
                    for (dayIndex in 0 until 7) {
                        val day = start.plusDays(week * 7L + dayIndex)
                        if (day.isAfter(today)) continue
                        drawRoundRect(
                            color = if (day in completedDays) filled else empty,
                            topLeft = Offset(week * step, dayIndex * step),
                            size = Size(cell, cell),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }
    }
}
