package com.mj.yata.ui.screen.taskdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.animation.animateColorAsState
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import java.util.UUID

/** Equal-width rectangular (not pill-shaped) toggle for the Subtasks/Notes/Comments chip row —
 * highlighted while its section is visible, plain otherwise. */
@Composable
private fun SectionToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "sectionToggleBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "sectionToggleText"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

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
    val taskState by remember(taskId) { viewModel.getTaskById(taskId) }.collectAsState(initial = null)
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val allTasks by viewModel.tasks.collectAsState()

    val accents = LocalYataAccents.current

    // Bottom sheet state
    var activeSheet by remember { mutableStateOf<DetailSheetType>(DetailSheetType.None) }

    // Subtask input state
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    val task = taskState
    if (task == null) {
        TaskDetailShimmer()
        return
    }

    // Subtasks/Notes/Comments section visibility — toggled via the chip row above them.
    // Defaults to whichever sections actually have content, instead of always showing all
    // three regardless of whether there's anything in them.
    val comments by remember(task.id) { viewModel.getCommentsForTask(task.id) }.collectAsState()
    var showSubtasks by remember(task.id) { mutableStateOf(task.subtasks.isNotEmpty()) }
    var showNotes by remember(task.id) { mutableStateOf(!task.notes.isNullOrBlank()) }
    var showComments by remember(task.id) { mutableStateOf(comments.isNotEmpty()) }
    // Comments load asynchronously (unlike subtasks/notes, which are already on `task`), so the
    // very first composition almost always sees an empty placeholder list before the real query
    // result arrives — keep syncing the default to the real data until the user manually toggles
    // the chip themselves, at which point their choice wins even if the comment count changes
    // later (e.g. adding a comment while the section is manually hidden shouldn't force it open).
    var userToggledComments by remember(task.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showToolbarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 80)
        }
    }
    LaunchedEffect(comments) {
        if (!userToggledComments) showComments = comments.isNotEmpty()
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
    val recurrenceHistory = remember(task, allTasks) {
        if (task.recurrence == null) {
            emptyList()
        } else {
            allTasks
                .filter {
                    it.id != task.id &&
                        it.done &&
                        it.completedAt != null &&
                        it.recurrence == null &&
                        it.title == task.title &&
                        it.projectId == task.projectId &&
                        it.listId == task.listId
                }
                .sortedByDescending { it.completedAt }
                .take(6)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                if (data.visuals.actionLabel == "Undo") {
                    DeleteUndoSnackbar(data)
                } else {
                    Snackbar(data)
                }
            }
        },
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
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showToolbarTitle,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Skip this occurrence (recurring tasks only) — advances the due date without
                    // completing it. Deferred until the Undo snackbar times out, same pattern as delete.
                    if (task.recurrence != null && !task.done) {
                        IconButton(onClick = {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Occurrence skipped",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.Dismissed) {
                                    viewModel.skipTaskOccurrence(task.id)
                                }
                            }
                        }) {
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
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Check + Title Row — tap the title to rename it in place.
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

                    var isEditingTitle by remember(task.id) { mutableStateOf(false) }
                    var titleHasFocusedOnce by remember(task.id) { mutableStateOf(false) }
                    val titleFocusRequester = remember(task.id) { FocusRequester() }
                    val titleColor = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    val titleStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        color = titleColor,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (isEditingTitle) {
                        // Bound to a local buffer, not task.title directly — task.title only
                        // updates after a round trip through the DB (write -> Room re-query ->
                        // Flow emission -> recompose), and fast typing outraces that, dropping
                        // characters. The buffer reflects every keystroke instantly; the DB write
                        // still happens on each change, it just isn't what the field displays.
                        var titleBuffer by remember(task.id) { mutableStateOf(task.title) }
                        LaunchedEffect(Unit) {
                            titleHasFocusedOnce = false
                            titleFocusRequester.requestFocus()
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = titleBuffer,
                            onValueChange = {
                                titleBuffer = it
                                if (it.isNotBlank()) viewModel.upsertTask(task.copy(title = it))
                            },
                            textStyle = titleStyle,
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(titleFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        titleHasFocusedOnce = true
                                    } else if (titleHasFocusedOnce) {
                                        isEditingTitle = false
                                    }
                                }
                        )
                    } else {
                        Text(
                            text = task.title,
                            style = titleStyle,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isEditingTitle = true }
                        )
                    }
                }
            }

            // 2. Meta rows — each its own surfaceContainerLow card (per handoff's MetaRow)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaRowItem(
                        icon = Icons.Default.Today,
                        label = "Due Date",
                        value = com.mj.yata.util.TaskScheduleUtils.formatDueDateTime(task.due, task.time),
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { activeSheet = DetailSheetType.ScheduleEditor }
                    )

                    // Carry forward — only makes sense for an open task that's already due
                    // today or overdue, not one that's done or scheduled for the future.
                    val canCarryForward = !task.done && task.due != null &&
                        task.due <= java.time.LocalDate.now().toString()
                    if (canCarryForward) {
                        YataSelectChip(
                            label = "Carry forward to next day",
                            selected = true,
                            onClick = {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusDays(1).toString()))
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            leading = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            showCheck = false
                        )
                    }

                    MetaRowItem(
                        icon = Icons.Default.Notifications,
                        label = "Reminder",
                        value = com.mj.yata.util.TaskScheduleUtils.formatReminder(task.reminder),
                        accentColor = if (task.reminder != null) MaterialTheme.colorScheme.secondary else null,
                        onClick = { activeSheet = DetailSheetType.ReminderPicker }
                    )

                    val repeatsVal = task.recurrence?.let {
                        com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)
                    } ?: "Does not repeat"
                    MetaRowItem(
                        icon = Icons.Default.Repeat,
                        label = "Repeats",
                        value = repeatsVal,
                        accentColor = if (task.recurrence != null) MaterialTheme.colorScheme.tertiary else null,
                        onClick = { activeSheet = DetailSheetType.RecurrenceBuilder }
                    )

                    if (recurrenceHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Recurring history",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            recurrenceHistory.forEach { historyTask ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.mj.yata.util.TaskScheduleUtils.formatDueDate(historyTask.due),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = com.mj.yata.util.TaskScheduleUtils.formatCompletedAt(historyTask.completedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

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

            // Section toggle chips — show/hide Subtasks/Notes/Comments independently, each
            // section animating open/closed instead of just popping in and out.
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SectionToggleChip("Subtasks", showSubtasks, { showSubtasks = !showSubtasks }, Modifier.weight(1f))
                    SectionToggleChip("Notes", showNotes, { showNotes = !showNotes }, Modifier.weight(1f))
                    SectionToggleChip("Comments", showComments, { showComments = !showComments; userToggledComments = true }, Modifier.weight(1f))
                }
            }

            // 5. Subtasks section
            item {
                AnimatedVisibility(
                    visible = showSubtasks,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val subtasks = task.subtasks
                    val subTotal = subtasks.size
                    val subDone = subtasks.count { it.done }
                    val subProgress = if (subTotal > 0) subDone.toFloat() / subTotal else 0f
                    val animatedSubProgress by animateFloatAsState(
                        targetValue = subProgress,
                        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
                        label = "subtaskProgressAnimation"
                    )

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
                            progress = { animatedSubProgress },
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
                                SpringyCheck(checked = sub.done, onCheckedChange = { toggleSubtask(sub.id, it) }, color = listColor, size = 20.dp)
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
                                    SpringyCheck(checked = child.done, onCheckedChange = { toggleSubtask(child.id, it) }, color = listColor, size = 18.dp)
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
                                        shape = RoundedCornerShape(12.dp)
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
                                shape = RoundedCornerShape(12.dp)
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
            }

            // 6. Notes card — tap to edit raw text, tap away to render as markdown.
            item {
                AnimatedVisibility(
                    visible = showNotes,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
                ) {
                var isEditingNotes by remember(task.id) { mutableStateOf(false) }
                // onFocusChanged fires once immediately on mount reporting isFocused=false (before
                // anything has actually requested focus) — without this guard, that spurious first
                // callback closed edit mode the instant it opened, so tapping notes appeared to do
                // nothing. Only a real loss of focus (after having genuinely gained it) should close it.
                var hasFocusedOnce by remember(task.id) { mutableStateOf(false) }
                val notesFocusRequester = remember(task.id) { FocusRequester() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isEditingNotes || task.notes.isNullOrBlank()) {
                        // Same local-buffer fix as the title field — task.notes only updates after
                        // a round trip through the DB, and fast typing outraces that otherwise.
                        var notesBuffer by remember(task.id) { mutableStateOf(task.notes ?: "") }
                        LaunchedEffect(isEditingNotes) {
                            if (isEditingNotes) {
                                hasFocusedOnce = false
                                notesFocusRequester.requestFocus()
                            }
                        }
                        OutlinedTextField(
                            value = notesBuffer,
                            onValueChange = {
                                notesBuffer = it
                                viewModel.upsertTask(task.copy(notes = it))
                            },
                            placeholder = { Text("Tap to add notes... (supports markdown)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(notesFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        // Also flips isEditingNotes true here, not just on the
                                        // explicit "tap to edit" path below — this field is also
                                        // shown by default whenever notes are blank (isEditingNotes
                                        // still false in that case). Without this, typing the
                                        // first character flips task.notes from blank to non-blank,
                                        // the outer `isEditingNotes || task.notes.isNullOrBlank()`
                                        // check goes false, and the field is yanked out from under
                                        // the user mid-keystroke and replaced with the read-only
                                        // markdown view.
                                        hasFocusedOnce = true
                                        isEditingNotes = true
                                    } else if (hasFocusedOnce) {
                                        isEditingNotes = false
                                    }
                                },
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        MarkdownText(
                            markdown = task.notes ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { isEditingNotes = true }
                                .padding(12.dp)
                        )
                    }
                }
                }
            }

            // 7. Comments card
            item {
                AnimatedVisibility(
                    visible = showComments,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
                ) {
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
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
                                        if (!com.mj.yata.util.TaskScheduleUtils.isPresetReminderInFuture(task.due, task.time, option)) {
                                            scope.launch { snackbarHostState.showSnackbar("That reminder time has already passed.") }
                                        } else {
                                            viewModel.upsertTask(task.copy(reminder = option))
                                            activeSheet = DetailSheetType.None
                                        }
                                    }
                                }
                                val customReminderSelected = task.reminder != null &&
                                    com.mj.yata.util.TaskScheduleUtils.parseTime(task.reminder) != null
                                LocalScheduleChip("Custom time", customReminderSelected) {
                                    showReminderTimePicker = true
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
                                        // Matches NewTaskSheet's project-selection behavior — a
                                        // project with its own due date carries it onto the task,
                                        // same as when the project is picked at creation time.
                                        val updated = if (pr.due != null) task.copy(projectId = pr.id, due = pr.due) else task.copy(projectId = pr.id)
                                        viewModel.upsertTask(updated)
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
            showTimePicker = false
        }
    )

    YataTimePickerLauncher(
        show = showReminderTimePicker,
        initialTime = task.reminder,
        onDismiss = { showReminderTimePicker = false },
        onConfirm = {
            showReminderTimePicker = false
            when {
                !com.mj.yata.util.TaskScheduleUtils.isCustomReminderBeforeDue(it, task.time) -> {
                    scope.launch { snackbarHostState.showSnackbar("Reminder must be before the due time.") }
                }
                !com.mj.yata.util.TaskScheduleUtils.isReminderTimeInFuture(task.due, it) -> {
                    scope.launch { snackbarHostState.showSnackbar("That reminder time has already passed today.") }
                }
                else -> {
                    viewModel.upsertTask(task.copy(reminder = it))
                    activeSheet = DetailSheetType.None
                }
            }
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

/** Handoff's MetaRow: own surfaceContainerLow card per row, 32dp icon tile, uppercase label over value. */
@Composable
fun MetaRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    swatchColor: Color? = null,
    rightContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (swatchColor != null) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(swatchColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        color = accentColor ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (rightContent != null) {
                rightContent()
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}





