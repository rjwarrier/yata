package com.mj.yata.ui.screen.holidaycalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Holiday
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.util.rememberAdaptiveSheetMaxWidth
import com.mj.yata.ui.widgets.TaskSectionHeader
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.util.TaskScheduleUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** For a recurring holiday the stored [Holiday.date] is just an arbitrary anchor year — showing
 * it (via [TaskScheduleUtils.formatDueDate], which is relative to *today*'s year) would print a
 * year for some entries and not others depending on when each happened to be added, with no
 * meaning to the user either way. Recurring entries always show month + day only; one-time entries
 * show the real date, since for those the year is the whole point. */
private fun holidayDateLabel(holiday: Holiday, locale: Locale): String {
    if (!holiday.recurring) return TaskScheduleUtils.formatDueDate(holiday.date)
    val date = LocalDate.parse(holiday.date)
    return date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
}

/** Tap-a-date calendar for managing [Holiday]s, reached from Settings → Task Defaults →
 * "Holidays" — also hosts the weekend-day picker (moved here from Settings directly, since both
 * configure what counts as a non-working day and share the same reschedule warning). Replaces
 * typing an ISO date: tapping a marked day reopens it for editing, tapping a blank one opens the
 * same sheet to add one. The list below the grid, split into "Yearly"/"One-time" sections, stays
 * the primary way to audit/remove holidays in bulk — a recurring holiday's grid marker repeats
 * every month-day match but its stored anchor date (and so its position in a flat, date-sorted
 * list) doesn't, which is also why the two kinds get separate sections instead of one interleaved
 * list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayCalendarScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val holidaysRaw by viewModel.holidays.collectAsStateWithLifecycle()
    val startOfWeekSunday by viewModel.startOfWeekSunday.collectAsStateWithLifecycle()
    val weekendDays by viewModel.weekendDays.collectAsStateWithLifecycle()
    val observeNonWorkingDays by viewModel.observeNonWorkingDays.collectAsStateWithLifecycle()
    val holidayList = remember(holidaysRaw) {
        holidaysRaw.mapNotNull(Holiday::decode).sortedBy { it.date.takeLast(5) }
    }
    val holidayLookup = remember(holidayList) { Holiday.index(holidayList) }

    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var sheetTargetDate by remember { mutableStateOf<LocalDate?>(null) }

    val weekStart = if (startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    val gridDays = remember(visibleMonth, weekStart) {
        val firstOfMonth = visibleMonth.atDay(1)
        val leadingBlank = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
        val daysInMonth = visibleMonth.lengthOfMonth()
        val gridStart = firstOfMonth.minusDays(leadingBlank.toLong())
        val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
        List(totalCells) { gridStart.plusDays(it.toLong()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_holidays)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_holidays_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_observe_non_working_days),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = stringResource(R.string.settings_observe_non_working_days_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = observeNonWorkingDays,
                        onCheckedChange = { viewModel.setObserveNonWorkingDays(it) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_weekend_days),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_weekend_days_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val orderedDays = listOf(
                            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
                            DayOfWeek.SUNDAY
                        )
                        val codeFor = mapOf(
                            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU",
                            DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
                            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
                            DayOfWeek.SUNDAY to "SU"
                        )
                        orderedDays.forEach { day ->
                            val code = codeFor.getValue(day)
                            val isSelected = code in weekendDays
                            val fullLabel = day.getDisplayName(TextStyle.FULL, locale)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        val updated = if (isSelected) weekendDays - code else weekendDays + code
                                        viewModel.setWeekendDays(updated)
                                    }
                                    .semantics { contentDescription = fullLabel },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.getDisplayName(TextStyle.NARROW, locale),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                            val holiday = holidayLookup(date.toString())
                            HolidayDayCell(
                                date = date,
                                inCurrentMonth = YearMonth.from(date) == visibleMonth,
                                isToday = date == today,
                                holiday = holiday,
                                onClick = { sheetTargetDate = date },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (holidayList.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    val (recurring, oneTime) = remember(holidayList) { holidayList.partition { it.recurring } }
                    if (recurring.isNotEmpty()) {
                        Column {
                            TaskSectionHeader(
                                title = stringResource(R.string.holiday_recurring_badge),
                                count = recurring.size,
                                horizontalPadding = 0.dp
                            )
                            recurring.forEach { holiday ->
                                HolidayListRow(
                                    holiday = holiday,
                                    dateLabel = holidayDateLabel(holiday, locale),
                                    onClick = { sheetTargetDate = LocalDate.parse(holiday.date) },
                                    onDelete = { viewModel.removeHoliday(holiday.encode()) }
                                )
                            }
                        }
                    }
                    if (oneTime.isNotEmpty()) {
                        Column {
                            TaskSectionHeader(
                                title = stringResource(R.string.holiday_one_time_section),
                                count = oneTime.size,
                                horizontalPadding = 0.dp
                            )
                            oneTime.forEach { holiday ->
                                HolidayListRow(
                                    holiday = holiday,
                                    dateLabel = holidayDateLabel(holiday, locale),
                                    onClick = { sheetTargetDate = LocalDate.parse(holiday.date) },
                                    onDelete = { viewModel.removeHoliday(holiday.encode()) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    sheetTargetDate?.let { date ->
        val existing = holidayLookup(date.toString())
        HolidayEditSheet(
            date = date,
            existing = existing,
            onDismiss = { sheetTargetDate = null },
            onSave = { label, recurring ->
                viewModel.addHoliday(existing?.date ?: date.toString(), label, recurring)
                sheetTargetDate = null
            },
            onDelete = {
                existing?.let { viewModel.removeHoliday(it.encode()) }
                sheetTargetDate = null
            }
        )
    }
}

@Composable
private fun HolidayListRow(
    holiday: Holiday,
    dateLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = holiday.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_holiday, holiday.label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun HolidayDayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    holiday: Holiday?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasHoliday = holiday != null
    val textColor = when {
        !inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        hasHoliday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (hasHoliday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(
                if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || hasHoliday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (hasHoliday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (holiday?.recurring == true) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayEditSheet(
    date: LocalDate,
    existing: Holiday?,
    onDismiss: () -> Unit,
    onSave: (label: String, recurring: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var recurring by remember(existing) { mutableStateOf(existing?.recurring ?: false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = rememberAdaptiveSheetMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = TaskScheduleUtils.formatDueDate(date.toString()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            TextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_holiday_name_label)) },
                shape = YataCompactFieldShape,
                colors = yataFieldColors()
            )
            FilterChip(
                selected = recurring,
                onClick = { recurring = !recurring },
                label = { Text(stringResource(R.string.settings_holiday_recurring_label)) },
                leadingIcon = if (recurring) {
                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existing != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cd_delete))
                    }
                }
                Button(
                    onClick = { onSave(label.trim(), recurring) },
                    enabled = label.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
