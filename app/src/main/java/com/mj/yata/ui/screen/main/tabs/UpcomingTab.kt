package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
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

/** yataSlideUp equivalent: 260ms emphasized-decelerate slide + fade, per handoff. */
private const val SLIDE_UP_MS = 260

/** Month-to-month slide. Slightly longer than [SLIDE_UP_MS] because it moves the whole grid and
 * resizes the card with it — at 260ms a six-row-to-four-row change reads as a snap rather than a
 * settle. Not tokenised in [YataDur] since it's specific to this one transition. */
private const val MONTH_SLIDE_MS = 320

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UpcomingTab(
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    userName: String,
    userPhotoUri: String? = null,
    selectedDay: LocalDate,
    onSelectedDayChange: (LocalDate) -> Unit,
    startOfWeekSunday: Boolean = true,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onQuickSnooze: (String, QuickSnoozePreset) -> Unit = { _, _ -> },
    onSwipeToDelete: (String) -> Unit = {},
    onBulkComplete: (List<String>) -> Unit = {},
    onBulkDelete: (List<String>) -> Unit = {},
    onBulkAddTag: (List<String>, String) -> Unit = { _, _ -> },
    onBulkSetProject: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkSetList: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkDuplicate: (List<String>) -> Unit = {},
    onBulkAssignPerson: (List<String>, String) -> Unit = { _, _ -> },
    onBulkReschedule: (List<String>, QuickSnoozePreset) -> Unit = { _, _ -> },
    onRenameTask: (String, String) -> Unit = { _, _ -> },
    onAddComment: (taskId: String, body: String) -> Unit = { _, _ -> },
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    taskRowDensity: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkAssignSheet by remember { mutableStateOf(false) }
    var showBulkRescheduleSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }

    var viewMode by remember { mutableStateOf(ScheduleViewMode.WEEK) }
    val today = com.mj.yata.util.AppClock.today
    val reduceMotion = com.mj.yata.ui.theme.LocalReduceMotion.current

    // Week mode: 7 days starting from today (not a fixed calendar week)
    val days = remember(today) {
        List(7) { today.plusDays(it.toLong()) }
    }

    // Month mode: full month grid, padded to line up under the correct weekday column
    var selectedMonth by remember(selectedDay) { mutableStateOf(YearMonth.from(selectedDay)) }
    val weekStart = if (startOfWeekSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val weekdayLabels = remember(weekStart) { (0..6).map { offset -> weekStart.plus(offset.toLong()) } }
    // Takes the month as a parameter rather than closing over selectedMonth, because the grid is
    // animated with AnimatedContent — during a transition both the outgoing and incoming months
    // are composed at once, and each needs its own days.
    fun gridDaysFor(month: YearMonth): List<LocalDate> {
        val firstOfMonth = month.atDay(1)
        val leadingBlank = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val gridStart = firstOfMonth.minusDays(leadingBlank.toLong())
        val totalCells = ((leadingBlank + daysInMonth + 6) / 7) * 7
        return List(totalCells) { gridStart.plusDays(it.toLong()) }
    }

    val listsById = remember(lists) { lists.associateBy { it.id } }
    val archivedProjectIds = remember(projects) { projects.archivedProjects().map { it.id }.toSet() }
    val activeTasks = remember(tasks, archivedProjectIds) {
        tasks.filter { it.projectId !in archivedProjectIds }
    }
    val tasksByDate = remember(activeTasks) { activeTasks.filter { it.due != null }.groupBy { it.due!! } }

    // State for filter chips
    var selectedFilter by remember { mutableStateOf("All") } // "All" | "Assigned to me" | "Delegated" | "High Priority"
    LaunchedEffect(peopleEnabled) {
        if (!peopleEnabled && selectedFilter != "All" && selectedFilter != "High Priority") selectedFilter = "All"
    }
    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }
    fun applyFilter(list: List<Task>): List<Task> = when (selectedFilter) {
        "Assigned to me" -> list.filter { it.assigneeIds.contains(myId) }
        "Delegated" -> list.filter { it.assigneeIds.isNotEmpty() && !it.assigneeIds.contains(myId) }
        "High Priority" -> list.filter { it.priority == "high" }
        else -> list
    }
    val accents = LocalYataAccents.current
    val defaultDotColor = MaterialTheme.colorScheme.primary
    fun dotColorsForDate(date: LocalDate): List<Color> =
        tasksByDate[date.toString()].orEmpty().take(3).map { t ->
            listsById[t.listId]?.let { accents.getAccent(it.color) } ?: defaultDotColor
        }

    val monthLabel = remember(selectedDay, selectedMonth, viewMode) {
        val base = if (viewMode == ScheduleViewMode.MONTH) selectedMonth.atDay(1) else selectedDay
        base.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(Locale.getDefault())
    }

    val selectedDayTasks = remember(tasksByDate, selectedDay, selectedFilter, myId) {
        applyFilter(tasksByDate[selectedDay.toString()].orEmpty())
    }

    val isSelectedToday = selectedDay == today
    val agendaLabel = remember(selectedDay) {
        selectedDay.format(com.mj.yata.util.AppFormats.headerDateFormatter())
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
                onReschedule = { showBulkRescheduleSheet = true },
                onDuplicate = { onBulkDuplicate(selectedIds.toList()); selectedIds.clear() },
                onDelete = { showBulkDeleteDialog = true },
                onAssign = { showBulkAssignSheet = true },
                tagsEnabled = tagsEnabled,
                peopleEnabled = peopleEnabled,
                modifier = Modifier.statusBarsPadding()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                com.mj.yata.ui.widgets.YataTopBarIconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.cd_open_drawer)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    if (viewMode == ScheduleViewMode.MONTH) {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.upcoming_previous_month),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.9.sp,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (viewMode == ScheduleViewMode.MONTH) {
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.upcoming_next_month),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                    val profileLabel = stringResource(R.string.cd_open_profile)
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = profileLabel }
                    ) {
                        PersonAvatar(
                            initials = com.mj.yata.util.initialsFor(userName),
                            accentKey = "accentC",
                            size = 40.dp,
                            photoUri = userPhotoUri
                        )
                    }
                }
            }
        }

        // 2. Upcoming / Calendar segmented control
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            SegmentedControl(
                items = listOf(ScheduleViewMode.WEEK, ScheduleViewMode.MONTH),
                selectedItem = viewMode,
                onItemSelected = { viewMode = it },
                labelProvider = { if (it == ScheduleViewMode.WEEK) "Upcoming" else "Calendar" }
            )
        }

        // 3. Day strip (Upcoming) or month grid (Calendar) — both slide up on mode switch
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                val enter = if (reduceMotion) {
                    fadeIn(tween(SLIDE_UP_MS, easing = YataEase.emphDecel))
                } else {
                    slideInVertically(animationSpec = tween(SLIDE_UP_MS, easing = YataEase.emphDecel)) { it / 6 } +
                        fadeIn(tween(SLIDE_UP_MS, easing = YataEase.emphDecel))
                }
                enter.togetherWith(fadeOut(tween(YataDur.fade)))
            },
            label = "scheduleMode"
        ) { mode ->
            when (mode) {
                ScheduleViewMode.WEEK -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { day ->
                        DayPill(
                            day = day,
                            selected = day == selectedDay,
                            isToday = day == today,
                            dotColors = dotColorsForDate(day),
                            onClick = { onSelectedDayChange(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ScheduleViewMode.MONTH -> Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            weekdayLabels.forEach { day ->
                                val isTodColumn = day == today.dayOfWeek
                                Text(
                                    text = day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isTodColumn) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isTodColumn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // One transition for the whole grid, rather than 42 independent ones.
                        // Each cell used to reset its own `visible` flag on a month change and
                        // re-enter on a staggered delay, so switching months tore the entire
                        // calendar down and rebuilt it cell by cell — the grid visibly emptied
                        // before it refilled, and every cell's key changed at the same time, so
                        // the LazyVerticalGrid disposed and recreated all of them underneath that.
                        // Sliding the grid as a single unit in the direction of travel is both
                        // cheaper and reads as the month moving rather than the calendar
                        // reassembling itself.
                        AnimatedContent(
                            targetState = selectedMonth,
                            transitionSpec = {
                                val forward = targetState > initialState
                                val enter = if (reduceMotion) {
                                    fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel))
                                } else {
                                    // A quarter-width offset, not a full one: the grid stays
                                    // partly in place so the eye tracks it instead of re-reading
                                    // a panel that flew in from off-screen.
                                    slideInHorizontally(
                                        animationSpec = tween(MONTH_SLIDE_MS, easing = YataEase.emphDecel)
                                    ) { width -> if (forward) width / 4 else -width / 4 } +
                                        fadeIn(tween(MONTH_SLIDE_MS, easing = YataEase.emphDecel))
                                }
                                val exit = if (reduceMotion) {
                                    fadeOut(tween(YataDur.fade))
                                } else {
                                    slideOutHorizontally(
                                        animationSpec = tween(MONTH_SLIDE_MS, easing = YataEase.emphAccel)
                                    ) { width -> if (forward) -width / 4 else width / 4 } +
                                        fadeOut(tween(MONTH_SLIDE_MS / 2))
                                }
                                // Months are 4, 5 or 6 rows tall, so without this the card's
                                // height jumps the instant the new month is measured. clip = false
                                // keeps the outgoing grid from being cut off mid-slide.
                                enter.togetherWith(exit).using(
                                    SizeTransform(clip = false) { _, _ ->
                                        tween(MONTH_SLIDE_MS, easing = YataEase.emphasized)
                                    }
                                )
                            },
                            label = "monthGrid"
                        ) { month ->
                            // Plain rows rather than a LazyVerticalGrid: at most 42 non-scrolling
                            // cells, so laziness bought nothing, and a lazy container inside an
                            // animating one measures awkwardly during the transition.
                            Column(modifier = Modifier.fillMaxWidth()) {
                                gridDaysFor(month).chunked(7).forEach { week ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        week.forEach { day ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                MonthDayCell(
                                                    day = day,
                                                    selected = day == selectedDay,
                                                    isToday = day == today,
                                                    inMonth = YearMonth.from(day) == month,
                                                    dotColors = dotColorsForDate(day),
                                                    onClick = { onSelectedDayChange(day) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 3.5 Filter chips — same set as Today, scoped to the agenda for the selected day
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOfNotNull("All", if (peopleEnabled) "Assigned to me" else null, if (peopleEnabled) "Delegated" else null, "High Priority").forEach { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(text = filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }

        val peopleById = remember(people) { people.associateBy { it.id } }

        // 4. Agenda — header + task list slide/fade together, keyed on the selected day
        AnimatedContent(
            targetState = selectedDay,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(220, easing = YataEase.emphDecel)) { it / 8 } +
                    fadeIn(tween(220, easing = YataEase.emphDecel)))
                    .togetherWith(fadeOut(tween(YataDur.fade)))
            },
            modifier = Modifier.weight(1f),
            label = "agenda"
        ) { day ->
            val dayTasks = if (day == selectedDay) selectedDayTasks else applyFilter(tasksByDate[day.toString()].orEmpty())
            val label = if (day == today) "Today" else agendaLabel

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "${dayTasks.size} ${if (dayTasks.size == 1) "task" else "tasks"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (day != today) {
                        AssistChip(
                            onClick = { onSelectedDayChange(today) },
                            label = { Text(stringResource(R.string.shortcut_today_short), style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (dayTasks.isEmpty()) {
                        item { UpcomingEmptyState() }
                    } else {
                        items(dayTasks, key = { it.id }, contentType = { "task" }) { task ->
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
                                onCommentClick = { pendingCommentTask = task },
                                onQuickSnooze = { preset -> onQuickSnooze(task.id, preset) },
                                onRenameTask = { title -> onRenameTask(task.id, title) },
                                density = taskRowDensity,
                                onSwipeToDelete = { onSwipeToDelete(task.id) },
                                swipeEnabled = !selectionMode,
                                modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                            )
                        }
                    }
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

    if (showBulkAssignSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkAssignSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkAssignPersonSheet(
                people = people,
                tasks = tasks,
                todayStr = com.mj.yata.util.AppClock.todayString,
                onSelectPerson = { personId ->
                    onBulkAssignPerson(selectedIds.toList(), personId)
                    selectedIds.clear()
                    showBulkAssignSheet = false
                },
                onDismiss = { showBulkAssignSheet = false }
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

    if (showBulkRescheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkRescheduleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkRescheduleSheet(
                onSelectPreset = { preset ->
                    onBulkReschedule(selectedIds.toList(), preset)
                    selectedIds.clear()
                    showBulkRescheduleSheet = false
                },
                onDismiss = { showBulkRescheduleSheet = false }
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.confirm_delete_tasks_title, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.action_this_can_t_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDelete(selectedIds.toList())
                    selectedIds.clear()
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingCommentTask?.let { task ->
        com.mj.yata.ui.widgets.QuickCommentDialog(
            taskTitle = task.title,
            onSubmit = { body ->
                onAddComment(task.id, body)
                pendingCommentTask = null
            },
            onDismiss = { pendingCommentTask = null }
        )
    }
}

/** 7-day agenda strip pill: selected = filled primary + scale + shadow, today = primaryContainer tint. */
@Composable
private fun DayPill(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    dotColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(YataDur.fade),
        label = "dayPillBg"
    )
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = tween(YataDur.micro, easing = YataEase.spring),
        label = "dayPillScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .padding(horizontal = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (selected) 3f else 0f
                shape = RoundedCornerShape(20.dp)
                clip = false
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Text(
            text = day.format(DateTimeFormatter.ofPattern("E")).uppercase(),
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
        Text(
            text = day.dayOfMonth.toString(),
            color = fg,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        )
        DayDots(dotColors = dotColors, overrideColor = if (selected) MaterialTheme.colorScheme.onPrimary else null)
    }
}

/** Fixed 38dp squircle month-grid cell. */
@Composable
private fun MonthDayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    dotColors: List<Color>,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(YataDur.micro, easing = YataEase.spring),
        label = "monthCellScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = if (selected) 3f else 0f
                        shape = RoundedCornerShape(16.dp)
                        clip = false
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp
                    ),
                    color = fg
                )
            }
            DayDots(dotColors = dotColors, overrideColor = if (selected) MaterialTheme.colorScheme.onPrimary else null)
        }
    }
}

/** Up to 3 dots colored by each task's list accent — a quick read of what's on a day. */
@Composable
private fun DayDots(dotColors: List<Color>, overrideColor: Color?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (dotColors.isEmpty()) {
            Spacer(modifier = Modifier.size(4.dp))
        } else {
            dotColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(overrideColor ?: color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun UpcomingEmptyState(modifier: Modifier = Modifier) {
    com.mj.yata.ui.widgets.TabEmptyState(
        icon = Icons.Outlined.EventAvailable,
        title = stringResource(R.string.upcoming_clear_day),
        subtitle = stringResource(R.string.upcoming_nothing_scheduled),
        modifier = modifier
    )
}
