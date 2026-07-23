package com.mj.yata.ui.screen.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.sheets.*
import com.mj.yata.util.taskMatchesQuery
import com.mj.yata.util.sortedByMode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonDetailScreen(
    viewModel: MainViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsState()
    val assignedTasks by remember(personId) { viewModel.getTasksForPerson(personId) }.collectAsState(initial = emptyList())
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val personGroups by viewModel.personGroups.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()

    val person = remember(people, personId) { people.find { it.id == personId } }
    val accents = LocalYataAccents.current

    var openExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(true) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hideCompleted by viewModel.hideCompletedPerson.collectAsState()
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Task deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                viewModel.deleteTask(task)
            }
        }
    }

    val openChevronRotation by animateFloatAsState(
        targetValue = if (openExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "openChevronRotation"
    )
    val completedChevronRotation by animateFloatAsState(
        targetValue = if (completedExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "completedChevronRotation"
    )

    if (person == null) {
        com.mj.yata.ui.widgets.ListDetailShimmer()
        return
    }

    val personColor = accents.getAccent(person.color)
    com.mj.yata.ui.theme.StatusBarColor(
        personColor.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.background)
    )
    var sortMode by remember { mutableStateOf(com.mj.yata.util.TaskSortMode.MANUAL) }
    var activeStatFilter by remember { mutableStateOf<com.mj.yata.ui.widgets.HeroStatKind?>(null) }
    val today = remember { java.time.LocalDate.now() }
    // Unfiltered-by-stat count, used by the hero's own primary text/progress so those always
    // reflect the true totals — only the rendered list below is narrowed by activeStatFilter.
    val openTasks = remember(assignedTasks, searchQuery, sortMode) {
        assignedTasks.filter { !it.done && taskMatchesQuery(it, searchQuery) }.sortedByMode(sortMode)
    }
    val displayedOpenTasks = remember(openTasks, activeStatFilter) {
        openTasks.filter { activeStatFilter == null || activeStatFilter!!.matches(it, today) }
    }
    val completedTasks = remember(assignedTasks, searchQuery) { assignedTasks.filter { it.done && taskMatchesQuery(it, searchQuery) } }

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
                    com.mj.yata.ui.widgets.DeleteUndoSnackbar(data)
                } else {
                    Snackbar(data)
                }
            }
        },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 2,
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
                            placeholder = { Text("Search ${person.name}'s tasks") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            person.name,
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
                            Icon(Icons.Default.Search, contentDescription = "Search this person's tasks")
                        }
                        com.mj.yata.ui.widgets.TaskSortMenuButton(
                            current = sortMode,
                            onSelect = { sortMode = it }
                        )
                        IconButton(onClick = { viewModel.setHideCompletedPerson(!hideCompleted) }) {
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
                                text = { Text("Edit person") },
                                onClick = {
                                    showMenu = false
                                    isEditSheetOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            if (!person.isMe) {
                                DropdownMenuItem(
                                    text = { Text(if (person.archived) "Unarchive person" else "Archive person") },
                                    onClick = {
                                        showMenu = false
                                        if (person.archived) {
                                            viewModel.setPersonArchived(person, false)
                                        } else {
                                            showDeleteDialog = true
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = personColor.copy(alpha = 0.16f)
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { isEditSheetOpen = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit person")
                }
                FloatingActionButton(
                    onClick = { isNewTaskSheetOpen = true },
                    containerColor = personColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add task for ${person.name}")
                }
            }
        }
    ) { innerPadding ->
        val listsById = remember(lists) { lists.associateBy { it.id } }
        val peopleById = remember(people) { people.associateBy { it.id } }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Hero section — bigger avatar + progress ring, plus overdue/high-priority/due-today
            // stats in addition to the open/completed count, all derived from assignedTasks (not
            // search-filtered, unlike openTasks/completedTasks below) so these always reflect the
            // person's real workload regardless of an active search query.
            item {
                val overdueCount = remember(assignedTasks) {
                    com.mj.yata.util.AnalyticsUtils.overdueCount(assignedTasks)
                }
                val highPriorityCount = remember(assignedTasks) {
                    assignedTasks.count { !it.done && it.priority == "high" }
                }
                val todayStr = remember { java.time.LocalDate.now().toString() }
                val dueTodayCount = remember(assignedTasks, todayStr) {
                    assignedTasks.count { !it.done && it.due == todayStr }
                }
                val totalCount = assignedTasks.size
                val doneCount = assignedTasks.count { it.done }
                val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

                com.mj.yata.ui.widgets.EntityHeroSection(
                    accentColor = personColor,
                    progress = progress,
                    primaryText = "${openTasks.size} open · ${completedTasks.size} completed",
                    overdueCount = overdueCount,
                    highPriorityCount = highPriorityCount,
                    dueTodayCount = dueTodayCount,
                    leadingContent = {
                        PersonAvatar(
                            initials = person.initials,
                            accentKey = person.color,
                            photoUri = person.photoUri,
                            size = 72.dp
                        )
                    },
                    activeFilter = activeStatFilter,
                    onStatClick = { activeStatFilter = if (activeStatFilter == it) null else it }
                )
            }

            // 1.5 Workload trend — overdue count as it stood at the end of each of the last 7
            // days, so a manager can see whether this person's backlog is growing or shrinking,
            // not just the live snapshot above.
            item {
                val trend = remember(assignedTasks) {
                    val today = java.time.LocalDate.now()
                    (6 downTo 0).map { offset ->
                        val day = today.minusDays(offset.toLong())
                        val overdueCount = assignedTasks.count { task ->
                            val due = task.due?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@count false
                            val completedDay = task.completedAt?.let {
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                            due.isBefore(day) && (completedDay == null || completedDay.isAfter(day))
                        }
                        day to overdueCount
                    }
                }
                val maxOverdue = (trend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "OVERDUE TREND (7 DAYS)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trend.forEach { (day, count) ->
                            val heightFraction = count.toFloat() / maxOverdue
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(if (count == 0) 0.06f else heightFraction.coerceAtLeast(0.06f))
                                    .background(
                                        if (count > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // 2. Open Tasks Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openExpanded = !openExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(
                                text = displayedOpenTasks.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(openChevronRotation)
                    )
                }
            }

            if (activeStatFilter != null) {
                item {
                    com.mj.yata.ui.widgets.ActiveFilterBanner(
                        kind = activeStatFilter!!,
                        onClear = { activeStatFilter = null }
                    )
                }
            }

            if (openExpanded) {
                if (displayedOpenTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No open tasks assigned.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(displayedOpenTasks, key = { it.id }, contentType = { "task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById) {
                            task.assigneeIds.mapNotNull { pid -> peopleById[pid] }
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
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                            ),
                            onCommentClick = { pendingCommentTask = task },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRenameTask = { viewModel.renameTask(task.id, it) },
                            density = taskRowDensity,
                            onSwipeToDelete = { deleteTaskWithUndo(task) },
                            showDueDate = true
                        )
                    }
                }
            }

            if (!hideCompleted) {
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            // 3. Completed Tasks Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { completedExpanded = !completedExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Completed Tasks",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(
                                text = completedTasks.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(completedChevronRotation)
                    )
                }
            }

            if (completedExpanded) {
                if (completedTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No completed tasks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(completedTasks, key = { it.id }, contentType = { "task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById) {
                            task.assigneeIds.mapNotNull { pid -> peopleById[pid] }
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
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                            ),
                            onCommentClick = { pendingCommentTask = task },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRenameTask = { viewModel.renameTask(task.id, it) },
                            density = taskRowDensity,
                            onSwipeToDelete = { deleteTaskWithUndo(task) },
                            showDueDate = true
                        )
                    }
                }
            }
            } // !hideCompleted
        }
    }

    if (isNewTaskSheetOpen) {
        val allTasks by viewModel.tasks.collectAsState()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isNewTaskSheetOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            NewTaskSheet(
                lists = lists,
                projects = projects,
                people = people,
                tags = tags,
                tasks = allTasks,
                initialAssigneeId = person.id,
                onAddTask = { title, listId, priority, assignees, taskTags, rec, due, time, reminder, section, taskProjectId, notes, subtasks ->
                    viewModel.addTask(title, listId, priority, assignees, taskTags, rec, notes = notes, due = due, time = time, reminder = reminder, section = section, projectId = taskProjectId, subtasks = subtasks)
                    isNewTaskSheetOpen = false
                },
                onGoToExistingTask = { id ->
                    isNewTaskSheetOpen = false
                    onNavigateToTaskDetail(id)
                },
                onCreateTag = { id, name, color ->
                    viewModel.upsertTag(Tag(id = id, name = name, color = color))
                },
                onCreatePerson = { id, name, color ->
                    viewModel.upsertPerson(Person(id = id, name = name, initials = initialsFor(name), color = color, isMe = false))
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
            PersonEditorSheet(
                initialName = person.name,
                initialColor = person.color,
                initialGroupId = person.groupId,
                initialPhotoUri = person.photoUri,
                groups = personGroups,
                onSave = { newName, newColor, newGroupId, newPhotoUri ->
                    val initials = newName.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    val updatedInitials = if (initials.isEmpty()) "P" else initials
                    viewModel.upsertPerson(person.copy(name = newName, color = newColor, initials = updatedInitials, groupId = newGroupId, photoUri = newPhotoUri))
                    isEditSheetOpen = false
                },
                onCreateGroup = { id, name, color ->
                    viewModel.upsertPersonGroup(com.mj.yata.domain.model.PersonGroup(id = id, name = name, color = color))
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Archive person?") },
            text = { Text("Their past assigned tasks and stats stay. They're hidden from the People tab — you can unarchive them anytime from this same menu.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.setPersonArchived(person, true)
                        onNavigateBack()
                    }
                ) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
}
