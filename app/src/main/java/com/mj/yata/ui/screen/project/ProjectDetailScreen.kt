package com.mj.yata.ui.screen.project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.util.taskMatchesQuery
import com.mj.yata.util.sortedByMode
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.AssigneeStack
import com.mj.yata.ui.widgets.DragDropReorderableColumn
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.TaskSectionHeader
import com.mj.yata.ui.sheets.*
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    viewModel: MainViewModel,
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val projectTasks by remember(projectId) { viewModel.getTasksForProject(projectId) }.collectAsState(initial = emptyList())
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()

    val project = remember(projects, projectId) { projects.find { it.id == projectId } }
    val accents = LocalYataAccents.current
    val context = LocalContext.current

    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRolloverDialog by remember { mutableStateOf(false) }
    var showOverdueRolloverDialog by remember { mutableStateOf(false) }
    val hideCompleted by viewModel.hideCompletedProject.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (project == null) {
        com.mj.yata.ui.widgets.ListDetailShimmer()
        return
    }

    val projectColor = accents.getAccent(project.color)
    com.mj.yata.ui.theme.StatusBarColor(
        projectColor.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.background)
    )
    // Split into Pending (draggable) / Completed (static) instead of one combined, interleaved
    // list — only Pending supports drag-reorder, Completed just renders below it with its own
    // header. Hiding completed drops both the tasks and the section headers entirely.
    var sortMode by remember { mutableStateOf(com.mj.yata.util.TaskSortMode.MANUAL) }
    val pendingProjectTasks = remember(projectTasks, sortMode) {
        projectTasks.filter { !it.done }.sortedByMode(sortMode)
    }
    val completedProjectTasks = remember(projectTasks, hideCompleted) {
        if (hideCompleted) emptyList() else projectTasks.filter { it.done }
    }
    val searchFilteredTasks = remember(projectTasks, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else projectTasks.filter { taskMatchesQuery(it, searchQuery) }
    }

    // Not keyed on pendingProjectTasks — any task write anywhere in the app (a reminder firing,
    // a recurring task rolling over) produces a new `tasks` list instance, which used to reset
    // this mid-drag and silently discard/corrupt the in-progress reorder. A LaunchedEffect
    // re-syncs from the source of truth on real changes, but skips doing so while dragging.
    var localOrder by remember { mutableStateOf(pendingProjectTasks) }
    var isDraggingTasks by remember { mutableStateOf(false) }
    LaunchedEffect(pendingProjectTasks) {
        if (!isDraggingTasks) localOrder = pendingProjectTasks
    }
    var pendingMoveTask by remember { mutableStateOf<Task?>(null) }
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }

    val totalTasks = projectTasks.size
    val doneTasks = projectTasks.count { it.done }
    val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

    val projectPeople = remember(projectTasks, people) {
        val pids = projectTasks.flatMap { it.assigneeIds }.toSet()
        people.filter { pids.contains(it.id) }
    }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text("Search in ${project.name}") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            project.name,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            searchQuery = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = if (searchActive) "Close search" else "Back")
                    }
                },
                actions = {
                    if (searchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in project")
                        }
                        com.mj.yata.ui.widgets.TaskSortMenuButton(
                            current = sortMode,
                            onSelect = { sortMode = it }
                        )
                        IconButton(onClick = { viewModel.setHideCompletedProject(!hideCompleted) }) {
                            Icon(
                                imageVector = if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (hideCompleted) "Show completed tasks" else "Hide completed tasks"
                            )
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit project") },
                                onClick = {
                                    showMenu = false
                                    isEditSheetOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Markdown") },
                                onClick = {
                                    showMenu = false
                                    val markdown = com.mj.yata.util.buildPendingTasksMarkdown(project, projectTasks)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Pending tasks", markdown))
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, markdown)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ${project.name} tasks"))
                                },
                                leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Roll over open tasks") },
                                onClick = {
                                    showMenu = false
                                    showRolloverDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Roll overdue forward") },
                                onClick = {
                                    showMenu = false
                                    showOverdueRolloverDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) }
                            )
                            if (project.archived) {
                                DropdownMenuItem(
                                    text = { Text("Restore project") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setProjectArchived(project, false)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete project") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive project") },
                                    onClick = {
                                        showMenu = false
                                        showArchiveDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = projectColor.copy(alpha = 0.16f)
                )
            )
        },
        floatingActionButton = {
            if (!project.archived) {
                com.mj.yata.ui.widgets.PressableScaleBox(
                    onClick = { isNewTaskSheetOpen = true }
                ) {
                    Surface(
                        color = projectColor,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add task")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val listsById = remember(lists) { lists.associateBy { it.id } }
        val peopleById = remember(people) { people.associateBy { it.id } }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // 1. Header row — a single compact row (ring + stats + assignees) instead of a
            // tall centered stack, so this doesn't eat a third of the screen before any tasks show.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(projectColor.copy(alpha = 0.16f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRing(
                    progress = progress,
                    size = 44.dp,
                    strokeWidth = 4.dp,
                    activeColor = projectColor
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$doneTasks / $totalTasks completed",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!project.description.isNullOrBlank()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (project.due != null) {
                        Text(
                            text = "Due " + com.mj.yata.util.TaskScheduleUtils.formatDueDate(project.due),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (projectPeople.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssigneeStack(
                        people = projectPeople,
                        avatarSize = 24.dp
                    )
                }
            }

            // 2. Task list — Pending (drag-to-reorder, or drag to the top/bottom edge to move to
            // another list/project) above a static Completed section. While searching, drag
            // reorder is disabled (committing a filtered subset's order would corrupt sortOrder
            // for the tasks hidden by the search), so it falls back to a flat matched list.
            @Composable
            fun taskRowFor(task: Task, modifier: Modifier = Modifier) {
                val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                val taskAssignees = remember(task.assigneeIds, peopleById, peopleFeatureEnabled) {
                    if (peopleFeatureEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                }
                val taskTags = remember(task, projects, tags, tagsFeatureEnabled) {
                    if (tagsFeatureEnabled) task.effectiveTags(projects, tags) else emptyList()
                }

                TaskRow(
                    task = task,
                    list = taskList,
                    assignees = taskAssignees,
                    tags = taskTags,
                    onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                    onTaskClick = { onNavigateToTaskDetail(task.id) },
                    modifier = modifier,
                    onCommentClick = { pendingCommentTask = task },
                    onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                    onRenameTask = { viewModel.renameTask(task.id, it) },
                    density = taskRowDensity,
                    showDueDate = true
                )
            }

            if (searchActive) {
                if (searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Type to search tasks in this project.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else if (searchFilteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(searchFilteredTasks, key = { it.id }) { task -> taskRowFor(task) }
                    }
                }
            } else if (pendingProjectTasks.isEmpty() && completedProjectTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (projectTasks.isEmpty()) "No tasks in this project yet." else "All tasks completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                val showPendingHeader = !hideCompleted && pendingProjectTasks.isNotEmpty()
                DragDropReorderableColumn(
                    items = localOrder,
                    key = { it.id },
                    onMove = { from, to -> localOrder = localOrder.toMutableList().apply { add(to, removeAt(from)) } },
                    onDragEnd = { viewModel.commitTaskOrder(localOrder) },
                    onDragToTopEdge = { task -> pendingMoveTask = task },
                    onDragToBottomEdge = { task -> pendingMoveTask = task },
                    onDragStateChanged = { isDraggingTasks = it },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    headerItemCount = if (showPendingHeader) 1 else 0,
                    header = {
                        if (showPendingHeader) {
                            item(key = "pending_header") { TaskSectionHeader("PENDING", pendingProjectTasks.size) }
                        }
                    },
                    footer = {
                        if (!hideCompleted && completedProjectTasks.isNotEmpty()) {
                            item(key = "completed_header") { TaskSectionHeader("COMPLETED", completedProjectTasks.size) }
                            items(completedProjectTasks, key = { it.id }) { task ->
                                taskRowFor(
                                    task = task,
                                    modifier = Modifier.animateItemPlacement(
                                        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                                    )
                                )
                            }
                        }
                    }
                ) { task -> taskRowFor(task) }
            }
        }
    }

    pendingMoveTask?.let { task ->
        ModalBottomSheet(
            onDismissRequest = { pendingMoveTask = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TaskMoveToPickerSheet(
                lists = lists,
                projects = projects.activeProjects().filter { it.id != project.id },
                onSelectList = { targetListId ->
                    viewModel.moveTaskToList(task.id, targetListId, null)
                    pendingMoveTask = null
                },
                onSelectProject = { targetProjectId ->
                    viewModel.moveTaskToList(task.id, null, targetProjectId)
                    pendingMoveTask = null
                }
            )
        }
    }

    pendingCommentTask?.let { task ->
        com.mj.yata.ui.widgets.QuickCommentDialog(
            taskTitle = task.title,
            onSubmit = { body ->
                viewModel.addComment(task.id, body)
                pendingCommentTask = null
            },
            onDismiss = { pendingCommentTask = null }
        )
    }

    if (isNewTaskSheetOpen) {
        val allTasks by viewModel.tasks.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { isNewTaskSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            NewTaskSheet(
                lists = lists,
                projects = projects.activeProjects(includeId = project.id),
                people = people.activePeople(),
                tags = tags,
                tasks = allTasks,
                initialProjectId = project.id,
                onAddTask = { title, listId, priority, assignees, taskTags, rec, due, time, reminder, section, taskProjectId, notes, subtasks ->
                    viewModel.addTask(title, listId, priority, assignees, taskTags, rec, notes = notes, due = due, time = time, reminder = reminder, section = section, projectId = taskProjectId, subtasks = subtasks)
                    isNewTaskSheetOpen = false
                },
                onGoToExistingTask = { id ->
                    isNewTaskSheetOpen = false
                    onNavigateToTaskDetail(id)
                },
                onCreateTag = { id, name, color ->
                    viewModel.upsertTag(com.mj.yata.domain.model.Tag(id = id, name = name, color = color))
                },
                onCreatePerson = { id, name, color ->
                    viewModel.upsertPerson(
                        com.mj.yata.domain.model.Person(id = id, name = name, initials = com.mj.yata.ui.sheets.initialsFor(name), color = color, isMe = false)
                    )
                },
                onDismiss = { isNewTaskSheetOpen = false },
                projectsEnabled = projectsFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                peopleEnabled = peopleFeatureEnabled
            )
        }
    }

    if (isEditSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isEditSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ProjectEditorSheet(
                initialName = project.name,
                initialColor = project.color,
                initialIcon = project.icon,
                initialDueDate = project.due,
                initialCommonTagIds = project.commonTagIds,
                initialDefaultReminder = project.defaultReminder,
                initialDescription = project.description,
                initialExcludeFromToday = project.excludeFromToday,
                tags = tags,
                onSave = { newName, newColor, newIcon, newDue, newCommonTagIds, newDefaultReminder, newDescription, newExcludeFromToday ->
                    viewModel.upsertProject(project.copy(name = newName, color = newColor, icon = newIcon, due = newDue, commonTagIds = newCommonTagIds, defaultReminder = newDefaultReminder, description = newDescription, excludeFromToday = newExcludeFromToday))
                    isEditSheetOpen = false
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("Archive project?") },
            text = { Text("Tasks inside stay linked. The project is hidden from active project surfaces until you restore it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArchiveDialog = false
                        viewModel.setProjectArchived(project, true)
                        onNavigateBack()
                    }
                ) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete archived project?") },
            text = { Text("Delete only the project to keep its tasks without a project, or delete the project and all tasks inside it.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteProjectOnly(project)
                            onNavigateBack()
                        }
                    ) {
                        Text("Project only")
                    }
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteProject(project)
                            onNavigateBack()
                        }
                    ) {
                        Text("Project + tasks", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRolloverDialog) {
        val eligibleCount = remember(projectTasks) { projectTasks.count { !it.done && it.recurrence == null } }
        AlertDialog(
            onDismissRequest = { showRolloverDialog = false },
            title = { Text("Roll over open tasks?") },
            text = { Text("Duplicates $eligibleCount open task(s) with due dates shifted one month forward. Recurring tasks are skipped since they already advance on their own.") },
            confirmButton = {
                TextButton(onClick = {
                    showRolloverDialog = false
                    viewModel.rolloverProjectTasks(project.id)
                }) {
                    Text("Roll over")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRolloverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOverdueRolloverDialog) {
        val today = remember { java.time.LocalDate.now() }
        val eligibleCount = remember(projectTasks, today) {
            projectTasks.count { task ->
                !task.done &&
                    task.recurrence == null &&
                    task.due?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
            }
        }
        AlertDialog(
            onDismissRequest = { showOverdueRolloverDialog = false },
            title = { Text("Roll overdue tasks forward?") },
            text = { Text("Duplicates $eligibleCount overdue open task(s), moving each due date forward by month until it lands in the future. Recurring tasks are skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    showOverdueRolloverDialog = false
                    viewModel.rolloverOverdueProjectTasks(project.id)
                }) {
                    Text("Roll forward")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverdueRolloverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
