package com.mj.yata.widget

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mj.yata.R
import com.mj.yata.MainActivity
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeLists
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.sheets.initialsFor
import com.mj.yata.ui.sheets.pickAccentFor
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.ui.widgets.MentionSuggestions
import com.mj.yata.ui.widgets.TRIGGER_LIST
import com.mj.yata.ui.widgets.TRIGGER_PERSON
import com.mj.yata.ui.widgets.TRIGGER_PROJECT
import com.mj.yata.ui.widgets.TRIGGER_TAG
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.consumeMentionToken
import com.mj.yata.ui.widgets.detectMentionToken
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.findSimilarTask
import com.mj.yata.util.resolveParsedQuickAddEntities
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Lightweight overlay dialog for the Quick Add widget — creating a one-off task shouldn't
 * require opening the whole app. Reads the target list/project this widget instance (or its
 * per-list chip) points at and drops the new task straight into it.
 */
@AndroidEntryPoint
class QuickAddDialogActivity : ComponentActivity() {

    @Inject lateinit var repository: YataRepository
    @Inject lateinit var userPreferences: com.mj.yata.data.local.datastore.UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetType = intent?.getStringExtra("target_type")?.takeIf { it.isNotBlank() }
        val targetId = intent?.getStringExtra("target_id")?.takeIf { it.isNotBlank() }
        val targetName = intent?.getStringExtra("target_name")?.takeIf { it.isNotBlank() }

        // Shared text (share sheet, or a long-pressed text selection) seeds the title — first
        // line goes through the same NaturalLanguageParser used for in-app typing and Tasker's
        // external input (CreateTaskRunner), remaining lines become the task's notes.
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent?.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
        val sharedLines = sharedText?.lines()?.filter { it.isNotBlank() }
        val sharedFirstLine = sharedLines?.firstOrNull()
        val sharedNotes = sharedLines?.drop(1)?.joinToString("\n")?.takeIf { it.isNotBlank() }
        val parsedShared = sharedFirstLine?.let { NaturalLanguageParser.parse(it) }

