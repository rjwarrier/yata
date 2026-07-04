package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.widgets.ColorPicker
import com.mj.yata.ui.widgets.YataDatePickerDialog
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate

/** Splits a comma-separated input into trimmed, de-duplicated, non-blank names. */
private fun parseBulkNames(input: String): List<String> =
    input.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()

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
    modifier: Modifier = Modifier
) {
    val accents = com.mj.yata.ui.theme.LocalYataAccents.current
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
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
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (newGroupName.isNotBlank()) {
                        val id = "grp_" + java.util.UUID.randomUUID().toString()
                        onCreateGroup(id, newGroupName.trim())
                        newGroupName = ""
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Create group")
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
                Text("New group", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorSheet(
    initialName: String = "",
    initialColor: String = "accentA",
    initialDueDate: String? = null,
    initialCommonTagIds: List<String> = emptyList(),
    tags: List<com.mj.yata.domain.model.Tag> = emptyList(),
    onSave: (String, String, String?, List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var dueDate by remember { mutableStateOf<String?>(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedTagIds = remember { mutableStateListOf<String>().apply { addAll(initialCommonTagIds) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New project" else "Edit project"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project name") },
            placeholder = { Text("e.g. Work list") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Project color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        // Project Due Date Picker Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = "Due Date",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = TaskScheduleUtils.formatDueDate(dueDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (dueDate != null) {
                IconButton(onClick = { dueDate = null }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear due date"
                    )
                }
            }
        }

        if (tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Common tags",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Every task in this project always carries these tags, live-synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val accents = com.mj.yata.ui.theme.LocalYataAccents.current
                androidx.compose.foundation.layout.FlowRow(
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, selectedColor, dueDate, selectedTagIds.toList()) },
                enabled = name.isNotBlank()
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
    modifier: Modifier = Modifier
) {
    val accents = com.mj.yata.ui.theme.LocalYataAccents.current
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("Group name") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newGroupName.isNotBlank()) {
                                onCreateGroup(newGroupName.trim())
                                newGroupName = ""
                                showNewGroupField = false
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Create group")
                        }
                    },
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            } else {
                com.mj.yata.ui.widgets.YataDashedAddChip(
                    label = "New group",
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
    groups: List<com.mj.yata.domain.model.PersonGroup> = emptyList(),
    onSave: (String, String, String?) -> Unit,
    onCreateGroup: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode) { if (isCreateMode) parseBulkNames(name) else emptyList() }
    val isBulk = bulkNames.size > 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (isCreateMode) "Add person" else "Edit person"
        val buttonText = if (!isCreateMode) "Save" else if (isBulk) "Add ${bulkNames.size} people" else "Add"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Person's name") },
            placeholder = { Text(if (isCreateMode) "e.g. Clara, Alex, Sam" else "e.g. Clara") },
            supportingText = if (isCreateMode) {
                { Text("Separate multiple names with commas to add several at once.") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isBulk) "Theme color (applied to all)" else "Initials theme color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        GroupPickerSection(
            label = "Group",
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
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (isCreateMode) {
                        bulkNames.forEach { onSave(it, selectedColor, selectedGroupId) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, selectedGroupId)
                    }
                },
                enabled = if (isCreateMode) bulkNames.isNotEmpty() else name.isNotBlank()
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
    groups: List<com.mj.yata.domain.model.TagGroup> = emptyList(),
    onSave: (String, String, String?) -> Unit,
    onCreateGroup: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode) { if (isCreateMode) parseBulkNames(name) else emptyList() }
    val isBulk = bulkNames.size > 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (isCreateMode) "New tag" else "Edit tag"
        val buttonText = if (!isCreateMode) "Save" else if (isBulk) "Create ${bulkNames.size} tags" else "Create"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Tag label") },
            placeholder = { Text(if (isCreateMode) "e.g. urgent, work, personal" else "e.g. urgent") },
            supportingText = if (isCreateMode) {
                { Text("Separate multiple tags with commas to create several at once.") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isBulk) "Color (applied to all)" else "Tag color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        GroupPickerSection(
            label = "Group",
            groups = groups,
            groupId = { it.id },
            groupName = { it.name },
            groupColorKey = { it.color },
            selectedGroupId = selectedGroupId,
            onSelect = { selectedGroupId = it },
            onCreateGroup = { groupName ->
                val id = "tg_" + java.util.UUID.randomUUID().toString()
                onCreateGroup(id, groupName, selectedColor)
                selectedGroupId = id
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (isCreateMode) {
                        bulkNames.forEach { onSave(it, selectedColor, selectedGroupId) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, selectedGroupId)
                    }
                },
                enabled = if (isCreateMode) bulkNames.isNotEmpty() else name.isNotBlank()
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
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New folder" else "Edit folder"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Folder name") },
            placeholder = { Text("e.g. Personal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Folder color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text(buttonText)
            }
        }
    }
}
