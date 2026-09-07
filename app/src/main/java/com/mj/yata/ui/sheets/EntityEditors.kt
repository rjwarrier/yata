package com.mj.yata.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.mj.yata.R
import com.mj.yata.ui.widgets.ColorPicker
import com.mj.yata.ui.widgets.YataDatePickerDialog
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate
import java.util.Locale

/** Splits a comma-separated input into trimmed, non-blank names, de-duplicated
 * case-insensitively — "Alex, alex, ALEX" is one name, not three near-duplicate entities. */
private fun parseBulkNames(input: String): List<String> =
    input.split(",").map { it.trim() }.filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.getDefault()) }

/** Drops any bulk-create candidate that already matches (case-insensitively) an existing
 * entity name, so re-running a bulk create doesn't silently spawn indistinguishable duplicates. */
private fun List<String>.excludingExisting(existingNames: List<String>): List<String> =
    filterNot { candidate -> existingNames.any { it.equals(candidate, ignoreCase = true) } }

/**
 * True when [candidate] collides with an existing entity name on everything but capitalisation.
 *
 * Every lookup in the app compares names case-insensitively — findBestEntityMatch (natural
 * language and quick add), the #/@/+/= mention autocomplete, shared-link import, the Tasker
 * plugin — so "Work" and "work" are not two entities the user can tell apart by name; they are
 * one name where whichever row is found first wins and the other becomes permanently unreachable
 * by name. Blocking the second at creation is the only point where that is still fixable.
 *
 * [selfName] is excluded so renaming an entity to a different capitalisation of its own name
 * ("work" to "Work") stays allowed — that is a rename, not a collision.
 */
private fun nameCollides(candidate: String, existingNames: List<String>, selfName: String = ""): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.equals(selfName.trim(), ignoreCase = true)) return false
    return existingNames.any { it.trim().equals(trimmed, ignoreCase = true) }
}

/** Cap for a single-entity name field (Project/List) — unbounded before this, unlike the
 * Project description field which already had a limit. */
private const val NAME_LIMIT = 100

/** Cap for Person/Tag name fields, which can hold several comma-separated names at once in
 * create mode — higher than [NAME_LIMIT] so a legitimate multi-name paste isn't truncated. */
private const val BULK_NAME_FIELD_LIMIT = 300
private const val TAG_DESCRIPTION_LIMIT = 160

