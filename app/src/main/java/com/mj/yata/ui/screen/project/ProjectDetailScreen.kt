package com.mj.yata.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.AssigneeStack
import com.mj.yata.ui.widgets.DragDropReorderableColumn
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.sheets.*

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
    val tasks by viewModel.tasks.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()

    val project = remember(projects, projectId) { projects.find { it.id == projectId } }
    val accents = LocalYataAccents.current

    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRolloverDialog by remember { mutableStateOf(false) }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val projectColor = accents.getAccent(project.color)
    val projectTasks = remember(tasks, project.id) { tasks.filter { it.projectId == project.id }.sortedBy { it.sortOrder } }

    var localOrder by remember(projectTasks) { mutableStateOf(projectTasks) }
    var pendingMoveTask by remember { mutableStateOf<Task?>(null) }

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

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(project.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                            text = { Text("Roll over open tasks") },
                            onClick = {
                                showMenu = false
                                showRolloverDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete project") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = projectColor.copy(alpha = 0.12f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isNewTaskSheetOpen = true },
                containerColor = projectColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
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
            // 1. Header Ring Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(projectColor.copy(alpha = 0.12f))
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressRing(
                    progress = progress,
                    size = 72.dp,
                    strokeWidth = 6.dp,
                    activeColor = projectColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$doneTasks / $totalTasks completed",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (project.due != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Due " + com.mj.yata.util.TaskScheduleUtils.formatDueDate(project.due),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (projectPeople.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AssigneeStack(
                        people = projectPeople,
                        avatarSize = 28.dp
                    )
                }
            }

            // 2. Flat task list — long-press to drag-reorder, or drag to the top/bottom
            // edge and hold to move the task into a different list/project.
            if (projectTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks in this project yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                DragDropReorderableColumn(
                    items = localOrder,
                    key = { it.id },
                    onMove = { from, to -> localOrder = localOrder.toMutableList().apply { add(to, removeAt(from)) } },
                    onDragEnd = { viewModel.commitTaskOrder(localOrder) },
                    onDragToTopEdge = { task -> pendingMoveTask = task },
                    onDragToBottomEdge = { task -> pendingMoveTask = task },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) { task ->
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
                        onTaskClick = { onNavigateToTaskDetail(task.id) }
                    )
                }
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
                projects = projects.filter { it.id != project.id },
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

    if (isNewTaskSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isNewTaskSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            NewTaskSheet(
                lists = lists,
                projects = projects,
                people = people,
                tags = tags,
                initialProjectId = project.id,
                onAddTask = { title, listId, priority, assignees, taskTags, rec, due, time, reminder, section, taskProjectId ->
                    viewModel.addTask(title, listId, priority, assignees, taskTags, rec, due = due, time = time, reminder = reminder, section = section, projectId = taskProjectId)
                    isNewTaskSheetOpen = false
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
                tags = tags,
                onSave = { newName, newColor, newIcon, newDue, newCommonTagIds, newDefaultReminder ->
                    viewModel.upsertProject(project.copy(name = newName, color = newColor, icon = newIcon, due = newDue, commonTagIds = newCommonTagIds, defaultReminder = newDefaultReminder))
                    isEditSheetOpen = false
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete project?") },
            text = { Text("All tasks inside this project will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteProject(project)
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
}