        setContent {
            YataTheme {
                val lists by repository.getLists().collectAsState(initial = emptyList())
                val projects by repository.getProjects().collectAsState(initial = emptyList())
                val tags by repository.getTags().collectAsState(initial = emptyList())
                val people by repository.getPeople().collectAsState(initial = emptyList())
                val projectsEnabled by userPreferences.projectsFeatureEnabledFlow.collectAsState(initial = false)
                val tagsEnabled by userPreferences.tagsFeatureEnabledFlow.collectAsState(initial = false)
                val peopleEnabled by userPreferences.peopleFeatureEnabledFlow.collectAsState(initial = false)

                var pendingDuplicate by remember { mutableStateOf<Task?>(null) }
                var pendingTitle by remember { mutableStateOf("") }
                var pendingProjectId by remember { mutableStateOf<String?>(null) }
                var pendingListId by remember { mutableStateOf<String?>(null) }
                var pendingTagIds by remember { mutableStateOf<List<String>>(emptyList()) }
                var pendingAssigneeIds by remember { mutableStateOf<List<String>>(emptyList()) }

                // explicitProjectId/explicitListId/explicitTagIds/explicitAssigneeIds come from the
                // #/@/+/= mention picker (see QuickAddDialogContent) — an explicit choice, so it
                // wins over whatever the NL parser would otherwise resolve from the remaining text
                // or the widget's own preset target.
                fun createTask(
                    title: String,
                    explicitProjectId: String?,
                    explicitListId: String?,
                    explicitTagIds: List<String>,
                    explicitAssigneeIds: List<String>
                ) {
                    lifecycleScope.launch {
                        val parsedTyped = NaturalLanguageParser.parse(title)
                        // Belt-and-suspenders re-check: the widget already drops a target once its
                        // list/project is gone, but a target can also be deleted in the gap between
                        // the widget rendering and this dialog's "Add" tap. listId/projectId are
                        // FK-enforced, so inserting against a since-deleted id would otherwise
                        // crash instead of just dropping the stale preset.
                        val presetList = if (targetType == "list" && targetId != null) {
                            repository.getListById(targetId).first()
                        } else null
                        val listStillExists = presetList != null
                        val presetProject = if (targetType == "project" && targetId != null) {
                            repository.getProjectById(targetId).first()
                        } else null
                        val projectStillExists = presetProject != null
                        val projectsEnabled = userPreferences.projectsFeatureEnabledFlow.first()
                        val tagsEnabled = userPreferences.tagsFeatureEnabledFlow.first()
                        val peopleEnabled = userPreferences.peopleFeatureEnabledFlow.first()
                        val allLists = repository.getLists().first()
                        val allProjects = repository.getProjects().first()
                        val allPeople = repository.getPeople().first()
                        val allTags = repository.getTags().first()
                        // Honours the same Auto-assign setting the New Task sheet does — a task
                        // added from the widget shouldn't differ from one added in the app.
                        val assigneeIds = if (userPreferences.autoAssignToMeFlow.first()) {
                            listOfNotNull(allPeople.find { it.isMe }?.id)
                        } else emptyList()
                        val typedResolution = resolveParsedQuickAddEntities(
                            quickAdd = parsedTyped,
                            baseListId = if (listStillExists) targetId else null,
                            baseProjectId = if (projectStillExists) targetId else null,
                            baseTagIds = emptyList(),
                            baseAssigneeIds = assigneeIds,
                            lists = allLists,
                            projects = allProjects,
                            people = allPeople,
                            tags = allTags,
                            projectsEnabled = projectsEnabled,
                            tagsEnabled = tagsEnabled,
                            peopleEnabled = peopleEnabled
                        )
                        val finalResolution = parsedShared?.let {
                            resolveParsedQuickAddEntities(
                                quickAdd = it,
                                baseListId = typedResolution.listId,
                                baseProjectId = typedResolution.projectId,
                                baseTagIds = typedResolution.tagIds,
                                baseAssigneeIds = typedResolution.assigneeIds,
                                lists = allLists,
                                projects = allProjects,
                                people = allPeople,
                                tags = allTags,
                                projectsEnabled = projectsEnabled,
                                tagsEnabled = tagsEnabled,
                                peopleEnabled = peopleEnabled
                            )
                        } ?: typedResolution
                        // An explicit #/@/+/= mention pick overrides whatever the NL parser or
                        // widget preset would otherwise resolve — see createTask's doc comment.
                        val resolvedProjectId = explicitProjectId ?: finalResolution.projectId
                        val resolvedListId = when {
                            explicitProjectId != null -> null
                            explicitListId != null -> explicitListId
                            else -> finalResolution.listId
                        }
                        val resolvedTagIds = (finalResolution.tagIds + explicitTagIds).distinct()
                        val resolvedAssigneeIds = (finalResolution.assigneeIds + explicitAssigneeIds).distinct()
                        val hasDestination = resolvedListId != null ||
                            resolvedProjectId != null ||
                            presetList != null ||
                            presetProject != null
                        val due = parsedTyped.due
                            ?: parsedShared?.due
                            ?: finalResolution.projectDue
                            ?: allProjects.find { it.id == resolvedProjectId }?.due
                            ?: presetProject?.due
                            ?: if (hasDestination) LocalDate.now().toString() else null
                        repository.upsertTask(
                            Task(
                                id = "t_" + UUID.randomUUID().toString(),
                                title = parsedTyped.title.takeIf { it.isNotBlank() } ?: title,
                                listId = resolvedListId,
                                projectId = resolvedProjectId,
                                section = "",
                                // Destination-specific quick add stays Today-oriented. With no
                                // preset or typed destination, this is capture-to-Inbox: no due
                                // date unless the text explicitly supplied one.
                                due = due,
                                // No preset/today fallback, unlike due: a start date only exists
                                // if the text actually said so. Defaulting one would defer every
                                // widget-created task out of Today, which is the opposite of what
                                // a quick-add is for.
                                startDate = parsedTyped.startDate ?: parsedShared?.startDate,
                                time = parsedTyped.time ?: parsedShared?.time,
                                reminder = parsedTyped.reminder ?: parsedShared?.reminder,
                                priority = parsedTyped.priority ?: "none",
                                flag = parsedTyped.flag || (parsedShared?.flag == true),
                                done = false,
                                assigneeIds = resolvedAssigneeIds,
                                tagIds = resolvedTagIds,
                                recurrence = parsedTyped.recurrence ?: parsedShared?.recurrence,
                                subtasks = emptyList(),
                                notes = sharedNotes
                            )
                        )
                        WidgetRefresher.refreshAll(this@QuickAddDialogActivity)

                        val destination = allProjects.find { it.id == resolvedProjectId }?.name
                            ?: allLists.find { it.id == resolvedListId }?.name
                            ?: presetProject?.name
                            ?: presetList?.name
                        val dueLabel = com.mj.yata.util.TaskScheduleUtils.formatDueDate(due)
                        android.widget.Toast.makeText(
                            this@QuickAddDialogActivity,
                            if (destination != null) {
                                getString(R.string.quick_add_created_in, destination, dueLabel)
                            } else {
                                getString(R.string.quick_add_created, dueLabel)
                            },
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        finish()
                    }
                }

                QuickAddDialogContent(
                    targetName = targetName,
                    initialTitle = parsedShared?.title?.takeIf { it.isNotBlank() } ?: sharedFirstLine.orEmpty(),
                    lists = lists,
                    projects = projects,
                    tags = tags,
                    people = people,
                    tagsEnabled = tagsEnabled,
                    peopleEnabled = peopleEnabled,
                    projectsEnabled = projectsEnabled,
                    onCreateTag = { id, name, color ->
                        lifecycleScope.launch { repository.upsertTag(Tag(id = id, name = name, color = color)) }
                    },
                    onCreatePerson = { id, name, color ->
                        lifecycleScope.launch {
                            repository.upsertPerson(
                                Person(id = id, name = name, initials = initialsFor(name), color = color, isMe = false)
                            )
                        }
                    },
                    onSubmit = { title, projectId, listId, tagIds, assigneeIds ->
                        lifecycleScope.launch {
                            val duplicate = findSimilarTask(title, repository.getTasks().first())
                            if (duplicate != null) {
                                pendingTitle = title
                                pendingProjectId = projectId
                                pendingListId = listId
                                pendingTagIds = tagIds
                                pendingAssigneeIds = assigneeIds
                                pendingDuplicate = duplicate
                            } else {
                                createTask(title, projectId, listId, tagIds, assigneeIds)
                            }
                        }
                    },
                    onDismiss = { finish() }
                )

                pendingDuplicate?.let { existing ->
                    AlertDialog(
                        onDismissRequest = { pendingDuplicate = null },
                        title = { Text(stringResource(R.string.action_similar_task_already_exists)) },
                        text = { Text(stringResource(R.string.duplicate_task_match_body, existing.title)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val title = pendingTitle
                                pendingDuplicate = null
                                createTask(title, pendingProjectId, pendingListId, pendingTagIds, pendingAssigneeIds)
                            }) {
                                Text(stringResource(R.string.action_ignore_and_create))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                val existingId = existing.id
                                pendingDuplicate = null
                                startActivity(
                                    Intent(this@QuickAddDialogActivity, MainActivity::class.java).apply {
                                        putExtra("navigate_to", "task_detail")
                                        putExtra("task_id", existingId)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                finish()
                            }) {
                                Text(stringResource(R.string.action_go_to_existing_task))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddDialogContent(
    targetName: String?,
    initialTitle: String = "",
    lists: List<YataList> = emptyList(),
    projects: List<Project> = emptyList(),
    tags: List<Tag> = emptyList(),
    people: List<Person> = emptyList(),
    tagsEnabled: Boolean = false,
    peopleEnabled: Boolean = false,
    projectsEnabled: Boolean = false,
    onCreateTag: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onCreatePerson: (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onSubmit: (title: String, projectId: String?, listId: String?, tagIds: List<String>, assigneeIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(TextFieldValue(initialTitle, TextRange(initialTitle.length))) }
    // Mirrors NewTaskSheet's mention detection: same triggers, same feature gating (lists have no
    // feature flag, so `=` needs none). Explicitly picking an entity here bypasses this dialog's
    // usual NL-parse-on-submit path entirely for that field — see createTask's explicit* params.
    val mention = remember(title, tagsEnabled, peopleEnabled, projectsEnabled) {
        detectMentionToken(title.text, title.selection.end)?.takeIf {
            when (it.trigger) {
                TRIGGER_TAG -> tagsEnabled
                TRIGGER_PERSON -> peopleEnabled
                TRIGGER_PROJECT -> projectsEnabled
                TRIGGER_LIST -> true
                else -> false
            }
        }
    }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    val selectedTagIds = remember { mutableStateListOf<String>() }
    val selectedAssigneeIds = remember { mutableStateListOf<String>() }
    val activePeople = remember(people, selectedAssigneeIds.toList()) {
        people.activePeople(includeIds = selectedAssigneeIds.toSet())
    }
    // An explicit mention pick overrides the preset widget target in this preview line, matching
    // what createTask actually resolves — otherwise picking a different project here would leave
    // the header still claiming "Adding to <old target>".
    val effectiveDestinationName = selectedProjectId?.let { id -> projects.activeProjects().find { it.id == id }?.name }
        ?: selectedListId?.let { id -> lists.activeLists().find { it.id == id }?.name }
        ?: targetName
    val parsedPreview = remember(title.text) { NaturalLanguageParser.parse(title.text) }
    val previewItems = listOfNotNull(
        parsedPreview.title
            .takeIf { it.isNotBlank() && it != title.text.trim() }
            ?.let { stringResource(R.string.quick_add_preview_title, it) },
        parsedPreview.due?.let { stringResource(R.string.quick_add_preview_due, TaskScheduleUtils.formatDueDate(it)) },
        parsedPreview.time?.let { stringResource(R.string.quick_add_preview_time, it) },
        parsedPreview.recurrence?.let {
            stringResource(
                R.string.quick_add_preview_repeat,
                com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)
            )
        },
        parsedPreview.priority?.let { stringResource(R.string.quick_add_preview_priority, it.uppercase()) },
        stringResource(R.string.quick_add_preview_flagged).takeIf { parsedPreview.flag },
        effectiveDestinationName?.let { stringResource(R.string.quick_add_preview_target, it) }
    )
    val focusRequester = remember { FocusRequester() }
    val noRipple = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .maxByOrNull { it.length }
            if (!spoken.isNullOrBlank()) {
                val newText = if (title.text.isBlank()) spoken else title.text.trimEnd() + " " + spoken
                title = TextFieldValue(newText, TextRange(newText.length))
            }
        }
    }
    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.quick_add_voice_prompt))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            speechLauncher.launch(intent)
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.quick_add_voice_unavailable), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: IllegalStateException) {
            // Not yet attached — skip autofocus rather than crash.
        }
    }

    // Lighter scrim (not a full app-modal dim) so this reads as a quick popup from the widget,
    // not the app opening — plus a fast scale/fade-in instead of appearing instantly, since the
    // transparent activity theme disables the system window animation entirely
    // (Theme.Yata.Transparent's windowAnimationStyle = null). Centered + imePadding so it sits
    // just above the keyboard once it appears, rather than the keyboard covering it.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(indication = null, interactionSource = noRipple) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.92f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.92f)
        ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* swallow — don't dismiss */ },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fun submit() {
                    if (title.text.isNotBlank()) {
                        onSubmit(title.text.trim(), selectedProjectId, selectedListId, selectedTagIds.toList(), selectedAssigneeIds.toList())
                    }
                }
                Text(stringResource(R.string.quick_add_dialog_quick_add), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (effectiveDestinationName != null) {
                    Text(
                        text = stringResource(R.string.quick_add_adding_to, effectiveDestinationName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.quick_add_dialog_what_needs_doing)) },
                    // Wraps rather than scrolling off to the right — same reason as the title
                    // field in NewTaskSheet. ImeAction.Done still submits, so the action key
                    // behaves as before and never inserts a newline.
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = startVoiceInput) {
                            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.cd_add_task_by_voice))
                        }
                    },
                    shape = YataFieldShape,
                    colors = yataFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                if (mention != null) {
                    MentionSuggestions(
                        mention = mention,
                        tags = tags,
                        people = activePeople,
                        onSelectTag = { tag ->
                            if (tag.id !in selectedTagIds) selectedTagIds.add(tag.id)
                            title = consumeMentionToken(title, mention)
                        },
                        onSelectPerson = { person ->
                            if (person.id !in selectedAssigneeIds) selectedAssigneeIds.add(person.id)
                            title = consumeMentionToken(title, mention)
                        },
                        onCreateTag = { name ->
                            val id = "tag_" + UUID.randomUUID().toString()
                            onCreateTag(id, name, pickAccentFor(name))
                            selectedTagIds.add(id)
                            title = consumeMentionToken(title, mention)
                        },
                        onCreatePerson = { name ->
                            val id = "p_" + UUID.randomUUID().toString()
                            onCreatePerson(id, name, pickAccentFor(name))
                            selectedAssigneeIds.add(id)
                            title = consumeMentionToken(title, mention)
                        },
                        projects = projects,
                        lists = lists,
                        onSelectProject = { project ->
                            // A task belongs to a project or a list, never both — same rule
                            // NewTaskSheet's mention picker and its chip row enforce.
                            selectedProjectId = project.id
                            selectedListId = null
                            title = consumeMentionToken(title, mention)
                        },
                        onSelectList = { list ->
                            selectedListId = list.id
                            selectedProjectId = null
                            title = consumeMentionToken(title, mention)
                        }
                    )
                }
                if (previewItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.quick_add_preview_heading),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            previewItems.forEach { item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { submit() },
                        enabled = title.text.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            }
        }
        }
    }
}
