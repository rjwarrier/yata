package com.mj.yata.ui.screen.markdownimport

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.domain.model.Task
import com.mj.yata.util.MarkdownUpdateField
import com.mj.yata.util.markdownChangedFields
import com.mj.yata.util.MarkdownPreviewFilter
import com.mj.yata.util.markdownVisibleIndices
import com.mj.yata.util.markdownImportSummary
import com.mj.yata.util.markdownReferenceTokens
import com.mj.yata.util.validMarkdownReference
import com.mj.yata.util.withBulkDuplicates
import com.mj.yata.util.MarkdownImportReview
import com.mj.yata.util.MarkdownDuplicateAction
import com.mj.yata.util.MarkdownDuplicateDecision
import com.mj.yata.util.duplicateAction
import com.mj.yata.util.markdownDuplicateMatches
import com.mj.yata.util.reviewMarkdownTasks
import com.mj.yata.util.withTaskEdit
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.widgets.TagChip
import com.mj.yata.ui.widgets.YataDropdownMenuItem
import com.mj.yata.util.MarkdownDocument
import com.mj.yata.util.MarkdownImportTooLargeException
import com.mj.yata.util.MarkdownTask
import com.mj.yata.util.MarkdownImportContext
import com.mj.yata.util.MarkdownTaskPreview
import com.mj.yata.util.prepareSmartImport
import com.mj.yata.util.RecurrenceEvaluator
import com.mj.yata.util.readMarkdownFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private data class MarkdownPreviewRequest(
    val document: MarkdownDocument,
    val context: MarkdownImportContext,
    val projectId: String?,
    val listId: String?,
    val useDates: Boolean
)
private data class PreparedMarkdownPreview(val request: MarkdownPreviewRequest, val tasks: List<MarkdownTaskPreview>)
private data class ReviewedMarkdownPreview(
    val base: List<MarkdownTaskPreview>, val review: MarkdownImportReview, val existing: List<Task>,
    val tasks: List<MarkdownTaskPreview>, val duplicates: Map<Int, Task>
)
private data class MarkdownEntityLabels(
    val projects: Map<String, String>,
    val lists: Map<String, String>,
    val people: Map<String, String>,
    val tags: Map<String, com.mj.yata.domain.model.Tag>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownImportScreen(
    viewModel: MainViewModel,
    initialProjectId: String?,
    initialListId: String?,
    onNavigateBack: () -> Unit,
    onImported: (projectId: String?, listId: String?) -> Unit
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val projectsEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val tagsEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val peopleEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val defaultPriority by viewModel.defaultPriority.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val referenceMemory by viewModel.markdownReferenceMemory.collectAsStateWithLifecycle()
    var confirmingImport by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(MarkdownPreviewFilter.ALL.name) }
    val filter = MarkdownPreviewFilter.valueOf(filterName)
    val dateAliases by viewModel.dateAliasDefinitions.collectAsStateWithLifecycle()
    val status by viewModel.markdownImportStatus.collectAsStateWithLifecycle()
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
    val review by remember(sessionId) { viewModel.markdownReview(sessionId) }.collectAsStateWithLifecycle()
    val existingTasks by viewModel.markdownExistingTasks.collectAsStateWithLifecycle()
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val pendingReferences by viewModel.markdownReferencePending.collectAsStateWithLifecycle()
    val creatingReference = pendingReferences > 0
    var importedDestination by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    fun updateReview(value: MarkdownImportReview) { viewModel.setMarkdownReview(sessionId, value) }
    val importing = status?.sessionId == sessionId && status?.running == true
    var projectId by rememberSaveable { mutableStateOf(initialProjectId) }
    var listId by rememberSaveable { mutableStateOf(initialListId) }
    var fileUri by rememberSaveable { mutableStateOf<String?>(null) }
    var fileRevision by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    val selectedSet = remember(selected) { selected.orEmpty().toSet() }
    var useDates by rememberSaveable { mutableStateOf(true) }
    var document by remember { mutableStateOf<MarkdownDocument?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var readError by remember { mutableIntStateOf(0) }
    val activeProjects = remember(projects) { projects.filter { !it.archived }.sortedBy { it.name.lowercase() } }
    val activeLists = remember(lists) { lists.filter { !it.archived }.sortedBy { it.name.lowercase() } }
    val activeProjectIds = remember(activeProjects) { activeProjects.mapTo(hashSetOf()) { it.id } }
    val activeListIds = remember(activeLists) { activeLists.mapTo(hashSetOf()) { it.id } }
    val projectOptions = remember(activeProjects) { activeProjects.map { it.id to it.name } }
    val listOptions = remember(activeLists) { activeLists.map { it.id to it.name } }
    val entityLabels = remember(projects, lists, people, tags) {
        MarkdownEntityLabels(projects.associate { it.id to it.name }, lists.associate { it.id to it.name },
            people.associate { it.id to it.name }, tags.associateBy { it.id })
    }
    val importContext = remember(projects, lists, people, tags, projectsEnabled, tagsEnabled, peopleEnabled, defaultPriority, autoAssignToMe) {
        MarkdownImportContext(projects, lists, people, tags, projectsEnabled, peopleEnabled, tagsEnabled,
            defaultPriority, if (autoAssignToMe) people.firstOrNull { it.isMe }?.let { listOf(it.id) }.orEmpty() else emptyList())
    }
    val request = remember(document, importContext, projectId, listId, useDates) {
        document?.let { MarkdownPreviewRequest(it, importContext, projectId, listId, useDates) }
    }
    val prepared by produceState<PreparedMarkdownPreview?>(null, request) {
        val current = request ?: return@produceState
        value = withContext(Dispatchers.Default) {
            PreparedMarkdownPreview(current, current.document.prepareSmartImport(current.context, current.projectId, current.listId, current.useDates))
        }
    }
    val basePreviews = prepared?.takeIf { it.request === request }?.tasks
    val rememberMappings = review.rememberMappings ?: referenceMemory?.enabled ?: true
    val sourceTokens = remember(basePreviews) { markdownReferenceTokens(basePreviews.orEmpty()) }
    val remembered = remember(referenceMemory, sourceTokens, rememberMappings, review.ignoredSavedReferences) {
        if (rememberMappings) referenceMemory?.mappings.orEmpty().filterKeys { it in sourceTokens && it !in review.ignoredSavedReferences }
        else emptyMap()
    }
    val appliedReview = remember(review, remembered) { review.copy(references = (remembered + review.references).entries.associate { it.key.lowercase() to it.value }) }
    val staleMappings = remembered.filter { (token, id) ->
        val relevant = when (token.first()) {
            '+', '=' -> projectId == null && listId == null
            '#' -> tagsEnabled
            '@' -> peopleEnabled
            else -> false
        }
        val explicit = review.references.entries.firstOrNull { it.key.equals(token, true) }?.value
        relevant && explicit == null && !validMarkdownReference(token, id, importContext)
    }
    val reviewed by produceState<ReviewedMarkdownPreview?>(null, basePreviews, appliedReview, existingTasks, referenceMemory) {
        val base = basePreviews ?: return@produceState
        val existing = existingTasks ?: return@produceState
        if (referenceMemory == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val tasks = reviewMarkdownTasks(base, appliedReview, importContext, projectId != null || listId != null, useDates)
            ReviewedMarkdownPreview(base, appliedReview, existing, tasks, markdownDuplicateMatches(tasks.map { it.task }, existing))
        }
    }
    val currentReview = reviewed?.takeIf { it.base === basePreviews && it.review === appliedReview && it.existing === existingTasks }
    val previews = currentReview?.tasks
    val duplicates = currentReview?.duplicates.orEmpty()
    val importIndices = selectedSet.filterTo(hashSetOf()) { index ->
        index !in duplicates || review.duplicateAction(index, duplicates[index]) != MarkdownDuplicateAction.SKIP
    }
    val updateTargets = importIndices.mapNotNull { index ->
        duplicates[index]?.takeIf { review.duplicateAction(index, duplicates[index]) == MarkdownDuplicateAction.UPDATE }?.let { index to it.id }
    }.toMap()
    val invalidDestinations = remember(previews, activeProjectIds, activeListIds) {
        previews?.mapIndexedNotNull { index, preview ->
            val task = preview.task
            index.takeIf {
                (task.projectId == null && task.listId == null) ||
                    (task.projectId != null && task.projectId !in activeProjectIds) ||
                    (task.listId != null && task.listId !in activeListIds)
            }
        }?.toSet().orEmpty()
    }
    val destinationValid = previews != null && importIndices.all { it in previews.indices && it !in invalidDestinations }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Persist read access so the preview can be rebuilt after process recreation.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selected = null
            editingIndex = null
            updateReview(MarkdownImportReview())
            document = null
            fileName = null
            readError = 0
            fileUri = uri.toString()
            fileRevision++
        }
    }
    LaunchedEffect(fileUri, fileRevision, dateAliases) {
        val uri = fileUri ?: return@LaunchedEffect
        loading = true
        readError = 0
        try {
            val file = withContext(Dispatchers.IO) { readMarkdownFile(context.contentResolver, Uri.parse(uri)) }
            document = file.document
            fileName = file.name
            if (selected == null) selected = file.document.tasks.indices.filter { !file.document.tasks[it].done }
            if (file.document.tasks.isEmpty()) readError = R.string.markdown_import_empty
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            readError = if (e is MarkdownImportTooLargeException) R.string.markdown_import_too_large
                else R.string.markdown_import_read_failed
        } finally {
            loading = false
        }
    }
    LaunchedEffect(status) {
        if (status?.sessionId == sessionId && status?.imported == true) {
            onImported(importedDestination?.first, importedDestination?.second)
        }
    }
    // Keep the single batch write attached to its preview until its result is known.
    BackHandler(enabled = importing || creatingReference) { }
    val tasks = document?.tasks.orEmpty()
    val selectedReferenceTokens = remember(previews, importIndices) {
        markdownReferenceTokens(previews.orEmpty().filterIndexed { index, _ -> index in importIndices })
    }
    val referencesValid = appliedReview.references.filterKeys { it.lowercase() in selectedReferenceTokens }.all { (token, id) -> when (token.first()) {
        '+' -> projectId != null || listId != null || activeProjects.any { it.id == id }
        '=' -> projectId != null || listId != null || activeLists.any { it.id == id }
        '@' -> people.any { it.id == id && !it.archived }
        else -> tags.any { it.id == id }
    } }
    val canImport = !importing && !creatingReference && staleMappings.keys.none { it in selectedReferenceTokens } && referenceMemory != null && referencesValid && !loading && readError == 0 && previews != null &&
        importIndices.isNotEmpty() && destinationValid

    val summary = remember(previews, selectedSet, duplicates, review) {
        markdownImportSummary(previews.orEmpty(), selectedSet, duplicates, review)
    }
    val visibleIndices = remember(previews, tasks, selectedSet, duplicates, filter) {
        if (filter == MarkdownPreviewFilter.ALL) tasks.indices.toList()
        else markdownVisibleIndices(previews.orEmpty(), selectedSet, duplicates, filter)
    }
    fun importApprovedTasks() {
        if (!canImport) return
        previews?.let { approved ->
            importedDestination = approved.filterIndexed { index, _ -> index in importIndices }
                .map { it.task.projectId to it.task.listId }.distinct().singleOrNull()
            viewModel.importMarkdownTasks(sessionId, approved.map { it.task }, importIndices, updateTargets)
            confirmingImport = false
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val controlsEnabled = !importing && !creatingReference
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.markdown_import_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !importing && !creatingReference) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 760.dp).fillMaxWidth().navigationBarsPadding().heightIn(min = 80.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pluralStringResource(R.plurals.selection_count, selectedSet.size, selectedSet.size),
                                style = MaterialTheme.typography.titleSmall)
                            if (selectedSet.size != importIndices.size) Text(
                                pluralStringResource(R.plurals.markdown_tasks_to_import, importIndices.size, importIndices.size),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(enabled = canImport, modifier = Modifier.heightIn(min = 48.dp),
                            onClick = { confirmingImport = true }) {
                            if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_import))
                        }
                    }
                }
            }
        }
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "source") {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(Icons.Default.Description, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp).size(24.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(document?.title ?: stringResource(R.string.markdown_import_title),
                                        style = MaterialTheme.typography.titleMedium)
                                    fileName?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                            if (document == null) Text(stringResource(R.string.markdown_import_help),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(
                                onClick = { picker.launch(arrayOf("*/*")) },
                                enabled = controlsEnabled && !loading,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.markdown_import_choose_file))
                            }
                            if (loading || (document != null && previews == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                if (readError != 0) item(key = "read-error") { MarkdownMessage(stringResource(readError), isError = true) }
                if (status?.sessionId == sessionId && status?.failed == true) item(key = "save-error") {
                    MarkdownMessage(stringResource(R.string.error_action_failed), isError = true)
                }
                item(key = "destination") {
                    Card(shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.new_task_organize), style = MaterialTheme.typography.titleMedium)
                            MarkdownDestinationPicker(
                                label = stringResource(R.string.entity_project),
                                selectedName = entityLabels.projects[projectId].takeIf { projectId in activeProjectIds },
                                options = projectOptions, enabled = controlsEnabled, onSelected = { projectId = it }
                            )
                            MarkdownDestinationPicker(
                                label = stringResource(R.string.entity_list),
                                selectedName = entityLabels.lists[listId].takeIf { listId in activeListIds },
                                options = listOptions, enabled = controlsEnabled, onSelected = { listId = it }
                            )
                            if (!destinationValid && document != null && previews != null && importIndices.isNotEmpty()) {
                                Text(stringResource(R.string.markdown_import_destination), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (tasks.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            ListItem(
                                modifier = Modifier.toggleable(useDates, enabled = controlsEnabled, role = Role.Switch, onValueChange = { useDates = it }),
                                headlineContent = { Text(stringResource(R.string.markdown_import_dates)) },
                                supportingContent = { Text(stringResource(R.string.markdown_import_dates_hint, document!!.referenceDate.toString())) },
                                leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                trailingContent = { Switch(checked = useDates, onCheckedChange = null, enabled = controlsEnabled) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    }
                }
                if (tasks.isNotEmpty()) {
                    item(key = "selection") {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.markdown_import_select_tasks), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.markdown_import_selection_hint), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { selected = tasks.indices.toList() },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    enabled = controlsEnabled && selectedSet.size < tasks.size) {
                                    Text(stringResource(R.string.action_select_all))
                                }
                                TextButton(onClick = { selected = emptyList() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    enabled = controlsEnabled && selectedSet.isNotEmpty()) {
                                    Text(stringResource(R.string.action_select_none))
                                }
                            }
                        }
                    }
                    if (previews != null) item(key = "review-tools") {
                        MarkdownReviewToolbar(summary, filter, duplicates.isNotEmpty(), controlsEnabled,
                            onFilter = { filterName = it.name },
                            onDuplicates = { action -> updateReview(review.withBulkDuplicates(duplicates, action)) })
                    }
                    val referenceTokens = (basePreviews.orEmpty().flatMap { it.unmatched } + appliedReview.references.keys + review.ignoredSavedReferences)
                        .distinctBy { it.lowercase() }.sorted()
                    if (referenceTokens.isNotEmpty()) item(key = "references") {
                        MarkdownReferenceControls(referenceTokens, importContext, appliedReview.references, controlsEnabled,
                            rememberMappings = rememberMappings,
                            stale = staleMappings.isNotEmpty(),
                            onRemember = { enabled ->
                                updateReview(review.copy(rememberMappings = enabled))
                                viewModel.rememberMarkdownMappings(enabled)
                                if (enabled) review.references.forEach { (token, id) -> viewModel.saveMarkdownReference(token, id) }
                            },
                            onMap = { token, id ->
                                val key = token.lowercase()
                                val latest = viewModel.markdownReview(sessionId).value
                                updateReview(latest.copy(
                                    references = if (id == null) latest.references.filterKeys { !it.equals(token, true) }
                                        else latest.references.filterKeys { !it.equals(token, true) } + (token to id),
                                    ignoredSavedReferences = if (id == null) latest.ignoredSavedReferences + key else latest.ignoredSavedReferences - key))
                                if (rememberMappings || id == null) viewModel.saveMarkdownReference(token, id)
                            }, onCreate = { token, result -> viewModel.createMarkdownReference(token, onResult = result) })
                    }
                    if (previews != null && visibleIndices.isEmpty()) item(key = "no-matches") {
                        Text(stringResource(R.string.search_no_matches), style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 24.dp))
                    }
                    items(visibleIndices, key = { index -> "$fileUri:$fileRevision:$index" }) { index ->
                        val task = tasks[index]
                        MarkdownTaskCard(
                            task, previews?.getOrNull(index), entityLabels, index in selectedSet, !importing && !creatingReference,
                            excludedSubtasks = review.excludedSubtasks[index].orEmpty(),
                            rawSubtasks = review.edits[index]?.takeIf { "subtasks" in it.fields }?.task?.subtasks ?: basePreviews?.getOrNull(index)?.task?.subtasks.orEmpty(),
                            duplicate = duplicates[index], duplicateAction = review.duplicateAction(index, duplicates[index]),
                            onDuplicateAction = { updateReview(review.copy(duplicateActions = review.duplicateActions + (index to MarkdownDuplicateDecision(it, requireNotNull(duplicates[index]).id)))) },
                            onEdit = { editingIndex = index },
                            onSubtaskSelected = { subtaskIndex, checked ->
                                val excluded = review.excludedSubtasks[index].orEmpty()
                                updateReview(review.copy(excludedSubtasks = review.excludedSubtasks +
                                    (index to if (checked) excluded - subtaskIndex else excluded + subtaskIndex)))
                            },
                            onSelected = { checked -> selected = if (checked) (selected.orEmpty() + index).distinct() else selected.orEmpty() - index }
                        )
                    }
                }
            }
        }
    }
    if (confirmingImport) AlertDialog(
        onDismissRequest = { if (!importing) confirmingImport = false },
        title = { Text(stringResource(R.string.markdown_summary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (previews == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else MarkdownSummaryText(summary)
                if (summary.unresolved > 0) MarkdownMessage(stringResource(R.string.markdown_import_unmatched,
                    previews.orEmpty().filterIndexed { index, _ -> index in importIndices }.flatMap { it.unmatched }.distinct().joinToString(", ", limit = 8, truncated = "…") { it.take(120) }))
                if (staleMappings.isNotEmpty()) MarkdownMessage(stringResource(R.string.markdown_mapping_missing), isError = true)
            }
        },
        confirmButton = { TextButton(onClick = { importApprovedTasks() }, enabled = canImport) { Text(stringResource(R.string.action_import)) } },
        dismissButton = { TextButton(onClick = { confirmingImport = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
    var editorTask by remember(editingIndex) { mutableStateOf<Task?>(null) }
    LaunchedEffect(editingIndex, previews) {
        if (editorTask == null) editorTask = editingIndex?.let { previews?.getOrNull(it)?.task }
    }
    editingIndex?.let { index ->
        editorTask?.let { task ->
            key(index) {
                MarkdownPreviewEditor(task, importContext, viewModel,
                    onSave = { edited ->
                        updateReview(review.withTaskEdit(index, task, edited))
                        editingIndex = null
                    }, onDismiss = { editingIndex = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkdownDestinationPicker(
    label: String,
    selectedName: String?,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: stringResource(R.string.task_detail_none),
            onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            YataDropdownMenuItem(text = { Text(stringResource(R.string.task_detail_none)) }, enabled = enabled,
                onClick = { expanded = false; onSelected(null) })
            options.forEach { (id, name) ->
                YataDropdownMenuItem(text = { Text(name) }, enabled = enabled,
                    onClick = { expanded = false; onSelected(id) })
            }
        }
    }
}

@Composable
internal fun MarkdownMessage(message: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().semantics { if (isError) liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MarkdownTaskCard(
    task: MarkdownTask,
    preview: MarkdownTaskPreview?,
    labels: MarkdownEntityLabels,
    selected: Boolean,
    enabled: Boolean,
    excludedSubtasks: Set<Int>,
    rawSubtasks: List<com.mj.yata.domain.model.Subtask>,
    duplicate: Task?,
    duplicateAction: MarkdownDuplicateAction,
    onDuplicateAction: (MarkdownDuplicateAction) -> Unit,
    onEdit: () -> Unit,
    onSubtaskSelected: (Int, Boolean) -> Unit,
    onSelected: (Boolean) -> Unit
) {
    var details by rememberSaveable { mutableStateOf(false) }
    val notes = preview?.task?.notes ?: task.notes
    val includedSubtasks = preview?.task?.subtasks ?: rawSubtasks
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 56.dp).toggleable(selected, enabled = enabled,
                    role = Role.Checkbox, onValueChange = onSelected),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                Text(preview?.task?.title ?: task.title, Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onEdit, enabled = enabled && preview != null) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.task_row_edit_title))
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            task.heading?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            preview?.let { MarkdownDetectedFields(it, labels) }
            if (task.done) MarkdownInfoChip(stringResource(R.string.export_done_label))
            if (duplicate != null && selected) {
                val targetContext = listOfNotNull(labels.projects[duplicate.projectId]?.let { "+$it" },
                    labels.lists[duplicate.listId]?.let { "=$it" }, duplicate.due).joinToString(" · ")
                MarkdownDuplicateControls(duplicate, targetContext, duplicateAction, enabled, onDuplicateAction) {
                    preview?.let { MarkdownUpdateChanges(duplicate, it.task, labels) }
                }
            }
            if (notes != null || rawSubtasks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { details = !details }) {
                        Text(stringResource(R.string.people_details))
                        Spacer(Modifier.width(8.dp))
                        Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    if (rawSubtasks.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.new_task_subtasks), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.task_detail_subtasks_progress, includedSubtasks.count { it.done }, includedSubtasks.size),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (details) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val byId = remember(rawSubtasks) { rawSubtasks.associateBy { it.id } }
                rawSubtasks.forEachIndexed { index, subtask ->
                    var depth = 0
                    var parent = subtask.parentSubtaskId
                    while (parent != null && depth < 6) { depth++; parent = byId[parent]?.parentSubtaskId }
                    Row(Modifier.fillMaxWidth().padding(start = (depth * 12).dp).heightIn(min = 48.dp).toggleable(
                        value = index !in excludedSubtasks, enabled = enabled && selected,
                        role = Role.Checkbox, onValueChange = { onSubtaskSelected(index, it) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = index !in excludedSubtasks, onCheckedChange = null, enabled = enabled && selected)
                        Text(subtask.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (subtask.done) Text(stringResource(R.string.export_done_label), style = MaterialTheme.typography.labelSmall)
                    }
                }
                notes?.let {
                    Text(stringResource(R.string.new_task_notes), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownDetectedFields(preview: MarkdownTaskPreview, entities: MarkdownEntityLabels) {
    val task = preview.task
    val project = entities.projects[task.projectId]
    val list = entities.lists[task.listId]
    val labels = buildList {
        task.due?.let { add(stringResource(R.string.quick_add_preview_due, it)) }
        project?.let { add("+$it") }
        list?.let { add("=$it") }
        task.assigneeIds.mapNotNull { entities.people[it] }.forEach { add("@$it") }
        task.startDate?.let { add(stringResource(R.string.smart_add_starts, it)) }
        task.time?.let { add(stringResource(R.string.quick_add_preview_time, it)) }
        task.recurrence?.let { add(stringResource(R.string.quick_add_preview_repeat, RecurrenceEvaluator.recurrenceSummary(it))) }
        task.reminder?.let { add(stringResource(R.string.export_reminder_pill, it)) }
        if (task.priority != "none") add(stringResource(R.string.quick_add_preview_priority, stringResource(
            when (task.priority) { "high" -> R.string.analytics_priority_high; "med" -> R.string.analytics_priority_medium; else -> R.string.analytics_priority_low }
        )))
        if (task.flag) add(stringResource(R.string.quick_add_preview_flagged))
    }
    if (task.due == null) Text(stringResource(R.string.search_filter_no_due_date), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (labels.isNotEmpty() || task.tagIds.isNotEmpty()) FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { MarkdownInfoChip(it) }
        task.tagIds.mapNotNull { entities.tags[it] }.forEach { TagChip(name = it.name, accentKey = it.color) }
    }
    if (preview.unmatched.isNotEmpty()) MarkdownMessage(
        stringResource(R.string.markdown_import_unmatched, preview.unmatched.joinToString(", ")))
}

@Composable
private fun MarkdownInfoChip(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MarkdownUpdateChanges(before: Task, incoming: Task, labels: MarkdownEntityLabels) {
    val (after, fields) = remember(before, incoming) { markdownChangedFields(before, incoming) }
    HorizontalDivider()
    Text(stringResource(R.string.markdown_changes), style = MaterialTheme.typography.labelLarge)
    if (fields.isEmpty()) Text(stringResource(R.string.markdown_no_changes), style = MaterialTheme.typography.bodySmall)
    fields.forEach { field ->
        val label = stringResource(when (field) {
            MarkdownUpdateField.TITLE -> R.string.tasker_field_title
            MarkdownUpdateField.PROJECT -> R.string.entity_project
            MarkdownUpdateField.LIST -> R.string.entity_list
            MarkdownUpdateField.DUE -> R.string.task_detail_due_date
            MarkdownUpdateField.START -> R.string.task_start_date
            MarkdownUpdateField.TIME -> R.string.new_task_time
            MarkdownUpdateField.REMINDER -> R.string.task_detail_reminder
            MarkdownUpdateField.RECURRENCE -> R.string.new_task_repeat
            MarkdownUpdateField.PRIORITY -> R.string.settings_default_priority
            MarkdownUpdateField.FLAG -> R.string.task_detail_flag
            MarkdownUpdateField.DONE -> R.string.export_done_label
            MarkdownUpdateField.PEOPLE -> R.string.new_task_assign
            MarkdownUpdateField.TAGS -> R.string.new_task_tags
            MarkdownUpdateField.NOTES -> R.string.new_task_notes
            MarkdownUpdateField.SUBTASKS -> R.string.new_task_subtasks
            MarkdownUpdateField.ESTIMATE -> R.string.settings_default_estimate_custom_label
        })
        Text(stringResource(R.string.markdown_change_value, label, markdownChangeValue(field, before, labels),
            markdownChangeValue(field, after, labels)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun markdownChangeValue(field: MarkdownUpdateField, task: Task, labels: MarkdownEntityLabels): String {
    val value = when (field) {
        MarkdownUpdateField.TITLE -> task.title
        MarkdownUpdateField.PROJECT -> labels.projects[task.projectId]
        MarkdownUpdateField.LIST -> labels.lists[task.listId]
        MarkdownUpdateField.DUE -> task.due
        MarkdownUpdateField.START -> task.startDate
        MarkdownUpdateField.TIME -> task.time
        MarkdownUpdateField.REMINDER -> task.reminder
        MarkdownUpdateField.RECURRENCE -> task.recurrence?.let { RecurrenceEvaluator.recurrenceSummary(it) }
        MarkdownUpdateField.PRIORITY -> stringResource(when (task.priority) {
            "high" -> R.string.analytics_priority_high; "med" -> R.string.analytics_priority_medium
            "low" -> R.string.analytics_priority_low; else -> R.string.analytics_priority_none
        })
        MarkdownUpdateField.FLAG -> stringResource(if (task.flag) R.string.cd_task_flagged else R.string.motion_mode_off)
        MarkdownUpdateField.DONE -> stringResource(if (task.done) R.string.export_done_label else R.string.person_detail_pending)
        MarkdownUpdateField.PEOPLE -> task.assigneeIds.joinToString(", ") { labels.people[it] ?: it }
        MarkdownUpdateField.TAGS -> task.tagIds.joinToString(", ") { labels.tags[it]?.name ?: it }
        MarkdownUpdateField.NOTES -> task.notes
        MarkdownUpdateField.SUBTASKS -> task.subtasks.joinToString(", ") { (if (it.done) "✓ " else "○ ") + it.title }
        MarkdownUpdateField.ESTIMATE -> task.estimateMinutes?.toString()
    }?.takeIf { it.isNotBlank() } ?: stringResource(R.string.task_detail_none)
    return if (value.length > 180) value.take(85) + "…" + value.takeLast(85) else value
}