/** One-shot picker sheet: tap a group row to assign, or create a new one. Used for bulk "Add to group" actions. */
@Composable
fun <G> GroupAssignSheet(
    title: String,
    groups: List<G>,
    groupId: (G) -> String,
    groupName: (G) -> String,
    groupColorKey: (G) -> String,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    newGroupIdPrefix: String = "grp_",
    modifier: Modifier = Modifier
) {
    val accents = com.mj.yata.ui.theme.LocalYataAccents.current
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        groups.forEach { group ->
            val color = accents.getAccent(groupColorKey(group))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectGroup(groupId(group)) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
                Text(groupName(group), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (showNewGroupField) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text(stringResource(R.string.entity_editors_group_name)) },
                    singleLine = true,
                    shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                    colors = com.mj.yata.ui.widgets.yataFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (newGroupName.isNotBlank()) {
                        val id = newGroupIdPrefix + java.util.UUID.randomUUID().toString()
                        onCreateGroup(id, newGroupName.trim())
                        newGroupName = ""
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.entity_editors_create_group))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showNewGroupField = true }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.entity_editors_new_group), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorSheet(
    initialName: String = "",
    initialColor: String = "accentA",
    initialIcon: String = "layers",
    initialDueDate: String? = null,
    initialCommonTagIds: List<String> = emptyList(),
    initialDefaultReminder: String? = null,
    initialDescription: String? = null,
    initialExcludeFromToday: Boolean = false,
    tags: List<com.mj.yata.domain.model.Tag> = emptyList(),
    /** Names of the other projects, for the case-insensitive collision check — see [nameCollides]. */
    existingNames: List<String> = emptyList(),
    onSave: (String, String, String, String?, List<String>, String?, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    val nameTaken = remember(name, existingNames, initialName) {
        nameCollides(name, existingNames, initialName)
    }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var dueDate by remember { mutableStateOf<String?>(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var defaultReminder by remember { mutableStateOf(initialDefaultReminder) }
    var description by remember { mutableStateOf(initialDescription ?: "") }
    var excludeFromToday by remember { mutableStateOf(initialExcludeFromToday) }
    val selectedTagIds = remember { mutableStateListOf<String>().apply { addAll(initialCommonTagIds) } }
    var reminderExpanded by remember { mutableStateOf(initialDefaultReminder != null) }
    var tagsExpanded by remember { mutableStateOf(initialCommonTagIds.isNotEmpty()) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    val descriptionLimit = 100

    val entranceScale = remember { androidx.compose.animation.core.Animatable(0.92f) }
    val entranceAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val entranceSlide = remember { androidx.compose.animation.core.Animatable(30f) }

    LaunchedEffect(Unit) {
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )
        }
        launch {
            entranceSlide.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )
        }
        launch {
            entranceAlpha.animateTo(1f, androidx.compose.animation.core.tween(durationMillis = 150))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                translationY = entranceSlide.value
                alpha = entranceAlpha.value
            }
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New project" else "Edit project"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"
        val accents = com.mj.yata.ui.theme.LocalYataAccents.current
        val selectedTagNames = tags.filter { it.id in selectedTagIds }.map { it.name }

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        TextField(
            value = name,
            onValueChange = { if (it.length <= NAME_LIMIT) name = it },
            label = { Text(stringResource(R.string.entity_editors_project_name)) },
            placeholder = { Text(stringResource(R.string.entity_editors_e_g_work_list)) },
            singleLine = true,
            isError = nameTaken,
            supportingText = if (nameTaken) {
                { Text(stringResource(R.string.entity_name_taken)) }
            } else null,
            shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = description,
            onValueChange = { if (it.length <= descriptionLimit) description = it },
            label = { Text(stringResource(R.string.entity_editors_description)) },
            placeholder = { Text(stringResource(R.string.entity_editors_what_s_this_project_about)) },
            supportingText = { Text(stringResource(R.string.editor_char_counter, description.length, descriptionLimit)) },
            minLines = 2,
            maxLines = 6,
            shape = com.mj.yata.ui.widgets.YataFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.entity_editors_exclude_from_today)) },
                supportingContent = { Text(stringResource(R.string.entity_editors_exclude_from_today_summary)) },
                trailingContent = {
                    Switch(checked = excludeFromToday, onCheckedChange = { excludeFromToday = it })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { excludeFromToday = !excludeFromToday }
            )
        }

        ProjectEditorSection(
            title = stringResource(R.string.entity_editors_project_due_date),
            summary = dueDate?.let { TaskScheduleUtils.formatDueDate(it) } ?: stringResource(R.string.date_no_due),
            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
            expanded = true,
            onToggle = null
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = stringResource(R.string.date_no_due),
                    selected = dueDate == null,
                    onClick = { dueDate = null }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = stringResource(R.string.date_today),
                    selected = dueDate == LocalDate.now().toString(),
                    onClick = { dueDate = LocalDate.now().toString() }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = stringResource(R.string.date_tomorrow),
                    selected = dueDate == LocalDate.now().plusDays(1).toString(),
                    onClick = { dueDate = LocalDate.now().plusDays(1).toString() }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = if (dueDate != null && dueDate != LocalDate.now().toString() && dueDate != LocalDate.now().plusDays(1).toString()) TaskScheduleUtils.formatDueDate(dueDate) else stringResource(R.string.entity_editors_pick_date),
                    selected = dueDate != null && dueDate != LocalDate.now().toString() && dueDate != LocalDate.now().plusDays(1).toString(),
                    onClick = { showDatePicker = true }
                )
            }
        }

        ProjectEditorSection(
            title = stringResource(R.string.entity_editors_default_reminder),
            summary = defaultReminder ?: stringResource(R.string.settings_none),
            leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            expanded = reminderExpanded,
            onToggle = { reminderExpanded = !reminderExpanded }
        ) {
            Text(
                text = stringResource(R.string.entity_editors_default_reminder_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.mj.yata.ui.widgets.YataSelectChip(stringResource(R.string.settings_none), defaultReminder == null, { defaultReminder = null })
                TaskScheduleUtils.reminderOptions.forEach { option ->
                    com.mj.yata.ui.widgets.YataSelectChip(option, defaultReminder == option, { defaultReminder = option })
                }
            }
        }

        if (tags.isNotEmpty()) {
            ProjectEditorSection(
                title = stringResource(R.string.entity_editors_common_tags),
                summary = selectedTagNames.takeIf { it.isNotEmpty() }?.joinToString(limit = 2, truncated = "+${selectedTagNames.size - 2}") ?: stringResource(R.string.settings_default_tags_empty),
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                expanded = tagsExpanded,
                onToggle = { tagsExpanded = !tagsExpanded },
                preview = {
                    CompactTagPreview(
                        tagNames = selectedTagNames,
                        tags = tags,
                        selectedTagIds = selectedTagIds
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.entity_editors_common_tags_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        val selected = selectedTagIds.contains(tag.id)
                        val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
                        com.mj.yata.ui.widgets.YataSelectChip(
                            label = tag.name,
                            selected = selected,
                            onClick = {
                                if (selected) selectedTagIds.remove(tag.id) else selectedTagIds.add(tag.id)
                            },
                            tint = color,
                            dotColor = color
                        )
                    }
                }
            }
        }

        ProjectEditorSection(
            title = stringResource(R.string.settings_section_appearance),
            summary = stringResource(R.string.projects_project_icon),
            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
            preview = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accents.getAccent(selectedColor))
                    )
                    Surface(
                        shape = CircleShape,
                        color = accents.getAccent(selectedColor).copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = com.mj.yata.ui.widgets.iconVectorFor(selectedIcon),
                            contentDescription = null,
                            tint = accents.getAccent(selectedColor),
                            modifier = Modifier.padding(5.dp).size(16.dp)
                        )
                    }
                }
            }
        ) {
            SectionLabel(stringResource(R.string.entity_editors_project_color))
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it },
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SectionLabel(stringResource(R.string.projects_project_icon))
            com.mj.yata.ui.widgets.IconPicker(
                options = com.mj.yata.ui.widgets.FOLDER_ICON_KEYS,
                selectedIconKey = selectedIcon,
                accentColor = accents.getAccent(selectedColor),
                onIconSelected = { selectedIcon = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if (name.isNotBlank() && !nameTaken) onSave(name, selectedColor, selectedIcon, dueDate, selectedTagIds.toList(), defaultReminder, description.trim().ifBlank { null }, excludeFromToday) },
                enabled = name.isNotBlank() && !nameTaken
            ) {
                Text(buttonText)
            }
        }
    }

    if (showDatePicker) {
        YataDatePickerDialog(
            initialDate = dueDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dueDate = it
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun ProjectEditorSection(
    title: String,
    summary: String,
    leadingIcon: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    preview: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            ListItem(
                leadingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp).size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                leadingIcon()
                            }
                        }
                    }
                },
                headlineContent = { Text(title) },
                supportingContent = {
                    Text(
                        text = summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        preview?.invoke(this)
                        if (onToggle != null) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun CompactTagPreview(
    tagNames: List<String>,
    tags: List<com.mj.yata.domain.model.Tag>,
    selectedTagIds: List<String>
) {
    if (selectedTagIds.isEmpty()) return
    val accents = com.mj.yata.ui.theme.LocalYataAccents.current
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        tags.filter { it.id in selectedTagIds }.take(3).forEach { tag ->
            val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        if (tagNames.size > 3) {
            Text(
                text = "+${tagNames.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

/** A single-select row of group chips + an inline "+ New group" creator. Shared by tag/person editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <G> GroupPickerSection(
    label: String,
    groups: List<G>,
    groupId: (G) -> String,
    groupName: (G) -> String,
    groupColorKey: (G) -> String,
    selectedGroupId: String?,
    onSelect: (String?) -> Unit,
    onCreateGroup: (name: String) -> Unit,
    onNewGroupDraftChange: (String) -> Unit = {},
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accents = com.mj.yata.ui.theme.LocalYataAccents.current
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { group ->
                val id = groupId(group)
                val selected = selectedGroupId == id
                val color = accents.getAccent(groupColorKey(group))
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = groupName(group),
                    selected = selected,
                    onClick = { onSelect(if (selected) null else id) },
                    tint = color,
                    dotColor = color
                )
            }
            if (showNewGroupField) {
                TextField(
                    value = newGroupName,
                    onValueChange = {
                        newGroupName = it
                        onNewGroupDraftChange(it)
                    },
                    placeholder = { Text(stringResource(R.string.entity_editors_group_name)) },
                    singleLine = true,
                    shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                    colors = com.mj.yata.ui.widgets.yataFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newGroupName.isNotBlank()) {
                                onCreateGroup(newGroupName.trim())
                                newGroupName = ""
                                onNewGroupDraftChange("")
                                showNewGroupField = false
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.entity_editors_create_group))
                        }
                    },
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            } else {
                com.mj.yata.ui.widgets.YataDashedAddChip(
                    label = stringResource(R.string.entity_editors_new_group),
                    onClick = { showNewGroupField = true }
                )
            }
        }
    }
}

@Composable
fun PersonEditorSheet(
    initialName: String = "",
    initialColor: String = "accentA",
    initialGroupId: String? = null,
    initialPhotoUri: String? = null,
    groups: List<com.mj.yata.domain.model.PersonGroup> = emptyList(),
    existingNames: List<String> = emptyList(),
    onSave: (String, String, String?, String?) -> Unit,
    onCreateGroup: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    var photoUri by remember { mutableStateOf(initialPhotoUri) }
    var photoExpanded by remember { mutableStateOf(initialPhotoUri != null) }
    var colorExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(initialGroupId != null) }
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode, existingNames) {
        if (isCreateMode) parseBulkNames(name).excludingExisting(existingNames) else emptyList()
    }
    // Every typed name already exists (case-insensitively), so excludingExisting emptied the list
    // and the Create button below is disabled. Without saying so it just greys out for no visible
    // reason — the user typed a perfectly good name, it merely differs in case from one already
    // there, which is exactly the case they cannot see.
    val allNamesTaken = remember(name, isCreateMode, bulkNames, existingNames, initialName) {
        if (isCreateMode) name.isNotBlank() && bulkNames.isEmpty()
        // Edit mode has no bulk list to empty — renaming onto another entity's name collides just
        // as badly, and was previously allowed outright.
        else nameCollides(name, existingNames, initialName)
    }
    val isBulk = bulkNames.size > 1

    val context = androidx.compose.ui.platform.LocalContext.current
    val photoScope = rememberCoroutineScope()
    // Holds the decoded picked image while the crop dialog is open; the cropper's confirmed
    // output is masked and copied into internal storage (never the picker's ephemeral content://
    // grant, which is lost on relaunch and reverts the avatar to initials).
    var pickedPhotoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoScope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.mj.yata.util.ProfilePhotoUtils.decodeSampledBitmap(context, uri)
                    } catch (e: Exception) {
                        null
                    }
                }
                pickedPhotoBitmap = bitmap
            }
        }
    }

    // Recognition happens once at import; the transparent source itself stays unchanged so the
    // avatar renderer can apply whatever Material palette is current later.
    pickedPhotoBitmap?.let { bitmap ->
        com.mj.yata.ui.widgets.CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                photoScope.launch {
                    val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // Mark white-on-transparent artwork for live Material tinting at render.
                        val isMaterialGlyph = com.mj.yata.util.ProfilePhotoUtils.looksLikeTransparentGlyph(cropped)
                        com.mj.yata.util.ProfilePhotoUtils.saveCircularAvatar(
                            context,
                            cropped,
                            isMaterialGlyph = isMaterialGlyph
                        )
                    }
                    photoUri = saved.toString()
                    pickedPhotoBitmap = null
                }
            },
            onCancel = { pickedPhotoBitmap = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (isCreateMode) "Add person" else "Edit person"
        val buttonText = if (!isCreateMode) "Save" else if (isBulk) "Add ${bulkNames.size} people" else "Add"
        val selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name
        val accents = com.mj.yata.ui.theme.LocalYataAccents.current

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        TextField(
            value = name,
            onValueChange = { if (it.length <= BULK_NAME_FIELD_LIMIT) name = it },
            label = { Text(stringResource(R.string.entity_editors_person_s_name)) },
            placeholder = { Text(if (isCreateMode) "e.g. Clara, Alex, Sam" else "e.g. Clara") },
            isError = allNamesTaken,
            supportingText = when {
                allNamesTaken -> { { Text(stringResource(R.string.entity_name_taken)) } }
                isCreateMode -> { { Text(stringResource(R.string.entity_editors_separate_multiple_names_with_commas_to_add)) } }
                else -> null
            },
            singleLine = true,
            shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (!isBulk) {
            ProjectEditorSection(
                title = "Photo",
                summary = if (photoUri == null) "Initials only" else "Custom photo",
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                expanded = photoExpanded,
                onToggle = { photoExpanded = !photoExpanded },
                preview = {
                    com.mj.yata.ui.widgets.PersonAvatar(
                        initials = initialsFor(name.ifBlank { "?" }),
                        accentKey = selectedColor,
                        photoUri = photoUri,
                        size = 36.dp
                    )
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }) {
                        Text(if (photoUri == null) "Add photo" else "Change photo")
                    }
                    if (photoUri != null) {
                        TextButton(onClick = { photoUri = null }) {
                            Text(stringResource(R.string.entity_editors_remove_photo), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        ProjectEditorSection(
            title = if (isBulk) "Theme color" else "Initials theme color",
            summary = if (isBulk) "Applied to all" else "Used behind initials",
            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
            expanded = colorExpanded,
            onToggle = { colorExpanded = !colorExpanded },
            preview = { ColorDot(accents.getAccent(selectedColor)) }
        ) {
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        ProjectEditorSection(
            title = stringResource(R.string.entity_editors_group_label),
            summary = selectedGroupName ?: stringResource(R.string.settings_none),
            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
            expanded = groupExpanded,
            onToggle = { groupExpanded = !groupExpanded }
        ) {
            GroupPickerSection(
                label = stringResource(R.string.entity_editors_group_label),
                groups = groups,
                groupId = { it.id },
                groupName = { it.name },
                groupColorKey = { it.color },
                selectedGroupId = selectedGroupId,
                onSelect = { selectedGroupId = it },
                onCreateGroup = { groupName ->
                    val id = "pg_" + java.util.UUID.randomUUID().toString()
                    onCreateGroup(id, groupName, selectedColor)
                    selectedGroupId = id
                },
                showLabel = false
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (isCreateMode) {
                        bulkNames.forEach { onSave(it, selectedColor, selectedGroupId, if (isBulk) null else photoUri) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, selectedGroupId, photoUri)
                    }
                },
                enabled = if (isCreateMode) bulkNames.isNotEmpty() else (name.isNotBlank() && !allNamesTaken)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun TagEditorSheet(
    initialName: String = "",
    initialColor: String = "accentA",
    initialGroupId: String? = null,
    initialHideCompletedByDefault: Boolean = false,
    initialDescription: String? = null,
    groups: List<com.mj.yata.domain.model.TagGroup> = emptyList(),
    existingNames: List<String> = emptyList(),
    onSave: (String, String, String?, Boolean, String?, com.mj.yata.domain.model.TagGroup?) -> Unit,
    onCreateGroup: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    var hideCompletedByDefault by remember { mutableStateOf(initialHideCompletedByDefault) }
    var description by remember { mutableStateOf(initialDescription.orEmpty()) }
    var pendingCreatedGroup by remember { mutableStateOf<com.mj.yata.domain.model.TagGroup?>(null) }
    var newGroupDraftName by remember { mutableStateOf("") }
    var colorExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(initialGroupId != null) }
    val visibleGroups = remember(groups, pendingCreatedGroup) {
        (groups + listOfNotNull(pendingCreatedGroup))
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode, existingNames) {
        if (isCreateMode) parseBulkNames(name).excludingExisting(existingNames) else emptyList()
    }
    // Every typed name already exists (case-insensitively), so excludingExisting emptied the list
    // and the Create button below is disabled. Without saying so it just greys out for no visible
    // reason — the user typed a perfectly good name, it merely differs in case from one already
    // there, which is exactly the case they cannot see.
    val allNamesTaken = remember(name, isCreateMode, bulkNames, existingNames, initialName) {
        if (isCreateMode) name.isNotBlank() && bulkNames.isEmpty()
        // Edit mode has no bulk list to empty — renaming onto another entity's name collides just
        // as badly, and was previously allowed outright.
        else nameCollides(name, existingNames, initialName)
    }
    val isBulk = bulkNames.size > 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (isCreateMode) "New tag" else "Edit tag"
        val buttonText = if (!isCreateMode) "Save" else if (isBulk) "Create ${bulkNames.size} tags" else "Create"
        val selectedGroupName = visibleGroups.firstOrNull { it.id == selectedGroupId }?.name
        val accents = com.mj.yata.ui.theme.LocalYataAccents.current

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        TextField(
            value = name,
            onValueChange = { if (it.length <= BULK_NAME_FIELD_LIMIT) name = it },
            label = { Text(stringResource(R.string.entity_editors_tag_label)) },
            placeholder = { Text(if (isCreateMode) "e.g. urgent, work, personal" else "e.g. urgent") },
            isError = allNamesTaken,
            supportingText = when {
                allNamesTaken -> { { Text(stringResource(R.string.entity_name_taken)) } }
                isCreateMode -> { { Text(stringResource(R.string.entity_editors_separate_multiple_tags_with_commas_to_crea)) } }
                else -> null
            },
            singleLine = true,
            shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = description,
            onValueChange = { if (it.length <= TAG_DESCRIPTION_LIMIT) description = it },
            label = { Text(stringResource(R.string.entity_editors_description)) },
            supportingText = { Text(stringResource(R.string.editor_char_counter, description.length, TAG_DESCRIPTION_LIMIT)) },
            minLines = 2,
            maxLines = 5,
            shape = com.mj.yata.ui.widgets.YataFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        ProjectEditorSection(
            title = if (isBulk) "Color" else "Tag color",
            summary = if (isBulk) "Applied to all" else name.ifBlank { stringResource(R.string.entity_tag) },
            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
            expanded = colorExpanded,
            onToggle = { colorExpanded = !colorExpanded },
            preview = { ColorDot(accents.getAccent(selectedColor)) }
        ) {
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        ProjectEditorSection(
            title = stringResource(R.string.entity_editors_group_label),
            summary = selectedGroupName ?: stringResource(R.string.settings_none),
            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
            expanded = groupExpanded,
            onToggle = { groupExpanded = !groupExpanded }
        ) {
            GroupPickerSection(
                label = stringResource(R.string.entity_editors_group_label),
                groups = visibleGroups,
                groupId = { it.id },
                groupName = { it.name },
                groupColorKey = { it.color },
                selectedGroupId = selectedGroupId,
                onSelect = { selectedGroupId = it },
                onCreateGroup = { groupName ->
                    val id = "tg_" + java.util.UUID.randomUUID().toString()
                    pendingCreatedGroup = com.mj.yata.domain.model.TagGroup(id = id, name = groupName, color = selectedColor)
                    onCreateGroup(id, groupName, selectedColor)
                    selectedGroupId = id
                },
                onNewGroupDraftChange = { newGroupDraftName = it },
                showLabel = false
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        ) {
            ListItem(
                leadingContent = {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                },
                headlineContent = { Text(stringResource(R.string.entity_editors_hide_completed_by_default)) },
                supportingContent = { Text(stringResource(R.string.entity_editors_hide_completed_by_default_summary)) },
                trailingContent = {
                    Switch(checked = hideCompletedByDefault, onCheckedChange = { hideCompletedByDefault = it })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { hideCompletedByDefault = !hideCompletedByDefault }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val draftedName = newGroupDraftName.trim()
                    val existingDraftGroup = visibleGroups.firstOrNull { it.name.equals(draftedName, ignoreCase = true) }
                    val draftedGroup = if (draftedName.isNotEmpty() && existingDraftGroup == null) {
                        com.mj.yata.domain.model.TagGroup(
                            id = "tg_" + java.util.UUID.randomUUID().toString(),
                            name = draftedName,
                            color = selectedColor
                        )
                    } else {
                        null
                    }
                    val groupIdToSave = existingDraftGroup?.id ?: draftedGroup?.id ?: selectedGroupId
                    val selectedPendingGroup = draftedGroup ?: pendingCreatedGroup?.takeIf { it.id == groupIdToSave }
                    val descriptionToSave = description.trim().ifBlank { null }
                    if (isCreateMode) {
                        bulkNames.forEach { onSave(it, selectedColor, groupIdToSave, hideCompletedByDefault, descriptionToSave, selectedPendingGroup) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, groupIdToSave, hideCompletedByDefault, descriptionToSave, selectedPendingGroup)
                    }
                },
                enabled = if (isCreateMode) bulkNames.isNotEmpty() else (name.isNotBlank() && !allNamesTaken)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun ListEditorSheet(
    initialName: String = "",
    initialColor: String = "accentA",
    initialIcon: String = "folder",
    initialExcludeFromToday: Boolean = false,
    onSave: (String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Names of the other lists, for the case-insensitive collision check — see [nameCollides]. */
    existingNames: List<String> = emptyList()
) {
    var name by remember { mutableStateOf(initialName) }
    val nameTaken = remember(name, existingNames, initialName) {
        nameCollides(name, existingNames, initialName)
    }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var excludeFromToday by remember { mutableStateOf(initialExcludeFromToday) }
    var appearanceExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New list" else "Edit list"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"
        val accents = com.mj.yata.ui.theme.LocalYataAccents.current

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        TextField(
            value = name,
            onValueChange = { if (it.length <= NAME_LIMIT) name = it },
            label = { Text(stringResource(R.string.entity_editors_list_name)) },
            placeholder = { Text(stringResource(R.string.entity_editors_e_g_personal)) },
            singleLine = true,
            isError = nameTaken,
            supportingText = if (nameTaken) {
                { Text(stringResource(R.string.entity_name_taken)) }
            } else null,
            shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
            colors = com.mj.yata.ui.widgets.yataFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        ProjectEditorSection(
            title = stringResource(R.string.settings_section_appearance),
            summary = stringResource(R.string.entity_editors_list_icon),
            leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
            preview = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(accents.getAccent(selectedColor))
                    Surface(
                        shape = CircleShape,
                        color = accents.getAccent(selectedColor).copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = com.mj.yata.ui.widgets.iconVectorFor(selectedIcon),
                            contentDescription = null,
                            tint = accents.getAccent(selectedColor),
                            modifier = Modifier.padding(5.dp).size(16.dp)
                        )
                    }
                }
            }
        ) {
            SectionLabel(stringResource(R.string.entity_editors_list_color))
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it },
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SectionLabel(stringResource(R.string.entity_editors_list_icon))
            com.mj.yata.ui.widgets.IconPicker(
                options = com.mj.yata.ui.widgets.FOLDER_ICON_KEYS,
                selectedIconKey = selectedIcon,
                accentColor = accents.getAccent(selectedColor),
                onIconSelected = { selectedIcon = it }
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.entity_editors_exclude_from_today)) },
                supportingContent = { Text(stringResource(R.string.entity_editors_exclude_from_today_summary)) },
                trailingContent = {
                    Switch(checked = excludeFromToday, onCheckedChange = { excludeFromToday = it })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { excludeFromToday = !excludeFromToday }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if (name.isNotBlank() && !nameTaken) onSave(name, selectedColor, selectedIcon, excludeFromToday) },
                enabled = name.isNotBlank() && !nameTaken
            ) {
                Text(buttonText)
            }
        }
    }
}

