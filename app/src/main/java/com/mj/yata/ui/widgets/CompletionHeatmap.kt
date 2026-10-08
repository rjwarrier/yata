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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.util.AppFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val MIN_CELL = 12.dp
private val MAX_CELL = 18.dp
private val GAP = 3.dp
private const val MAX_WEEKS = 26

/**
 * GitHub-style grid of the days a recurring series was completed: one column per week, one row
 * per weekday, oldest week on the left, today's week on the right, with today ringed. Fits as many
 * whole weeks as the width allows (capped at [MAX_WEEKS]) — about five months on a phone — then
 * stretches the cells to fill the row rather than leaving a ragged gap at the end. The summary
 * line above it carries the same information as text, so the grid itself is decorative.
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
    val todayRing = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        val weeks = ((maxWidth + GAP) / (MIN_CELL + GAP)).toInt().coerceIn(1, MAX_WEEKS)
        val cellSize = ((maxWidth - GAP * (weeks - 1)) / weeks).coerceIn(MIN_CELL, MAX_CELL)
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
            Canvas(Modifier.fillMaxWidth().height(cellSize * 7 + GAP * 6)) {
                val cell = cellSize.toPx()
                val step = cell + GAP.toPx()
                val radius = CornerRadius(3.dp.toPx())
                val ringWidth = 1.5.dp.toPx()
                for (week in 0 until weeks) {
                    for (dayIndex in 0 until 7) {
                        val day = start.plusDays(week * 7L + dayIndex)
                        if (day.isAfter(today)) continue
                        val topLeft = Offset(week * step, dayIndex * step)
                        drawRoundRect(
                            color = if (day in completedDays) filled else empty,
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = radius
                        )
                        if (day == today) {
                            drawRoundRect(
                                color = todayRing,
                                topLeft = topLeft,
                                size = Size(cell, cell),
                                cornerRadius = radius,
                                style = Stroke(width = ringWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}
