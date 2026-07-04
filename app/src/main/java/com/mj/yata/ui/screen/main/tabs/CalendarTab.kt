package com.mj.yata.ui.screen.main.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.TaskRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTab(
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    userName: String,
    startOfWeekSunday: Boolean,
    selectedDay: LocalDate,
    onSelectedDayChange: (LocalDate) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    var selectedMonth by remember { mutableStateOf(YearMonth.from(selectedDay)) }

    val tasksByDate = remember(tasks) {
        tasks.filter { it.due != null }.groupBy { it.due!! }
    }

    val weekStart = if (startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val weekdayLabels = remember(weekStart) {
        (0..6).map { offset -> weekStart.plus(offset.toLong()) }
    }

    // Full grid of dates for this month, padded with the trailing days of the previous
    // month so the first cell lines up under the correct weekday column.
    val gridDays = remember(selectedMonth, weekStart) {
        val firstOfMonth = selectedMonth.atDay(1)
        val leadingBlank = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
        val daysInMonth = selectedMonth.lengthOfMonth()
        val gridStart = firstOfMonth.minusDays(leadingBlank.toLong())
        val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
        List(totalCells) { gridStart.plusDays(it.toLong()) }
    }

    val selectedDayTasks = remember(tasksByDate, selectedDay) {
        tasksByDate[selectedDay.toString()] ?: emptyList()
    }
    val selectedDayLabel = remember(selectedDay) {
        selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }
    val monthLabel = remember(selectedMonth) {
        selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(Locale.getDefault())
    }

    val listsById = remember(lists) { lists.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Open drawer", tint = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                )
                IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(modifier = Modifier.clickable { onProfileClick() }) {
                    PersonAvatar(
                        initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                        accentKey = "accentC",
                        size = 32.dp
                    )
                }
            }
        }

        // Weekday header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            weekdayLabels.forEach { day ->
                Text(
                    text = day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Month grid — not scrollable itself (fixed rows for the visible month), the day
        // agenda below it scrolls.
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            userScrollEnabled = false
        ) {
            items(gridDays) { day ->
                val inMonth = YearMonth.from(day) == selectedMonth
                val isSelected = day == selectedDay
                val isToday = day == today
                val hasTasks = tasksByDate.containsKey(day.toString())

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(2.dp)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelectedDayChange(day) }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .background(
                                color = when {
                                    !hasTasks -> Color.Transparent
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = selectedDayLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (selectedDayTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks scheduled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                items(selectedDayTasks, key = { it.id }) { task ->
                    val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                    val taskAssignees = remember(task.assigneeIds, peopleById) {
                        task.assigneeIds.mapNotNull { pid -> peopleById[pid] }
                    }
                    val taskTags = remember(task, projects, tags) { task.effectiveTags(projects, tags) }

                    TaskRow(
                        task = task,
                        list = taskList,
                        assignees = taskAssignees,
                        tags = taskTags,
                        onToggleDone = { onToggleDone(task.id) },
                        onTaskClick = { onTaskClick(task.id) }
                    )
                }
            }
        }
    }
}
