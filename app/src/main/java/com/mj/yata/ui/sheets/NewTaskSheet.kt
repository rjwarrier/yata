package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.PriorityBars
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.TagChip
import com.mj.yata.ui.widgets.YataDashedAddChip
import com.mj.yata.ui.widgets.YataDatePickerDialog
import com.mj.yata.ui.widgets.YataSelectChip
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate

private val MENTION_SWATCHES = listOf("accentA", "accentB", "accentC", "accentD", "accentE", "accentF", "accentG", "accentH")
private fun pickAccentFor(name: String): String =
    MENTION_SWATCHES[Math.floorMod(name.hashCode(), MENTION_SWATCHES.size)]

private data class MentionToken(val trigger: Char, val query: String, val startIndex: Int)

/** Finds an in-progress `#tag` or `@person` token ending at the cursor, if any. */
private fun detectMentionToken(text: String, cursor: Int): MentionToken? {
    if (cursor <= 0 || cursor > text.length) return null
    var i = cursor - 1
    while (i >= 0) {
        val c = text[i]
        if (c == '#' || c == '@') {
            val precededByBoundary = i == 0 || text[i - 1].isWhitespace()
            if (!precededByBoundary) return null
            val query = text.substring(i + 1, cursor)
            if (query.any { it.isWhitespace() }) return null
            return MentionToken(c, query, i)
        }
        if (c.isWhitespace()) return null
        i--
    }
    return null
}

/** Removes the active mention token (trigger char + query) from the field, collapsing the gap. */
private fun consumeMentionToken(value: TextFieldValue, mention: MentionToken): TextFieldValue {
    val before = value.text.substring(0, mention.startIndex)
    val after = value.text.substring(value.selection.end)
    return TextFieldValue(before + after, TextRange(before.length))
}

