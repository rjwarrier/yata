package com.mj.yata.ui.screen.markdownimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.mj.yata.util.MarkdownImportSummary
import com.mj.yata.util.MarkdownPreviewFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import com.mj.yata.R
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.sheets.NewTaskDraft
import com.mj.yata.ui.sheets.NewTaskSheet
import com.mj.yata.ui.sheets.TaskDraftEditorOptions
import com.mj.yata.util.MarkdownDuplicateAction
import com.mj.yata.util.MarkdownImportContext

@Composable
internal fun MarkdownReferenceControls(
    tokens: List<String>, context: MarkdownImportContext, mappings: Map<String, String>, enabled: Boolean,
    onMap: (String, String?) -> Unit, onCreate: (String, (String?) -> Unit) -> Unit,
    rememberMappings: Boolean, stale: Boolean, onRemember: (Boolean) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val optionsByType = remember(context.projects, context.lists, context.people, context.tags) {
        mapOf(
            '+' to context.projects.filter { !it.archived }.map { it.id to it.name },
            '=' to context.lists.filter { !it.archived }.map { it.id to it.name },
            '@' to context.people.filter { !it.archived }.map { it.id to it.name },
            '#' to context.tags.map { it.id to it.name }
        ).mapValues { (_, options) -> options.sortedBy { it.second.lowercase() } }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded },
            headlineContent = { Text(stringResource(R.string.markdown_resolve_references), style = MaterialTheme.typography.titleSmall) },
            leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingContent = { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        )
        if (stale) MarkdownMessage(stringResource(R.string.markdown_mapping_missing), isError = true,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (expanded) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.markdown_remember_mappings), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = rememberMappings, onCheckedChange = onRemember, enabled = enabled)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (expanded) LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tokens, key = { it.lowercase() }) { token ->
                    val options = optionsByType[token.first()].orEmpty()
                    val mappedId = mappings.entries.firstOrNull { it.key.equals(token, true) }?.value
                    Column {
                        MarkdownDestinationPicker(token, options.firstOrNull { it.first == mappedId }?.second,
                            options, enabled && creating == null) { onMap(token, it) }
                        TextButton(enabled = enabled && creating == null, onClick = {
                            creating = token
                            failed = false
                            onCreate(token) { id ->
                                if (id != null) onMap(token, id) else failed = true
                                creating = null
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.markdown_create_reference, token))
                        }
                    }
                }
            }
            if (creating != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) MarkdownMessage(stringResource(R.string.error_action_failed), isError = true)
            if (expanded || creating != null || failed) Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MarkdownDuplicateControls(
    existing: Task, targetContext: String, action: MarkdownDuplicateAction, enabled: Boolean, onAction: (MarkdownDuplicateAction) -> Unit,
    changes: @Composable () -> Unit = {}
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.action_similar_task_already_exists), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.duplicate_task_match_body, existing.title), style = MaterialTheme.typography.bodyMedium)
            if (targetContext.isNotBlank()) Text(targetContext, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                MarkdownDuplicateAction.entries.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = action == value,
                            enabled = enabled, role = Role.RadioButton, onClick = { onAction(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = action == value, onClick = null, enabled = enabled)
                        Text(stringResource(when (value) {
                            MarkdownDuplicateAction.SKIP -> R.string.welcome_skip
                            MarkdownDuplicateAction.COPY -> R.string.markdown_import_separately
                            MarkdownDuplicateAction.UPDATE -> R.string.markdown_update_existing
                        }), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (action == MarkdownDuplicateAction.UPDATE) {
                Text(stringResource(R.string.markdown_update_keeps_context), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                changes()
            }
        }
    }
}

@Composable
internal fun MarkdownPreviewEditor(
    task: Task, context: MarkdownImportContext, viewModel: MainViewModel, onSave: (Task) -> Unit, onDismiss: () -> Unit
) {
    val pending by viewModel.markdownReferencePending.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    val aliases = remember { mutableStateMapOf<String, String>() }
    fun create(id: String, token: String, color: String) {
        viewModel.createMarkdownReference(token, id, color) { actual ->
            if (actual != null) aliases[id] = actual else { aliases[id] = ""; failed = true }
        }
    }
    val draft = remember(task) { NewTaskDraft(task.title, task.listId, task.priority, task.assigneeIds, task.tagIds,
        task.recurrence, task.due, task.startDate, task.time, task.reminder, task.section, task.projectId,
        task.notes, task.subtasks, task.flag, task.estimateMinutes) }
    Dialog(onDismissRequest = { if (pending == 0) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            AdaptiveContentBox {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    if (pending > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (failed) Text(stringResource(R.string.error_action_failed), color = MaterialTheme.colorScheme.error)
                    NewTaskSheet(
                        lists = context.lists, projects = context.projects, people = context.people, tags = context.tags,
                        initialDraft = draft, autoAssignToMe = false,
                        headerTitleRes = R.string.task_row_edit_title, editorOptions = TaskDraftEditorOptions(pending == 0),
                        projectsEnabled = context.projectsEnabled, peopleEnabled = context.peopleEnabled, tagsEnabled = context.tagsEnabled,
                        onCreateTag = { id, name, color -> create(id, "#$name", color) },
                        onCreatePerson = { id, name, color -> create(id, "@$name", color) },
                        onDismiss = { if (pending == 0) onDismiss() },
                        onAddTask = { edited ->
                            onSave(task.copy(title = edited.title, listId = edited.listId, projectId = edited.projectId,
                                priority = edited.priority, assigneeIds = edited.assigneeIds.mapNotNull { (aliases[it] ?: it).takeIf(String::isNotBlank) }.distinct(),
                                tagIds = edited.tagIds.mapNotNull { (aliases[it] ?: it).takeIf(String::isNotBlank) }.distinct(), recurrence = edited.recurrence,
                                due = edited.due, startDate = edited.startDate, time = edited.time, reminder = edited.reminder,
                                section = edited.section, notes = edited.notes, subtasks = edited.subtasks, flag = edited.flag,
                                estimateMinutes = edited.estimateMinutes))
                        }
                    )
                }
                }
            }
    }
}

@Composable
internal fun MarkdownSummaryText(summary: MarkdownImportSummary) {
    Text(pluralStringResource(R.plurals.markdown_summary_counts, summary.newTasks + summary.updatedTasks,
        summary.newTasks, summary.updatedTasks, summary.skippedRows, summary.unresolved), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarkdownReviewToolbar(
    summary: MarkdownImportSummary, filter: MarkdownPreviewFilter, hasDuplicates: Boolean, enabled: Boolean,
    onFilter: (MarkdownPreviewFilter) -> Unit, onDuplicates: (MarkdownDuplicateAction) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.markdown_summary), style = MaterialTheme.typography.titleMedium)
            MarkdownSummaryText(summary)
            if (hasDuplicates) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDuplicates(MarkdownDuplicateAction.SKIP) }, enabled = enabled) {
                    Text(stringResource(R.string.markdown_skip_matches))
                }
                OutlinedButton(onClick = { onDuplicates(MarkdownDuplicateAction.COPY) }, enabled = enabled) {
                    Text(stringResource(R.string.markdown_copy_matches))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownPreviewFilter.entries.forEach { value ->
                    FilterChip(selected = filter == value, onClick = { onFilter(value) }, enabled = enabled,
                        label = { Text(stringResource(when (value) {
                            MarkdownPreviewFilter.ALL -> R.string.task_filter_all
                            MarkdownPreviewFilter.SELECTED -> R.string.settings_language_selected
                            MarkdownPreviewFilter.DUPLICATES -> R.string.markdown_duplicates
                            MarkdownPreviewFilter.UNRESOLVED -> R.string.markdown_unresolved
                        })) })
                }
            }
            Text(stringResource(R.string.markdown_filter_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
