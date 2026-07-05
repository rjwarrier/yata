package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.TaskRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ScheduleViewMode { WEEK, MONTH }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UpcomingTab(
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    userName: String,
    selectedDay: LocalDate,
    onSelectedDayChange: (LocalDate) -> Unit,
    startOfWeekSunday: Boolean = true,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onBulkComplete: (List<String>) -> Unit = {},
    onBulkDelete: (List<String>) -> Unit = {},
    onBulkAddTag: (List<String>, String) -> Unit = { _, _ -> },
    onBulkSetProject: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkSetList: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkDuplicate: (List<String>) -> Unit = {},
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    var viewMode by remember { mutableStateOf(ScheduleViewMode.WEEK) }
    val today = remember { LocalDate.now() }

    // Week mode: 7 days starting from today
    val days = remember(today) {
        List(7) { today.plusDays(it.toLong()) }
    }

    // Month mode: full month grid, padded to line up under the correct weekday column
    var selectedMonth by remember(selectedDay) { mutableStateOf(YearMonth.from(selectedDay)) }
    val weekStart = if (startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val weekdayLabels = remember(weekStart) { (0..6).map { offset -> weekStart.plus(offset.toLong()) } }
    val gridDays = remember(selectedMonth, weekStart) {
        val firstOfMonth = selectedMonth.atDay(1)
        val leadingBlank = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
        val daysInMonth = selectedMonth.lengthOfMonth()
        val gridStart = firstOfMonth.minusDays(leadingBlank.toLong())
        val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
        List(totalCells) { gridStart.plusDays(it.toLong()) }
    }
    val tasksByDate = remember(tasks) { tasks.filter { it.due != null }.groupBy { it.due!! } }

    val monthLabel = remember(selectedDay, selectedMonth, viewMode) {
        val base = if (viewMode == ScheduleViewMode.MONTH) selectedMonth.atDay(1) else selectedDay
        base.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(Locale.getDefault())
    }

    val selectedDayTasks = remember(tasks, selectedDay) {
        val dateStr = selectedDay.toString()
        tasks.filter { it.due == dateStr }
    }

    val selectedDayLabel = remember(selectedDay) {
        selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar — swaps to a selection bar once tasks are selected.
        if (selectionMode) {
            com.mj.yata.ui.sheets.TaskSelectionTopBar(
                selectedCount = selectedIds.size,
                onCancel = { selectedIds.clear() },
                onComplete = { onBulkComplete(selectedIds.toList()); selectedIds.clear() },
                onAddTag = { showBulkTagSheet = true },
                onMove = { showBulkMoveSheet = true },
                onDuplicate = { onBulkDuplicate(selectedIds.toList()); selectedIds.clear() },
                onDelete = { showBulkDeleteDialog = true },
                tagsEnabled = tagsEnabled,
                modifier = Modifier.statusBarsPadding()
            )
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (viewMode == ScheduleViewMode.MONTH) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    )
                    IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            } else {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Week / Month toggle
        SegmentedControl(
            items = listOf(ScheduleViewMode.WEEK, ScheduleViewMode.MONTH),
            selectedItem = viewMode,
            onItemSelected = { viewMode = it },
            labelProvider = { if (it == ScheduleViewMode.WEEK) "Upcoming" else "Calendar" },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (viewMode == ScheduleViewMode.WEEK) {
            // 2a. 7-Day Agenda Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    val isSelected = day == selectedDay
                    val hasTasks = tasks.any { it.due == day.toString() }

                    val dayName = day.format(DateTimeFormatter.ofPattern("E")).uppercase()
                    val dayOfMonth = day.dayOfMonth.toString()

                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                        label = "dayPillColor"
                    )
                    val onPillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                        label = "dayPillLabelColor"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillColor)
                            .clickable { onSelectedDayChange(day) }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = dayName,
                            color = onPillColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = dayOfMonth,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )

                        // Dot indicating tasks
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = when {
                                        isSelected && hasTasks -> MaterialTheme.colorScheme.onPrimary
                                        hasTasks -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        } else {
            // 2b. Month grid
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                weekdayLabels.forEach { day ->
                    Text(
                        text = day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Agenda Header
        Text(
            text = selectedDayLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        val listsById = remember(lists) { lists.associateBy { it.id } }
        val peopleById = remember(people) { people.associateBy { it.id } }

        // 4. Task list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (selectedDayTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
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
                    val taskAssignees = remember(task.assigneeIds, peopleById, peopleEnabled) {
                        if (peopleEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                    }
                    val taskTags = remember(task, projects, tags, tagsEnabled) {
                        if (tagsEnabled) task.effectiveTags(projects, tags) else emptyList()
                    }

                    TaskRow(
                        task = task,
                        list = taskList,
                        assignees = taskAssignees,
                        tags = taskTags,
                        onToggleDone = { onToggleDone(task.id) },
                        onTaskClick = {
                            if (selectionMode) {
                                if (selectedIds.contains(task.id)) selectedIds.remove(task.id) else selectedIds.add(task.id)
                            } else {
                                onTaskClick(task.id)
                            }
                        },
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(task.id),
                        onLongClick = { if (!selectedIds.contains(task.id)) selectedIds.add(task.id) },
                        modifier = Modifier.animateItemPlacement(tween(YataDur.sheet, easing = YataEase.emphasized))
                    )
                }
            }
        }
    }

    if (showBulkTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkTagPickerSheet(
                tags = tags,
                onSelectTag = { tagId ->
                    onBulkAddTag(selectedIds.toList(), tagId)
                    selectedIds.clear()
                    showBulkTagSheet = false
                },
                onDismiss = { showBulkTagSheet = false }
            )
        }
    }

    if (showBulkMoveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkMoveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkMoveSheet(
                projects = projects,
                lists = lists,
                onSelectProject = { projectId ->
                    onBulkSetProject(selectedIds.toList(), projectId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onSelectList = { listId ->
                    onBulkSetList(selectedIds.toList(), listId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onDismiss = { showBulkMoveSheet = false },
                projectsEnabled = projectsEnabled
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} tasks?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDelete(selectedIds.toList())
                    selectedIds.clear()
                    showBulkDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
