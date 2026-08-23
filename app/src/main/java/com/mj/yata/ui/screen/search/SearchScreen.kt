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
import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.archivedProjects
import com.mj.yata.domain.model.effectiveDue
import com.mj.yata.domain.model.effectiveTagIds
import com.mj.yata.domain.model.effectiveTags
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.QuickAddHighlightType
import com.mj.yata.util.findBestEntityMatch
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
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.util.rememberAdaptiveLayoutInfo
import com.mj.yata.ui.util.rememberAdaptiveSheetMaxWidth
import com.mj.yata.ui.widgets.ContextualHelpButton
import com.mj.yata.ui.widgets.ContextualHelpTopic
import com.mj.yata.ui.widgets.TaskPreviewPane
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

    /** [weekendDays]/[holidays]/[observeNonWorkingDays] default to off, so a caller that hasn't
     * been updated compiles and filters exactly as before. */
    fun matches(
        task: Task,
        today: LocalDate,
        myId: String,
        weekendDays: Set<String> = emptySet(),
        holidays: List<Holiday> = emptyList(),
        observeNonWorkingDays: Boolean = false
    ): Boolean {
        val effectiveDue = task.effectiveDue(weekendDays, holidays, observeNonWorkingDays)
        return when (this) {
            FOCUS -> !task.done && (task.flag || task.priority == "high" || effectiveDue == today.toString() || effectiveDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true)
            MORNING_REVIEW -> !task.done && (effectiveDue == today.toString() || effectiveDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true)
            EVENING_REVIEW -> !task.done && (task.due == today.plusDays(1).toString() || (task.due == null && task.priority != "none"))
            STALE_TASKS -> !task.done && task.due == null && task.time == null && task.recurrence == null && task.priority == "none" && !task.flag
            AT_RISK -> !task.done && (
                effectiveDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true ||
                    (task.priority == "high" && task.due == null) ||
                    (task.flag && task.due == null)
                )
            ASSIGNED_TO_ME -> task.assigneeIds.contains(myId)
            OVERDUE -> !task.done && effectiveDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
            HIGH_PRIORITY -> task.priority == "high"
            FLAGGED -> task.flag
            DUE_TODAY -> effectiveDue == today.toString()
            NO_DUE_DATE -> task.due == null
        }
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

/** Tag/person/project/list/priority/flag recognized in the query text, resolved to real entity
 * ids via [findBestEntityMatch] — the same fuzzy name matcher quick-add uses. A task must satisfy
 * every populated field (AND across types, and AND within [tagIds]/[assigneeIds] too — "tagged
 * urgent blocked" requires both tags, matching how bulk tag-assignment elsewhere in the app
 * treats multiple tags). Deliberately excludes dates/times/recurrence: NaturalLanguageParser
 * resolves a relative phrase like "next week" to one exact date, which is right for *setting* a
 * due date but wrong for *filtering* by one ("next week" should match any day in that range, not
 * one exact date) — that needs its own range-aware handling, not a reuse of this. */
internal data class ParsedSearchEntities(
    val tagIds: List<String> = emptyList(),
    val assigneeIds: List<String> = emptyList(),
    val projectId: String? = null,
    val listId: String? = null,
    val priority: String? = null,
    val flag: Boolean = false
) {
    val isEmpty: Boolean
        get() = tagIds.isEmpty() && assigneeIds.isEmpty() && projectId == null && listId == null && priority == null && !flag
}

/** Keyed so a recognized entity can be individually dismissed from the current search without
 * editing the typed text — see [ParsedSearchEntities.withoutDismissed]. */
internal fun ParsedSearchEntities.entityKeys(): List<String> = buildList {
    tagIds.forEach { add("tag:$it") }
    assigneeIds.forEach { add("assignee:$it") }
    projectId?.let { add("project:$it") }
    listId?.let { add("list:$it") }
    priority?.let { add("priority") }
    if (flag) add("flag")
}

internal fun ParsedSearchEntities.withoutDismissed(dismissed: Set<String>): ParsedSearchEntities = copy(
    tagIds = tagIds.filterNot { "tag:$it" in dismissed },
    assigneeIds = assigneeIds.filterNot { "assignee:$it" in dismissed },
    projectId = projectId?.takeUnless { "project:$it" in dismissed },
    listId = listId?.takeUnless { "list:$it" in dismissed },
    priority = priority?.takeUnless { "priority" in dismissed },
    flag = flag && "flag" !in dismissed
)

internal fun Task.matchesSearchEntities(entities: ParsedSearchEntities, projectsById: Map<String, Project>): Boolean {
    if (entities.tagIds.isNotEmpty()) {
        val effectiveIds = effectiveTagIds(projectsById)
        if (!entities.tagIds.all { it in effectiveIds }) return false
    }
    if (entities.assigneeIds.isNotEmpty() && !entities.assigneeIds.all { it in assigneeIds }) return false
    if (entities.projectId != null && projectId != entities.projectId) return false
    if (entities.listId != null && listId != entities.listId) return false
    if (entities.priority != null && priority != entities.priority) return false
    if (entities.flag && !flag) return false
    return true
}

internal data class ParsedSearchQuery(
    val filters: List<SmartFilter>,
    val entities: ParsedSearchEntities,
    val residualText: String
)

/** Highlight types [NaturalLanguageParser] recognizes that this reuses. Date/time/recurrence
 * types are deliberately absent — see [ParsedSearchEntities]'s doc comment — so phrases like
 * "next week" stay untouched in [ParsedSearchQuery.residualText] and keep matching literally
 * against task text exactly as they did before this reuse, rather than silently vanishing. */
private val SEARCH_ENTITY_HIGHLIGHT_TYPES = setOf(
    QuickAddHighlightType.Project,
    QuickAddHighlightType.List,
    QuickAddHighlightType.Tag,
    QuickAddHighlightType.Assignee,
    QuickAddHighlightType.Priority,
    QuickAddHighlightType.Flag
)

/** Strips recognized filter phrases and entities out of the typed query and reports which
 * [SmartFilter]s/[ParsedSearchEntities] they correspond to — the box itself always keeps showing
 * exactly what was typed (this only changes what actually gets searched/filtered), same "detect,
 * don't rewrite the input" rule NaturalLanguageParser follows for quick-add.
 *
 * Runs in two passes: the canned [searchFilterPhrases] table first (unchanged), then
 * [NaturalLanguageParser] on whatever text that pass left behind, so a canned phrase can't also
 * get mis-recognized as an entity mention. */
internal fun parseSearchQuery(
    raw: String,
    tags: List<Tag> = emptyList(),
    people: List<Person> = emptyList(),
    projects: List<Project> = emptyList(),
    lists: List<YataList> = emptyList()
): ParsedSearchQuery {
    var remaining = raw
    val matched = mutableListOf<SmartFilter>()
    for ((phrase, filter) in searchFilterPhrases) {
        val regex = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(phrase)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(remaining)) {
            matched.add(filter)
            remaining = regex.replace(remaining, " ")
        }
    }
    remaining = remaining.replace(Regex("\\s{2,}"), " ").trim()

    val quickAdd = NaturalLanguageParser.parse(remaining)
    // Entity spans only, removed from the end backwards so earlier indices stay valid.
    var residual = remaining
    quickAdd.highlightSpans
        .filter { it.type in SEARCH_ENTITY_HIGHLIGHT_TYPES }
        .map { it.range }
        .sortedByDescending { it.first }
        .forEach { range ->
            if (range.first in residual.indices && range.last < residual.length) {
                residual = residual.removeRange(range.first, range.last + 1)
            }
        }
    residual = residual.replace(Regex("\\s{2,}"), " ").trim()

    val entities = ParsedSearchEntities(
        tagIds = quickAdd.tagNames.mapNotNull { name -> findBestEntityMatch(name, tags, nameExtractor = { it.name }) }.map { it.id }.distinct(),
        assigneeIds = quickAdd.assigneeNames.mapNotNull { name -> findBestEntityMatch(name, people, nameExtractor = { it.name }) }.map { it.id }.distinct(),
        projectId = quickAdd.projectName?.let { findBestEntityMatch(it, projects, nameExtractor = { p -> p.name }) }?.id,
        listId = quickAdd.listName?.let { findBestEntityMatch(it, lists, nameExtractor = { l -> l.name }) }?.id,
        priority = quickAdd.priority,
        flag = quickAdd.flag
    )

    return ParsedSearchQuery(matched.distinct(), entities, residual)
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

/** A recognized-entity chip (tag/person/project/list/priority/flag) — always "on" while shown,
 * dismissible via its trailing close icon rather than toggle-selected like [CompactSearchFilterChip],
 * since there's no pre-existing catalog of these to toggle back on afterward. */
@Composable
private fun EntityFilterChip(
    label: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    InputChip(
        selected = true,
        onClick = onDismiss,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                modifier = Modifier.size(16.dp)
            )
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 32.dp)
    )
}

