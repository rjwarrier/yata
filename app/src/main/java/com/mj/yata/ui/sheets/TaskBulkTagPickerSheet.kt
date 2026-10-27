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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import com.mj.yata.R
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors

/** Draft all tag operations locally; only Apply writes tags and tasks. */
@OptIn(ExperimentalMaterial3Api::class)
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.new_task_tags),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                modifier = Modifier.weight(1f)
            )
            if (!removing) IconButton(onClick = { newTagName = search; showCreateDialog = true }, enabled = !applying) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tags_new_tag))
            }
            IconButton(onClick = onDismiss, enabled = !applying) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !removing, onClick = { removing = false }, enabled = !applying,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) {
                Text(stringResource(R.string.action_add))
            }
            SegmentedButton(selected = removing, onClick = { removing = true }, enabled = !applying,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) {
                Text(stringResource(R.string.settings_remove))
            }
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
            TextButton(onClick = { createTag(search, pickAccentFor(search)) }, enabled = !applying) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mention_create_tag, search), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(filtered, key = { it.id }) { tag ->
                    val count = counts[tag.id] ?: 0
                    val state = when { count == 0 -> ToggleableState.Off; count == selectedTasks.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                    val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
                    val originalCount = baselineCounts[tag.id] ?: 0
                    val canApply = !applying && selectedTasks.isNotEmpty() &&
                        (tag.id in changes || if (removing) (removableCounts[tag.id] ?: 0) > 0 else originalCount < selectedTasks.size)
                    val staged = changes[tag.id]
                    val selected = count > 0
                    val base = MaterialTheme.colorScheme.surfaceContainerLow
                    val neutral = color.copy(alpha = 0.05f).compositeOver(base)
                    val tinted = color.copy(alpha = 0.14f).compositeOver(base)
                    val indicator = when {
                        staged == true -> Icons.Default.Add
                        staged == false -> Icons.Default.Remove
                        state == ToggleableState.On -> Icons.Default.Check
                        state == ToggleableState.Indeterminate -> Icons.Default.Remove
                        else -> null
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { stage(tag.id) },
                        enabled = canApply,
                        modifier = Modifier.fillMaxWidth().semantics { toggleableState = state },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = neutral,
                            selectedContainerColor = tinted,
                            disabledContainerColor = neutral,
                            disabledSelectedContainerColor = tinted,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = canApply, selected = selected,
                            borderColor = if (staged != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = if (staged != null) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.28f)
                        ),
                        leadingIcon = { Box(Modifier.size(8.dp).background(color, CircleShape)) },
                        trailingIcon = indicator?.let { icon ->
                            { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        },
                        label = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tag.name, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.editor_char_counter, count, selectedTasks.size),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (failed) Text(stringResource(R.string.error_action_failed), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(pluralStringResource(R.plurals.bulk_tag_pending, changes.size, changes.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Button(modifier = Modifier.heightIn(min = 48.dp), enabled = !applying && selectedTasks.isNotEmpty() && changes.isNotEmpty(), onClick = {
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
