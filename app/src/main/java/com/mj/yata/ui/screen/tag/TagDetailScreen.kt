package com.mj.yata.ui.screen.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Label
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
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.sheets.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailScreen(
    viewModel: MainViewModel,
    tagId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tags by viewModel.tags.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tagGroups by viewModel.tagGroups.collectAsState()

    val tag = remember(tags, tagId) { tags.find { it.id == tagId } }
    val accents = LocalYataAccents.current

    var isEditSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (tag == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val tagColor = if (tag.color == "error") {
        MaterialTheme.colorScheme.error
    } else {
        accents.getAccent(tag.color)
    }

    val taggedTasks = remember(tasks, lists, projects, tag.id) {
        tasks.filter { it.effectiveTagIds(lists, projects).contains(tag.id) }
    }
    val doneTasks = taggedTasks.count { it.done }
    val openTasks = taggedTasks.size - doneTasks

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#" + tag.name, fontWeight = FontWeight.Bold) },
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
                            text = { Text("Edit tag") },
                            onClick = {
                                showMenu = false
                                isEditSheetOpen = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete tag") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tagColor.copy(alpha = 0.12f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Header Area
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tagColor.copy(alpha = 0.12f))
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(tagColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = tagColor,
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
            if (taggedTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks carrying this tag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(taggedTasks, key = { it.id }) { task ->
                    val taskList = lists.find { it.id == task.listId }
                    val taskAssignees = task.assigneeIds.mapNotNull { pid -> people.find { it.id == pid } }
                    val taskTags = task.effectiveTags(lists, projects, tags)

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

    if (isEditSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isEditSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TagEditorSheet(
                initialName = tag.name,
                initialColor = tag.color,
                initialGroupId = tag.groupId,
                groups = tagGroups,
                onSave = { newName, newColor, newGroupId ->
                    viewModel.upsertTag(tag.copy(name = newName.lowercase().trim(), color = newColor, groupId = newGroupId))
                    isEditSheetOpen = false
                },
                onCreateGroup = { id, name, color ->
                    viewModel.upsertTagGroup(com.mj.yata.domain.model.TagGroup(id = id, name = name, color = color))
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete tag?") },
            text = { Text("This tag will be removed from all tasks.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTag(tag)
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