internal fun initialsFor(name: String): String =
    name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }
        .take(2).joinToString("").uppercase().ifEmpty { "P" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTaskSheet(
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    onAddTask: (
        title: String,
        listId: String,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        due: String?,
        time: String?,
        reminder: String?
    ) -> Unit,
    onCreateTag: (id: String, name: String, color: String) -> Unit,
    onCreatePerson: (id: String, name: String, color: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialAssigneeId: String? = null
) {
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var selectedListId by remember { mutableStateOf(lists.firstOrNull()?.id ?: "") }
    var selectedPriority by remember { mutableStateOf("none") }

    // Initial due date based on selected list's project due date
    val initialDueDate = remember(lists, projects) {
        val initialListId = lists.firstOrNull()?.id ?: ""
        val listObj = lists.find { it.id == initialListId }
        val projectObj = listObj?.let { l -> projects.find { it.id == l.projectId } }
        projectObj?.due ?: LocalDate.now().toString()
    }
    var selectedDueDate by remember { mutableStateOf<String?>(initialDueDate) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedReminder by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }
    val selectedAssigneeIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(initialAssigneeId, myId) {
        if (selectedAssigneeIds.isEmpty()) {
            selectedAssigneeIds.add(initialAssigneeId ?: myId)
        }
    }

    val selectedTagIds = remember { mutableStateListOf<String>() }
    var selectedRecurrence by remember { mutableStateOf<Recurrence?>(null) }
    var activePanel by remember { mutableStateOf<String?>(null) }

    val accents = LocalYataAccents.current
    val list = lists.find { it.id == selectedListId }
    val listName = list?.name ?: "List"
    val listColor = list?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val canCreateTask = title.text.isNotBlank() && selectedListId.isNotBlank()

    val mention = remember(title) { detectMentionToken(title.text, title.selection.end) }

    // Automatically sync due date to project's due date when changing list selection
    var lastLoadedProjectId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedListId, lists, projects) {
        val listObj = lists.find { it.id == selectedListId }
        val projectObj = listObj?.let { l -> projects.find { it.id == l.projectId } }
        if (projectObj != null && projectObj.id != lastLoadedProjectId) {
            lastLoadedProjectId = projectObj.id
            selectedDueDate = projectObj.due ?: LocalDate.now().toString()
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // FocusRequester not yet attached (composable left composition before this ran) — skip autofocus.
        }
    }

    val submit = {
        if (canCreateTask) {
            onAddTask(
                title.text.trim(),
                selectedListId,
                selectedPriority,
                selectedAssigneeIds.toList(),
                selectedTagIds.toList(),
                selectedRecurrence,
                selectedDueDate,
                selectedTime,
                selectedReminder
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "New task",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.size(2.dp))

            // Big borderless title input with primary underline
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .border(
                        androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)
                    )
                    .padding(vertical = 10.dp)
                    .drawBottomBorder(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (title.text.isEmpty()) {
                        Text(
                            text = "What needs doing? Try # for tags, @ for people",
                            style = TextStyle(
                                fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                    inner()
                }
            )

            if (mention != null) {
                MentionSuggestions(
                    mention = mention,
                    tags = tags,
                    people = people,
                    onSelectTag = { tag ->
                        selectedTagIds.add(tag.id)
                        title = consumeMentionToken(title, mention)
                    },
                    onSelectPerson = { person ->
                        selectedAssigneeIds.add(person.id)
                        title = consumeMentionToken(title, mention)
                    },
                    onCreateTag = { name ->
                        val id = "tag_" + java.util.UUID.randomUUID().toString()
                        onCreateTag(id, name, pickAccentFor(name))
                        selectedTagIds.add(id)
                        title = consumeMentionToken(title, mention)
                    },
                    onCreatePerson = { name ->
                        val id = "p_" + java.util.UUID.randomUUID().toString()
                        onCreatePerson(id, name, pickAccentFor(name))
                        selectedAssigneeIds.add(id)
                        title = consumeMentionToken(title, mention)
                    }
                )
            }

            if (lists.isEmpty()) {
                PanelHint("Create a project first. Each project adds a list where new tasks can live.")
            }

            // Attribute chip row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YataSelectChip(
                    label = TaskScheduleUtils.formatDueDate(selectedDueDate),
                    selected = selectedDueDate != null,
                    onClick = { activePanel = if (activePanel == "DueDate") null else "DueDate" },
                    tint = MaterialTheme.colorScheme.primary,
                    leading = { Icon(Icons.Default.Today, contentDescription = null, tint = if (selectedDueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp)) },
                    showCheck = false
                )
                YataSelectChip(
                    label = listName,
                    selected = true,
                    onClick = { activePanel = if (activePanel == "List") null else "List" },
                    tint = listColor,
                    dotColor = listColor,
                    showCheck = false
                )
                YataSelectChip(
                    label = if (selectedPriority == "none") "Priority" else selectedPriority.uppercase(),
                    selected = selectedPriority != "none",
                    onClick = { activePanel = if (activePanel == "Priority") null else "Priority" },
                    tint = priorityChipColor(selectedPriority, accents),
                    leading = {
                        if (selectedPriority == "none") {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = priorityChipColor(selectedPriority, accents), modifier = Modifier.size(14.dp))
                        } else {
                            PriorityBars(priority = selectedPriority)
                        }
                    },
                    showCheck = false
                )
                YataSelectChip(
                    label = selectedRecurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) } ?: "Repeat",
                    selected = selectedRecurrence != null,
                    onClick = { activePanel = if (activePanel == "Repeat") null else "Repeat" },
                    tint = MaterialTheme.colorScheme.tertiary,
                    leading = { Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp)) },
                    showCheck = false
                )
            }

            // Assigned to — always shows real avatar+name chips, not a count
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Assigned to")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedAssigneeIds.forEach { pid ->
                        val person = people.find { it.id == pid }
                        if (person != null) {
                            AssignedPersonChip(
                                person = person,
                                accents = accents,
                                onRemove = { selectedAssigneeIds.remove(pid) }
                            )
                        }
                    }
                    YataDashedAddChip(
                        label = if (selectedAssigneeIds.isEmpty()) "Assign" else "Add",
                        onClick = { activePanel = if (activePanel == "People") null else "People" }
                    )
                }
            }

            // Tags — always shows real tag chips, not a count
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Tags")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTagIds.forEach { tid ->
                        val tag = tags.find { it.id == tid }
                        if (tag != null) {
                            TagChip(
                                name = tag.name,
                                accentKey = tag.color,
                                onRemoveClick = { selectedTagIds.remove(tid) }
                            )
                        }
                    }
                    YataDashedAddChip(
                        label = if (selectedTagIds.isEmpty()) "Tag" else "Add",
                        onClick = { activePanel = if (activePanel == "Tags") null else "Tags" }
                    )
                }
            }

            // Reveal panel
            if (activePanel != null) {
                YataRevealPanel {
                    when (activePanel) {
                        "DueDate" -> DueDatePanel(
                            selectedDueDate = selectedDueDate,
                            onPick = { selectedDueDate = it },
                            onClear = { selectedDueDate = null; selectedTime = null; selectedReminder = null },
                            onPickDate = { showDatePicker = true }
                        )
                        "Time" -> TimePanel(
                            hasDueDate = selectedDueDate != null,
                            selectedTime = selectedTime,
                            onPick = { selectedTime = it },
                            onCustom = { showTimePicker = true }
                        )
                        "Reminder" -> ReminderPanel(
                            hasDueDate = selectedDueDate != null,
                            selectedReminder = selectedReminder,
                            onPick = { selectedReminder = it }
                        )
                        "List" -> ListPanel(
                            projects = projects,
                            lists = lists,
                            selectedListId = selectedListId,
                            accents = accents,
                            onSelect = { selectedListId = it }
                        )
                        "Priority" -> Column {
                            SegmentedControl(
                                items = listOf("none", "low", "med", "high"),
                                selectedItem = selectedPriority,
                                onItemSelected = { selectedPriority = it },
                                labelProvider = { it.uppercase() }
                            )
                        }
                        "People" -> PeoplePanel(
                            people = people,
                            selectedAssigneeIds = selectedAssigneeIds,
                            accents = accents
                        )
                        "Tags" -> TagsPanel(
                            tags = tags,
                            selectedTagIds = selectedTagIds,
                            accents = accents
                        )
                        "Repeat" -> RepeatPanel(
                            selectedRecurrence = selectedRecurrence,
                            onSelect = { selectedRecurrence = it }
                        )
                    }
                }
            }

            // Secondary chip row: Time + Reminder (only meaningful once a due date exists)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YataSelectChip(
                    label = selectedTime ?: "Time",
                    selected = selectedTime != null,
                    onClick = { activePanel = if (activePanel == "Time") null else "Time" },
                    tint = MaterialTheme.colorScheme.tertiary,
                    leading = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = if (selectedTime != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) },
                    showCheck = false
                )
                YataSelectChip(
                    label = TaskScheduleUtils.formatReminder(selectedReminder),
                    selected = selectedReminder != null,
                    onClick = { activePanel = if (activePanel == "Reminder") null else "Reminder" },
                    tint = MaterialTheme.colorScheme.secondary,
                    leading = { Icon(Icons.Default.Notifications, contentDescription = null, tint = if (selectedReminder != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) },
                    showCheck = false
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
        }

        // Footer submit bar
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatText = selectedRecurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) }
            Text(
                text = listName + (repeatText?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canCreateTask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(enabled = canCreateTask) { submit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.Send,
                    contentDescription = "Create task",
                    tint = if (canCreateTask) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        YataDatePickerDialog(
            initialDate = selectedDueDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                selectedDueDate = it
                showDatePicker = false
            }
        )
    }

    YataTimePickerLauncher(
        show = showTimePicker,
        initialTime = selectedTime,
        onDismiss = { showTimePicker = false },
        onConfirm = { selectedTime = it }
    )
}

