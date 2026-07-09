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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
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
    onProfileClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    onSwipeToDelete: (String) -> Unit = {},
    onBulkComplete: (List<String>) -> Unit = {},
    onBulkDelete: (List<String>) -> Unit = {},
    onBulkAddTag: (List<String>, String) -> Unit = { _, _ -> },
    onBulkSetProject: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkSetList: (List<String>, String?) -> Unit = { _, _ -> },
    onBulkDuplicate: (List<String>) -> Unit = {},
    onBulkAssignPerson: (List<String>, String) -> Unit = { _, _ -> },
    onAddComment: (taskId: String, body: String) -> Unit = { _, _ -> },
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    taskRowDensity: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    hideCompleted: Boolean = false,
    onHideCompletedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkAssignSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }

    val todayStr = remember { LocalDate.now().toString() }
    val todayDateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase()
    }

    // Projects/lists marked "Exclude from Today" hide their tasks from this screen entirely,
    // even if a task inside ends up overdue — meant for a backlog with no fixed date yet.
    val excludedProjectIds = remember(projects) { projects.filter { it.excludeFromToday }.map { it.id }.toSet() }
    val excludedListIds = remember(lists) { lists.filter { it.excludeFromToday }.map { it.id }.toSet() }

    // Filter tasks due today (or overdue)
    val todayTasks = remember(tasks, todayStr, excludedProjectIds, excludedListIds) {
        tasks.filter {
            it.due != null && it.due <= todayStr &&
                it.projectId !in excludedProjectIds && it.listId !in excludedListIds
        }
    }

    // State for filter chips
    var selectedFilter by remember { mutableStateOf("All") } // "All" | "Assigned to me" | "Delegated" | "High Priority"
    LaunchedEffect(peopleEnabled) {
        if (!peopleEnabled && selectedFilter != "All" && selectedFilter != "High Priority") selectedFilter = "All"
    }

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }

    val filteredTasks = remember(todayTasks, selectedFilter, myId) {
        when (selectedFilter) {
            "Assigned to me" -> todayTasks.filter { it.assigneeIds.contains(myId) }
            "Delegated" -> todayTasks.filter { it.assigneeIds.isNotEmpty() && !it.assigneeIds.contains(myId) }
            "High Priority" -> todayTasks.filter { it.priority == "high" }
            else -> todayTasks
        }
    }

    // Always reflect all of today's tasks here, not the chip-filtered subset below —
    // otherwise picking "High Priority" etc. would skew the ring/"X to go" text.
    val doneCount = todayTasks.count { it.done }
    val totalCount = todayTasks.size
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val remainingCount = totalCount - doneCount

    // Flat list, no more Morning/Afternoon grouping — split into Pending/Completed instead of
    // interleaving them in raw sortOrder. Hiding completed drops both the tasks and the section
    // headers themselves, rather than leaving an empty "Completed" heading around.
    var sortMode by remember { mutableStateOf(com.mj.yata.util.TaskSortMode.MANUAL) }
    val pendingTasks = remember(filteredTasks, sortMode) {
        filteredTasks.filter { !it.done }.sortedByMode(sortMode)
    }
    val completedTasks = remember(filteredTasks, hideCompleted) {
        if (hideCompleted) emptyList() else filteredTasks.filter { it.done }
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
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search tasks",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                com.mj.yata.ui.widgets.TaskSortMenuButton(
                    current = sortMode,
                    onSelect = { sortMode = it }
                )
                IconButton(onClick = { onHideCompletedChange(!hideCompleted) }) {
                    Icon(
                        imageVector = if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hideCompleted) "Show completed tasks" else "Hide completed tasks",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Profile avatar triggers Settings
                Box(modifier = Modifier.clickable { onProfileClick() }) {
                    PersonAvatar(
                        initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                        accentKey = "accentC",
                        size = 32.dp,
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
            if (filteredTasks.isEmpty()) {
                item {
                    com.mj.yata.ui.widgets.TabEmptyState(
                        icon = Icons.Default.TaskAlt,
                        title = "All caught up",
                        subtitle = if (selectedFilter == "All") "No tasks for today." else "No tasks match this filter."
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
                        density = taskRowDensity,
                        onSwipeToDelete = { onSwipeToDelete(task.id) },
                        swipeEnabled = !selectionMode,
                        horizontalPadding = 12.dp,
                        modifier = Modifier.animateItemPlacement(tween(YataDur.sheet, easing = YataEase.emphasized))
                    )
                }

                // Headers only appear when completed tasks aren't hidden — hiding them collapses
                // back to a plain flat list with no "Pending"/"Completed" labels at all.
                if (!hideCompleted && pendingTasks.isNotEmpty()) {
                    item(key = "pending_header") {
                        TaskSectionHeader("PENDING", pendingTasks.size, horizontalPadding = 12.dp)
                    }
                }
                items(pendingTasks, key = { it.id }) { task -> taskRowContent(task) }
                if (!hideCompleted && completedTasks.isNotEmpty()) {
                    item(key = "completed_header") {
                        TaskSectionHeader("COMPLETED", completedTasks.size, horizontalPadding = 12.dp)
                    }
                    items(completedTasks, key = { it.id }) { task -> taskRowContent(task) }
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