/**
 * Add/rename/remove the user-defined section headings a project's tasks can be grouped under
 * (see [com.mj.yata.domain.model.Project.sectionNames]). Order here is display order — new
 * sections are appended, existing ones aren't reorderable from this sheet (reordering would need
 * its own drag surface for what's usually a handful of short-lived headings; renaming/deleting
 * covers the common edit). Deleting a name doesn't touch any task — a task whose `section` no
 * longer matches anything just falls back into the implicit "No section" bucket.
 */
@Composable
fun ManageSectionsSheet(
    initialSections: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sections by remember { mutableStateOf(initialSections) }
    var newSectionName by remember { mutableStateOf("") }

    fun commitAndClose() {
        onSave(sections.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.getDefault()) })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.project_manage_sections),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
        )
        Text(
            text = stringResource(R.string.project_manage_sections_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEachIndexed { index, section ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = section,
                        onValueChange = { updated ->
                            sections = sections.toMutableList().apply { set(index, updated) }
                        },
                        singleLine = true,
                        shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                        colors = com.mj.yata.ui.widgets.yataFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { sections = sections.toMutableList().apply { removeAt(index) } }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_section_remove))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = newSectionName,
                onValueChange = { newSectionName = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.project_section_new)) },
                shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                colors = com.mj.yata.ui.widgets.yataFieldColors(),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (newSectionName.isNotBlank()) {
                        sections = sections + newSectionName.trim()
                        newSectionName = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_section_add))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { commitAndClose() }) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