private fun Modifier.drawBottomBorder(color: Color): Modifier = this.then(
    drawWithCache {
        val strokeWidth = 2.dp.toPx()
        onDrawBehind {
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = strokeWidth
            )
        }
    }
)

@Composable
private fun priorityChipColor(priority: String, accents: com.mj.yata.ui.theme.YataAccents) = when (priority) {
    "high" -> MaterialTheme.colorScheme.error
    "med" -> accents.accentD
    "low" -> accents.accentE
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AssignedPersonChip(
    person: Person,
    accents: com.mj.yata.ui.theme.YataAccents,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp).background(accents.getAccent(person.color), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(person.initials, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            text = if (person.isMe) "You" else person.name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove ${person.name}",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .clickable { onRemove() }
        )
    }
}

@Composable
private fun MentionSuggestions(
    mention: MentionToken,
    tags: List<Tag>,
    people: List<Person>,
    onSelectTag: (Tag) -> Unit,
    onSelectPerson: (Person) -> Unit,
    onCreateTag: (String) -> Unit,
    onCreatePerson: (String) -> Unit
) {
    val accents = LocalYataAccents.current
    val query = mention.query
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (mention.trigger == '#') {
                val matches = tags.filter { it.name.contains(query, ignoreCase = true) }
                if (matches.isEmpty() && query.isBlank()) {
                    PanelHint("Type to search or create a tag")
                }
                matches.take(5).forEach { tag ->
                    val color = accents.getAccent(tag.color)
                    MentionRow(
                        label = tag.name,
                        onClick = { onSelectTag(tag) },
                        leading = { Box(modifier = Modifier.size(8.dp).background(color, CircleShape)) }
                    )
                }
                if (query.isNotBlank() && matches.none { it.name.equals(query, ignoreCase = true) }) {
                    MentionRow(
                        label = "Create tag \"$query\"",
                        onClick = { onCreateTag(query) },
                        leading = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                val matches = people.filter { it.name.contains(query, ignoreCase = true) }
                if (matches.isEmpty() && query.isBlank()) {
                    PanelHint("Type to search or create a person")
                }
                matches.take(5).forEach { person ->
                    MentionRow(
                        label = if (person.isMe) "You" else person.name,
                        onClick = { onSelectPerson(person) },
                        leading = {
                            Box(
                                modifier = Modifier.size(20.dp).background(accents.getAccent(person.color), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(person.initials, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                if (query.isNotBlank() && matches.none { it.name.equals(query, ignoreCase = true) }) {
                    MentionRow(
                        label = "Create person \"$query\"",
                        onClick = { onCreatePerson(query) },
                        leading = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionRow(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading()
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = labelColor)
    }
}

@Composable
private fun YataRevealPanel(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueDatePanel(
    selectedDueDate: String?,
    onPick: (String?) -> Unit,
    onClear: () -> Unit,
    onPickDate: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        YataSelectChip("Today", selectedDueDate == LocalDate.now().toString(), { onPick(LocalDate.now().toString()) })
        YataSelectChip("Tomorrow", selectedDueDate == LocalDate.now().plusDays(1).toString(), { onPick(LocalDate.now().plusDays(1).toString()) })
        YataSelectChip("Next week", selectedDueDate == LocalDate.now().plusWeeks(1).toString(), { onPick(LocalDate.now().plusWeeks(1).toString()) })
        YataSelectChip("No due date", selectedDueDate == null, { onClear() })
        YataSelectChip("Pick date", false, { onPickDate() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimePanel(
    hasDueDate: Boolean,
    selectedTime: String?,
    onPick: (String?) -> Unit,
    onCustom: () -> Unit
) {
    if (!hasDueDate) {
        PanelHint("Add a due date first so the time has something to attach to.")
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YataSelectChip("9:00 AM", selectedTime == "9:00 AM", { onPick("9:00 AM") })
            YataSelectChip("12:00 PM", selectedTime == "12:00 PM", { onPick("12:00 PM") })
            YataSelectChip("6:00 PM", selectedTime == "6:00 PM", { onPick("6:00 PM") })
            YataSelectChip("Custom time", false, { onCustom() })
            YataSelectChip("Clear", selectedTime == null, { onPick(null) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPanel(
    hasDueDate: Boolean,
    selectedReminder: String?,
    onPick: (String?) -> Unit
) {
    if (!hasDueDate) {
        PanelHint("Pick a due date before setting a reminder.")
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YataSelectChip("None", selectedReminder == null, { onPick(null) })
            TaskScheduleUtils.reminderOptions.forEach { option ->
                YataSelectChip(option, selectedReminder == option, { onPick(option) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListPanel(
    projects: List<Project>,
    lists: List<YataList>,
    selectedListId: String,
    accents: com.mj.yata.ui.theme.YataAccents,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        projects.forEach { pr ->
            val prLists = lists.filter { it.projectId == pr.id }
            if (prLists.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = pr.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        prLists.forEach { l ->
                            val color = accents.getAccent(l.color)
                            YataSelectChip(
                                label = l.name,
                                selected = l.id == selectedListId,
                                onClick = { onSelect(l.id) },
                                tint = color,
                                dotColor = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeoplePanel(
    people: List<Person>,
    selectedAssigneeIds: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    accents: com.mj.yata.ui.theme.YataAccents
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        people.forEach { person ->
            val selected = selectedAssigneeIds.contains(person.id)
            YataSelectChip(
                label = if (person.isMe) "You" else person.name.substringBefore(" "),
                selected = selected,
                onClick = {
                    if (selected) selectedAssigneeIds.remove(person.id) else selectedAssigneeIds.add(person.id)
                },
                tint = MaterialTheme.colorScheme.tertiary,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(accents.getAccent(person.color), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(person.initials, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                height = 36.dp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsPanel(
    tags: List<Tag>,
    selectedTagIds: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    accents: com.mj.yata.ui.theme.YataAccents
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            val selected = selectedTagIds.contains(tag.id)
            val color = accents.getAccent(tag.color)
            YataSelectChip(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPanel(
    selectedRecurrence: Recurrence?,
    onSelect: (Recurrence?) -> Unit
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf<Pair<String, Recurrence?>>(
                "None" to null,
                "Daily" to Recurrence("daily", 1, null, null, RecurrenceEnds.Never),
                "Weekdays" to Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never),
                "Weekly" to Recurrence("weekly", 1, listOf("TH"), null, RecurrenceEnds.Never),
                "Monthly" to Recurrence("monthly", 1, null, 16, RecurrenceEnds.Never)
            )
            presets.forEach { (label, rec) ->
                val isSelected = if (rec == null) selectedRecurrence == null
                    else selectedRecurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) } == com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(rec)
                YataSelectChip(
                    label = label,
                    selected = isSelected,
                    onClick = { onSelect(rec) },
                    tint = MaterialTheme.colorScheme.tertiary,
                    showCheck = false
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "For custom rules, open a task → Repeats.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PanelHint(text: String) {
    Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
