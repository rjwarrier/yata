package com.mj.yata.ui.screen.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.sheets.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    viewModel: MainViewModel,
    listId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()

    val list = remember(lists, listId) { lists.find { it.id == listId } }
    val accents = LocalYataAccents.current

    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (list == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val listColor = accents.getAccent(list.color)
    val listTasks = remember(tasks, list.id) { tasks.filter { it.listId == list.id } }
    val doneTasks = listTasks.count { it.done }
    val openTasks = listTasks.size - doneTasks

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleListStarred(list.id) }) {
                        Icon(
                            imageVector = if (list.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (list.starred) "Unstar folder" else "Star folder",
                            tint = if (list.starred) accents.accentD else MaterialTheme.colorScheme.onSurface
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
                            text = { Text("Edit folder") },
                            onClick = {
                                showMenu = false
                                isEditSheetOpen = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = listColor.copy(alpha = 0.12f)
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Header Area
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(listColor.copy(alpha = 0.12f))
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(listColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = listColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$openTasks open · $doneTasks completed",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Tasks list
            if (listTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks in this list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(listTasks, key = { it.id }) { task ->
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
    }

    if (isNewTaskSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isNewTaskSheetOpen = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            NewTaskSheet(
                lists = listOf(list), // Force this list
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
            ListEditorSheet(
                initialName = list.name,
                initialColor = list.color,
                onSave = { newName, newColor ->
                    viewModel.upsertList(list.copy(name = newName, color = newColor))
                    isEditSheetOpen = false
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete folder?") },
            text = { Text("All tasks inside this folder will be permanently deleted.") },
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


