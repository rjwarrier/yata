package com.mj.yata.ui.screen.markdownimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onMap: (String, String?) -> Unit, onCreate: (String, (String?) -> Unit) -> Unit
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.markdown_resolve_references)) }
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
                        }) { Text(stringResource(R.string.markdown_create_reference, token)) }
                    }
                }
            }
            if (creating != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.error_action_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun MarkdownDuplicateControls(
    existing: Task, targetContext: String, action: MarkdownDuplicateAction, enabled: Boolean, onAction: (MarkdownDuplicateAction) -> Unit
) {
    Text(stringResource(R.string.duplicate_task_match_body, existing.title), style = MaterialTheme.typography.bodySmall)
    if (targetContext.isNotBlank()) Text(targetContext, style = MaterialTheme.typography.bodySmall)
    Column {
        MarkdownDuplicateAction.entries.forEach { value ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = action == value, onClick = { onAction(value) }, enabled = enabled)
                TextButton(onClick = { onAction(value) }, enabled = enabled) {
                    Text(stringResource(when (value) {
                        MarkdownDuplicateAction.SKIP -> R.string.welcome_skip
                        MarkdownDuplicateAction.COPY -> R.string.markdown_import_separately
                        MarkdownDuplicateAction.UPDATE -> R.string.markdown_update_existing
                    }))
                }
            }
        }
    }
    if (action == MarkdownDuplicateAction.UPDATE) Text(stringResource(R.string.markdown_update_keeps_context),
        style = MaterialTheme.typography.bodySmall)
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
