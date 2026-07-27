package com.mj.yata.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.archivedProjects
import com.mj.yata.domain.model.effectiveTags
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.widgets.TaskRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** One-tap filters shown before/alongside a text query — each is a self-contained predicate so
 * toggling several combines them with AND (narrows further, doesn't union). */
internal enum class SmartFilter(val label: String) {
    FOCUS("Focus mode"),
    MORNING_REVIEW("Morning review"),
    EVENING_REVIEW("Evening review"),
    STALE_TASKS("Stale nudges"),
    AT_RISK("At risk"),
    ASSIGNED_TO_ME("Assigned to me"),
    OVERDUE("Overdue") ,
    HIGH_PRIORITY("High priority"),
    FLAGGED("Flagged"),
    DUE_TODAY("Due today"),
    NO_DUE_DATE("No due date");

    fun matches(task: Task, today: LocalDate, myId: String): Boolean = when (this) {
        FOCUS -> !task.done && (task.flag || task.priority == "high" || task.due == today.toString() || task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true)
        MORNING_REVIEW -> !task.done && (task.due == today.toString() || task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true)
        EVENING_REVIEW -> !task.done && (task.due == today.plusDays(1).toString() || (task.due == null && task.priority != "none"))
        STALE_TASKS -> !task.done && task.due == null && task.time == null && task.recurrence == null && task.priority == "none" && !task.flag
        AT_RISK -> !task.done && (
            task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true ||
                (task.priority == "high" && task.due == null) ||
                (task.flag && task.due == null)
            )
        ASSIGNED_TO_ME -> task.assigneeIds.contains(myId)
        OVERDUE -> !task.done && task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
        HIGH_PRIORITY -> task.priority == "high"
        FLAGGED -> task.flag
        DUE_TODAY -> task.due == today.toString()
        NO_DUE_DATE -> task.due == null
    }
}

private fun List<SmartFilter>.encodedSmartFilterSet(): String =
    distinct().sortedBy { it.name }.joinToString(",") { it.name }

private fun String.toSmartFilters(): List<SmartFilter> =
    split(",").mapNotNull { name -> SmartFilter.entries.find { it.name == name } }

private fun String.smartFilterSetLabel(): String =
    toSmartFilters().joinToString(" + ") { it.label }

/** Natural-language phrases recognized in the search box, mapped to the same [SmartFilter]
 * chips a user could tap by hand — longer/more specific phrases first so e.g. "no due date"
 * claims itself whole before the shorter "no date" alternative would also match a substring
 * of it. */
internal val searchFilterPhrases = listOf(
    "no due date" to SmartFilter.NO_DUE_DATE,
    "no date" to SmartFilter.NO_DUE_DATE,
    "undated" to SmartFilter.NO_DUE_DATE,
    "high priority" to SmartFilter.HIGH_PRIORITY,
    "assigned to me" to SmartFilter.ASSIGNED_TO_ME,
    "due today" to SmartFilter.DUE_TODAY,
    "overdue" to SmartFilter.OVERDUE,
    "flagged" to SmartFilter.FLAGGED
)

internal data class ParsedSearchQuery(val filters: List<SmartFilter>, val residualText: String)

/** Strips recognized filter phrases out of the typed query and reports which [SmartFilter]s
 * they correspond to — the box itself always keeps showing exactly what was typed (this only
 * changes what actually gets searched/filtered), same "detect, don't rewrite the input" rule
 * NaturalLanguageParser follows for quick-add. */
internal fun parseSearchQuery(raw: String): ParsedSearchQuery {
    var remaining = raw
    val matched = mutableListOf<SmartFilter>()
    for ((phrase, filter) in searchFilterPhrases) {
        val regex = Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(remaining)) {
            matched.add(filter)
            remaining = regex.replace(remaining, " ")
        }
    }
    return ParsedSearchQuery(matched.distinct(), remaining.replace(Regex("\\s{2,}"), " ").trim())
}

