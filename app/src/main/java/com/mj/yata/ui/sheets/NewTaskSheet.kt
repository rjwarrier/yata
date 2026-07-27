package com.mj.yata.ui.sheets

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Mic
import com.mj.yata.R
import com.mj.yata.ui.widgets.PressableScaleBox
import com.mj.yata.util.findBestEntityMatch
import com.mj.yata.util.toProperCase
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.MentionSuggestions
import com.mj.yata.ui.widgets.PriorityBars
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.consumeMentionToken
import com.mj.yata.ui.widgets.detectMentionToken
import com.mj.yata.ui.widgets.TagChip
import com.mj.yata.ui.widgets.YataDashedAddChip
import com.mj.yata.ui.widgets.YataDatePickerDialog
import com.mj.yata.ui.widgets.YataSelectChip
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.findSimilarTask
import java.time.LocalDate

internal fun pickAccentFor(name: String): String =
    com.mj.yata.ui.theme.ALL_ACCENT_KEYS[Math.floorMod(name.hashCode(), com.mj.yata.ui.theme.ALL_ACCENT_KEYS.size)]

/** Forced on every chip in the Assigned-to/Tags rows so mixed content (avatars, dots, dashed
 * "add" pills) never drifts out of alignment — matches the attribute-chip row's YataSelectChip height. */
