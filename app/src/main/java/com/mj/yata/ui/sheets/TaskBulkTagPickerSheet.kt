package com.mj.yata.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.usecase.BulkTagEdit
import com.mj.yata.domain.usecase.previewBulkTagEdit
import java.util.UUID
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import com.mj.yata.R
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors

/** Draft all tag operations locally; only Apply writes tags and tasks. */
@Composable
fun TaskBulkTagPickerSheet(
    tags: List<Tag>,
    onApply: suspend (BulkTagEdit) -> Boolean,
    onDismiss: () -> Unit,
    selectedTasks: List<Task>,
    projects: List<Project> = emptyList(),
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    var query by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newTagName by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var applying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var additions by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var removals by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var newTagValues by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val newTags = remember(newTagValues) { newTagValues.chunked(3).filter { it.size == 3 }.map { Tag(it[0], it[1], it[2]) } }
    val availableTags = remember(tags, newTags) { (tags + newTags).distinctBy { it.id } }
    val changes = remember(additions, removals) { additions.associateWith { true } + removals.associateWith { false } }
    val preview = remember(selectedTasks, projects, changes) { previewBulkTagEdit(selectedTasks, projects, changes) }
    val counts = remember(preview, projects) {
        val inherited = projects.associate { it.id to it.commonTagIds }
        buildMap<String, Int> { preview.forEach { task ->
            (task.tagIds + inherited[task.projectId].orEmpty()).distinct().forEach { put(it, getOrDefault(it, 0) + 1) }
        } }
    }
    val baselineCounts = remember(selectedTasks, projects) {
        val inherited = projects.associate { it.id to it.commonTagIds }
        buildMap<String, Int> { selectedTasks.forEach { task ->
            (task.tagIds + inherited[task.projectId].orEmpty()).distinct().forEach { put(it, getOrDefault(it, 0) + 1) }
        } }
    }
    val removableCounts = remember(selectedTasks, projects) {
        val inherited = projects.associate { it.id to it.commonTagIds.toSet() }
        buildMap<String, Int> { selectedTasks.forEach { task ->
            task.tagIds.distinct().filter { it !in inherited[task.projectId].orEmpty() }
                .forEach { put(it, getOrDefault(it, 0) + 1) }
        } }
    }
    val validIds = remember(availableTags) { availableTags.mapTo(hashSetOf()) { it.id } }
    LaunchedEffect(validIds) { additions = additions.filter { it in validIds }; removals = removals.filter { it in validIds } }
    BackHandler(enabled = applying) { }
    fun stage(id: String) {
        failed = false
        if (removing) {
            val already = id in removals
            additions = additions - id
            removals = if (already) removals - id else removals + id
        } else {
            val already = id in additions
            removals = removals - id
            additions = if (already) additions - id else additions + id
        }
    }
    fun createTag(raw: String, color: String) {
        val name = raw.trim().removePrefix("#").trim()
        if (name.isBlank()) return
        val existing = availableTags.firstOrNull { it.name.trim().equals(name, true) }
        val id = existing?.id ?: "tag_${UUID.randomUUID()}".also { newTagValues = newTagValues + listOf(it, name, color) }
        removals = removals - id
        additions = (additions + id).distinct()
    }
    val search = query.trim().removePrefix("#").trim()
    val sortedTags = remember(availableTags) { availableTags.sortedBy { it.name.lowercase() } }
    val filtered = remember(sortedTags, search) {
        if (search.isEmpty()) sortedTags else sortedTags.filter { it.name.contains(search, ignoreCase = true) }
    }
    val canCreateSearch = remember(availableTags, search) {
        search.isNotBlank() && availableTags.none { it.name.trim().equals(search, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.new_task_tags),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, enabled = !applying) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(enabled = !applying, selected = !removing, onClick = { removing = false }, label = { Text(stringResource(R.string.action_add)) })
            FilterChip(enabled = !applying, selected = removing, onClick = { removing = true }, label = { Text(stringResource(R.string.settings_remove)) })
        }
        if (removing) Text(stringResource(R.string.task_detail_tags_sync_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = query,
            enabled = !applying,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.mention_hint_tag), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = YataFieldShape,
            colors = yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (!removing && canCreateSearch) {
            FilledTonalButton(
                onClick = { createTag(search, pickAccentFor(search)) }, enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mention_create_tag, search), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else if (!removing) {
            FilledTonalButton(
                onClick = { newTagName = ""; showCreateDialog = true }, enabled = !applying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tags_new_tag))
            }
        }
        if (filtered.isEmpty()) {
            Text(
                stringResource(if (tags.isEmpty()) R.string.tags_empty_title else R.string.search_no_matches),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filtered, key = { it.id }) { tag ->
                    val count = counts[tag.id] ?: 0
                    val state = when { count == 0 -> ToggleableState.Off; count == selectedTasks.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                    val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
                    val ink = accents.onAccentFor(color)
                    val originalCount = baselineCounts[tag.id] ?: 0
                    val canApply = !applying && selectedTasks.isNotEmpty() &&
                        (tag.id in changes || if (removing) (removableCounts[tag.id] ?: 0) > 0 else originalCount < selectedTasks.size)
                    Surface(
                        onClick = { stage(tag.id) },
                        enabled = canApply,
                        modifier = Modifier.fillMaxWidth().semantics { toggleableState = state; role = Role.Checkbox },
                        shape = CircleShape,
                        color = color,
                        contentColor = ink
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TriStateCheckbox(
                                state = state,
                                onClick = null,
                                enabled = canApply,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ink,
                                    uncheckedColor = ink.copy(alpha = 0.8f),
                                    checkmarkColor = color,
                                    disabledCheckedColor = ink.copy(alpha = 0.7f),
                                    disabledUncheckedColor = ink.copy(alpha = 0.6f),
                                    disabledIndeterminateColor = ink.copy(alpha = 0.7f)
                                )
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    (if (changes[tag.id] == true) "+ " else if (changes[tag.id] == false) "− " else "") + tag.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(R.string.editor_char_counter, count, selectedTasks.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (failed) Text(stringResource(R.string.error_action_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(pluralStringResource(R.plurals.bulk_tag_pending, changes.size, changes.size),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Button(enabled = !applying && selectedTasks.isNotEmpty() && changes.isNotEmpty(), onClick = {
                applying = true
                failed = false
                scope.launch {
                    try { if (!onApply(BulkTagEdit(changes, newTags))) failed = true }
                    finally { applying = false }
                }
            }) {
                if (applying) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(stringResource(R.string.custom_color_picker_apply))
            }
        }
    }

    if (showCreateDialog) {
        val nameFocus = remember { FocusRequester() }
        val name = newTagName.trim().removePrefix("#").trim()
        fun create() {
            if (name.isNotBlank()) {
                showCreateDialog = false
                createTag(name, pickAccentFor(name))
            }
        }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.tags_new_tag)) },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text(stringResource(R.string.entity_editors_tag_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    shape = YataFieldShape,
                    colors = yataFieldColors(),
                    modifier = Modifier.focusRequester(nameFocus)
                )
                LaunchedEffect(Unit) { nameFocus.requestFocus() }
            },
            confirmButton = { TextButton(onClick = { create() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) } },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