private fun Task.matchesSearchText(
    query: String,
    peopleById: Map<String, Person>,
    tags: List<Tag>,
    projects: List<Project>
): Boolean {
    val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = buildString {
        append(title).append(' ')
        append(notes.orEmpty()).append(' ')
        assigneeIds.mapNotNull { peopleById[it]?.name }.forEach { append(it).append(' ') }
        effectiveTags(projects, tags).forEach { append(it.name).append(' ') }
        subtasks.forEach { append(it.title).append(' ') }
    }.lowercase()
    return terms.all { haystack.contains(it.lowercase()) }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    initialSmartFilterSet: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val archivedTasks by viewModel.archivedTasks.collectAsStateWithLifecycle()
    val deletedTasks by viewModel.deletedTasks.collectAsStateWithLifecycle()
    val taskRowDensity by viewModel.taskRowDensity.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    // The box always shows exactly what was typed — only the derived search text and filter
    // chips change. `parseSearchQuery` recognizes phrases like "high priority"/"overdue" and
    // reports them as SmartFilter chips to auto-activate, with the phrase itself excluded from
    // the plain-text search so e.g. "high priority report" both toggles the chip and searches
    // for "report".
    val parsedSearchQuery = remember(query) { parseSearchQuery(query) }
    val activeFilters = remember { mutableStateListOf<SmartFilter>() }
    LaunchedEffect(parsedSearchQuery.filters) {
        parsedSearchQuery.filters.forEach { filter -> if (filter !in activeFilters) activeFilters.add(filter) }
    }
    // The text field itself always reflects `query` immediately; filtering runs against
    // `debouncedQuery`, which lags by a beat so a long task list with heavy per-row composables
    // doesn't re-filter synchronously on every single keystroke.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(200)
        debouncedQuery = parsedSearchQuery.residualText
    }
    var appliedInitialSmartFilterSet by remember(initialSmartFilterSet) { mutableStateOf(false) }
    LaunchedEffect(initialSmartFilterSet, appliedInitialSmartFilterSet) {
        if (!appliedInitialSmartFilterSet && !initialSmartFilterSet.isNullOrBlank()) {
            activeFilters.clear()
            activeFilters.addAll(initialSmartFilterSet.toSmartFilters())
            appliedInitialSmartFilterSet = true
        }
    }

    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkAssignSheet by remember { mutableStateOf(false) }
    var showBulkRescheduleSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var includeArchived by remember { mutableStateOf(false) }
    var includeTrash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Swipe-to-delete on a single task reuses the same deferred-Undo-snackbar pattern as the
    // bulk-delete dialog below, just for one id at a time.
    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "Task deleted", undoWindowSeconds)
            if (!result) {
                viewModel.deleteTask(task)
            }
        }
    }

    val peopleById = remember(people) { people.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    val listsById = remember(lists) { lists.associateBy { it.id } }

    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()
    val savedSmartFilterSets by viewModel.savedSmartFilterSets.collectAsStateWithLifecycle()
    val currentSmartFilterSet = activeFilters.toList().encodedSmartFilterSet()
    val canSaveCurrentSmartFilterSet = currentSmartFilterSet.isNotBlank() && currentSmartFilterSet !in savedSmartFilterSets

    val archivedProjectIds = remember(projects) { projects.archivedProjects().map { it.id }.toSet() }
    val archivedTaskIds = remember(archivedTasks) { archivedTasks.map { it.id }.toSet() }
    val deletedTaskIds = remember(deletedTasks) { deletedTasks.map { it.id }.toSet() }
    val queryTasks by remember(debouncedQuery) {
        viewModel.searchTasks(debouncedQuery)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }
    val filteredTasks = remember(tasks, queryTasks, archivedTasks, deletedTasks, debouncedQuery, activeFilters.toList(), archivedProjectIds, myId, includeArchived, includeTrash, peopleById, tags, projects) {
        if (debouncedQuery.isBlank() && activeFilters.isEmpty() && !includeArchived && !includeTrash) {
            emptyList()
        } else {
            val today = LocalDate.now()
            val activeSource = if (debouncedQuery.isBlank()) tasks else queryTasks
            val archivedSource = if (!includeArchived) {
                emptyList()
            } else if (debouncedQuery.isBlank()) {
                archivedTasks
            } else {
                archivedTasks.filter { it.matchesSearchText(debouncedQuery, peopleById, tags, projects) }
            }
            val trashSource = if (!includeTrash) {
                emptyList()
            } else if (debouncedQuery.isBlank()) {
                deletedTasks
            } else {
                deletedTasks.filter { it.matchesSearchText(debouncedQuery, peopleById, tags, projects) }
            }
            val sourceTasks = (activeSource + archivedSource + trashSource).distinctBy { it.id }
            sourceTasks.filter { task ->
                if (!includeArchived && task.projectId in archivedProjectIds) return@filter false
                activeFilters.all { it.matches(task, today, myId) }
            }
        }
    }

    fun toggleTaskWithUndo(task: Task) {
        viewModel.toggleTaskDone(task.id) {}
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, if (task.done) "Task marked open" else "Task completed", undoWindowSeconds)
            if (result) {
                viewModel.restoreTasks(listOf(task))
            }
        }
    }

    fun completeSelectedWithUndo() {
        val previous = tasks.filter { it.id in selectedIds }
        if (previous.isEmpty()) return
        viewModel.bulkCompleteTasks(selectedIds.toList())
        selectedIds.clear()
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "${previous.size} task(s) completed", undoWindowSeconds)
            if (result) {
                viewModel.restoreTasks(previous)
            }
        }
    }

    if (selectionMode) {
        val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
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
                    onComplete = { completeSelectedWithUndo() },
                    onAddTag = { showBulkTagSheet = true },
                    onMove = { showBulkMoveSheet = true },
                    onReschedule = { showBulkRescheduleSheet = true },
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
                savedSmartFilterSets = savedSmartFilterSets,
                canSaveCurrentSmartFilterSet = canSaveCurrentSmartFilterSet,
                onSaveActiveFilters = { viewModel.saveSmartFilterSet(currentSmartFilterSet) },
                onApplySavedFilter = { encoded ->
                    activeFilters.clear()
                    activeFilters.addAll(encoded.toSmartFilters())
                },
                onRemoveSavedFilter = { encoded -> viewModel.removeSmartFilterSet(encoded) },
                onClearSearchFilters = {
                    query = ""
                    activeFilters.clear()
                    includeArchived = false
                    includeTrash = false
                },
                includeArchived = includeArchived,
                includeTrash = includeTrash,
                onToggleIncludeArchived = { includeArchived = !includeArchived },
                onToggleIncludeTrash = { includeTrash = !includeTrash },
                filteredTasks = filteredTasks,
                lists = lists,
                projects = projects,
                tags = tags,
                listsById = listsById,
                peopleById = peopleById,
                archivedTaskIds = archivedTaskIds,
                deletedTaskIds = deletedTaskIds,
                archivedProjectIds = archivedProjectIds,
                viewModel = viewModel,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onTaskClick = onNavigateToTaskDetail,
                onToggleTask = ::toggleTaskWithUndo,
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
                placeholder = { Text(stringResource(R.string.search_search_or_try_overdue_flagged)) },
                leadingIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
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
                    savedSmartFilterSets = savedSmartFilterSets,
                    canSaveCurrentSmartFilterSet = canSaveCurrentSmartFilterSet,
                    onSaveActiveFilters = { viewModel.saveSmartFilterSet(currentSmartFilterSet) },
                    onApplySavedFilter = { encoded ->
                        activeFilters.clear()
                        activeFilters.addAll(encoded.toSmartFilters())
                    },
                    onRemoveSavedFilter = { encoded -> viewModel.removeSmartFilterSet(encoded) },
                    onClearSearchFilters = {
                        query = ""
                    activeFilters.clear()
                    includeArchived = false
                    includeTrash = false
                },
                    includeArchived = includeArchived,
                    includeTrash = includeTrash,
                    onToggleIncludeArchived = { includeArchived = !includeArchived },
                    onToggleIncludeTrash = { includeTrash = !includeTrash },
                    filteredTasks = filteredTasks,
                    lists = lists,
                    projects = projects,
                    tags = tags,
                    listsById = listsById,
                    peopleById = peopleById,
                    archivedTaskIds = archivedTaskIds,
                    deletedTaskIds = deletedTaskIds,
                    archivedProjectIds = archivedProjectIds,
                    viewModel = viewModel,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onTaskClick = onNavigateToTaskDetail,
                    onToggleTask = ::toggleTaskWithUndo,
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

    if (showBulkRescheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkRescheduleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            com.mj.yata.ui.sheets.TaskBulkRescheduleSheet(
                onSelectPreset = { preset ->
                    viewModel.bulkRescheduleTasks(selectedIds.toList(), preset)
                    selectedIds.clear()
                    showBulkRescheduleSheet = false
                },
                onDismiss = { showBulkRescheduleSheet = false }
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.confirm_delete_tasks_title, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.action_this_can_t_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    selectedIds.clear()
                    showBulkDeleteDialog = false
                    scope.launch {
                        val result = showUndoSnackbar(snackbarHostState, if (ids.size == 1) "Task deleted" else "${ids.size} tasks deleted", undoWindowSeconds)
                        if (!result) {
                            viewModel.bulkDeleteTasks(ids)
                        }
                    }
                }) {
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
    savedSmartFilterSets: Set<String>,
    canSaveCurrentSmartFilterSet: Boolean,
    onSaveActiveFilters: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
    onRemoveSavedFilter: (String) -> Unit,
    onClearSearchFilters: () -> Unit,
    includeArchived: Boolean,
    includeTrash: Boolean,
    onToggleIncludeArchived: () -> Unit,
    onToggleIncludeTrash: () -> Unit,
    filteredTasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    tags: List<Tag>,
    listsById: Map<String, YataList>,
    peopleById: Map<String, Person>,
    archivedTaskIds: Set<String>,
    deletedTaskIds: Set<String>,
    archivedProjectIds: Set<String>,
    viewModel: MainViewModel,
    selectionMode: Boolean,
    selectedIds: MutableList<String>,
    onTaskClick: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onSwipeToDelete: (Task) -> Unit = {},
    tagsEnabled: Boolean = true,
    peopleEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }
    val taskRowDensity by viewModel.taskRowDensity.collectAsStateWithLifecycle()

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
                FilterChip(
                    selected = includeArchived,
                    onClick = onToggleIncludeArchived,
                    label = { Text("Archived") }
                )
                FilterChip(
                    selected = includeTrash,
                    onClick = onToggleIncludeTrash,
                    label = { Text("Trash") }
                )
                if (canSaveCurrentSmartFilterSet) {
                    AssistChip(
                        onClick = onSaveActiveFilters,
                        label = { Text(stringResource(R.string.action_save)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
        if (savedSmartFilterSets.isNotEmpty()) {
            item {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedSmartFilterSets
                        .mapNotNull { encoded ->
                            val filters = encoded.toSmartFilters()
                            if (filters.isEmpty()) null else encoded to filters
                        }
                        .sortedBy { (_, filters) -> filters.joinToString(",") { it.label } }
                        .forEach { (encoded, filters) ->
                            InputChip(
                                selected = activeFilters.toSet() == filters.toSet(),
                                onClick = { onApplySavedFilter(encoded) },
                                label = { Text(encoded.smartFilterSetLabel()) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveSavedFilter(encoded) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.search_remove_saved_filter),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
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
                com.mj.yata.ui.widgets.TabEmptyState(
                    icon = Icons.Default.Search,
                    title = "No matches",
                    subtitle = "Try a different query or loosen the active filters.",
                    actionLabel = "Clear search",
                    onAction = onClearSearchFilters
                )
            }
        } else {
            items(filteredTasks, key = { it.id }, contentType = { "task" }) { task ->
                val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                val taskAssignees = remember(task.assigneeIds, peopleById, peopleEnabled) {
                    if (peopleEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                }
                val taskTags = remember(task, projects, tags, tagsEnabled) {
                    if (tagsEnabled) task.effectiveTags(projects, tags) else emptyList()
                }

                Column(
                    modifier = Modifier.animateItem(placementSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized))
                ) {
                    val lifecycleBadges = listOfNotNull(
                        "In Trash".takeIf { task.id in deletedTaskIds || task.deletedAt != null },
                        "Archived".takeIf { task.id in archivedTaskIds || task.archived },
                        "Archived project".takeIf { task.projectId in archivedProjectIds }
                    )
                    if (lifecycleBadges.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            lifecycleBadges.forEach { label ->
                                Surface(
                                    color = if (label == "In Trash") {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (label == "In Trash") {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    TaskRow(
                        task = task,
                        list = taskList,
                        assignees = taskAssignees,
                        tags = taskTags,
                        onToggleDone = { onToggleTask(task) },
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
                        onCommentClick = { pendingCommentTask = task },
                        onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                        onRenameTask = { viewModel.renameTask(task.id, it) },
                        density = taskRowDensity,
                        onSwipeToDelete = { onSwipeToDelete(task) },
                        swipeEnabled = !selectionMode && task.id !in deletedTaskIds,
                        showDueDate = true
                    )
                }
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
