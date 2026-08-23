package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.isWeekendDate
import com.mj.yata.ui.screen.main.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Everything [DueDateCalendarDialog] needs to grey out and warn about non-working days — bundled
 * into one value rather than three separate parameters, since [com.mj.yata.ui.sheets.NewTaskSheet]
 * already sits close to a parameter-count limit that has caused real build failures before (see
 * its own doc comment); one added parameter here instead of three matters for that budget. */
data class DueDatePickerContext(
    val weekendDays: Set<String> = emptySet(),
    val holidays: List<Holiday> = emptyList(),
    val startOfWeekSunday: Boolean = true
) {
    companion object {
        val None = DueDatePickerContext()
    }
}

/** Builds a [DueDatePickerContext] from [MainViewModel]'s state, so every [NewTaskSheet]/
 * [TaskDetailScreen] call site can pass one without repeating three separate `collectAsState`
 * calls each. */
@Composable
fun rememberDueDatePickerContext(viewModel: MainViewModel): DueDatePickerContext {
    val weekendDays by viewModel.weekendDays.collectAsStateWithLifecycle()
    val holidaysRaw by viewModel.holidays.collectAsStateWithLifecycle()
    val startOfWeekSunday by viewModel.startOfWeekSunday.collectAsStateWithLifecycle()
    return remember(weekendDays, holidaysRaw, startOfWeekSunday) {
        DueDatePickerContext(
            weekendDays = weekendDays,
            holidays = holidaysRaw.mapNotNull(Holiday::decode),
            startOfWeekSunday = startOfWeekSunday
        )
    }
}

/** Holiday lookup is O(1) via [Holiday.index] rather than scanning the list per grid cell — this
 * dialog can re-render its whole visible month (up to 42 cells) on every recomposition. */
private fun nonWorkingDayLabel(
    date: LocalDate,
    holidayLookup: (String) -> Holiday?,
    weekendDays: Set<String>,
    locale: Locale
): String? {
    holidayLookup(date.toString())?.let { return it.label }
    if (isWeekendDate(date, weekendDays)) {
        return date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }
    return null
}

/** Custom day-grid date picker used for a task's due date, in place of the stock Material3
 * [YataDatePickerDialog] — M3's `DatePicker` has no public API to style individual days
 * differently (only a single uniform `dayContentColor` and enable/disable via `SelectableDates`),
 * so greying out holidays/weekends while keeping them tappable needs a grid built by hand. Tapping
 * a non-working day warns immediately, before the date is applied, rather than after the task is
 * saved — the caller only ever sees [onConfirm] for a date the user has already chosen to use. */
@Composable
fun DueDateCalendarDialog(
    initialDate: String?,
    context: DueDatePickerContext,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val locale = Locale.getDefault()
    val today = remember { LocalDate.now() }
    val selectedDate = remember(initialDate) { initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selectedDate ?: today)) }
    // Holds both the date and its already-resolved warning label together, rather than just the
    // date and re-deriving the label again when rendering the AlertDialog below — recomputing
    // from `context` a second time would be redundant work and, if `context` ever changed between
    // the tap and the render (e.g. holidays reloading mid-composition), could silently disagree
    // with the label the user actually saw when they tapped.
    var pendingWarning by remember { mutableStateOf<Pair<LocalDate, String>?>(null) }

    val weekStart = if (context.startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val holidayLookup = remember(context.holidays) { Holiday.index(context.holidays) }
    val gridDays = remember(visibleMonth, weekStart) {
        val firstOfMonth = visibleMonth.atDay(1)
        val leadingBlank = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
        val daysInMonth = visibleMonth.lengthOfMonth()
        val gridStart = firstOfMonth.minusDays(leadingBlank.toLong())
        val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
        List(totalCells) { gridStart.plusDays(it.toLong()) }
    }

    fun attemptSelect(date: LocalDate) {
        val label = nonWorkingDayLabel(date, holidayLookup, context.weekendDays, locale)
        if (label != null) {
            pendingWarning = date to label
        } else {
            onConfirm(date.toString())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.upcoming_previous_month))
                    }
                    Text(
                        text = "${visibleMonth.month.getDisplayName(TextStyle.FULL, locale)} ${visibleMonth.year}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.upcoming_next_month))
                    }
                }

                val weekdayLabels = remember(weekStart, locale) {
                    (0..6).map { offset -> weekStart.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale) }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayLabels.forEach { label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                gridDays.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DueDateDayCell(
                                date = date,
                                inCurrentMonth = YearMonth.from(date) == visibleMonth,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                isNonWorkingDay = nonWorkingDayLabel(date, holidayLookup, context.weekendDays, locale) != null,
                                onClick = { attemptSelect(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }

    pendingWarning?.let { (date, label) ->
        AlertDialog(
            onDismissRequest = { pendingWarning = null },
            text = { Text(stringResource(R.string.date_picker_nonworking_day_warning, label)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingWarning = null
                    onConfirm(date.toString())
                }) {
                    Text(stringResource(R.string.date_picker_use_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWarning = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DueDateDayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isNonWorkingDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isNonWorkingDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        isNonWorkingDay -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}
