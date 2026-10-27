package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.domain.model.Tag
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors

/** Shared by every task multiselect surface; creation also applies the tag to the selection. */
@Composable
fun TaskBulkTagPickerSheet(
    tags: List<Tag>,
    onSelectTag: (String) -> Unit,
    onCreateTag: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    var query by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newTagName by rememberSaveable { mutableStateOf("") }
    val search = query.trim().removePrefix("#").trim()
    val sortedTags = remember(tags) { tags.sortedBy { it.name.lowercase() } }
    val filtered = remember(sortedTags, search) {
        if (search.isEmpty()) sortedTags else sortedTags.filter { it.name.contains(search, ignoreCase = true) }
    }
    val canCreateSearch = remember(tags, search) {
        search.isNotBlank() && tags.none { it.name.trim().equals(search, ignoreCase = true) }
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
                stringResource(R.string.bulk_add_tag_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        OutlinedTextField(
            value = query,
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
        if (canCreateSearch) {
            FilledTonalButton(
                onClick = { onCreateTag(search, pickAccentFor(search)) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mention_create_tag, search), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            FilledTonalButton(
                onClick = { newTagName = ""; showCreateDialog = true },
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
                    val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
                    OutlinedCard(
                        onClick = { onSelectTag(tag.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val nameFocus = remember { FocusRequester() }
        val name = newTagName.trim().removePrefix("#").trim()
        fun create() {
            if (name.isNotBlank()) {
                showCreateDialog = false
                onCreateTag(name, pickAccentFor(name))
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
