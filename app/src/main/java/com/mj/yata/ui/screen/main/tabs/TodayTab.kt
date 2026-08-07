package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.PI
import kotlin.math.sin

private enum class TodayTaskFilter { ALL, ASSIGNED_TO_ME, DELEGATED, HIGH_PRIORITY }

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
    /** Gates the manual sync button — there is nothing remote to sync until a server is set up. */
    backupSyncEnabled: Boolean = false,
    confettiEnabled: Boolean = true,
    syncing: Boolean = false,
    lastSyncSucceeded: Boolean? = null,
    syncProgress: SyncProgressState? = null,
    syncButtonEnabled: Boolean = true,
    onSyncClick: () -> Unit = {},
    /** When Today has nothing due, show tomorrow/day-after tasks automatically (dimmed, but fully
     * clickable/completable) instead of requiring the empty state's "Show upcoming tasks" button
     * to be tapped each time. Settings → Task Defaults. */
    showUpcomingWhenEmpty: Boolean = false,
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
    val excludedListIds = remember(lists) { lists.hiddenFromMainTaskListIds() }

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }

    // Due-or-overdue, minus anything deferred or being waited on — see Task.isActionableToday,
    // which the home-screen widgets and the daily agenda digest share so they can't drift from
    // this screen. The container exclusions stay here since they're specific to the in-app view.
    val todayTasks = remember(tasks, todayStr, excludedProjectIds, excludedListIds, myId) {
        val nowMillis = System.currentTimeMillis()
        tasks.filter {
            it.isActionableToday(todayStr, nowMillis, myId) &&
                it.projectId !in excludedProjectIds && it.listId !in excludedListIds
        }
    }

    // State for filter chips
    var selectedFilter by remember { mutableStateOf(TodayTaskFilter.ALL) }
    LaunchedEffect(peopleEnabled) {
        if (!peopleEnabled && selectedFilter != TodayTaskFilter.ALL && selectedFilter != TodayTaskFilter.HIGH_PRIORITY) {
            selectedFilter = TodayTaskFilter.ALL
        }
    }

    val filteredTasks = remember(todayTasks, selectedFilter, myId) {
        when (selectedFilter) {
            TodayTaskFilter.ASSIGNED_TO_ME -> todayTasks.filter { it.assigneeIds.contains(myId) }
            TodayTaskFilter.DELEGATED -> todayTasks.filter { it.assigneeIds.isNotEmpty() && !it.assigneeIds.contains(myId) }
            TodayTaskFilter.HIGH_PRIORITY -> todayTasks.filter { it.priority == "high" }
            TodayTaskFilter.ALL -> todayTasks
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

    // Scoped to what's still open, not the whole day: time already spent isn't capacity you still
    // have to find, so a finished task shouldn't keep inflating the plan.
    val openTodayTasks = remember(todayTasks) { todayTasks.filter { !it.done } }
    val plannedMinutes = remember(openTodayTasks) { com.mj.yata.util.EstimateUtils.plannedMinutes(openTodayTasks) }
    val unestimatedCount = remember(openTodayTasks) { com.mj.yata.util.EstimateUtils.unestimatedCount(openTodayTasks) }

    var activeStatFilter by remember { mutableStateOf<com.mj.yata.ui.widgets.HeroStatKind?>(null) }
    val overdueCount = remember(progressBaseTasks) { com.mj.yata.util.AnalyticsUtils.overdueCount(progressBaseTasks) }
    val highPriorityCount = remember(progressBaseTasks) { progressBaseTasks.count { !it.done && it.priority == "high" } }
    val dueTodayCount = remember(progressBaseTasks, todayStr) { progressBaseTasks.count { !it.done && it.due == todayStr } }

    // Flat list, no more Morning/Afternoon grouping — split into Pending/Completed instead of
    // interleaving them in raw sortOrder. Hiding completed drops both the tasks and the section
    // headers themselves, rather than leaving an empty "Completed" heading around.
    val pendingTasks = remember(filteredTasks, sortMode, activeStatFilter, today) {
        val statFilter = activeStatFilter
        filteredTasks
            .filter { !it.done }
            .filter { statFilter == null || statFilter.matches(it, today) }
            .sortedByMode(sortMode)
    }
    val completedTasks = remember(filteredTasks, hideCompleted) {
        if (hideCompleted) emptyList() else filteredTasks.filter { it.done }
    }

    // Fallback preview shown only when Today is genuinely empty (see the empty-state branch
    // below) — tomorrow's and the day after's open tasks, dimmed but fully interactive. Not
    // routed through the exclude-from-Today/deferral machinery those due-today lists use: this
    // is a peek at what's coming, not a claim that these tasks belong to today, so a task in an
    // excluded project or one that's merely deferred still shows here exactly as it'll appear
    // when its own day arrives.
    val tomorrowStr = remember(today) { today.plusDays(1).toString() }
    val dayAfterTomorrowStr = remember(today) { today.plusDays(2).toString() }
    val tomorrowPreviewTasks = remember(tasks, tomorrowStr, sortMode) {
        tasks.filter { !it.done && it.due == tomorrowStr }.sortedByMode(sortMode)
    }
    val dayAfterTomorrowPreviewTasks = remember(tasks, dayAfterTomorrowStr, sortMode) {
        tasks.filter { !it.done && it.due == dayAfterTomorrowStr }.sortedByMode(sortMode)
    }
    val hasUpcomingPreview = tomorrowPreviewTasks.isNotEmpty() || dayAfterTomorrowPreviewTasks.isNotEmpty()
    // The button's temporary reveal — resets on next screen visit/recomposition scope, unlike
    // showUpcomingWhenEmpty which is a standing Settings choice. Only relevant when that setting
    // is off; when it's on the preview always shows and this is moot.
    var upcomingPreviewRevealed by remember { mutableStateOf(false) }
    val showUpcomingPreview = showUpcomingWhenEmpty || upcomingPreviewRevealed

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
                if (backupSyncEnabled) {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onSyncClick, enabled = syncButtonEnabled) {
                        SyncStatusIcon(lastSyncSucceeded = if (syncing) null else lastSyncSucceeded)
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

        AnimatedVisibility(visible = syncProgress != null && !selectionMode) {
            syncProgress?.let { progress ->
                SyncProgressPill(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
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
                    text = if (remainingCount == 0 && totalCount > 0) {
                        stringResource(R.string.today_all_caught_up_bang)
                    } else {
                        pluralStringResource(R.plurals.today_to_go, remainingCount, remainingCount)
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                // Planned effort for what's still open today. Hidden entirely when nothing today
                // carries an estimate — a "0m planned" line on an unestimated day reads as a
                // real figure rather than an absent one. The unestimated count rides alongside so
                // the total is never mistaken for the whole day when it only covers part of it.
                if (plannedMinutes != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (unestimatedCount > 0) {
                            stringResource(
                                R.string.today_planned_with_unestimated,
                                com.mj.yata.util.EstimateUtils.format(plannedMinutes),
                                unestimatedCount
                            )
                        } else {
                            stringResource(
                                R.string.today_planned,
                                com.mj.yata.util.EstimateUtils.format(plannedMinutes)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

        activeStatFilter?.let { statFilter ->
            com.mj.yata.ui.widgets.ActiveFilterBanner(
                kind = statFilter,
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
            listOfNotNull(
                TodayTaskFilter.ALL,
                if (peopleEnabled) TodayTaskFilter.ASSIGNED_TO_ME else null,
                if (peopleEnabled) TodayTaskFilter.DELEGATED else null,
                TodayTaskFilter.HIGH_PRIORITY
            ).forEach { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(text = todayTaskFilterLabel(filter)) },
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
        val projectsById = remember(projects) { projects.associateBy { it.id } }
        val tagsById = remember(tags) { tags.associateBy { it.id } }

        // 4. Task list — flat, no Morning/Afternoon grouping; completed tasks sort to the end.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 88.dp)
        ) {
            if (pendingTasks.isEmpty() && completedTasks.isEmpty()) {
                val filtered = selectedFilter != TodayTaskFilter.ALL || activeStatFilter != null
                item {
                    com.mj.yata.ui.widgets.TabEmptyState(
                        icon = Icons.Default.TaskAlt,
                        title = stringResource(R.string.today_all_caught_up),
                        subtitle = stringResource(if (!filtered) R.string.today_no_tasks else R.string.task_filter_no_matches),
                        actionLabel = stringResource(if (!filtered) R.string.cd_add_task else R.string.action_show_all),
                        onAction = {
                            if (!filtered) onNewTaskClick() else {
                                selectedFilter = TodayTaskFilter.ALL
                                activeStatFilter = null
                            }
                        },
                        // Full size only when this is the only thing on screen — with an
                        // upcoming-tasks preview (or its reveal button) below, the large default
                        // is just pushing real content further down.
                        compact = !filtered && hasUpcomingPreview
                    )
                }

                // Only when Today is genuinely empty (not just filtered down to nothing) is a
                // peek at what's coming worth offering — a filtered-empty state means there's
                // real work today, just hidden by the chips.
                if (!filtered && hasUpcomingPreview) {
                    if (!showUpcomingPreview) {
                        item(key = "upcoming_preview_button") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(onClick = { upcomingPreviewRevealed = true }) {
                                    Text(stringResource(R.string.today_show_upcoming_tasks))
                                }
                            }
                        }
                    } else {
                        // Dimmed but fully interactive — same TaskRow, same onToggleDone/onTaskClick
                        // wiring as the normal list below, just visually de-emphasized via alpha
                        // so it reads as "not today's business" without hiding it behind selection.
                        @Composable
                        fun LazyItemScope.dimmedPreviewRow(task: Task) {
                            val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                            val taskAssignees = remember(task.assigneeIds, peopleById, peopleEnabled) {
                                if (peopleEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                            }
                            val taskTags = remember(task, projectsById, tagsById, tagsEnabled) {
                                if (tagsEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
                            }
                            TaskRow(
                                task = task,
                                list = taskList,
                                assignees = taskAssignees,
                                tags = taskTags,
                                onToggleDone = { onToggleDone(task.id) },
                                onTaskClick = { onTaskClick(task.id) },
                                onCommentClick = { pendingCommentTask = task },
                                onQuickSnooze = { preset -> onQuickSnooze(task.id, preset) },
                                onRenameTask = { title -> onRenameTask(task.id, title) },
                                density = taskRowDensity,
                                horizontalPadding = 12.dp,
                                showDueDate = false,
                                modifier = Modifier
                                    .alpha(0.5f)
                                    .animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                            )
                        }

                        if (tomorrowPreviewTasks.isNotEmpty()) {
                            item(key = "upcoming_preview_tomorrow_header") {
                                TaskSectionHeader(stringResource(R.string.today_tomorrow_header), tomorrowPreviewTasks.size, horizontalPadding = 12.dp)
                            }
                            items(tomorrowPreviewTasks, key = { "preview_tmrw_" + it.id }, contentType = { "task" }) { task -> dimmedPreviewRow(task) }
                        }
                        if (dayAfterTomorrowPreviewTasks.isNotEmpty()) {
                            item(key = "upcoming_preview_day_after_header") {
                                TaskSectionHeader(stringResource(R.string.today_day_after_tomorrow_header), dayAfterTomorrowPreviewTasks.size, horizontalPadding = 12.dp)
                            }
                            items(dayAfterTomorrowPreviewTasks, key = { "preview_dat_" + it.id }, contentType = { "task" }) { task -> dimmedPreviewRow(task) }
                        }
                    }
                }
            } else {
                @Composable
                fun LazyItemScope.taskRowContent(task: Task) {
                    val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                    val taskAssignees = remember(task.assigneeIds, peopleById, peopleEnabled) {
                        if (peopleEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                    }
                    val taskTags = remember(task, projectsById, tagsById, tagsEnabled) {
                        if (tagsEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
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
                        TaskSectionHeader(stringResource(R.string.today_pending_header), pendingTasks.size, horizontalPadding = 12.dp)
                    }
                }
                items(pendingTasks, key = { "pending_" + it.id }, contentType = { "task" }) { task -> taskRowContent(task) }
                if (!hideCompleted && completedTasks.isNotEmpty()) {
                    item(key = "completed_header") {
                        TaskSectionHeader(stringResource(R.string.today_completed_header), completedTasks.size, horizontalPadding = 12.dp)
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

@Composable
private fun SyncProgressPill(
    progress: SyncProgressState,
    modifier: Modifier = Modifier
) {
    val percent = progress.percent.coerceIn(0, 100)
    val transition = rememberInfiniteTransition(label = "SyncWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncWavePhase"
    )
    val shape = RoundedCornerShape(28.dp)
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .semantics { contentDescription = "${progress.label}, $percent percent" }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
            }
            WavyProgressLine(
                progress = percent / 100f,
                phase = phase,
                contentColor = contentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            )
        }
    }
}

@Composable
private fun WavyProgressLine(
    progress: Float,
    phase: Float,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = contentColor.copy(alpha = 0.24f)
    val progressColor = contentColor
    Canvas(modifier = modifier) {
        val baselineY = size.height / 2f
        val stroke = 4.dp.toPx()
        drawLine(
            color = trackColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val targetX = size.width * progress.coerceIn(0f, 1f)
        if (targetX <= 0f) return@Canvas

        val amplitude = 3.dp.toPx()
        val waveLength = 34.dp.toPx()
        val step = 3.dp.toPx().coerceAtLeast(1f)
        var previous = Offset(
            x = 0f,
            y = baselineY + sin((phase * 2f * PI).toFloat()) * amplitude
        )
        var x = step
        while (x <= targetX) {
            val y = baselineY + sin(((x / waveLength) + phase) * 2f * PI).toFloat() * amplitude
            val current = Offset(x, y)
            drawLine(
                color = progressColor,
                start = previous,
                end = current,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            previous = current
            x += step
        }
    }
}

@Composable
private fun SyncStatusIcon(lastSyncSucceeded: Boolean?) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudSync,
            contentDescription = stringResource(R.string.cd_sync_now)
        )
        lastSyncSucceeded?.let { success ->
            val containerColor = if (success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val contentColor = if (success) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
            val statusDescription = stringResource(
                if (success) R.string.sync_status_finished else R.string.sync_status_failed
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(containerColor)
                    .semantics {
                        contentDescription = statusDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}

@Composable
private fun todayTaskFilterLabel(filter: TodayTaskFilter): String = stringResource(
    when (filter) {
        TodayTaskFilter.ALL -> R.string.task_filter_all
        TodayTaskFilter.ASSIGNED_TO_ME -> R.string.search_filter_assigned_to_me
        TodayTaskFilter.DELEGATED -> R.string.task_filter_delegated
        TodayTaskFilter.HIGH_PRIORITY -> R.string.search_filter_high_priority
    }
)
