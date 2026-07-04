package com.mj.yata.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.AssigneeStack
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.sheets.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: MainViewModel,
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
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

    // Map to keep track of expanded/collapsed list sections
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val projectColor = accents.getAccent(project.color)
    val projectLists = remember(lists, project.id) { lists.filter { it.projectId == project.id } }
    val listIds = projectLists.map { it.id }
    val projectTasks = remember(tasks, listIds) { tasks.filter { listIds.contains(it.listId) } }

    val totalTasks = projectTasks.size
    val doneTasks = projectTasks.count { it.done }
    val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

    val projectPeople = remember(projectTasks, people) {
        val pids = projectTasks.flatMap { it.assigneeIds }.toSet()
        people.filter { pids.contains(it.id) }
    }

    Scaffold(
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Header Ring Area
            item {
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
            }

            // 2. Project Lists Sections
            items(projectLists) { list ->
                val listTasks = tasks.filter { it.listId == list.id }
                val listTotal = listTasks.size
                val listDone = listTasks.count { it.done }

                val isExpanded = expandedStates[list.id] ?: true
                val swatchColor = accents.getAccent(list.color)

                // Section Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStates[list.id] = !isExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(swatchColor, CircleShape)
                        )
                        Text(
                            text = list.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(
                                text = "$listDone/$listTotal",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expanded Section Task List
                if (isExpanded) {
                    if (listTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tasks in this list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        listTasks.forEach { task ->
                            val taskAssignees = task.assigneeIds.mapNotNull { pid -> people.find { it.id == pid } }
                            val taskTags = task.effectiveTags(lists, projects, tags)

                            TaskRow(
                                task = task,
                                list = list,
                                assignees = taskAssignees,
                                tags = taskTags,
                                onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                                onTaskClick = { onNavigateToTaskDetail(task.id) },
                                showList = false
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }

    if (isNewTaskSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isNewTaskSheetOpen = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            NewTaskSheet(
                lists = projectLists, // Offer project lists only as options
                projects = projects,
                people = people,
                tags = tags,
                onAddTask = { title, listId, priority, assignees, taskTags, rec, due, time, reminder ->
                    viewModel.addTask(title, listId, priority, assignees, taskTags, rec, due = due, time = time, reminder = reminder)
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
                onDismiss = { isNewTaskSheetOpen = false }
            )
        }
    }

    if (isEditSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isEditSheetOpen = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ProjectEditorSheet(
                initialName = project.name,
                initialColor = project.color,
                initialDueDate = project.due,
                initialCommonTagIds = project.commonTagIds,
                tags = tags,
                onSave = { newName, newColor, newDue, newCommonTagIds ->
                    viewModel.upsertProject(project.copy(name = newName, color = newColor, due = newDue, commonTagIds = newCommonTagIds))
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
            text = { Text("All folders and tasks inside this project will be permanently deleted.") },
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
}