private val CHIP_ROW_HEIGHT = 34.dp

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
    tasks: List<Task> = emptyList(),
    onGoToExistingTask: (String) -> Unit = {},
    onAddTask: (
        title: String,
        listId: String?,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        due: String?,
        time: String?,
        reminder: String?,
        section: String,
        projectId: String?,
        notes: String?,
        subtasks: List<Subtask>,
        flag: Boolean
    ) -> Unit,
    onAddTaskAndContinue: ((
        title: String,
        listId: String?,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        due: String?,
        time: String?,
        reminder: String?,
        section: String,
        projectId: String?,
        notes: String?,
        subtasks: List<Subtask>,
        flag: Boolean
    ) -> Unit)? = null,
    onCreateTag: (id: String, name: String, color: String) -> Unit,
    onCreatePerson: (id: String, name: String, color: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialAssigneeId: String? = null,
    initialProjectId: String? = null,
    initialListId: String? = null,
    initialTagId: String? = null,
    initialDueDateOverride: String? = null,
    projectsEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    peopleEnabled: Boolean = true,
    voiceLanguage: String = "default",
    defaultDueDate: com.mj.yata.domain.model.DefaultDueDate = com.mj.yata.domain.model.DefaultDueDate.TODAY,
    defaultPriority: String = "none"
) {
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var selectedListId by remember { mutableStateOf(initialListId) }
    var selectedProjectId by remember { mutableStateOf(initialProjectId) }
    var selectedPriority by remember { mutableStateOf(defaultPriority) }
    // No manual toggle exists for this yet (unlike due/time/priority below) — quick-add is
    // currently the only way to flag a task before it's created, so there's no "manually set"
    // state to protect it from being overwritten.
    var selectedFlag by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf("Afternoon") }

    // Initial due date: an explicit override (e.g. the day tapped on the calendar) wins,
    // otherwise the pre-selected project's due date, otherwise the user's configured default
    // (which is TODAY unless changed, preserving the previous hardcoded behavior).
    val initialDueDate = remember(projects, initialProjectId, initialDueDateOverride, defaultDueDate) {
        if (initialDueDateOverride != null) {
            initialDueDateOverride
        } else if (initialProjectId != null) {
            val projectObj = projects.find { it.id == initialProjectId }
            projectObj?.due
        } else {
            defaultDueDate.resolve()
        }
    }
    var selectedDueDate by remember { mutableStateOf<String?>(initialDueDate) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedReminder by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedRecurrence by remember { mutableStateOf<Recurrence?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }

    // Quick-add: typing a date/time phrase in the title (e.g. "tomorrow 3pm") prefills these
    // chips. Once the user picks a due date/time manually, their choice always wins over
    // further parsing — see setDueDate/setTime below.
    var dueManuallySet by remember { mutableStateOf(initialDueDateOverride != null) }
    var timeManuallySet by remember { mutableStateOf(false) }
    var recurrenceManuallySet by remember { mutableStateOf(false) }
    var reminderManuallySet by remember { mutableStateOf(false) }
    var priorityManuallySet by remember { mutableStateOf(false) }
    var quickAddDismissed by remember { mutableStateOf(false) }
    var ignoredQuickAddFields by remember { mutableStateOf(setOf<String>()) }
    var keepAdding by remember { mutableStateOf(false) }
    val setDueDate: (String?) -> Unit = { selectedDueDate = it; dueManuallySet = true }
    val setTime: (String?) -> Unit = { selectedTime = it; timeManuallySet = true }
    val setRecurrence: (Recurrence?) -> Unit = { selectedRecurrence = it; recurrenceManuallySet = true }
    val setReminder: (String?) -> Unit = { selectedReminder = it; reminderManuallySet = true }
    val setPriority: (String) -> Unit = { selectedPriority = it; priorityManuallySet = true }

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }
    val selectedAssigneeIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(initialAssigneeId, myId, peopleEnabled) {
        if (peopleEnabled && selectedAssigneeIds.isEmpty()) {
            selectedAssigneeIds.add(initialAssigneeId ?: myId)
        }
    }

    val selectedTagIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(initialTagId, tagsEnabled) {
        if (tagsEnabled && initialTagId != null && !selectedTagIds.contains(initialTagId)) {
            selectedTagIds.add(initialTagId)
        }
    }
    var activePanel by remember { mutableStateOf<String?>(null) }

    var notes by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf<Subtask>() }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val accents = LocalYataAccents.current
    val list = lists.find { it.id == selectedListId }
    val listName = list?.name ?: "List"
    val listColor = list?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val project = projects.find { it.id == selectedProjectId }
    val projectColor = project?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val activeProjects = remember(projects, selectedProjectId) {
        projects.activeProjects(includeId = selectedProjectId)
    }
    val activePeople = remember(people, selectedAssigneeIds.toList()) {
        people.activePeople(includeIds = selectedAssigneeIds.toSet())
    }
    val canCreateTask = title.text.isNotBlank()

    // Pasting/typing several newline-separated lines offers to create one task per line
    // instead of a single task with a garbled multi-line title — each gets its own
    // NaturalLanguageParser pass (see bulkTaskLines below), same engine as single-task mode.
    var bulkModeDismissed by remember { mutableStateOf(false) }
    val bulkTaskLines = remember(title.text) {
        title.text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }
    val isBulkTasks = !bulkModeDismissed && bulkTaskLines.size > 1

    val mention = remember(title, tagsEnabled, peopleEnabled) {
        detectMentionToken(title.text, title.selection.end)
            ?.takeIf { (it.trigger == '#' && tagsEnabled) || (it.trigger == '@' && peopleEnabled) }
    }

    // Skipped entirely in bulk mode — parsing the whole multi-line blob as one task would
    // pick up stray date/priority words from any line and misapply them sheet-wide (e.g. the
    // due-date chip) even though bulk creation uses each line's own parse instead.
    val quickAdd = remember(title.text) { if (isBulkTasks) ParsedQuickAdd("", null, null, null, highlightRanges = emptyList()) else NaturalLanguageParser.parse(title.text) }
    val quickAddMatched = !isBulkTasks && !quickAddDismissed && quickAdd.title != title.text.trim()
    val finalTitlePreview = remember(title.text, quickAddMatched, quickAdd.title) {
        if (quickAddMatched) quickAdd.title else title.text.trim()
    }
    val conflictHints = remember(tasks, finalTitlePreview, selectedDueDate, selectedTime, isBulkTasks) {
        if (finalTitlePreview.isBlank() || isBulkTasks) {
            emptyList()
        } else {
            buildList {
                val duplicate = findSimilarTask(finalTitlePreview, tasks)
                if (duplicate != null) add("Similar open task: ${duplicate.title}")
                if (selectedDueDate != null && selectedTime != null) {
                    val slotCount = tasks.count { !it.done && it.due == selectedDueDate && it.time == selectedTime }
                    if (slotCount > 0) add("$slotCount open task(s) already at $selectedTime")
                }
            }.take(2)
        }
    }
    LaunchedEffect(quickAdd, quickAddDismissed, isBulkTasks, ignoredQuickAddFields) {
        if (!quickAddDismissed && !isBulkTasks) {
            if ("due" !in ignoredQuickAddFields && !dueManuallySet && quickAdd.due != null) selectedDueDate = quickAdd.due
            if ("time" !in ignoredQuickAddFields && !timeManuallySet && quickAdd.time != null) selectedTime = quickAdd.time
            if ("recurrence" !in ignoredQuickAddFields && !recurrenceManuallySet && quickAdd.recurrence != null) selectedRecurrence = quickAdd.recurrence
            if ("reminder" !in ignoredQuickAddFields && !reminderManuallySet && quickAdd.reminder != null) selectedReminder = quickAdd.reminder
            if ("priority" !in ignoredQuickAddFields && !priorityManuallySet && quickAdd.priority != null) selectedPriority = quickAdd.priority
            if ("flag" !in ignoredQuickAddFields && quickAdd.flag) selectedFlag = true

            if ("project" !in ignoredQuickAddFields && selectedProjectId == null && quickAdd.projectName != null) {
                findBestEntityMatch(quickAdd.projectName, projects, { it.name })?.let { selectedProjectId = it.id }
            }
            if ("list" !in ignoredQuickAddFields && selectedListId == null && quickAdd.listName != null) {
                findBestEntityMatch(quickAdd.listName, lists, { it.name })?.let { selectedListId = it.id }
            }
            if ("tags" !in ignoredQuickAddFields && quickAdd.tagNames.isNotEmpty()) {
                val matchedTagIds = quickAdd.tagNames.mapNotNull { target ->
                    findBestEntityMatch(target, tags, { it.name })?.id
                }.distinct().filterNot { it in selectedTagIds }
                selectedTagIds.addAll(matchedTagIds)
            }
            if ("people" !in ignoredQuickAddFields && quickAdd.assigneeNames.isNotEmpty()) {
                val matchedAssigneeIds = quickAdd.assigneeNames.mapNotNull { target ->
                    findBestEntityMatch(target, activePeople, { it.name })?.id
                }.distinct().filterNot { it in selectedAssigneeIds }
                selectedAssigneeIds.addAll(matchedAssigneeIds)
            }
        }
    }

    // Automatically sync due date/reminder to the selected project's defaults when it changes
    var lastLoadedProjectId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedProjectId, projects) {
        val projectObj = projects.find { it.id == selectedProjectId }
        if (projectObj != null && projectObj.id != lastLoadedProjectId) {
            lastLoadedProjectId = projectObj.id
            setDueDate(projectObj.due ?: LocalDate.now().toString())
            if (selectedReminder == null) {
                selectedReminder = projectObj.defaultReminder
            }
        }
    }

    var isVoiceOverlayOpen by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                val parsed = NaturalLanguageParser.parse(spoken)
                val properTitle = parsed.title.toProperCase()
                title = TextFieldValue(properTitle, TextRange(properTitle.length))
                if (parsed.due != null) { selectedDueDate = parsed.due; dueManuallySet = true }
                if (parsed.time != null) { selectedTime = parsed.time; timeManuallySet = true }
                if (parsed.recurrence != null) { selectedRecurrence = parsed.recurrence; recurrenceManuallySet = true }
                if (parsed.reminder != null) { selectedReminder = parsed.reminder; reminderManuallySet = true }
                if (parsed.priority != null) { selectedPriority = parsed.priority; priorityManuallySet = true }
                if (parsed.flag) selectedFlag = true
                if (parsed.projectName != null) {
                    findBestEntityMatch(parsed.projectName, projects, { it.name })?.let { selectedProjectId = it.id }
                }
                if (parsed.listName != null) {
                    findBestEntityMatch(parsed.listName, lists, { it.name })?.let { selectedListId = it.id }
                }
                if (parsed.tagNames.isNotEmpty()) {
                    val matchedTagIds = parsed.tagNames.mapNotNull { target ->
                        findBestEntityMatch(target, tags, { it.name })?.id
                    }.distinct()
                    selectedTagIds.addAll(matchedTagIds)
                }
                if (parsed.assigneeNames.isNotEmpty()) {
                    val matchedAssigneeIds = parsed.assigneeNames.mapNotNull { target ->
                        findBestEntityMatch(target, activePeople, { it.name })?.id
                    }.distinct()
                    selectedAssigneeIds.addAll(matchedAssigneeIds)
                }
                quickAddDismissed = false
            }
        }
    }

    val startVoiceInput = {
        isVoiceOverlayOpen = true
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // FocusRequester not yet attached (composable left composition before this ran) — skip autofocus.
        }
    }

    var pendingDuplicateTask by remember { mutableStateOf<Task?>(null) }

    val resetAfterCreateAnother = {
        title = TextFieldValue("")
        selectedPriority = defaultPriority
        selectedFlag = false
        selectedTime = null
        selectedReminder = null
        selectedRecurrence = null
        notes = ""
        subtasks.clear()
        newSubtaskTitle = ""
        dueManuallySet = initialDueDateOverride != null
        timeManuallySet = false
        recurrenceManuallySet = false
        reminderManuallySet = false
        priorityManuallySet = false
        quickAddDismissed = false
        bulkModeDismissed = false
        activePanel = null
        selectedDueDate = initialDueDate
    }

    val createTask = {
        if (isBulkTasks) {
            // Each line gets its own NaturalLanguageParser pass — independent due/time/
            // priority/recurrence/flag per task — sharing only the sheet-level fields that
            // aren't naturally per-line (project/list/section/assignees/tags/notes/subtasks).
            bulkTaskLines.forEach { line ->
                val parsed = NaturalLanguageParser.parse(line)
                onAddTask(
                    parsed.title,
                    selectedListId,
                    parsed.priority ?: "none",
                    selectedAssigneeIds.toList(),
                    selectedTagIds.toList(),
                    parsed.recurrence,
                    parsed.due ?: initialDueDate,
                    parsed.time,
                    parsed.reminder ?: selectedReminder,
                    selectedSection,
                    selectedProjectId,
                    notes.trim().ifBlank { null },
                    subtasks.toList(),
                    parsed.flag
                )
            }
        } else {
            val finalTitle = (if (quickAddMatched) quickAdd.title else title.text.trim()).toProperCase()
            val add = if (keepAdding && onAddTaskAndContinue != null) onAddTaskAndContinue else onAddTask
            add(
                finalTitle,
                selectedListId,
                selectedPriority,
                selectedAssigneeIds.toList(),
                selectedTagIds.toList(),
                selectedRecurrence,
                selectedDueDate,
                selectedTime,
                selectedReminder,
                selectedSection,
                selectedProjectId,
                notes.trim().ifBlank { null },
                subtasks.toList(),
                selectedFlag
            )
            if (keepAdding && onAddTaskAndContinue != null) {
                resetAfterCreateAnother()
            }
        }
    }

    val submit = {
        if (canCreateTask) {
            if (isBulkTasks) {
                // Trust the paste — checking N lines against the duplicate-similarity heuristic
                // would mean up to N separate prompts, which is worse than just creating them.
                createTask()
            } else {
                val finalTitle = if (quickAddMatched) quickAdd.title else title.text.trim()
                val duplicate = findSimilarTask(finalTitle, tasks)
                if (duplicate != null) {
                    pendingDuplicateTask = duplicate
                } else {
                    createTask()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // safeDrawing covers the IME *and* the system bars, so this replaces the previous
            // imePadding(). The host Dialog opts out of decor fitting, and API 35 enforces
            // edge-to-edge, so without the bars inset the title row sits under the status bar
            // and the create button ends up behind the gesture nav bar.
            .safeDrawingPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "New task",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        val entranceScale = remember { Animatable(0.92f) }
        val entranceAlpha = remember { Animatable(0f) }
        val entranceSlide = remember { Animatable(30f) }

        LaunchedEffect(Unit) {
            launch {
                entranceScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                entranceSlide.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                entranceAlpha.animateTo(1f, tween(durationMillis = 150))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    translationY = entranceSlide.value
                    alpha = entranceAlpha.value
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.size(2.dp))

            // Chip-tints recognized date/time/recurrence phrases in place (e.g. "tomorrow
            // 3pm") without altering the actual text or cursor mapping — they're stripped
            // from the title only once the task is actually created (see `submit` below).
            // A real rounded pill isn't possible inline in an editable BasicTextField, so this
            // approximates one with a tinted background + bold colored text (same accent@16%
            // language TagChip/YataSelectChip use elsewhere) — far more visible than a thin underline.
            val quickAddChipColor = MaterialTheme.colorScheme.primary
            val quickAddVisualTransformation = remember(quickAdd.highlightRanges, quickAddMatched, quickAddChipColor) {
                VisualTransformation { text ->
                    if (!quickAddMatched || quickAdd.highlightRanges.isEmpty()) {
                        TransformedText(text, OffsetMapping.Identity)
                    } else {
                        val annotated = buildAnnotatedString {
                            append(text.text)
                            quickAdd.highlightRanges.forEach { range ->
                                val start = range.first.coerceIn(0, text.text.length)
                                val end = (range.last + 1).coerceIn(0, text.text.length)
                                if (start < end) {
                                    addStyle(
                                        SpanStyle(
                                            color = quickAddChipColor,
                                            fontWeight = FontWeight.Bold,
                                            background = quickAddChipColor.copy(alpha = 0.16f)
                                        ),
                                        start,
                                        end
                                    )
                                }
                            }
                        }
                        TransformedText(annotated, OffsetMapping.Identity)
                    }
                }
            }

            // Big borderless title input with primary underline, plus a mic button that
            // dictates straight into it (same field the NL quick-add parser already reads).
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = title,
                    onValueChange = { newValue ->
                        if (newValue.text != title.text) {
                            quickAddDismissed = false
                            ignoredQuickAddFields = emptySet()
                            bulkModeDismissed = false
                        }
                        title = newValue
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    visualTransformation = quickAddVisualTransformation,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .border(
                            androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)
                        )
                        .padding(vertical = 10.dp)
                        .drawBottomBorder(MaterialTheme.colorScheme.primary)
                        .testTag("new_task_title_input"),
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
                PressableScaleBox(
                    onClick = startVoiceInput,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.cd_add_task_by_voice),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (mention != null) {
                MentionSuggestions(
                    mention = mention,
                    tags = tags,
                    people = activePeople,
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

            if (isBulkTasks) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${bulkTaskLines.size} tasks detected — each line becomes its own task, parsed separately",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.new_task_treat_as_a_single_task_instead),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { bulkModeDismissed = true }
                    )
                }
            } else if (quickAddMatched) {
                val detectedItems = listOfNotNull<Triple<String, () -> Unit, () -> Unit>>(
                    quickAdd.due?.takeIf { "due" !in ignoredQuickAddFields }?.let {
                        Triple("Due ${TaskScheduleUtils.formatDueDate(it)}", { activePanel = "DueDate" }, {
                            setDueDate(null)
                            ignoredQuickAddFields = ignoredQuickAddFields + "due"
                        })
                    },
                    quickAdd.time?.takeIf { "time" !in ignoredQuickAddFields }?.let {
                        Triple("Time $it", { activePanel = "Time" }, {
                            setTime(null)
                            ignoredQuickAddFields = ignoredQuickAddFields + "time"
                        })
                    },
                    quickAdd.recurrence?.takeIf { "recurrence" !in ignoredQuickAddFields }?.let {
                        Triple("Repeat ${com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)}", {
                            activePanel = null
                            showRecurrenceSheet = true
                        }, {
                            setRecurrence(null)
                            ignoredQuickAddFields = ignoredQuickAddFields + "recurrence"
                        })
                    },
                    quickAdd.reminder?.takeIf { "reminder" !in ignoredQuickAddFields }?.let {
                        Triple("Remind $it", { activePanel = "Reminder" }, {
                            setReminder(null)
                            ignoredQuickAddFields = ignoredQuickAddFields + "reminder"
                        })
                    },
                    quickAdd.priority?.takeIf { "priority" !in ignoredQuickAddFields }?.let {
                        Triple("${it.uppercase()} priority", { activePanel = "Priority" }, {
                            setPriority("none")
                            ignoredQuickAddFields = ignoredQuickAddFields + "priority"
                        })
                    },
                    "Flagged".takeIf { quickAdd.flag && "flag" !in ignoredQuickAddFields }?.let {
                        Triple(it, { selectedFlag = !selectedFlag }, {
                            selectedFlag = false
                            ignoredQuickAddFields = ignoredQuickAddFields + "flag"
                        })
                    },
                    quickAdd.projectName?.takeIf { "project" !in ignoredQuickAddFields }?.let {
                        Triple("Project $it", { activePanel = "Project" }, {
                            selectedProjectId = null
                            ignoredQuickAddFields = ignoredQuickAddFields + "project"
                        })
                    },
                    quickAdd.listName?.takeIf { "list" !in ignoredQuickAddFields }?.let {
                        Triple("List $it", { activePanel = "List" }, {
                            selectedListId = null
                            ignoredQuickAddFields = ignoredQuickAddFields + "list"
                        })
                    },
                    quickAdd.tagNames.takeIf { it.isNotEmpty() && "tags" !in ignoredQuickAddFields }?.joinToString(", ") { "#$it" }?.let {
                        Triple("Tags $it", { activePanel = "Tags" }, {
                            val matchedTagIds = quickAdd.tagNames.mapNotNull { target ->
                                findBestEntityMatch(target, tags, { tag -> tag.name })?.id
                            }.toSet()
                            selectedTagIds.removeAll(matchedTagIds)
                            ignoredQuickAddFields = ignoredQuickAddFields + "tags"
                        })
                    },
                    quickAdd.assigneeNames.takeIf { it.isNotEmpty() && "people" !in ignoredQuickAddFields }?.joinToString(", ") { "@$it" }?.let {
                        Triple("People $it", { activePanel = "People" }, {
                            val matchedAssigneeIds = quickAdd.assigneeNames.mapNotNull { target ->
                                findBestEntityMatch(target, activePeople, { person -> person.name })?.id
                            }.toSet()
                            selectedAssigneeIds.removeAll(matchedAssigneeIds)
                            ignoredQuickAddFields = ignoredQuickAddFields + "people"
                        })
                    }
                )
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Smart add understood this task as:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.new_task_ignore_detected_date_time),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { quickAddDismissed = true }
                        )
                    }
                    Text(
                        text = "Title: ${quickAdd.title.toProperCase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        detectedItems.forEach { (item, onItemClick, onDismissItem) ->
                            InputChip(
                                selected = true,
                                onClick = onItemClick,
                                label = { Text(item) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Ignore $item",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDismissItem() }
                                    )
                                }
                            )
                        }
                    }
                }
                }
            }

            if (conflictHints.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Heads up",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    conflictHints.forEach { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
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
                if (projectsEnabled) {
                    YataSelectChip(
                        label = project?.name ?: "Project",
                        selected = project != null,
                        onClick = { activePanel = if (activePanel == "Project") null else "Project" },
                        tint = projectColor,
                        dotColor = if (project != null) projectColor else null,
                        showCheck = false
                    )
                }
                YataSelectChip(
                    label = listName,
                    selected = list != null,
                    onClick = { activePanel = if (activePanel == "List") null else "List" },
                    tint = listColor,
                    dotColor = if (list != null) listColor else null,
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
                // Time/Reminder live in this same row (not a separate one further down) so the
                // reveal panel below always opens right under whichever chip triggered it.
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

            // Reveal panel — right under the attribute chips that open it, so it never appears
            // to open "below" Assigned to/Tags.
            if (activePanel != null) {
                YataRevealPanel {
                    when (activePanel) {
                        "DueDate" -> DueDatePanel(
                            selectedDueDate = selectedDueDate,
                            onPick = { setDueDate(it) },
                            onClear = { setDueDate(null); setTime(null); setReminder(null) },
                            onPickDate = { showDatePicker = true }
                        )
                        "Time" -> TimePanel(
                            hasDueDate = selectedDueDate != null,
                            selectedTime = selectedTime,
                            onPick = { setTime(it) },
                            onCustom = { showTimePicker = true }
                        )
                        "Reminder" -> ReminderPanel(
                            hasDueDate = selectedDueDate != null,
                            selectedReminder = selectedReminder,
                            onPick = { setReminder(it) },
                            onCustom = { showReminderTimePicker = true }
                        )
                        "Project" -> ProjectPanel(
                            projects = activeProjects,
                            selectedProjectId = selectedProjectId,
                            accents = accents,
                            onSelect = { selectedProjectId = it }
                        )
                        "List" -> ListPanel(
                            lists = lists,
                            selectedListId = selectedListId,
                            accents = accents,
                            onSelect = { selectedListId = it }
                        )
                        "Priority" -> Column {
                            SegmentedControl(
                                items = listOf("none", "low", "med", "high"),
                                selectedItem = selectedPriority,
                                onItemSelected = { setPriority(it) },
                                labelProvider = { it.uppercase() }
                            )
                        }
                        "People" -> PeoplePanel(
                            people = activePeople,
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
                            selectedDueDate = selectedDueDate,
                            onSelect = { setRecurrence(it) },
                            onCustom = {
                                activePanel = null
                                showRecurrenceSheet = true
                            }
                        )
                    }
                }
            }

            // Assigned to — always shows real avatar+name chips, not a count
            if (peopleEnabled) {
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
                            onClick = { activePanel = if (activePanel == "People") null else "People" },
                            height = CHIP_ROW_HEIGHT
                        )
                    }
                }
            }

            // Tags — always shows real tag chips, not a count
            if (tagsEnabled) {
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
                                    onRemoveClick = { selectedTagIds.remove(tid) },
                                    modifier = Modifier.height(CHIP_ROW_HEIGHT)
                                )
                            }
                        }
                        YataDashedAddChip(
                            label = if (selectedTagIds.isEmpty()) "Tag" else "Add",
                            onClick = { activePanel = if (activePanel == "Tags") null else "Tags" },
                            height = CHIP_ROW_HEIGHT
                        )
                    }
                }
            }

            // Notes — kept last, after every attribute chip/reveal panel, so opening a panel
            // (List, Priority, Repeat, etc.) never pushes these below the fold or reflows them.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text(stringResource(R.string.new_task_add_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtasks — also last, for the same reason.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Subtasks")
                subtasks.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { subtasks.remove(sub) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.new_task_remove_subtask), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        placeholder = { Text(stringResource(R.string.action_add_a_subtask)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            subtasks.add(
                                Subtask(
                                    id = "sub_" + java.util.UUID.randomUUID().toString(),
                                    title = newSubtaskTitle.trim(),
                                    done = false,
                                    sortOrder = subtasks.size
                                )
                            )
                            newSubtaskTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_subtask))
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
        }

        // Footer submit bar
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        if (onAddTaskAndContinue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { keepAdding = !keepAdding }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = keepAdding,
                    onCheckedChange = { keepAdding = it }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create another",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Keep this sheet open after saving.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatText = selectedRecurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) }
            val contextText = listOfNotNull(project?.name, list?.name).joinToString(" · ").ifEmpty { "No project or list" }
            Text(
                text = contextText + (repeatText?.let { " · $it" } ?: ""),
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
                    contentDescription = if (isBulkTasks) "Create ${bulkTaskLines.size} tasks" else "Create task",
                    tint = if (canCreateTask) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    pendingDuplicateTask?.let { existing ->
        AlertDialog(
            onDismissRequest = { pendingDuplicateTask = null },
            title = { Text(stringResource(R.string.action_similar_task_already_exists)) },
            text = { Text(stringResource(R.string.duplicate_task_match_body, existing.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDuplicateTask = null
                    createTask()
                }) {
                    Text(stringResource(R.string.action_ignore_and_create))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val existingId = existing.id
                    pendingDuplicateTask = null
                    onGoToExistingTask(existingId)
                }) {
                    Text(stringResource(R.string.action_go_to_existing_task))
                }
            }
        )
    }

    if (showDatePicker) {
        YataDatePickerDialog(
            initialDate = selectedDueDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                setDueDate(it)
                showDatePicker = false
            }
        )
    }

    YataTimePickerLauncher(
        show = showTimePicker,
        initialTime = selectedTime,
        onDismiss = { showTimePicker = false },
        onConfirm = {
            setTime(it)
            showTimePicker = false
        }
    )

    YataTimePickerLauncher(
        show = showReminderTimePicker,
        initialTime = selectedReminder,
        onDismiss = { showReminderTimePicker = false },
        onConfirm = {
            when {
                !TaskScheduleUtils.isCustomReminderBeforeDue(it, selectedTime) -> {
                    android.widget.Toast.makeText(context, "Reminder must be before the due time.", android.widget.Toast.LENGTH_SHORT).show()
                }
                !TaskScheduleUtils.isReminderTimeInFuture(selectedDueDate, it) -> {
                    android.widget.Toast.makeText(context, "That reminder time has already passed today.", android.widget.Toast.LENGTH_SHORT).show()
                }
                else -> {
                    setReminder(it)
                }
            }
            showReminderTimePicker = false
        }
    )

    if (showRecurrenceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecurrenceSheet = false }
        ) {
            RecurrenceSheet(
                initialRecurrence = selectedRecurrence,
                onSave = {
                    setRecurrence(it)
                    showRecurrenceSheet = false
                },
                onDismiss = { showRecurrenceSheet = false },
                referenceDate = selectedDueDate
            )
        }
    }

    if (isVoiceOverlayOpen) {
        com.mj.yata.ui.widgets.VoiceTaskOverlay(
            isOpen = isVoiceOverlayOpen,
            onDismiss = { isVoiceOverlayOpen = false },
            voiceLanguage = voiceLanguage,
            onTaskRecognized = { parsed ->
                val properTitle = parsed.title.toProperCase()
                title = TextFieldValue(properTitle, TextRange(properTitle.length))
                if (parsed.due != null) { selectedDueDate = parsed.due; dueManuallySet = true }
                if (parsed.time != null) { selectedTime = parsed.time; timeManuallySet = true }
                if (parsed.recurrence != null) { selectedRecurrence = parsed.recurrence; recurrenceManuallySet = true }
                if (parsed.reminder != null) { selectedReminder = parsed.reminder; reminderManuallySet = true }
                if (parsed.priority != null) { selectedPriority = parsed.priority; priorityManuallySet = true }
                if (parsed.flag) selectedFlag = true
                if (parsed.projectName != null) {
                    findBestEntityMatch(parsed.projectName, projects, { it.name })?.let { selectedProjectId = it.id }
                }
                if (parsed.listName != null) {
                    findBestEntityMatch(parsed.listName, lists, { it.name })?.let { selectedListId = it.id }
                }
                if (parsed.tagNames.isNotEmpty()) {
                    val matchedTagIds = parsed.tagNames.mapNotNull { target ->
                        findBestEntityMatch(target, tags, { it.name })?.id
                    }.distinct()
                    selectedTagIds.addAll(matchedTagIds)
                }
                if (parsed.assigneeNames.isNotEmpty()) {
                    val matchedAssigneeIds = parsed.assigneeNames.mapNotNull { target ->
                        findBestEntityMatch(target, activePeople, { it.name })?.id
                    }.distinct()
                    selectedAssigneeIds.addAll(matchedAssigneeIds)
                }
                quickAddDismissed = false
            }
        )
    }
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
            .height(CHIP_ROW_HEIGHT)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 4.dp, end = 8.dp),
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.cd_remove_person, person.name),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .clickable { onRemove() }
        )
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
    onPick: (String?) -> Unit,
    onCustom: () -> Unit
) {
    if (!hasDueDate) {
        PanelHint("Pick a due date before setting a reminder.")
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            YataSelectChip("None", selectedReminder == null, { onPick(null) })
            TaskScheduleUtils.reminderOptions.forEach { option ->
                YataSelectChip(option, selectedReminder == option, { onPick(option) })
            }
            val customReminderSelected = selectedReminder != null && TaskScheduleUtils.parseTime(selectedReminder) != null
            YataSelectChip("Custom time", customReminderSelected, { onCustom() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListPanel(
    lists: List<YataList>,
    selectedListId: String?,
    accents: com.mj.yata.ui.theme.YataAccents,
    onSelect: (String?) -> Unit
) {
    if (lists.isEmpty()) {
        PanelHint("No lists yet — create one from the drawer.")
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YataSelectChip(
                label = "None",
                selected = selectedListId == null,
                onClick = { onSelect(null) }
            )
            lists.forEach { l ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectPanel(
    projects: List<Project>,
    selectedProjectId: String?,
    accents: com.mj.yata.ui.theme.YataAccents,
    onSelect: (String?) -> Unit
) {
    if (projects.isEmpty()) {
        PanelHint("No projects yet — create one from the Projects tab.")
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YataSelectChip(
                label = "None",
                selected = selectedProjectId == null,
                onClick = { onSelect(null) }
            )
            projects.forEach { pr ->
                val color = accents.getAccent(pr.color)
                YataSelectChip(
                    label = pr.name,
                    selected = pr.id == selectedProjectId,
                    onClick = { onSelect(pr.id) },
                    tint = color,
                    dotColor = color
                )
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
        people.sortedBy { it.name.lowercase() }.forEach { person ->
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
        tags.sortedBy { it.name.lowercase() }.forEach { tag ->
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
    selectedDueDate: String?,
    onSelect: (Recurrence?) -> Unit,
    onCustom: () -> Unit
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val baseDate = selectedDueDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()
            val weeklyDay = when (baseDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "MO"
                java.time.DayOfWeek.TUESDAY -> "TU"
                java.time.DayOfWeek.WEDNESDAY -> "WE"
                java.time.DayOfWeek.THURSDAY -> "TH"
                java.time.DayOfWeek.FRIDAY -> "FR"
                java.time.DayOfWeek.SATURDAY -> "SA"
                java.time.DayOfWeek.SUNDAY -> "SU"
            }
            val presets = listOf<Pair<String, Recurrence?>>(
                "None" to null,
                "Daily" to Recurrence("daily", 1, null, null, RecurrenceEnds.Never),
                "Weekdays" to Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never),
                "Weekly" to Recurrence("weekly", 1, listOf(weeklyDay), null, RecurrenceEnds.Never),
                "Monthly" to Recurrence("monthly", 1, null, baseDate.dayOfMonth, RecurrenceEnds.Never),
                "Yearly" to Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
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
            YataDashedAddChip(
                label = "Custom…",
                onClick = onCustom
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Custom rules support intervals, weekdays, monthly dates, yearly repeats, and end dates.",
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
