package com.mj.yata.ui.screen.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.theme.YataEase
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** One-tap filters shown before/alongside a text query — each is a self-contained predicate so
 * toggling several combines them with AND (narrows further, doesn't union). */
internal enum class SmartFilter(@StringRes val labelRes: Int) {
    FOCUS(R.string.search_filter_focus),
    MORNING_REVIEW(R.string.search_filter_morning_review),
    EVENING_REVIEW(R.string.search_filter_evening_review),
    STALE_TASKS(R.string.search_filter_stale_tasks),
    AT_RISK(R.string.search_filter_at_risk),
    ASSIGNED_TO_ME(R.string.search_filter_assigned_to_me),
    OVERDUE(R.string.search_filter_overdue),
    HIGH_PRIORITY(R.string.search_filter_high_priority),
    FLAGGED(R.string.search_filter_flagged),
    DUE_TODAY(R.string.search_filter_due_today),
    NO_DUE_DATE(R.string.search_filter_no_due_date);

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

@Composable
internal fun String.smartFilterSetLabel(): String {
    val labels = toSmartFilters().map { stringResource(it.labelRes) }
    return labels.ifEmpty { listOf(stringResource(R.string.search_filter_saved_view)) }.joinToString(" + ")
}

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
    "flagged" to SmartFilter.FLAGGED,
    "sin fecha limite" to SmartFilter.NO_DUE_DATE,
    "sin fecha límite" to SmartFilter.NO_DUE_DATE,
    "sin fecha" to SmartFilter.NO_DUE_DATE,
    "sin vencer" to SmartFilter.NO_DUE_DATE,
    "alta prioridad" to SmartFilter.HIGH_PRIORITY,
    "prioridad alta" to SmartFilter.HIGH_PRIORITY,
    "asignadas a mi" to SmartFilter.ASSIGNED_TO_ME,
    "asignadas a mí" to SmartFilter.ASSIGNED_TO_ME,
    "asignado a mi" to SmartFilter.ASSIGNED_TO_ME,
    "asignado a mí" to SmartFilter.ASSIGNED_TO_ME,
    "vencen hoy" to SmartFilter.DUE_TODAY,
    "para hoy" to SmartFilter.DUE_TODAY,
    "hoy" to SmartFilter.DUE_TODAY,
    "atrasadas" to SmartFilter.OVERDUE,
    "vencidas" to SmartFilter.OVERDUE,
    "marcadas" to SmartFilter.FLAGGED,
    "destacadas" to SmartFilter.FLAGGED,
    "sem data limite" to SmartFilter.NO_DUE_DATE,
    "sem data limite" to SmartFilter.NO_DUE_DATE,
    "sem data" to SmartFilter.NO_DUE_DATE,
    "sem vencimento" to SmartFilter.NO_DUE_DATE,
    "alta prioridade" to SmartFilter.HIGH_PRIORITY,
    "prioridade alta" to SmartFilter.HIGH_PRIORITY,
    "atribuídas a mim" to SmartFilter.ASSIGNED_TO_ME,
    "atribuidas a mim" to SmartFilter.ASSIGNED_TO_ME,
    "atribuído a mim" to SmartFilter.ASSIGNED_TO_ME,
    "atribuido a mim" to SmartFilter.ASSIGNED_TO_ME,
    "vencem hoje" to SmartFilter.DUE_TODAY,
    "para hoje" to SmartFilter.DUE_TODAY,
    "hoje" to SmartFilter.DUE_TODAY,
    "atrasadas" to SmartFilter.OVERDUE,
    "vencidas" to SmartFilter.OVERDUE,
    "marcadas" to SmartFilter.FLAGGED,
    "sinalizadas" to SmartFilter.FLAGGED,
    "sans échéance" to SmartFilter.NO_DUE_DATE,
    "sans echeance" to SmartFilter.NO_DUE_DATE,
    "sans date" to SmartFilter.NO_DUE_DATE,
    "haute priorité" to SmartFilter.HIGH_PRIORITY,
    "haute priorite" to SmartFilter.HIGH_PRIORITY,
    "assignées à moi" to SmartFilter.ASSIGNED_TO_ME,
    "assignees a moi" to SmartFilter.ASSIGNED_TO_ME,
    "pour aujourd'hui" to SmartFilter.DUE_TODAY,
    "pour aujourd’hui" to SmartFilter.DUE_TODAY,
    "aujourd'hui" to SmartFilter.DUE_TODAY,
    "aujourd’hui" to SmartFilter.DUE_TODAY,
    "en retard" to SmartFilter.OVERDUE,
    "échues" to SmartFilter.OVERDUE,
    "echues" to SmartFilter.OVERDUE,
    "marquées" to SmartFilter.FLAGGED,
    "marquees" to SmartFilter.FLAGGED
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
        val regex = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(phrase)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
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
    tagsById: Map<String, Tag>,
    projectsById: Map<String, Project>
): Boolean {
    val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = buildString {
        append(title).append(' ')
        append(notes.orEmpty()).append(' ')
        assigneeIds.mapNotNull { peopleById[it]?.name }.forEach { append(it).append(' ') }
        effectiveTags(projectsById, tagsById).forEach { append(it.name).append(' ') }
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
    val projectsById = remember(projects) { projects.associateBy { it.id } }

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
    val filteredTasks = remember(tasks, queryTasks, archivedTasks, deletedTasks, debouncedQuery, activeFilters.toList(), archivedProjectIds, myId, includeArchived, includeTrash, peopleById, tagsById, projectsById) {
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
                archivedTasks.filter { it.matchesSearchText(debouncedQuery, peopleById, tagsById, projectsById) }
            }
            val trashSource = if (!includeTrash) {
                emptyList()
            } else if (debouncedQuery.isBlank()) {
                deletedTasks
            } else {
                deletedTasks.filter { it.matchesSearchText(debouncedQuery, peopleById, tagsById, projectsById) }
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
                projectsById = projectsById,
                tagsById = tagsById,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    projectsById = projectsById,
                    tagsById = tagsById,
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
                tasks = tasks,
                todayStr = com.mj.yata.util.AppClock.todayString,
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
    projectsById: Map<String, Project>,
    tagsById: Map<String, Tag>,
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
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val activeFilterCount = activeFilters.size +
            if (includeArchived) 1 else 0 +
            if (includeTrash) 1 else 0
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.search_filters),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (activeFilterCount == 0) {
                                    stringResource(R.string.search_filter_hint)
                                } else {
                                    pluralStringResource(R.plurals.search_active_filters_count, activeFilterCount, activeFilterCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canSaveCurrentSmartFilterSet) {
                            FilledTonalIconButton(onClick = onSaveActiveFilters) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = stringResource(R.string.action_save)
                                )
                            }
                        }
                    }

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = activeFilters.contains(filter),
                                onClick = { onToggleFilter(filter) },
                                label = { Text(stringResource(filter.labelRes)) }
                            )
                        }
                        FilterChip(
                            selected = includeArchived,
                            onClick = onToggleIncludeArchived,
                            label = { Text(stringResource(R.string.search_filter_archived)) }
                        )
                        FilterChip(
                            selected = includeTrash,
                            onClick = onToggleIncludeTrash,
                            label = { Text(stringResource(R.string.search_filter_trash)) }
                        )
                    }
                }
            }
        }
        if (savedSmartFilterSets.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_saved_views),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            savedSmartFilterSets
                                .mapNotNull { encoded ->
                                    val filters = encoded.toSmartFilters()
                                    if (filters.isEmpty()) null else encoded to filters
                                }
                                .sortedBy { (_, filters) -> filters.joinToString(",") { it.name } }
                                .forEach { (encoded, filters) ->
                                    InputChip(
                                        selected = activeFilters.toSet() == filters.toSet(),
                                        onClick = { onApplySavedFilter(encoded) },
                                        label = { Text(encoded.smartFilterSetLabel()) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.FilterList,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
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
            }
        }
        if (filteredTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.search_results_count, filteredTasks.size, filteredTasks.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (query.isNotBlank() || activeFilters.isNotEmpty() || includeArchived || includeTrash) {
                        TextButton(onClick = onClearSearchFilters) {
                            Text(stringResource(R.string.search_clear))
                        }
                    }
                }
            }
        }
        if (query.isBlank() && activeFilters.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .size(28.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.search_prompt_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.search_prompt_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (filteredTasks.isEmpty()) {
            item {
                com.mj.yata.ui.widgets.TabEmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_no_matches),
                    subtitle = stringResource(R.string.search_no_matches_subtitle),
                    actionLabel = stringResource(R.string.search_clear),
                    onAction = onClearSearchFilters
                )
            }
        } else {
            items(filteredTasks, key = { it.id }, contentType = { "task" }) { task ->
                val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                val taskAssignees = remember(task.assigneeIds, peopleById, peopleEnabled) {
                    if (peopleEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                }
                val taskTags = remember(task, projectsById, tagsById, tagsEnabled) {
                    if (tagsEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
                }

                Column(
                    modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                ) {
                    val lifecycleBadges = listOfNotNull(
                        R.string.search_badge_in_trash.takeIf { task.id in deletedTaskIds || task.deletedAt != null },
                        R.string.search_badge_archived.takeIf { task.id in archivedTaskIds || task.archived },
                        R.string.search_badge_archived_project.takeIf { task.projectId in archivedProjectIds }
                    )
                    if (lifecycleBadges.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            lifecycleBadges.forEach { labelRes ->
                                val isTrashBadge = labelRes == R.string.search_badge_in_trash
                                Surface(
                                    color = if (isTrashBadge) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isTrashBadge) {
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
