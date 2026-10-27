package com.mj.yata.ui.screen.markdownimport

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.widgets.YataDropdownMenu
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
    val dateAliases by viewModel.dateAliasDefinitions.collectAsStateWithLifecycle()
    val status by viewModel.markdownImportStatus.collectAsStateWithLifecycle()
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
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
    val previews = prepared?.takeIf { it.request === request }?.tasks
    val destinationValid = previews?.filterIndexed { index, _ -> index in selectedSet }?.all { preview ->
        val task = preview.task
        (task.projectId != null || task.listId != null) &&
            (task.projectId == null || activeProjects.any { it.id == task.projectId }) &&
            (task.listId == null || activeLists.any { it.id == task.listId })
    } == true

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Persist read access so the preview can be rebuilt after process recreation.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selected = null
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
            Toast.makeText(context, R.string.markdown_import_success, Toast.LENGTH_LONG).show()
            val destinations = previews.orEmpty().filterIndexed { index, _ -> index in selectedSet }
                .map { it.task.projectId to it.task.listId }.distinct()
            val destination = destinations.singleOrNull()
            onImported(destination?.first, destination?.second)
        }
    }
    // Keep the single batch write attached to its preview until its result is known.
    BackHandler(enabled = importing) { }
    val tasks = document?.tasks.orEmpty()
    val canImport = !importing && !loading && readError == 0 && previews != null &&
        selectedSet.isNotEmpty() && destinationValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.markdown_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !importing) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        pluralStringResource(R.plurals.selection_count, selectedSet.size, selectedSet.size),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = canImport,
                        onClick = {
                            previews?.let { viewModel.importMarkdownTasks(sessionId, it.map { preview -> preview.task }, selectedSet) }
                        }
                    ) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_import))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (document == null) {
                    Text(stringResource(R.string.markdown_import_help), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = {
                        // Providers label .md as text/markdown, text/plain, octet-stream or no MIME.
                        picker.launch(arrayOf("*/*"))
                    },
                    enabled = !importing && !loading
                ) { Text(stringResource(R.string.markdown_import_choose_file)) }
                fileName?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                document?.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                if (loading || (document != null && previews == null)) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                if (readError != 0) Text(stringResource(readError), color = MaterialTheme.colorScheme.error)
                if (status?.sessionId == sessionId && status?.failed == true) {
                    Text(stringResource(R.string.error_action_failed), color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                MarkdownDestinationPicker(
                    label = stringResource(R.string.settings_default_project),
                    selectedName = activeProjects.firstOrNull { it.id == projectId }?.name,
                    options = activeProjects.map { it.id to it.name },
                    enabled = !importing,
                    onSelected = { projectId = it }
                )
                Spacer(Modifier.height(8.dp))
                MarkdownDestinationPicker(
                    label = stringResource(R.string.settings_default_list),
                    selectedName = activeLists.firstOrNull { it.id == listId }?.name,
                    options = activeLists.map { it.id to it.name },
                    enabled = !importing,
                    onSelected = { listId = it }
                )
                if (!destinationValid) {
                    Text(stringResource(R.string.markdown_import_destination), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (tasks.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = useDates,
                            enabled = !importing,
                            role = Role.Switch,
                            onValueChange = { useDates = it }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.markdown_import_dates), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.markdown_import_dates_hint, document!!.referenceDate.toString()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = useDates, onCheckedChange = null, enabled = !importing)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.markdown_import_select_tasks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.markdown_import_selection_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { selected = tasks.indices.toList() },
                            enabled = !importing && selectedSet.size < tasks.size,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_select_all)) }
                        TextButton(
                            onClick = { selected = emptyList() },
                            enabled = !importing && selectedSet.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_select_none)) }
                    }
                }
                itemsIndexed(tasks, key = { index, _ -> "$fileUri:$fileRevision:$index" }) { index, task ->
                    MarkdownTaskCard(task, previews?.getOrNull(index), importContext, index in selectedSet, !importing) { checked ->
                        selected = if (checked) (selected.orEmpty() + index).distinct() else selected.orEmpty() - index
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownDestinationPicker(
    label: String,
    selectedName: String?,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(selectedName ?: stringResource(R.string.task_detail_none), Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
            YataDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                YataDropdownMenuItem(
                    text = { Text(stringResource(R.string.task_detail_none)) },
                    onClick = { expanded = false; onSelected(null) }
                )
                options.forEach { (id, name) ->
                    YataDropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelected(id) })
                }
            }
        }
    }
}

@Composable
private fun MarkdownTaskCard(
    task: MarkdownTask,
    preview: MarkdownTaskPreview?,
    context: MarkdownImportContext,
    selected: Boolean,
    enabled: Boolean,
    onSelected: (Boolean) -> Unit
) {
    var details by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelected),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                Text(preview?.task?.title ?: task.title, Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
            task.heading?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            Text(
                if (preview?.task?.due != null) stringResource(R.string.quick_add_preview_due, preview.task.due!!)
                else stringResource(R.string.search_filter_no_due_date),
                style = MaterialTheme.typography.bodySmall
            )
            preview?.let { MarkdownDetectedFields(it, context) }
            if (task.done) Text(stringResource(R.string.export_done_label), style = MaterialTheme.typography.labelMedium)
            if (task.subtasks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.new_task_subtasks), style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(R.string.task_detail_subtasks_progress, task.subtasks.count { it.done }, task.subtasks.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (task.notes != null || task.subtasks.isNotEmpty()) {
                TextButton(onClick = { details = !details }) {
                    Text(stringResource(R.string.people_details))
                    Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            if (details) {
                task.subtasks.forEachIndexed { index, subtask ->
                    var depth = 0
                    var parent = subtask.parentIndex
                    while (parent != null && parent < index && depth < 6) {
                        depth++
                        parent = task.subtasks[parent].parentIndex
                    }
                    Text(
                        "${if (subtask.done) "☑" else "☐"} ${subtask.title}",
                        Modifier.padding(start = (depth * 12).dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                task.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun MarkdownDetectedFields(preview: MarkdownTaskPreview, context: MarkdownImportContext) {
    val task = preview.task
    val project = context.projects.firstOrNull { it.id == task.projectId }
    val list = context.lists.firstOrNull { it.id == task.listId }
    val labels = buildList {
        project?.let { add("+${it.name}") }
        list?.let { add("=${it.name}") }
        task.tagIds.mapNotNull { id -> context.tags.firstOrNull { it.id == id } }.forEach { add("#${it.name}") }
        task.assigneeIds.mapNotNull { id -> context.people.firstOrNull { it.id == id } }.forEach { add("@${it.name}") }
        task.startDate?.let { add(stringResource(R.string.smart_add_starts, it)) }
        task.time?.let { add(stringResource(R.string.quick_add_preview_time, it)) }
        task.recurrence?.let { add(stringResource(R.string.quick_add_preview_repeat, RecurrenceEvaluator.recurrenceSummary(it))) }
        task.reminder?.let { add(stringResource(R.string.export_reminder_pill, it)) }
        if (task.priority != "none") add(stringResource(R.string.quick_add_preview_priority, stringResource(
            when (task.priority) { "high" -> R.string.analytics_priority_high; "med" -> R.string.analytics_priority_medium; else -> R.string.analytics_priority_low }
        )))
        if (task.flag) add(stringResource(R.string.quick_add_preview_flagged))
    }
    if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    if (preview.unmatched.isNotEmpty()) Text(
        stringResource(R.string.markdown_import_unmatched, preview.unmatched.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
