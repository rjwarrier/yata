package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.TaskSectionHeader
import com.mj.yata.util.sortedByMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodayTab(
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    userName: String,
    userPhotoUri: String? = null,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNextDaysClick: () -> Unit = {},
    onNewTaskClick: () -> Unit = {},
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
    sortMode: com.mj.yata.util.TaskSortMode = com.mj.yata.util.TaskSortMode.MANUAL,
    onSortModeChange: (com.mj.yata.util.TaskSortMode) -> Unit = {},
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    taskRowDensity: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    hideCompleted: Boolean = false,
    onHideCompletedChange: (Boolean) -> Unit = {},
    /** Gates the manual sync button — there is nothing to sync to until cloud backup is set up. */
    cloudSyncEnabled: Boolean = false,
    confettiEnabled: Boolean = true,
    syncing: Boolean = false,
    onSyncClick: () -> Unit = {},
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

    val today = com.mj.yata.util.AppClock.today
    val todayStr = com.mj.yata.util.AppClock.todayString
    val todayDateLabel = remember(today, com.mj.yata.util.AppFormats.dateFormat) {
        today.format(com.mj.yata.util.AppFormats.headerDateFormatter()).uppercase()
    }

    // Projects/lists marked "Exclude from Today" hide their tasks from this screen entirely,
    // even if a task inside ends up overdue — meant for a backlog with no fixed date yet.
    val excludedProjectIds = remember(projects) { projects.hiddenFromMainTaskProjectIds() }
    val excludedListIds = remember(lists) { lists.filter { it.excludeFromToday }.map { it.id }.toSet() }

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }

    // Filter tasks due today (or overdue). Deferred tasks — those whose start date hasn't
    // arrived — drop out here too: they're due (or overdue) but not yet actionable, and the point
    // of a start date is that Today lists only what can be worked on now. They stay visible in
    // their project/list and reappear here on their own, the day the start date arrives. Tasks
    // delegated to someone else with a future "waiting on" follow-up date drop out the same way
    // (see Task.isWaitingOn) — they'll reappear once that date arrives, without a background job.
    val todayTasks = remember(tasks, todayStr, excludedProjectIds, excludedListIds, myId) {
        val nowMillis = System.currentTimeMillis()
        tasks.filter {
            it.due != null && it.due <= todayStr && !it.isDeferredOn(todayStr) && !it.isWaitingOn(nowMillis, myId) &&
                it.projectId !in excludedProjectIds && it.listId !in excludedListIds
        }
    }

    // State for filter chips
    var selectedFilter by remember { mutableStateOf("All") } // "All" | "Assigned to me" | "Delegated" | "High Priority"
    LaunchedEffect(peopleEnabled) {
        if (!peopleEnabled && selectedFilter != "All" && selectedFilter != "High Priority") selectedFilter = "All"
    }

    val filteredTasks = remember(todayTasks, selectedFilter, myId) {
        when (selectedFilter) {
            "Assigned to me" -> todayTasks.filter { it.assigneeIds.contains(myId) }
            "Delegated" -> todayTasks.filter { it.assigneeIds.isNotEmpty() && !it.assigneeIds.contains(myId) }
            "High Priority" -> todayTasks.filter { it.priority == "high" }
            else -> todayTasks
        }
    }

    // Always reflect all of today's tasks here, not the chip-filtered subset below —
    // otherwise picking "High Priority" etc. would skew the ring/"X to go" text. Also scoped to
    // Task.wasPendingAsOf(today) rather than raw todayTasks, so a task done on a previous day
    // doesn't keep inflating the total forever (see that function's doc for why).
    val progressBaseTasks = remember(todayTasks, today) {
        todayTasks.filter { it.wasPendingAsOf(today) }
    }
    val doneCount = progressBaseTasks.count { it.done }
    val totalCount = progressBaseTasks.size
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val remainingCount = totalCount - doneCount

    var activeStatFilter by remember { mutableStateOf<com.mj.yata.ui.widgets.HeroStatKind?>(null) }
    val overdueCount = remember(progressBaseTasks) { com.mj.yata.util.AnalyticsUtils.overdueCount(progressBaseTasks) }
    val highPriorityCount = remember(progressBaseTasks) { progressBaseTasks.count { !it.done && it.priority == "high" } }
    val dueTodayCount = remember(progressBaseTasks, todayStr) { progressBaseTasks.count { !it.done && it.due == todayStr } }

    // Flat list, no more Morning/Afternoon grouping — split into Pending/Completed instead of
    // interleaving them in raw sortOrder. Hiding completed drops both the tasks and the section
    // headers themselves, rather than leaving an empty "Completed" heading around.
    val pendingTasks = remember(filteredTasks, sortMode, activeStatFilter, today) {
        filteredTasks
            .filter { !it.done }
            .filter { activeStatFilter == null || activeStatFilter!!.matches(it, today) }
            .sortedByMode(sortMode)
    }
    val completedTasks = remember(filteredTasks, hideCompleted) {
        if (hideCompleted) emptyList() else filteredTasks.filter { it.done }
    }

    // Fires when today's last open task is ticked off. Keyed on remainingCount rather than the
    // pendingTasks list below it, because that one is narrowed by the stat-filter chips — picking
    // "High priority" and clearing those is not clearing the day.
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var lastRemaining by remember { mutableIntStateOf(-1) }
    var lastDone by remember { mutableIntStateOf(-1) }
    LaunchedEffect(remainingCount, doneCount) {
        val previousRemaining = lastRemaining
        val previousDone = lastDone
        lastRemaining = remainingCount
        lastDone = doneCount
        // previousRemaining > 0 skips the first pass and skips arriving on an already-clear day;
        // doneCount having gone up is what separates finishing the last task from deleting it.
        if (confettiEnabled && previousRemaining > 0 && remainingCount == 0 && doneCount > previousDone) {
            confettiTrigger++
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gets the container too — it's the same class of control in the same bar, and
            // leaving it flat would make the one button on the left look unfinished next to the
            // filled cluster on the right.
            com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer)
                )
            }
            // Icon colours come from IconButtonDefaults via LocalContentColor rather than a
            // hardcoded `tint` on each Icon, so these follow the theme (including the disabled
            // state) instead of being pinned to onSurface in every case.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (cloudSyncEnabled) {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onSyncClick, enabled = !syncing) {
                        if (syncing) {
                            // Replaces the icon in place rather than sitting next to it, so the
                            // row doesn't reflow mid-sync. Sized to the icon, not the button.
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = stringResource(R.string.cd_sync_now)
                            )
                        }
                    }
                }
                com.mj.yata.ui.widgets.TaskSortMenuButton(
                    current = sortMode,
                    onSelect = onSortModeChange,
                    filledContainer = true
                )
                // A toggle, so it gets the M3 toggle component: checked state is carried by the
                // filled container, not by swapping the icon alone. As a plain IconButton the
                // only cue that completed tasks were hidden was an eye icon with a slash through
                // it, which reads as an action ("hide") rather than a state ("hidden").
                com.mj.yata.ui.widgets.YataTopBarIconToggleButton(
                    checked = hideCompleted,
                    onCheckedChange = onHideCompletedChange
                ) {
                    Icon(
                        imageVector = if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (hideCompleted) R.string.today_show_completed else R.string.today_hide_completed
                        )
                    )
                }
                com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onNextDaysClick) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = stringResource(R.string.today_next_10_days)
                    )
                }
                // Search is the last action before the avatar, which keeps the corner.
                com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.today_search_tasks)
                    )
                }
                // Wrapped in an IconButton so the avatar gets the same 48dp touch target and
                // circular ripple as its neighbours — as a bare clickable Box it was a 32dp
                // target with no ripple and nothing for TalkBack to announce.
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

        // 2. Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayDateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (remainingCount == 0 && totalCount > 0) "All caught up!" else "$remainingCount to go",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            ProgressRing(
                progress = progress,
                size = 56.dp,
                strokeWidth = 5.dp
            )
        }

        // 2.5 Overdue/high-priority/due-today stats — tap one to filter Pending in place,
        // matching the same tap-to-filter stat row on the Person/Project/List/Tag hero sections.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            com.mj.yata.ui.widgets.HeroStatCell(
                label = stringResource(R.string.today_stat_overdue),
                value = overdueCount,
                accentColor = MaterialTheme.colorScheme.primary,
                valueColor = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                active = activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.OVERDUE,
                onClick = { activeStatFilter = if (activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.OVERDUE) null else com.mj.yata.ui.widgets.HeroStatKind.OVERDUE },
                modifier = Modifier.weight(1f)
            )
            com.mj.yata.ui.widgets.HeroStatCell(
                label = stringResource(R.string.today_stat_high_priority),
                value = highPriorityCount,
                accentColor = MaterialTheme.colorScheme.primary,
                active = activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.HIGH_PRIORITY,
                onClick = { activeStatFilter = if (activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.HIGH_PRIORITY) null else com.mj.yata.ui.widgets.HeroStatKind.HIGH_PRIORITY },
                modifier = Modifier.weight(1f)
            )
            com.mj.yata.ui.widgets.HeroStatCell(
                label = stringResource(R.string.today_stat_due_today),
                value = dueTodayCount,
                accentColor = MaterialTheme.colorScheme.primary,
                active = activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.DUE_TODAY,
                onClick = { activeStatFilter = if (activeStatFilter == com.mj.yata.ui.widgets.HeroStatKind.DUE_TODAY) null else com.mj.yata.ui.widgets.HeroStatKind.DUE_TODAY },
                modifier = Modifier.weight(1f)
            )
        }

        if (activeStatFilter != null) {
            com.mj.yata.ui.widgets.ActiveFilterBanner(
                kind = activeStatFilter!!,
                onClear = { activeStatFilter = null }
            )
        }

        // 3. Scrollable filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

        Spacer(modifier = Modifier.height(8.dp))

        val listsById = remember(lists) { lists.associateBy { it.id } }
        val peopleById = remember(people) { people.associateBy { it.id } }

        // 4. Task list — flat, no Morning/Afternoon grouping; completed tasks sort to the end.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 88.dp)
        ) {
            if (pendingTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    val filtered = selectedFilter != "All" || activeStatFilter != null
                    com.mj.yata.ui.widgets.TabEmptyState(
                        icon = Icons.Default.TaskAlt,
                        title = stringResource(R.string.today_all_caught_up),
                        subtitle = if (!filtered) "No tasks for today." else "No tasks match this filter.",
                        actionLabel = if (!filtered) "Add task" else "Show all",
                        onAction = {
                            if (!filtered) onNewTaskClick() else {
                                selectedFilter = "All"
                                activeStatFilter = null
                            }
                        }
                    )
                }
            } else {
                @Composable
                fun LazyItemScope.taskRowContent(task: Task) {
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
                        horizontalPadding = 12.dp,
                        showDueDate = true,
                        modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                    )
                }

                // Headers only appear when completed tasks aren't hidden — hiding them collapses
                // back to a plain flat list with no "Pending"/"Completed" labels at all.
                if (!hideCompleted && pendingTasks.isNotEmpty()) {
                    item(key = "pending_header") {
                        TaskSectionHeader("PENDING", pendingTasks.size, horizontalPadding = 12.dp)
                    }
                }
                items(pendingTasks, key = { "pending_" + it.id }, contentType = { "task" }) { task -> taskRowContent(task) }
                if (!hideCompleted && completedTasks.isNotEmpty()) {
                    item(key = "completed_header") {
                        TaskSectionHeader("COMPLETED", completedTasks.size, horizontalPadding = 12.dp)
                    }
                    items(completedTasks, key = { "completed_" + it.id }, contentType = { "task" }) { task -> taskRowContent(task) }
                }
            }
        }
    }
        com.mj.yata.ui.widgets.ConfettiOverlay(trigger = confettiTrigger)
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
                todayStr = todayStr,
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