@Composable
private fun CompactSearchFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 32.dp)
    )
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
    val weekendDays by viewModel.weekendDays.collectAsStateWithLifecycle()
    val holidaysRaw by viewModel.holidays.collectAsStateWithLifecycle()
    val holidays = remember(holidaysRaw) { holidaysRaw.mapNotNull(Holiday::decode) }
    val observeNonWorkingDays by viewModel.observeNonWorkingDays.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    // The box always shows exactly what was typed — only the derived search text and filter
    // chips change. `parseSearchQuery` recognizes phrases like "high priority"/"overdue" and
    // reports them as SmartFilter chips to auto-activate, with the phrase itself excluded from
    // the plain-text search so e.g. "high priority report" both toggles the chip and searches
    // for "report".
    val parsedSearchQuery = remember(query, tags, people, projects, lists) {
        parseSearchQuery(query, tags, people, projects, lists)
    }
    val activeFilters = remember { mutableStateListOf<SmartFilter>() }
    LaunchedEffect(parsedSearchQuery.filters) {
        parsedSearchQuery.filters.forEach { filter -> if (filter !in activeFilters) activeFilters.add(filter) }
    }
    // The text field itself always reflects `query` immediately; filtering runs against
    // `debouncedQuery`/`debouncedEntities`, which lag by a beat so a long task list with heavy
    // per-row composables doesn't re-filter synchronously on every single keystroke — same reason
    // NaturalLanguageParser's own recognition is debounced here even though quick-add runs it
    // live: quick-add's list of matches to redraw is one preview card, search's is every row.
    var debouncedQuery by remember { mutableStateOf("") }
    var debouncedEntities by remember { mutableStateOf(ParsedSearchEntities()) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(200)
        debouncedQuery = parsedSearchQuery.residualText
        debouncedEntities = parsedSearchQuery.entities
    }
    // Recognized tags/people/project/list/priority/flag, shown as removable chips alongside the
    // SmartFilter ones. Dismissing one only excludes it from matching for the current text —
    // editing the query re-parses from scratch, which is also when a dismissal naturally falls
    // away since it's keyed to the query, not persisted state.
    val dismissedEntityKeys = remember(query) { mutableStateListOf<String>() }
    val activeEntities = remember(debouncedEntities, dismissedEntityKeys.toList()) {
        debouncedEntities.withoutDismissed(dismissedEntityKeys.toSet())
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
    var previewTaskId by remember { mutableStateOf<String?>(null) }
    val adaptiveLayout = rememberAdaptiveLayoutInfo()
    val useWideSearch = adaptiveLayout.isWide
    val adaptiveSheetMaxWidth = rememberAdaptiveSheetMaxWidth()
    var includeArchived by remember { mutableStateOf(false) }
    var includeTrash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Swipe-to-delete on a single task reuses the same deferred-Undo-snackbar pattern as the
    // bulk-delete dialog below, just for one id at a time.
    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, context.getString(R.string.task_deleted), undoWindowSeconds)
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
    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }
    // Live, archived and trash all run through the same matchesSearchText — they used to diverge:
    // live tasks went through a SQL FTS query (prefix-token match, no project-inherited tags),
    // archived/trash went through this substring match, so the same query could find a task in
    // one bucket and miss its otherwise-identical archived copy. One matcher, one behavior.
    val filteredTasks = remember(tasks, archivedTasks, deletedTasks, debouncedQuery, activeFilters.toList(), activeEntities, archivedProjectIds, myId, includeArchived, includeTrash, peopleById, tagsById, projectsById, weekendDays, holidays, observeNonWorkingDays) {
        if (debouncedQuery.isBlank() && activeFilters.isEmpty() && activeEntities.isEmpty && !includeArchived && !includeTrash) {
            emptyList()
        } else {
            val today = LocalDate.now()
            val activeSource = if (debouncedQuery.isBlank()) {
                tasks
            } else {
                tasks.filter { it.matchesSearchText(debouncedQuery, peopleById, tagsById, projectsById) }
            }
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
                activeFilters.all { it.matches(task, today, myId, weekendDays, holidays, observeNonWorkingDays) } && task.matchesSearchEntities(activeEntities, projectsById)
            }
        }
    }
    val previewTask = remember(filteredTasks, previewTaskId) {
        filteredTasks.find { it.id == previewTaskId }
    }
    LaunchedEffect(filteredTasks, useWideSearch) {
        if (!useWideSearch) {
            previewTaskId = null
        } else if (previewTaskId != null && filteredTasks.none { it.id == previewTaskId }) {
            previewTaskId = filteredTasks.firstOrNull()?.id
        }
    }

    fun toggleTaskWithUndo(task: Task) {
        viewModel.toggleTaskDone(task.id) {}
        scope.launch {
            val message = context.getString(if (task.done) R.string.task_marked_open else R.string.task_completed)
            val result = showUndoSnackbar(snackbarHostState, message, undoWindowSeconds)
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
            val message = context.resources.getQuantityString(R.plurals.tasks_completed_count, previous.size, previous.size)
            val result = showUndoSnackbar(snackbarHostState, message, undoWindowSeconds)
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
                com.mj.yata.ui.screen.main.AdaptiveBottomNav(
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
                    onDuplicate = { viewModel.bulkDuplicateTasks(selectedIds.toList()) { single -> onNavigateToTaskDetail(single.id) }; selectedIds.clear() },
                    onDelete = { showBulkDeleteDialog = true },
                    onAssign = { showBulkAssignSheet = true },
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled,
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { innerPadding ->
            AdaptiveContentBox(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
            SearchResultsList(
                query = query,
                activeFilters = activeFilters,
                onToggleFilter = { if (activeFilters.contains(it)) activeFilters.remove(it) else activeFilters.add(it) },
                activeEntities = activeEntities,
                onDismissEntity = { key -> dismissedEntityKeys.add(key) },
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
    } else {
        // M3 SearchBar, permanently expanded since this is a dedicated search destination.
        AdaptiveContentBox(
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        } else {
                            ContextualHelpButton(
                                title = stringResource(R.string.help_about_search_saved_views),
                                topics = listOf(
                                    ContextualHelpTopic(
                                        title = "Smart phrases",
                                        body = "Typing words like overdue, flagged, assigned to me, or no due date turns on the matching filter while keeping your typed text visible."
                                    ),
                                    ContextualHelpTopic(
                                        title = "Filters narrow results",
                                        body = "Multiple filter chips combine together, so each chip makes the list more focused instead of broader."
                                    ),
                                    ContextualHelpTopic(
                                        title = "Saved views",
                                        body = "Tap the bookmark button after choosing filters to save that view. Saved views also appear in the drawer and command palette."
                                    )
                                )
                            )
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                }
            ) {
                if (useWideSearch) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SearchResultsList(
                            query = query,
                            activeFilters = activeFilters,
                            onToggleFilter = { if (activeFilters.contains(it)) activeFilters.remove(it) else activeFilters.add(it) },
                activeEntities = activeEntities,
                onDismissEntity = { key -> dismissedEntityKeys.add(key) },
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
                            onTaskClick = { previewTaskId = it },
                            onToggleTask = ::toggleTaskWithUndo,
                            onSwipeToDelete = ::deleteTaskWithUndo,
                            tagsEnabled = tagsFeatureEnabled,
                            peopleEnabled = peopleFeatureEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        TaskPreviewPane(
                            task = previewTask,
                            list = previewTask?.listId?.let { listsById[it] },
                            project = previewTask?.projectId?.let { projectsById[it] },
                            people = previewTask?.assigneeIds?.mapNotNull { peopleById[it] }.orEmpty(),
                            tags = previewTask?.effectiveTags(projectsById, tagsById).orEmpty(),
                            onOpenTask = { taskId -> onNavigateToTaskDetail(taskId) },
                            onToggleTask = { task -> toggleTaskWithUndo(task) },
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(min = 320.dp, max = 380.dp)
                        )
                    }
                } else {
                    SearchResultsList(
                    query = query,
                    activeFilters = activeFilters,
                    onToggleFilter = { if (activeFilters.contains(it)) activeFilters.remove(it) else activeFilters.add(it) },
                activeEntities = activeEntities,
                onDismissEntity = { key -> dismissedEntityKeys.add(key) },
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
    }

    if (showBulkTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetMaxWidth = adaptiveSheetMaxWidth
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetMaxWidth = adaptiveSheetMaxWidth
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetMaxWidth = adaptiveSheetMaxWidth
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetMaxWidth = adaptiveSheetMaxWidth
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
                        val message = context.resources.getQuantityString(R.plurals.tasks_deleted, ids.size, ids.size)
                        val result = showUndoSnackbar(snackbarHostState, message, undoWindowSeconds)
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
    activeEntities: ParsedSearchEntities,
    onDismissEntity: (String) -> Unit,
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
    val weekendDays by viewModel.weekendDays.collectAsStateWithLifecycle()
    val holidaysRaw by viewModel.holidays.collectAsStateWithLifecycle()
    val holidays = remember(holidaysRaw) { holidaysRaw.mapNotNull(Holiday::decode) }
    val observeNonWorkingDays by viewModel.observeNonWorkingDays.collectAsStateWithLifecycle()

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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.search_filters),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (activeFilterCount == 0) {
                                stringResource(R.string.search_filter_hint_compact)
                            } else {
                                pluralStringResource(R.plurals.search_active_filters_count, activeFilterCount, activeFilterCount)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (canSaveCurrentSmartFilterSet) {
                            FilledTonalIconButton(
                                onClick = onSaveActiveFilters,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = stringResource(R.string.action_save),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SmartFilter.entries.forEach { filter ->
                            CompactSearchFilterChip(
                                selected = activeFilters.contains(filter),
                                onClick = { onToggleFilter(filter) },
                                label = stringResource(filter.labelRes)
                            )
                        }
                        CompactSearchFilterChip(
                            selected = includeArchived,
                            onClick = onToggleIncludeArchived,
                            label = stringResource(R.string.search_filter_archived)
                        )
                        CompactSearchFilterChip(
                            selected = includeTrash,
                            onClick = onToggleIncludeTrash,
                            label = stringResource(R.string.search_filter_trash)
                        )
                    }

                    // Tags/people/project/list/priority/flag recognized in the typed text (see
                    // parseSearchQuery) — dismissible so a mis-recognized word doesn't silently
                    // narrow results with no visible explanation.
                    if (!activeEntities.isEmpty) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activeEntities.tagIds.forEach { id ->
                                tagsById[id]?.let { tag ->
                                    EntityFilterChip(
                                        label = stringResource(R.string.voice_task_overlay_tag_chip, tag.name),
                                        onDismiss = { onDismissEntity("tag:$id") }
                                    )
                                }
                            }
                            activeEntities.assigneeIds.forEach { id ->
                                peopleById[id]?.let { person ->
                                    EntityFilterChip(
                                        label = stringResource(R.string.voice_task_overlay_assignee_chip, person.name),
                                        onDismiss = { onDismissEntity("assignee:$id") }
                                    )
                                }
                            }
                            activeEntities.projectId?.let { id ->
                                projectsById[id]?.let { project ->
                                    EntityFilterChip(
                                        label = stringResource(R.string.voice_task_overlay_project_chip, project.name),
                                        onDismiss = { onDismissEntity("project:$id") }
                                    )
                                }
                            }
                            activeEntities.listId?.let { id ->
                                listsById[id]?.let { list ->
                                    EntityFilterChip(
                                        label = stringResource(R.string.voice_task_overlay_list_chip, list.name),
                                        onDismiss = { onDismissEntity("list:$id") }
                                    )
                                }
                            }
                            activeEntities.priority?.let { priority ->
                                EntityFilterChip(
                                    label = stringResource(R.string.voice_task_overlay_priority_chip, priority.replaceFirstChar { it.uppercase() }),
                                    onDismiss = { onDismissEntity("priority") }
                                )
                            }
                            if (activeEntities.flag) {
                                EntityFilterChip(
                                    label = stringResource(R.string.quick_add_preview_flagged),
                                    onDismiss = { onDismissEntity("flag") }
                                )
                            }
                        }
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
                        showDueDate = true,
                        weekendDays = weekendDays,
                        holidays = holidays,
                        observeNonWorkingDays = observeNonWorkingDays
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
