package com.mj.yata.ui.screen.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.DragDropReorderableColumn
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.TaskSectionHeader
import com.mj.yata.ui.sheets.*
import com.mj.yata.util.taskMatchesQuery
import com.mj.yata.util.sortedByMode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListDetailScreen(
    viewModel: MainViewModel,
    listId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()

    val list = remember(lists, listId) { lists.find { it.id == listId } }
    val accents = LocalYataAccents.current

    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hideCompleted by viewModel.hideCompletedList.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val listColor = accents.getAccent(list.color)
    com.mj.yata.ui.theme.StatusBarColor(
        listColor.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.background)
    )
    val listTasks = remember(tasks, list.id) { tasks.filter { it.listId == list.id }.sortedBy { it.sortOrder } }
    val doneTasks = listTasks.count { it.done }
    val openTasks = listTasks.size - doneTasks
    // Split into Pending (draggable) / Completed (static) instead of one combined, interleaved
    // list. Hiding completed drops both the tasks and the section headers entirely.
    var sortMode by remember { mutableStateOf(com.mj.yata.util.TaskSortMode.MANUAL) }
    val pendingListTasks = remember(listTasks, sortMode) {
        listTasks.filter { !it.done }.sortedByMode(sortMode)
    }
    val completedListTasks = remember(listTasks, hideCompleted) {
        if (hideCompleted) emptyList() else listTasks.filter { it.done }
    }
    val searchFilteredTasks = remember(listTasks, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else listTasks.filter { taskMatchesQuery(it, searchQuery) }
    }

    // Not keyed on pendingListTasks — see ProjectDetailScreen for why: any task write anywhere
    // in the app used to reset this mid-drag and discard/corrupt the in-progress reorder.
    var localOrder by remember { mutableStateOf(pendingListTasks) }
    var isDraggingTasks by remember { mutableStateOf(false) }
    LaunchedEffect(pendingListTasks) {
        if (!isDraggingTasks) localOrder = pendingListTasks
    }
    var pendingMoveTask by remember { mutableStateOf<Task?>(null) }
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
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
            // Title-less bar: the list name lives in the hero header below (per handoff's List Detail).
            // Except while searching, where the title slot hosts the inline search field.
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
                            placeholder = { Text("Search in ${list.name}") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
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
                            Icon(Icons.Default.Search, contentDescription = "Search in list")
                        }
                        IconButton(onClick = { viewModel.toggleListStarred(list.id) }) {
                            Icon(
                                imageVector = if (list.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (list.starred) "Unstar list" else "Star list",
                                tint = if (list.starred) accents.accentD else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        com.mj.yata.ui.widgets.TaskSortMenuButton(
                            current = sortMode,
                            onSelect = { sortMode = it }
                        )
                        IconButton(onClick = { viewModel.setHideCompletedList(!hideCompleted) }) {
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
                                text = { Text("Edit list") },
                                onClick = {
                                    showMenu = false
                                    isEditSheetOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete list") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = listColor.copy(alpha = 0.18f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isNewTaskSheetOpen = true },
                containerColor = listColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { innerPadding ->
        val peopleById = remember(people) { people.associateBy { it.id } }
        val progress = if (listTasks.isNotEmpty()) doneTasks.toFloat() / listTasks.size else 0f

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // 1. Hero header — icon tile, list name, progress ring (per handoff's List Detail)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(listColor.copy(alpha = 0.18f))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(listColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = com.mj.yata.ui.widgets.iconVectorFor(list.icon),
                        contentDescription = null,
                        tint = listColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$openTasks open · $doneTasks completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                com.mj.yata.ui.widgets.ProgressRing(
                    progress = progress,
                    size = 48.dp,
                    strokeWidth = 5.dp,
                    activeColor = listColor
                )
            }

            // 2. Tasks list — Pending (drag-to-reorder, or drag to the top/bottom edge to move
            // to another list/project) above a static Completed section. While searching, drag
            // reorder is disabled (see ProjectDetailScreen for why) and it falls back to a flat
            // matched list.
            @Composable
            fun taskRowFor(task: Task, modifier: Modifier = Modifier) {
                val taskAssignees = remember(task.assigneeIds, peopleById, peopleFeatureEnabled) {
                    if (peopleFeatureEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                }
                val taskTags = remember(task, projects, tags, tagsFeatureEnabled) {
                    if (tagsFeatureEnabled) task.effectiveTags(projects, tags) else emptyList()
                }

                TaskRow(
                    task = task,
                    list = list,
                    assignees = taskAssignees,
                    tags = taskTags,
                    onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                    onTaskClick = { onNavigateToTaskDetail(task.id) },
                    modifier = modifier,
                    showList = false,
                    onCommentClick = { pendingCommentTask = task },
                    density = taskRowDensity
                )
            }

            if (searchActive) {
                if (searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Type to search tasks in this list.",
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
            } else if (pendingListTasks.isEmpty() && completedListTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (listTasks.isEmpty()) "No tasks in this list." else "All tasks completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                val showPendingHeader = !hideCompleted && pendingListTasks.isNotEmpty()
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
                            item(key = "pending_header") { TaskSectionHeader("PENDING", pendingListTasks.size) }
                        }
                    },
                    footer = {
                        if (!hideCompleted && completedListTasks.isNotEmpty()) {
                            item(key = "completed_header") { TaskSectionHeader("COMPLETED", completedListTasks.size) }
                            items(completedListTasks, key = { it.id }) { task ->
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
                lists = lists.filter { it.id != list.id },
                projects = projects,
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
        ModalBottomSheet(
            onDismissRequest = { isNewTaskSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            NewTaskSheet(
                lists = listOf(list), // Force this list
                projects = projects,
                people = people,
                tags = tags,
                tasks = tasks,
                initialListId = list.id,
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
            ListEditorSheet(
                initialName = list.name,
                initialColor = list.color,
                initialIcon = list.icon,
                initialExcludeFromToday = list.excludeFromToday,
                onSave = { newName, newColor, newIcon, newExcludeFromToday ->
                    viewModel.upsertList(list.copy(name = newName, color = newColor, icon = newIcon, excludeFromToday = newExcludeFromToday))
                    isEditSheetOpen = false
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete list?") },
            text = { Text("All tasks inside this list will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteList(list)
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


