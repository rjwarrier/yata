package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

/** Cap for a single-entity name field (Project/List) — unbounded before this, unlike the
 * Project description field which already had a limit. */
private const val NAME_LIMIT = 100

/** Cap for Person/Tag name fields, which can hold several comma-separated names at once in
 * create mode — higher than [NAME_LIMIT] so a legitimate multi-name paste isn't truncated. */
private const val BULK_NAME_FIELD_LIMIT = 300

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
    initialIcon: String = "layers",
    initialDueDate: String? = null,
    initialCommonTagIds: List<String> = emptyList(),
    initialDefaultReminder: String? = null,
    initialDescription: String? = null,
    initialExcludeFromToday: Boolean = false,
    tags: List<com.mj.yata.domain.model.Tag> = emptyList(),
    onSave: (String, String, String, String?, List<String>, String?, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var dueDate by remember { mutableStateOf<String?>(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var defaultReminder by remember { mutableStateOf(initialDefaultReminder) }
    var description by remember { mutableStateOf(initialDescription ?: "") }
    var excludeFromToday by remember { mutableStateOf(initialExcludeFromToday) }
    val selectedTagIds = remember { mutableStateListOf<String>().apply { addAll(initialCommonTagIds) } }
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New project" else "Edit project"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= NAME_LIMIT) name = it },
            label = { Text("Project name") },
            placeholder = { Text("e.g. Work list") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= descriptionLimit) description = it },
            label = { Text("Description") },
            placeholder = { Text("What's this project about?") },
            supportingText = { Text("${description.length}/$descriptionLimit") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { excludeFromToday = !excludeFromToday },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exclude from Today",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Tasks here never show on the Today screen, even if overdue — for a backlog you'll schedule later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = excludeFromToday, onCheckedChange = { excludeFromToday = it })
        }

        // Project Due Date Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Project Due Date",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = "No due date",
                    selected = dueDate == null,
                    onClick = { dueDate = null }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = "Today",
                    selected = dueDate == LocalDate.now().toString(),
                    onClick = { dueDate = LocalDate.now().toString() }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = "Tomorrow",
                    selected = dueDate == LocalDate.now().plusDays(1).toString(),
                    onClick = { dueDate = LocalDate.now().plusDays(1).toString() }
                )
                com.mj.yata.ui.widgets.YataSelectChip(
                    label = if (dueDate != null && dueDate != LocalDate.now().toString() && dueDate != LocalDate.now().plusDays(1).toString()) TaskScheduleUtils.formatDueDate(dueDate) else "Pick date...",
                    selected = dueDate != null && dueDate != LocalDate.now().toString() && dueDate != LocalDate.now().plusDays(1).toString(),
                    onClick = { showDatePicker = true }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Default reminder",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Pre-fills the reminder on new tasks created in this project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.mj.yata.ui.widgets.YataSelectChip("None", defaultReminder == null, { defaultReminder = null })
                TaskScheduleUtils.reminderOptions.forEach { option ->
                    com.mj.yata.ui.widgets.YataSelectChip(option, defaultReminder == option, { defaultReminder = option })
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Project icon",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            com.mj.yata.ui.widgets.IconPicker(
                options = com.mj.yata.ui.widgets.FOLDER_ICON_KEYS,
                selectedIconKey = selectedIcon,
                accentColor = com.mj.yata.ui.theme.LocalYataAccents.current.getAccent(selectedColor),
                onIconSelected = { selectedIcon = it }
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
                onClick = { if (name.isNotBlank()) onSave(name, selectedColor, selectedIcon, dueDate, selectedTagIds.toList(), defaultReminder, description.trim().ifBlank { null }, excludeFromToday) },
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
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode, existingNames) {
        if (isCreateMode) parseBulkNames(name).excludingExisting(existingNames) else emptyList()
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

    pickedPhotoBitmap?.let { bitmap ->
        com.mj.yata.ui.widgets.CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                photoScope.launch {
                    val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.mj.yata.util.ProfilePhotoUtils.saveCircularAvatar(context, cropped)
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

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= BULK_NAME_FIELD_LIMIT) name = it },
            label = { Text("Person's name") },
            placeholder = { Text(if (isCreateMode) "e.g. Clara, Alex, Sam" else "e.g. Clara") },
            supportingText = if (isCreateMode) {
                { Text("Separate multiple names with commas to add several at once.") }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (!isBulk) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                com.mj.yata.ui.widgets.PersonAvatar(
                    initials = initialsFor(name.ifBlank { "?" }),
                    accentKey = selectedColor,
                    photoUri = photoUri,
                    size = 56.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            Text("Remove photo", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

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
                        bulkNames.forEach { onSave(it, selectedColor, selectedGroupId, if (isBulk) null else photoUri) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, selectedGroupId, photoUri)
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
    initialHideCompletedByDefault: Boolean = false,
    groups: List<com.mj.yata.domain.model.TagGroup> = emptyList(),
    existingNames: List<String> = emptyList(),
    onSave: (String, String, String?, Boolean) -> Unit,
    onCreateGroup: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    var hideCompletedByDefault by remember { mutableStateOf(initialHideCompletedByDefault) }
    val isCreateMode = initialName.isEmpty()
    val bulkNames = remember(name, isCreateMode, existingNames) {
        if (isCreateMode) parseBulkNames(name).excludingExisting(existingNames) else emptyList()
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

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= BULK_NAME_FIELD_LIMIT) name = it },
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hideCompletedByDefault = !hideCompletedByDefault },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hide completed by default",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "This tag's detail screen opens with completed tasks hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = hideCompletedByDefault, onCheckedChange = { hideCompletedByDefault = it })
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
                onClick = {
                    if (isCreateMode) {
                        bulkNames.forEach { onSave(it, selectedColor, selectedGroupId, hideCompletedByDefault) }
                    } else if (name.isNotBlank()) {
                        onSave(name, selectedColor, selectedGroupId, hideCompletedByDefault)
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
    initialIcon: String = "folder",
    initialExcludeFromToday: Boolean = false,
    onSave: (String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var excludeFromToday by remember { mutableStateOf(initialExcludeFromToday) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val titleText = if (initialName.isEmpty()) "New list" else "Edit list"
        val buttonText = if (initialName.isEmpty()) "Create" else "Save"

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= NAME_LIMIT) name = it },
            label = { Text("List name") },
            placeholder = { Text("e.g. Personal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "List color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPicker(
                selectedColorKey = selectedColor,
                onColorSelected = { selectedColor = it }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "List icon",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            com.mj.yata.ui.widgets.IconPicker(
                options = com.mj.yata.ui.widgets.FOLDER_ICON_KEYS,
                selectedIconKey = selectedIcon,
                accentColor = com.mj.yata.ui.theme.LocalYataAccents.current.getAccent(selectedColor),
                onIconSelected = { selectedIcon = it }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { excludeFromToday = !excludeFromToday },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exclude from Today",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Tasks here never show on the Today screen, even if overdue — for a backlog you'll schedule later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = excludeFromToday, onCheckedChange = { excludeFromToday = it })
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
                onClick = { if (name.isNotBlank()) onSave(name, selectedColor, selectedIcon, excludeFromToday) },
                enabled = name.isNotBlank()
            ) {
                Text(buttonText)
            }
        }
    }
}
