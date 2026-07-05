package com.mj.yata.ui.screen.taskdetail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.inheritedTagIds
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.*
import com.mj.yata.ui.sheets.RecurrenceSheet
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface DetailSheetType {
    object None : DetailSheetType
    object ScheduleEditor : DetailSheetType
    object ReminderPicker : DetailSheetType
    object RecurrenceBuilder : DetailSheetType
    object ListPicker : DetailSheetType
    object ProjectPicker : DetailSheetType
    object AssigneePicker : DetailSheetType
    object TagPicker : DetailSheetType
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    viewModel: MainViewModel,
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()

    val task = remember(tasks, taskId) { tasks.find { it.id == taskId } }
    val accents = LocalYataAccents.current

    // Bottom sheet state
    var activeSheet by remember { mutableStateOf<DetailSheetType>(DetailSheetType.None) }

    // Subtask input state
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (task == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val taskList = remember(task, lists) { lists.find { it.id == task.listId } }
    val project = remember(task, projects) { projects.find { it.id == task.projectId } }
    val taskAssignees = remember(task, people) { task.assigneeIds.mapNotNull { pid -> people.find { it.id == pid } } }
    val ownTagIds = task.tagIds
    val inheritedTagIds = remember(task, projects) { task.inheritedTagIds(projects) }
    val ownTags = remember(ownTagIds, tags) { ownTagIds.mapNotNull { tid -> tags.find { it.id == tid } } }
    val inheritedTags = remember(inheritedTagIds, ownTagIds, tags) {
        inheritedTagIds.filter { it !in ownTagIds }.mapNotNull { tid -> tags.find { it.id == tid } }
    }

    val listColor = taskList?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Skip this occurrence (recurring tasks only) — advances the due date without completing it
                    if (task.recurrence != null && !task.done) {
                        IconButton(onClick = { viewModel.skipTaskOccurrence(task.id) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Skip this occurrence")
                        }
                    }
                    // Flag toggle
                    IconButton(onClick = { viewModel.toggleTaskFlag(task.id) }) {
                        Icon(
                            imageVector = if (task.flag) Icons.Default.Flag else Icons.Default.OutlinedFlag,
                            contentDescription = "Flag",
                            tint = if (task.flag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Duplicate — clones the task (new id, done reset), keeps everything else
                    IconButton(onClick = {
                        viewModel.duplicateTask(task.id)
                        scope.launch { snackbarHostState.showSnackbar("Task duplicated") }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate task")
                    }
                    // Delete/Archive — deletion is deferred until the Undo snackbar times out,
                    // so the coroutine must outlive this composable's own scope (it navigates
                    // back only once the delete actually happens).
                    IconButton(onClick = {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Task deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.Dismissed) {
                                viewModel.deleteTask(task)
                                onNavigateBack()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete task")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Check + Title Row
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpringyCheck(
                        checked = task.done,
                        onCheckedChange = { viewModel.toggleTaskDone(task.id) {} },
                        color = listColor,
                        size = 28.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = task.title,
                        color = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                }
            }

            // 2. Meta rows container (surfaceContainerLow, 16dp radius)
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetaRowItem(
                            icon = Icons.Default.Today,
                            label = "Due Date",
                            value = com.mj.yata.util.TaskScheduleUtils.formatDueDateTime(task.due, task.time),
                            onClick = { activeSheet = DetailSheetType.ScheduleEditor }
                        )

                        MetaRowItem(
                            icon = Icons.Default.Notifications,
                            label = "Reminder",
                            value = com.mj.yata.util.TaskScheduleUtils.formatReminder(task.reminder),
                            valueColor = if (task.reminder != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { activeSheet = DetailSheetType.ReminderPicker }
                        )

                        val repeatsVal = task.recurrence?.let {
                            com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)
                        } ?: "Does not repeat"
                        MetaRowItem(
                            icon = Icons.Default.Repeat,
                            label = "Repeats",
                            value = repeatsVal,
                            valueColor = if (task.recurrence != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { activeSheet = DetailSheetType.RecurrenceBuilder }
                        )

                        if (projectsFeatureEnabled) {
                            MetaRowItem(
                                icon = Icons.Default.Layers,
                                label = "Project",
                                value = project?.name ?: "None",
                                onClick = { activeSheet = DetailSheetType.ProjectPicker }
                            )
                        }

                        MetaRowItem(
                            icon = Icons.Default.Folder,
                            label = "List",
                            value = taskList?.name ?: "None",
                            swatchColor = listColor,
                            onClick = { activeSheet = DetailSheetType.ListPicker }
                        )

                        // Priority
                        MetaRowItem(
                            icon = Icons.Default.Flag,
                            label = "Priority",
                            value = task.priority.uppercase(),
                            rightContent = { PriorityBars(priority = task.priority) },
                            onClick = { viewModel.cycleTaskPriority(task.id) }
                        )

                        // Section (Morning / Afternoon bucket on the Today tab)
                        MetaRowItem(
                            icon = Icons.Default.WbSunny,
                            label = "Section",
                            value = task.section,
                            onClick = {
                                viewModel.upsertTask(task.copy(section = if (task.section == "Morning") "Afternoon" else "Morning"))
                            }
                        )
                    }
                }
            }

            // 3. Assignees section
            if (peopleFeatureEnabled) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assigned to",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        taskAssignees.forEach { person ->
                            InputChip(
                                selected = true,
                                onClick = { activeSheet = DetailSheetType.AssigneePicker },
                                label = { Text(person.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(accents.getAccent(person.color), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            person.initials,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                        // Dashed add assignee
                        InputChip(
                            selected = false,
                            onClick = { activeSheet = DetailSheetType.AssigneePicker },
                            label = { Text("Assign...") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // 4. Tags section
            if (tagsFeatureEnabled) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Inherited from the project — live-synced, not removable here.
                        inheritedTags.forEach { tag ->
                            TagChip(name = tag.name, accentKey = tag.color)
                        }
                        ownTags.forEach { tag ->
                            TagChip(
                                name = tag.name,
                                accentKey = tag.color,
                                onRemoveClick = {
                                    val newTags = task.tagIds.filter { it != tag.id }
                                    viewModel.upsertTask(task.copy(tagIds = newTags))
                                }
                            )
                        }
                        // Dashed add tag
                        InputChip(
                            selected = false,
                            onClick = { activeSheet = DetailSheetType.TagPicker },
                            label = { Text("Tag...") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (inheritedTags.isNotEmpty()) {
                        Text(
                            text = "Tags with no ✕ come from this task's project and stay in sync automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 5. Subtasks section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val subtasks = task.subtasks
                    val subTotal = subtasks.size
                    val subDone = subtasks.count { it.done }
                    val subProgress = if (subTotal > 0) subDone.toFloat() / subTotal else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtasks",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$subDone/$subTotal done",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    if (subTotal > 0) {
                        LinearProgressIndicator(
                            progress = subProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    val topLevelSubtasks = remember(subtasks) { subtasks.filter { it.parentSubtaskId == null }.sortedBy { it.sortOrder } }
                    val childrenByParent = remember(subtasks) { subtasks.filter { it.parentSubtaskId != null }.groupBy { it.parentSubtaskId } }

                    fun toggleSubtask(id: String, done: Boolean) {
                        val updated = subtasks.map { if (it.id == id) it.copy(done = done) else it }
                        viewModel.upsertTask(task.copy(subtasks = updated))
                    }

                    fun deleteSubtask(id: String) {
                        // Cascade: dropping a parent also drops its (depth-1) children.
                        val updated = subtasks.filter { it.id != id && it.parentSubtaskId != id }
                        viewModel.upsertTask(task.copy(subtasks = updated))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topLevelSubtasks.forEach { sub ->
                            var addingChild by remember(sub.id) { mutableStateOf(false) }
                            var childTitle by remember(sub.id) { mutableStateOf("") }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleSubtask(sub.id, !sub.done) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Checkbox(checked = sub.done, onCheckedChange = { toggleSubtask(sub.id, it) })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sub.title,
                                    color = if (sub.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { addingChild = !addingChild }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add sub-item", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { deleteSubtask(sub.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete subtask", modifier = Modifier.size(16.dp))
                                }
                            }

                            // Depth-1 children — no further nesting allowed, so no "add child" here.
                            childrenByParent[sub.id]?.sortedBy { it.sortOrder }?.forEach { child ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp)
                                        .clickable { toggleSubtask(child.id, !child.done) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(checked = child.done, onCheckedChange = { toggleSubtask(child.id, it) })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = child.title,
                                        color = if (child.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (child.done) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { deleteSubtask(child.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete sub-item", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            if (addingChild) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    OutlinedTextField(
                                        value = childTitle,
                                        onValueChange = { childTitle = it },
                                        placeholder = { Text("Add a sub-item...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (childTitle.isNotBlank()) {
                                                val siblingCount = childrenByParent[sub.id]?.size ?: 0
                                                val newChild = Subtask(
                                                    id = "sub_" + UUID.randomUUID().toString(),
                                                    title = childTitle.trim(),
                                                    done = false,
                                                    parentSubtaskId = sub.id,
                                                    sortOrder = siblingCount
                                                )
                                                viewModel.upsertTask(task.copy(subtasks = subtasks + newChild))
                                                childTitle = ""
                                                addingChild = false
                                            }
                                        },
                                        enabled = childTitle.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add sub-item")
                                    }
                                }
                            }
                        }

                        // Add subtask input field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text("Add a subtask...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        val newSub = Subtask(
                                            id = "sub_" + UUID.randomUUID().toString(),
                                            title = newSubtaskTitle.trim(),
                                            done = false,
                                            sortOrder = topLevelSubtasks.size
                                        )
                                        viewModel.upsertTask(task.copy(subtasks = subtasks + newSub))
                                        newSubtaskTitle = ""
                                    }
                                },
                                enabled = newSubtaskTitle.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add subtask")
                            }
                        }
                    }
                }
            }

            // 6. Notes card — tap to edit raw text, tap away to render as markdown.
            item {
                var isEditingNotes by remember(task.id) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isEditingNotes || task.notes.isNullOrBlank()) {
                        OutlinedTextField(
                            value = task.notes ?: "",
                            onValueChange = { viewModel.upsertTask(task.copy(notes = it)) },
                            placeholder = { Text("Tap to add notes... (supports markdown)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                                .onFocusChanged { if (!it.isFocused) isEditingNotes = false },
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        MarkdownText(
                            markdown = task.notes ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isEditingNotes = true }
                                .padding(12.dp)
                        )
                    }
                }
            }

            // 7. Comments card
            item {
                val comments by remember(task.id) { viewModel.getCommentsForTask(task.id) }.collectAsState()
                var newComment by remember { mutableStateOf("") }
                val peopleById = remember(people) { people.associateBy { it.id } }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newComment,
                            onValueChange = { newComment = it },
                            placeholder = { Text("Add a comment...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newComment.isNotBlank()) {
                                    viewModel.addComment(task.id, newComment.trim())
                                    newComment = ""
                                }
                            },
                            enabled = newComment.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Post comment")
                        }
                    }
                    comments.forEach { comment ->
                        val author = comment.authorId?.let { peopleById[it] }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = comment.body,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = listOfNotNull(
                                        author?.let { if (it.isMe) "You" else it.name },
                                        com.mj.yata.util.TaskScheduleUtils.formatDueDate(
                                            java.time.Instant.ofEpochMilli(comment.createdAt)
                                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                                        )
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteComment(comment) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete comment", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Sheets Router
    if (activeSheet != DetailSheetType.None) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = DetailSheetType.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            when (activeSheet) {
                DetailSheetType.ScheduleEditor -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Due date and time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LocalScheduleChip("Today", task.due == java.time.LocalDate.now().toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().toString()))
                            }
                            LocalScheduleChip("Tomorrow", task.due == java.time.LocalDate.now().plusDays(1).toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusDays(1).toString()))
                            }
                            LocalScheduleChip("Next week", task.due == java.time.LocalDate.now().plusWeeks(1).toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusWeeks(1).toString()))
                            }
                            LocalScheduleChip("No due date", task.due == null) {
                                viewModel.upsertTask(task.copy(due = null, time = null, reminder = null))
                                activeSheet = DetailSheetType.None
                            }
                            LocalScheduleChip("Pick date", false) {
                                showDatePicker = true
                            }
                        }

                        if (task.due == null) {
                            LocalPanelHint("Pick a due date to unlock time and reminder options.")
                        } else {
                            Text(
                                text = "Time",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LocalScheduleChip("9:00 AM", task.time == "9:00 AM") { viewModel.upsertTask(task.copy(time = "9:00 AM")) }
                                LocalScheduleChip("12:00 PM", task.time == "12:00 PM") { viewModel.upsertTask(task.copy(time = "12:00 PM")) }
                                LocalScheduleChip("6:00 PM", task.time == "6:00 PM") { viewModel.upsertTask(task.copy(time = "6:00 PM")) }
                                LocalScheduleChip("Custom time", false) { showTimePicker = true }
                                LocalScheduleChip("Clear", task.time == null) { viewModel.upsertTask(task.copy(time = null)) }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { activeSheet = DetailSheetType.None }) {
                                Text("Done")
                            }
                        }
                    }
                }
                DetailSheetType.ReminderPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (task.due == null) {
                            LocalPanelHint("Pick a due date before setting a reminder.")
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LocalScheduleChip("None", task.reminder == null) {
                                    viewModel.upsertTask(task.copy(reminder = null))
                                    activeSheet = DetailSheetType.None
                                }
                                com.mj.yata.util.TaskScheduleUtils.reminderOptions.forEach { option ->
                                    LocalScheduleChip(option, task.reminder == option) {
                                        viewModel.upsertTask(task.copy(reminder = option))
                                        activeSheet = DetailSheetType.None
                                    }
                                }
                            }
                        }
                    }
                }
                DetailSheetType.RecurrenceBuilder -> RecurrenceSheet(
                    initialRecurrence = task.recurrence,
                    onSave = { rec ->
                        viewModel.upsertTask(task.copy(recurrence = rec))
                        activeSheet = DetailSheetType.None
                    },
                    onDismiss = { activeSheet = DetailSheetType.None }
                )
                DetailSheetType.ListPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Select list", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            YataSelectChip(
                                label = "None",
                                selected = task.listId == null,
                                onClick = {
                                    viewModel.upsertTask(task.copy(listId = null))
                                    activeSheet = DetailSheetType.None
                                }
                            )
                            lists.forEach { list ->
                                val color = accents.getAccent(list.color)
                                YataSelectChip(
                                    label = list.name,
                                    selected = list.id == task.listId,
                                    onClick = {
                                        viewModel.upsertTask(task.copy(listId = list.id))
                                        activeSheet = DetailSheetType.None
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.ProjectPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Select project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            YataSelectChip(
                                label = "None",
                                selected = task.projectId == null,
                                onClick = {
                                    viewModel.upsertTask(task.copy(projectId = null))
                                    activeSheet = DetailSheetType.None
                                }
                            )
                            projects.forEach { pr ->
                                val color = accents.getAccent(pr.color)
                                YataSelectChip(
                                    label = pr.name,
                                    selected = pr.id == task.projectId,
                                    onClick = {
                                        viewModel.upsertTask(task.copy(projectId = pr.id))
                                        activeSheet = DetailSheetType.None
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.AssigneePicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Assign people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            people.forEach { person ->
                                val isAssigned = task.assigneeIds.contains(person.id)
                                YataSelectChip(
                                    label = if (person.isMe) "You" else person.name,
                                    selected = isAssigned,
                                    onClick = {
                                        val newAss = if (isAssigned) task.assigneeIds - person.id else task.assigneeIds + person.id
                                        viewModel.upsertTask(task.copy(assigneeIds = newAss))
                                    },
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    leading = {
                                        Box(
                                            modifier = Modifier.size(22.dp).background(accents.getAccent(person.color), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(person.initials, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    height = 38.dp
                                )
                            }
                        }
                    }
                }
                DetailSheetType.TagPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Select tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                val isSelected = task.tagIds.contains(tag.id)
                                val color = accents.getAccent(tag.color)
                                YataSelectChip(
                                    label = tag.name,
                                    selected = isSelected,
                                    onClick = {
                                        val newTags = if (isSelected) task.tagIds - tag.id else task.tagIds + tag.id
                                        viewModel.upsertTask(task.copy(tagIds = newTags))
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.None -> Unit
            }
        }
    }
    if (showDatePicker) {
        YataDatePickerDialog(
            initialDate = task.due,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.upsertTask(task.copy(due = it))
                showDatePicker = false
            }
        )
    }

    YataTimePickerLauncher(
        show = showTimePicker,
        initialTime = task.time,
        onDismiss = { showTimePicker = false },
        onConfirm = {
            viewModel.upsertTask(task.copy(time = it))
        }
    )
}

@Composable
private fun LocalScheduleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun LocalPanelHint(text: String) {
    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetaRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    swatchColor: Color? = null,
    rightContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        
        if (swatchColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(swatchColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (rightContent != null) {
            rightContent()
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}





