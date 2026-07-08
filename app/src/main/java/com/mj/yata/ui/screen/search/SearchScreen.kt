package com.mj.yata.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.effectiveTags
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.widgets.TaskRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

/** One-tap filters shown before/alongside a text query — each is a self-contained predicate so
 * toggling several combines them with AND (narrows further, doesn't union). */
private enum class SmartFilter(val label: String) {
    OVERDUE("Overdue") ,
    HIGH_PRIORITY("High priority"),
    FLAGGED("Flagged"),
    DUE_TODAY("Due today"),
    NO_DUE_DATE("No due date");

    fun matches(task: Task, today: LocalDate): Boolean = when (this) {
        OVERDUE -> !task.done && task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
        HIGH_PRIORITY -> task.priority == "high"
        FLAGGED -> task.flag
        DUE_TODAY -> task.due == today.toString()
        NO_DUE_DATE -> task.due == null
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()

    var query by remember { mutableStateOf("") }
    // The text field itself always reflects `query` immediately; filtering runs against
    // `debouncedQuery`, which lags by a beat so a long task list with heavy per-row composables
    // doesn't re-filter synchronously on every single keystroke.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(200)
        debouncedQuery = query
    }
    val activeFilters = remember { mutableStateListOf<SmartFilter>() }

    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkAssignSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Swipe-to-delete on a single task reuses the same deferred-Undo-snackbar pattern as the
    // bulk-delete dialog below, just for one id at a time.
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

    val peopleById = remember(people) { people.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    val listsById = remember(lists) { lists.associateBy { it.id } }

    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()

    val filteredTasks = remember(tasks, debouncedQuery, activeFilters.toList(), peopleById, tagsById) {
        if (debouncedQuery.isBlank() && activeFilters.isEmpty()) {
            emptyList()
        } else {
            val today = LocalDate.now()
            tasks.filter { task ->
                val matchesQuery = debouncedQuery.isBlank() ||
                    task.title.contains(debouncedQuery, ignoreCase = true) ||
                    task.notes?.contains(debouncedQuery, ignoreCase = true) == true ||
                    task.subtasks.any { it.title.contains(debouncedQuery, ignoreCase = true) } ||
                    task.tagIds.any { tagsById[it]?.name?.contains(debouncedQuery, ignoreCase = true) == true } ||
                    task.assigneeIds.any { peopleById[it]?.name?.contains(debouncedQuery, ignoreCase = true) == true }
                matchesQuery && activeFilters.all { it.matches(task, today) }
            }
        }
    }

    if (selectionMode) {
        val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
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
                com.mj.yata.ui.sheets.TaskSelectionTopBar(
                    selectedCount = selectedIds.size,
                    onCancel = { selectedIds.clear() },
                    onComplete = { viewModel.bulkCompleteTasks(selectedIds.toList()); selectedIds.clear() },
                    onAddTag = { showBulkTagSheet = true },
                    onMove = { showBulkMoveSheet = true },
                    onDuplicate = { viewModel.bulkDuplicateTasks(selectedIds.toList()); selectedIds.clear() },
                    onDelete = { showBulkDeleteDialog = true },
                    onAssign = { showBulkAssignSheet = true },
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled,
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { innerPadding ->
            SearchResultsList(
                query = query,
                activeFilters = activeFilters,
                onToggleFilter = { if (activeFilters.contains(it)) activeFilters.remove(it) else activeFilters.add(it) },
                filteredTasks = filteredTasks,
                lists = lists,
                projects = projects,
                tags = tags,
                listsById = listsById,
                peopleById = peopleById,
                viewModel = viewModel,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onTaskClick = onNavigateToTaskDetail,
                onSwipeToDelete = ::deleteTaskWithUndo,
                tagsEnabled = tagsFeatureEnabled,
                peopleEnabled = peopleFeatureEnabled,
                modifier = modifier.padding(innerPadding)
            )
        }
    } else {
        // M3 SearchBar, permanently expanded since this is a dedicated search destination.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                active = true,
                onActiveChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tasks...") },
                leadingIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            ) {
                SearchResultsList(
                    query = query,
                    activeFilters = activeFilters,
                    onToggleFilter = { if (activeFilters.contains(it)) activeFilters.remove(it) else activeFilters.add(it) },
                    filteredTasks = filteredTasks,
                    lists = lists,
                    projects = projects,
                    tags = tags,
                    listsById = listsById,
                    peopleById = peopleById,
                    viewModel = viewModel,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onTaskClick = onNavigateToTaskDetail,
                    onSwipeToDelete = ::deleteTaskWithUndo,
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled
                )
            }
        }
    }

    if (showBulkTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkTagPickerSheet(
                tags = tags,
                onSelectTag = { tagId ->
                    viewModel.bulkAddTag(selectedIds.toList(), tagId)
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkAssignPersonSheet(
                people = people,
                onSelectPerson = { personId ->
                    viewModel.bulkAssignPerson(selectedIds.toList(), personId)
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkMoveSheet(
                projects = projects,
                lists = lists,
                onSelectProject = { projectId ->
                    viewModel.bulkSetProject(selectedIds.toList(), projectId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onSelectList = { listId ->
                    viewModel.bulkSetList(selectedIds.toList(), listId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onDismiss = { showBulkMoveSheet = false },
                projectsEnabled = projectsFeatureEnabled
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
                    val ids = selectedIds.toList()
                    selectedIds.clear()
                    showBulkDeleteDialog = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = if (ids.size == 1) "Task deleted" else "${ids.size} tasks deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.Dismissed) {
                            viewModel.bulkDeleteTasks(ids)
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    query: String,
    activeFilters: List<SmartFilter>,
    onToggleFilter: (SmartFilter) -> Unit,
    filteredTasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    tags: List<Tag>,
    listsById: Map<String, YataList>,
    peopleById: Map<String, Person>,
    viewModel: MainViewModel,
    selectionMode: Boolean,
    selectedIds: MutableList<String>,
    onTaskClick: (String) -> Unit,
    onSwipeToDelete: (Task) -> Unit = {},
    tagsEnabled: Boolean = true,
    peopleEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilters.contains(filter),
                        onClick = { onToggleFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }
        if (query.isBlank() && activeFilters.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type something to search, or pick a filter above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else if (filteredTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching tasks found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(filteredTasks, key = { it.id }) { task ->
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
                    onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
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
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                    ),
                    onCommentClick = { pendingCommentTask = task },
                    density = taskRowDensity,
                    onSwipeToDelete = { onSwipeToDelete(task) },
                    swipeEnabled = !selectionMode
                )
            }
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
}
