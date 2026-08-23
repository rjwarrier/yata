package com.mj.yata.ui.screen.taskdetail

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.SubtaskCompletionAction
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.domain.model.inheritedTagIds
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.*
import com.mj.yata.ui.sheets.RecurrenceSheet
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.util.rememberAdaptiveLayoutInfo
import com.mj.yata.ui.util.rememberAdaptiveSheetMaxWidth
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.findBestEntityMatch
import com.mj.yata.util.withParsedQuickAdd
import java.util.UUID

/** Equal-width rectangular (not pill-shaped) toggle for the Subtasks/Notes/Comments chip row —
 * highlighted while its section is visible, plain otherwise. While collapsed, [count] (if
 * non-null and > 0) is appended as a "(n)" badge so the chip previews how much content is
 * hidden — the badge drops once expanded since the content itself is then visible below. */
@Composable
private fun SectionToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "sectionToggleBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "sectionToggleText"
    )
    val displayLabel = if (!selected && count != null && count > 0) "$label ($count)" else label

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

private fun List<Subtask>.toExportSubtaskRows(): List<com.mj.yata.util.export.ExportSubtaskRow> {
    val childrenByParent = filter { it.parentSubtaskId != null }
        .groupBy { it.parentSubtaskId }
        .mapValues { (_, children) -> children.sortedBy { it.sortOrder } }
    val rows = mutableListOf<com.mj.yata.util.export.ExportSubtaskRow>()

    fun appendSubtree(subtask: Subtask, depth: Int) {
        rows += com.mj.yata.util.export.ExportSubtaskRow(
            title = subtask.title,
            done = subtask.done,
            depth = depth
        )
        childrenByParent[subtask.id].orEmpty().forEach { appendSubtree(it, depth + 1) }
    }

    filter { it.parentSubtaskId == null }
        .sortedBy { it.sortOrder }
        .forEach { appendSubtree(it, 0) }
    return rows
}

sealed interface DetailSheetType {
    object None : DetailSheetType
    object ScheduleEditor : DetailSheetType
    object ReminderPicker : DetailSheetType
    object RecurrenceBuilder : DetailSheetType
    object ListPicker : DetailSheetType
    object ProjectPicker : DetailSheetType
    object AssigneePicker : DetailSheetType
    object TagPicker : DetailSheetType
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    viewModel: MainViewModel,
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val taskState by remember(taskId) { viewModel.getTaskById(taskId) }.collectAsStateWithLifecycle(initialValue = null)
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val allTasks by viewModel.tasks.collectAsStateWithLifecycle()
    val useWideDetail = rememberAdaptiveLayoutInfo().isWide

    val accents = LocalYataAccents.current

    // Bottom sheet state
    var activeSheet by remember { mutableStateOf<DetailSheetType>(DetailSheetType.None) }

    // Subtask input state
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showFollowUpPicker by remember { mutableStateOf(false) }
    var pendingParentCompletionSubtasks by remember(taskId) { mutableStateOf<List<Subtask>?>(null) }

    val task = taskState
    val showMissingTask = com.mj.yata.ui.widgets.rememberMissingContentVisible(taskId, task == null)
    if (task == null) {
        if (showMissingTask) {
            MissingContentState(
                itemName = stringResource(R.string.entity_task),
                onNavigateBack = onNavigateBack
            )
        } else {
            TaskDetailShimmer()
        }
        return
    }

    // Subtasks/Notes/Comments section visibility — toggled via the chip row above them.
    // Defaults to whichever sections actually have content, instead of always showing all
    // three regardless of whether there's anything in them.
    val comments by remember(task.id) { viewModel.getCommentsForTask(task.id) }.collectAsStateWithLifecycle()
    var showSubtasks by remember(task.id) { mutableStateOf(task.subtasks.isNotEmpty()) }
    var showNotes by remember(task.id) { mutableStateOf(!task.notes.isNullOrBlank()) }
    var showComments by remember(task.id) { mutableStateOf(comments.isNotEmpty()) }
    // Comments load asynchronously (unlike subtasks/notes, which are already on `task`), so the
    // very first composition almost always sees an empty placeholder list before the real query
    // result arrives — keep syncing the default to the real data until the user manually toggles
    // the chip themselves, at which point their choice wins even if the comment count changes
    // later (e.g. adding a comment while the section is manually hidden shouldn't force it open).
    var userToggledComments by remember(task.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showToolbarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 80)
        }
    }
    LaunchedEffect(comments) {
        if (!userToggledComments) showComments = comments.isNotEmpty()
    }

    val listsById = remember(lists) { lists.associateBy { it.id } }
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    val taskList = remember(task, listsById) { listsById[task.listId] }
    val project = remember(task, projectsById) { projectsById[task.projectId] }
    val taskAssignees = remember(task, peopleById) { task.assigneeIds.mapNotNull { pid -> peopleById[pid] } }
    val ownTagIds = task.tagIds
    val inheritedTagIds = remember(task, projectsById) { task.inheritedTagIds(projectsById) }
    val ownTags = remember(ownTagIds, tagsById) { ownTagIds.mapNotNull { tid -> tagsById[tid] } }
    val inheritedTags = remember(inheritedTagIds, ownTagIds, tagsById) {
        inheritedTagIds.filter { it !in ownTagIds }.mapNotNull { tid -> tagsById[tid] }
    }

    val listColor = taskList?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val recurrenceHistory = remember(task, allTasks) {
        if (task.recurrence == null) {
            emptyList()
        } else {
            allTasks
                .filter {
                    it.id != task.id &&
                        it.done &&
                        it.completedAt != null &&
                        it.recurrence == null &&
                        it.title == task.title &&
                        it.projectId == task.projectId &&
                        it.listId == task.listId
                }
                .sortedByDescending { it.completedAt }
                .take(6)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var exportFormatPending by remember { mutableStateOf<com.mj.yata.util.export.ExportFormat?>(null) }
    var exportInProgress by remember { mutableStateOf(false) }
    val longTaskLinkWarningGate = com.mj.yata.util.export.rememberLongTaskLinkWarningGate()

    LaunchedEffect(Unit) {
        viewModel.postponementWarnings.collect { warning ->
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.task_postponement_warning,
                    warning.taskTitle,
                    warning.postponementCount
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.weekendRescheduleWarnings.collect { warning ->
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.task_weekend_reschedule_warning,
                    warning.taskTitle,
                    warning.dayLabel
                )
            )
        }
    }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()
    val subtaskCompletionAction by viewModel.subtaskCompletionAction.collectAsStateWithLifecycle()
    val profileUserName by viewModel.userName.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> YataSnackbar(data) }
        },
        bottomBar = {
            com.mj.yata.ui.screen.main.AdaptiveBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showToolbarTitle,
                        enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphasized)) +
                            expandVertically(tween(YataDur.fade, easing = YataEase.emphasized)),
                        exit = fadeOut(tween(YataDur.micro, easing = YataEase.emphasized)) +
                            shrinkVertically(tween(YataDur.micro, easing = YataEase.emphasized))
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Skip this occurrence (recurring tasks only) — advances the due date without
                    // completing it. Deferred until the Undo snackbar times out, same pattern as delete.
                    // Flag toggle
                    IconButton(onClick = { viewModel.toggleTaskFlag(task.id) }) {
                        Icon(
                            imageVector = if (task.flag) Icons.Default.Flag else Icons.Default.OutlinedFlag,
                            contentDescription = stringResource(R.string.task_detail_flag),
                            tint = if (task.flag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Duplicate — clones the task (new id, done reset), keeps everything else
                    // Export as PDF/Image — options (include notes/comments) are confirmed via
                    // TaskExportOptionsDialog before the off-screen render actually happens.
                    var showExportMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                    YataDropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        if (task.recurrence != null && !task.done) {
                            YataDropdownMenuItem(
                                text = { Text(stringResource(R.string.task_detail_skip_this_occurrence)) },
                                onClick = {
                                    showExportMenu = false
                                    scope.launch {
                                        val result = showUndoSnackbar(snackbarHostState, context.getString(R.string.task_occurrence_skipped), undoWindowSeconds)
                                        if (!result) viewModel.skipTaskOccurrence(task.id)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) }
                            )
                        }
                        YataDropdownMenuItem(
                            text = { Text(stringResource(R.string.task_detail_duplicate_task)) },
                            onClick = {
                                showExportMenu = false
                                viewModel.duplicateTask(task.id) { duplicated ->
                                    onNavigateToTaskDetail(duplicated.id)
                                }
                                scope.launch { snackbarHostState.showSuccess(context.getString(R.string.task_duplicated)) }
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                        YataDropdownMenuItem(
                            text = { Text(stringResource(R.string.action_export_as_image)) },
                            onClick = {
                                showExportMenu = false
                                exportFormatPending = com.mj.yata.util.export.ExportFormat.IMAGE
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                        )
                        YataDropdownMenuItem(
                            text = { Text(stringResource(R.string.action_export_as_pdf)) },
                            onClick = {
                                showExportMenu = false
                                exportFormatPending = com.mj.yata.util.export.ExportFormat.PDF
                            },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                        )
                        // Archive is not delete: the task keeps everything and simply leaves the
                        // normal listings until unarchived from Settings -> Archive.
                        YataDropdownMenuItem(
                            text = { Text(if (task.archived) stringResource(R.string.task_unarchive_action) else stringResource(R.string.task_archive_action)) },
                            onClick = {
                                showExportMenu = false
                                val nowArchived = !task.archived
                                viewModel.setTaskArchived(task.id, nowArchived)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (nowArchived) context.getString(R.string.task_archived) else context.getString(R.string.task_unarchived),
                                        actionLabel = com.mj.yata.ui.widgets.UNDO_ACTION_LABEL,
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.setTaskArchived(task.id, !nowArchived)
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    if (task.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null
                                )
                            }
                        )
                        // Deep link back to this task — paste into a note, a message, or an
                        // automation and it reopens exactly here.
                        YataDropdownMenuItem(
                            text = { Text(stringResource(R.string.task_detail_copy_link_to_task)) },
                            onClick = {
                                showExportMenu = false
                                clipboardManager.setText(
                                    AnnotatedString(com.mj.yata.ui.navigation.DeepLink.task(task.id))
                                )
                                scope.launch { snackbarHostState.showSuccess(context.getString(R.string.task_link_copied)) }
                            },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                        )
                        HorizontalDivider()
                        YataDropdownMenuItem(
                            text = { Text(stringResource(R.string.task_detail_delete_task), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showExportMenu = false
                                scope.launch {
                                    val result = showUndoSnackbar(snackbarHostState, context.getString(R.string.task_deleted), undoWindowSeconds)
                                    if (!result) {
                                        viewModel.deleteTask(task)
                                        onNavigateBack()
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                    // Delete/Archive — deletion is deferred until the Undo snackbar times out,
                    // so the coroutine must outlive this composable's own scope (it navigates
                    // back only once the delete actually happens).
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContentBox(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
            // 1. Check + Title Row — tap the title to rename it in place. Also the one place
            // this screen recognizes inline #tag/@person mentions while editing (matching
            // NewTaskSheet's own title field) — previously silently ignored here entirely.
            item {
                Column {
                    var isEditingTitle by remember(task.id) { mutableStateOf(false) }
                    var titleHasFocusedOnce by remember(task.id) { mutableStateOf(false) }
                    var titleEdited by remember(task.id) { mutableStateOf(false) }
                    var titleQuickAddDismissed by remember(task.id) { mutableStateOf(false) }
                    var ignoredTitleQuickAddFields by remember(task.id) { mutableStateOf(setOf<String>()) }
                    var titleEditBaseline by remember(task.id) { mutableStateOf<Task?>(null) }
                    val titleFocusRequester = remember(task.id) { FocusRequester() }
                    val titleColor = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    val titleStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        color = titleColor,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Bound to a local buffer, not task.title directly — task.title only
                    // updates after a round trip through the DB (write -> Room re-query ->
                    // Flow emission -> recompose), and fast typing outraces that, dropping
                    // characters. The buffer reflects every keystroke instantly; the DB write
                    // still happens on each change, it just isn't what the field displays.
                    // A TextFieldValue (not a plain String) so the mention detector below knows
                    // where the cursor actually is, not just the end of the string.
                    var titleBuffer by remember(task.id) {
                        mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length)))
                    }
                    val quickAdd = remember(titleBuffer.text) { NaturalLanguageParser.parse(titleBuffer.text) }
                    val quickAddMatched = isEditingTitle && titleEdited && !titleQuickAddDismissed &&
                        quickAdd.title != titleBuffer.text.trim()
                    val quickAddVisualTransformation = rememberQuickAddHighlightTransformation(
                        spans = quickAdd.highlightSpans,
                        enabled = quickAddMatched
                    )
                    val mention = remember(titleBuffer, tagsFeatureEnabled, peopleFeatureEnabled, projectsFeatureEnabled) {
                        if (!isEditingTitle) {
                            null
                        } else {
                            detectMentionToken(titleBuffer.text, titleBuffer.selection.end)
                                ?.takeIf {
                                    when (it.trigger) {
                                        TRIGGER_TAG -> tagsFeatureEnabled
                                        TRIGGER_PERSON -> peopleFeatureEnabled
                                        TRIGGER_PROJECT -> projectsFeatureEnabled
                                        TRIGGER_LIST -> true
                                        else -> false
                                    }
                                }
                        }
                    }
                    val effectiveIgnoredTitleQuickAddFields = remember(ignoredTitleQuickAddFields, mention) {
                        ignoredTitleQuickAddFields + quickAddFieldsOwnedByMention(mention)
                    }
                    fun upsertTitleEdit(value: TextFieldValue, parsed: ParsedQuickAdd = quickAdd) {
                        val rawTitle = value.text
                        if (rawTitle.isBlank()) return
                        val activeMentionIgnoredFields = quickAddFieldsOwnedByMention(
                            detectMentionToken(rawTitle, value.selection.end)
                        )
                        val updated = task.copy(title = rawTitle).withParsedQuickAdd(
                            quickAdd = parsed,
                            ignoredFields = ignoredTitleQuickAddFields + activeMentionIgnoredFields,
                            lists = lists,
                            projects = projects,
                            people = people,
                            tags = tags,
                            projectsEnabled = projectsFeatureEnabled,
                            tagsEnabled = tagsFeatureEnabled,
                            peopleEnabled = peopleFeatureEnabled
                        )
                        viewModel.upsertTask(updated)
                    }
                    fun finishTitleEdit() {
                        if (titleEdited && quickAddMatched && quickAdd.title.isNotBlank()) {
                            val finalTitleValue = TextFieldValue(quickAdd.title, TextRange(quickAdd.title.length))
                            titleBuffer = finalTitleValue
                            upsertTitleEdit(finalTitleValue, quickAdd)
                        }
                        isEditingTitle = false
                        titleEditBaseline = null
                    }
                    fun restoreSmartField(field: String, restoredTask: (Task, Task?) -> Task) {
                        ignoredTitleQuickAddFields = ignoredTitleQuickAddFields + field
                        viewModel.upsertTask(restoredTask(task.copy(title = titleBuffer.text), titleEditBaseline))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpringyCheck(
                            checked = task.done,
                            onCheckedChange = { viewModel.toggleTaskDone(task.id) {} },
                            color = listColor,
                            size = 28.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))

                        if (isEditingTitle) {
                            LaunchedEffect(Unit) {
                                titleHasFocusedOnce = false
                                titleFocusRequester.requestFocus()
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = titleBuffer,
                                onValueChange = { newValue ->
                                    titleBuffer = newValue
                                    titleEdited = true
                                    titleQuickAddDismissed = false
                                    ignoredTitleQuickAddFields = emptySet()
                                    upsertTitleEdit(newValue, NaturalLanguageParser.parse(newValue.text))
                                },
                                textStyle = titleStyle,
                                // Wraps rather than scrolling off to the right. This is the field
                                // for editing an *existing* title, so it's the one most likely to
                                // already hold text longer than the line — as a single line the
                                // start of it was unreachable.
                                maxLines = 4,
                                visualTransformation = quickAddVisualTransformation,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            titleHasFocusedOnce = true
                                        } else if (titleHasFocusedOnce) {
                                            finishTitleEdit()
                                        }
                                    }
                            )
                        } else {
                            Text(
                                text = task.title,
                                style = titleStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        titleBuffer = TextFieldValue(task.title, TextRange(task.title.length))
                                        titleEdited = false
                                        titleQuickAddDismissed = false
                                        ignoredTitleQuickAddFields = emptySet()
                                        titleEditBaseline = task
                                        isEditingTitle = true
                                    }
                            )
                        }
                    }

                    if (mention != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MentionSuggestions(
                            mention = mention,
                            tags = tags,
                            people = people,
                            onSelectTag = { tag ->
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                val newTagIds = if (task.tagIds.contains(tag.id)) task.tagIds else task.tagIds + tag.id
                                viewModel.upsertTask(task.copy(title = newTitleValue.text, tagIds = newTagIds))
                            },
                            onSelectPerson = { person ->
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                val newAssigneeIds = if (task.assigneeIds.contains(person.id)) task.assigneeIds else task.assigneeIds + person.id
                                viewModel.upsertTask(task.copy(title = newTitleValue.text, assigneeIds = newAssigneeIds))
                            },
                            onCreateTag = { name ->
                                val id = "tag_" + UUID.randomUUID().toString()
                                viewModel.upsertTag(com.mj.yata.domain.model.Tag(id = id, name = name, color = com.mj.yata.ui.sheets.pickAccentFor(name)))
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                viewModel.upsertTask(task.copy(title = newTitleValue.text, tagIds = task.tagIds + id))
                            },
                            onCreatePerson = { name ->
                                val id = "p_" + UUID.randomUUID().toString()
                                viewModel.upsertPerson(
                                    com.mj.yata.domain.model.Person(
                                        id = id,
                                        name = name,
                                        initials = com.mj.yata.ui.sheets.initialsFor(name),
                                        color = com.mj.yata.ui.sheets.pickAccentFor(name),
                                        isMe = false
                                    )
                                )
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                viewModel.upsertTask(task.copy(title = newTitleValue.text, assigneeIds = task.assigneeIds + id))
                            },
                            projects = projects,
                            lists = lists,
                            onSelectProject = { project ->
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                viewModel.upsertTask(
                                    task.copy(
                                        title = newTitleValue.text,
                                        projectId = project.id,
                                        listId = null,
                                        due = project.due ?: task.due
                                    )
                                )
                            },
                            onSelectList = { list ->
                                val newTitleValue = consumeMentionToken(titleBuffer, mention)
                                titleBuffer = newTitleValue
                                viewModel.upsertTask(task.copy(title = newTitleValue.text, listId = list.id, projectId = null))
                            }
                        )
                    }

                    if (quickAddMatched) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val detectedItems = listOfNotNull<Triple<String, () -> Unit, () -> Unit>>(
                            quickAdd.due?.takeIf { "due" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("Due ${TaskScheduleUtils.formatDueDate(it)}", { activeSheet = DetailSheetType.ScheduleEditor }, {
                                    restoreSmartField("due") { current, baseline -> current.copy(due = baseline?.due) }
                                })
                            },
                            quickAdd.startDate?.takeIf { "start" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple(stringResource(R.string.smart_add_starts, TaskScheduleUtils.formatDueDate(it)), { activeSheet = DetailSheetType.ScheduleEditor }, {
                                    restoreSmartField("start") { current, baseline -> current.copy(startDate = baseline?.startDate) }
                                })
                            },
                            quickAdd.time?.takeIf { "time" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("Time $it", { activeSheet = DetailSheetType.ScheduleEditor }, {
                                    restoreSmartField("time") { current, baseline -> current.copy(time = baseline?.time) }
                                })
                            },
                            quickAdd.recurrence?.takeIf { "recurrence" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("Repeat ${com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)}", { activeSheet = DetailSheetType.RecurrenceBuilder }, {
                                    restoreSmartField("recurrence") { current, baseline -> current.copy(recurrence = baseline?.recurrence) }
                                })
                            },
                            quickAdd.reminder?.takeIf { "reminder" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("Remind $it", { activeSheet = DetailSheetType.ReminderPicker }, {
                                    restoreSmartField("reminder") { current, baseline -> current.copy(reminder = baseline?.reminder) }
                                })
                            },
                            quickAdd.priority?.takeIf { "priority" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("${it.uppercase()} priority", { }, {
                                    restoreSmartField("priority") { current, baseline -> current.copy(priority = baseline?.priority ?: "none") }
                                })
                            },
                            "Flagged".takeIf { quickAdd.flag && "flag" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple(it, { viewModel.toggleTaskFlag(task.id) }, {
                                    restoreSmartField("flag") { current, baseline -> current.copy(flag = baseline?.flag ?: false) }
                                })
                            },
                            quickAdd.projectName?.takeIf { projectsFeatureEnabled && "project" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("Project $it", { activeSheet = DetailSheetType.ProjectPicker }, {
                                    restoreSmartField("project") { current, baseline ->
                                        current.copy(projectId = baseline?.projectId, listId = baseline?.listId, due = baseline?.due)
                                    }
                                })
                            },
                            quickAdd.listName?.takeIf { "list" !in effectiveIgnoredTitleQuickAddFields }?.let {
                                Triple("List $it", { activeSheet = DetailSheetType.ListPicker }, {
                                    restoreSmartField("list") { current, baseline ->
                                        current.copy(listId = baseline?.listId, projectId = baseline?.projectId)
                                    }
                                })
                            },
                            quickAdd.tagNames.takeIf { tagsFeatureEnabled && it.isNotEmpty() && "tags" !in effectiveIgnoredTitleQuickAddFields }?.joinToString(", ") { "#$it" }?.let {
                                Triple("Tags $it", { activeSheet = DetailSheetType.TagPicker }, {
                                    restoreSmartField("tags") { current, baseline -> current.copy(tagIds = baseline?.tagIds ?: current.tagIds) }
                                })
                            },
                            quickAdd.assigneeNames.takeIf { peopleFeatureEnabled && it.isNotEmpty() && "people" !in effectiveIgnoredTitleQuickAddFields }?.joinToString(", ") { "@$it" }?.let {
                                Triple("People $it", { activeSheet = DetailSheetType.AssigneePicker }, {
                                    restoreSmartField("people") { current, baseline -> current.copy(assigneeIds = baseline?.assigneeIds ?: current.assigneeIds) }
                                })
                            }
                        )
                        if (detectedItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
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
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.new_task_smart_add_summary),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.new_task_ignore_detected_date_time),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .clickable { titleQuickAddDismissed = true }
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.new_task_detected_title, quickAdd.title),
                                    style = MaterialTheme.typography.bodyMedium,
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
                                            colors = InputChipDefaults.inputChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.surface,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.new_task_ignore_field, item),
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
                }
            }

            // 2. Meta rows — each its own surfaceContainerLow card (per handoff's MetaRow)
            item {
                @Composable
                fun CoreMetaRows(itemModifier: Modifier = Modifier) {
                    MetaRowItem(
                        icon = Icons.Default.Today,
                        label = stringResource(R.string.task_detail_due_date),
                        value = com.mj.yata.util.TaskScheduleUtils.formatDueDateTime(task.due, task.time),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = itemModifier,
                        onClick = { activeSheet = DetailSheetType.ScheduleEditor }
                    )

                    // Only shown once a start date exists. An always-present "No start date" row
                    // would put a field most tasks never use above Reminder and Repeat, which
                    // nearly all of them do — it's set from the schedule editor instead.
                    if (task.startDate != null) {
                        MetaRowItem(
                            icon = Icons.Default.EventAvailable,
                            label = stringResource(R.string.task_start_date),
                            value = com.mj.yata.util.TaskScheduleUtils.formatDueDate(task.startDate),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = itemModifier,
                            onClick = { activeSheet = DetailSheetType.ScheduleEditor }
                        )
                    }

                    MetaRowItem(
                        icon = Icons.Default.Notifications,
                        label = stringResource(R.string.task_detail_reminder),
                        value = com.mj.yata.util.TaskScheduleUtils.formatReminder(task.reminder),
                        accentColor = if (task.reminder != null) MaterialTheme.colorScheme.secondary else null,
                        modifier = itemModifier,
                        onClick = { activeSheet = DetailSheetType.ReminderPicker }
                    )

                    val repeatsVal = task.recurrence?.let {
                        com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it)
                    } ?: "Does not repeat"
                    MetaRowItem(
                        icon = Icons.Default.Repeat,
                        label = stringResource(R.string.task_detail_repeats),
                        value = repeatsVal,
                        accentColor = if (task.recurrence != null) MaterialTheme.colorScheme.tertiary else null,
                        modifier = itemModifier,
                        onClick = { activeSheet = DetailSheetType.RecurrenceBuilder }
                    )
                    if (projectsFeatureEnabled) {
                        MetaRowItem(
                            icon = Icons.Default.Layers,
                            label = stringResource(R.string.entity_project),
                            value = project?.name ?: stringResource(R.string.task_detail_none),
                            modifier = itemModifier,
                            onClick = { activeSheet = DetailSheetType.ProjectPicker }
                        )
                    }

                    MetaRowItem(
                        icon = Icons.Default.Folder,
                        label = stringResource(R.string.entity_list),
                        value = taskList?.name ?: stringResource(R.string.task_detail_none),
                        swatchColor = listColor,
                        modifier = itemModifier,
                        onClick = { activeSheet = DetailSheetType.ListPicker }
                    )

                    // Priority
                    MetaRowItem(
                        icon = Icons.Default.Flag,
                        label = stringResource(R.string.new_task_priority),
                        value = task.priority.uppercase(),
                        rightContent = { PriorityBars(priority = task.priority) },
                        modifier = itemModifier,
                        onClick = { viewModel.cycleTaskPriority(task.id) }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (useWideDetail) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2
                        ) {
                            CoreMetaRows(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoreMetaRows()
                        }
                    }

                    // Carry forward — only makes sense for an open task that's already due
                    // today or overdue, not one that's done or scheduled for the future.
                    val canCarryForward = !task.done && task.due != null &&
                        task.due <= java.time.LocalDate.now().toString()
                    if (canCarryForward) {
                        YataSelectChip(
                            label = stringResource(R.string.task_detail_carry_forward),
                            selected = true,
                            onClick = {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusDays(1).toString()))
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            leading = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            showCheck = false
                        )
                    }

                    // Reliable streak (linked via TaskEntity.seriesId, not the title-heuristic
                    // recurrenceHistory below) — only counts completions since seriesId tracking
                    // was added, so a brand-new streak isn't itself a bug.
                    var streak by remember(task.id) { mutableIntStateOf(0) }
                    LaunchedEffect(task.id, task.recurrence) {
                        if (task.recurrence != null) {
                            viewModel.streakForTask(task.id) { streak = it }
                        } else {
                            streak = 0
                        }
                    }
                    if (task.recurrence != null && streak >= 2) {
                        Text(
                            text = stringResource(R.string.task_detail_streak_days, streak),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    if (recurrenceHistory.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.task_detail_recurring_history),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            recurrenceHistory.forEach { historyTask ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.mj.yata.util.TaskScheduleUtils.formatDueDate(historyTask.due),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = com.mj.yata.util.TaskScheduleUtils.formatCompletedAt(historyTask.completedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Assignees section
            if (peopleFeatureEnabled) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.new_task_assigned_to),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // The first assignee is the owner by convention (assigneeIds is built by
                        // append, so index 0 is whoever was assigned first) — everyone else is a
                        // collaborator. Only labeled once there's more than one, since a single
                        // assignee's ownership is implicit.
                        taskAssignees.forEachIndexed { index, person ->
                            InputChip(
                                selected = true,
                                onClick = { activeSheet = DetailSheetType.AssigneePicker },
                                label = {
                                    Text(
                                        if (index == 0 && taskAssignees.size > 1) {
                                            stringResource(R.string.task_assignee_owner, person.name)
                                        } else {
                                            person.name
                                        }
                                    )
                                },
                                leadingIcon = {
                                    com.mj.yata.ui.widgets.PersonAvatar(
                                        initials = person.initials,
                                        accentKey = person.color,
                                        size = 20.dp,
                                        photoUri = person.photoUri
                                    )
                                }
                            )
                        }
                        // Dashed add assignee
                        InputChip(
                            selected = false,
                            onClick = { activeSheet = DetailSheetType.AssigneePicker },
                            label = { Text(stringResource(R.string.task_detail_assign)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // 3.5 "Waiting on" follow-up — only shown for tasks owned (assigneeIds[0]) by
            // someone other than you. Setting a future date snoozes it out of Today until then
            // (see Task.isWaitingOn); it never touches the task's own due date.
            if (peopleFeatureEnabled) {
                val myId = people.find { it.isMe }?.id
                val ownerId = task.assigneeIds.firstOrNull()
                if (ownerId != null && ownerId != myId) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.task_waiting_on),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val followUpDate = task.followUpAt?.let {
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                            LocalScheduleChip("Tomorrow", followUpDate == java.time.LocalDate.now().plusDays(1)) {
                                val millis = java.time.LocalDate.now().plusDays(1)
                                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                viewModel.setTaskFollowUp(task.id, millis)
                            }
                            LocalScheduleChip("Next week", followUpDate == java.time.LocalDate.now().plusWeeks(1)) {
                                val millis = java.time.LocalDate.now().plusWeeks(1)
                                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                viewModel.setTaskFollowUp(task.id, millis)
                            }
                            LocalScheduleChip("Pick date", false) { showFollowUpPicker = true }
                            LocalScheduleChip(stringResource(R.string.task_waiting_on_none), task.followUpAt == null) {
                                viewModel.setTaskFollowUp(task.id, null)
                            }
                        }
                        LocalPanelHint(
                            stringResource(
                                if (task.followUpAt != null) {
                                    R.string.task_waiting_on_set_hint
                                } else {
                                    R.string.task_waiting_on_unset_hint
                                }
                            )
                        )
                    }
                }
            }

            // 3.6 Estimate — planned effort, feeding Today's "X planned" capacity line. Always
            // offered (unlike Section), since any task can carry one.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.task_estimate),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocalScheduleChip(stringResource(R.string.task_estimate_none), task.estimateMinutes == null) {
                            viewModel.setTaskEstimate(task.id, null)
                        }
                        com.mj.yata.util.EstimateUtils.PRESETS.forEach { minutes ->
                            LocalScheduleChip(
                                com.mj.yata.util.EstimateUtils.format(minutes),
                                task.estimateMinutes == minutes
                            ) {
                                viewModel.setTaskEstimate(task.id, minutes)
                            }
                        }
                    }
                    LocalPanelHint(stringResource(R.string.task_estimate_hint))
                }
            }

            // 3.7 Section — only shown once the task's own project has defined sections
            // (Project.sectionNames); otherwise there's nothing meaningful to pick from and this
            // row would just be clutter. See ManageSectionsSheet for defining them.
            if (project != null && project.sectionNames.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.task_section),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocalScheduleChip(stringResource(R.string.task_section_none), task.section !in project.sectionNames) {
                            viewModel.upsertTask(task.copy(section = ""))
                        }
                        project.sectionNames.forEach { sectionName ->
                            LocalScheduleChip(sectionName, task.section == sectionName) {
                                viewModel.upsertTask(task.copy(section = sectionName))
                            }
                        }
                    }
                }
            }

            // 4. Tags section
            if (tagsFeatureEnabled) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.tab_tags),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Inherited from the project — live-synced, not removable here.
                        inheritedTags.forEach { tag ->
                            TagChip(name = tag.name, accentKey = tag.color)
                        }
                        ownTags.forEach { tag ->
                            TagChip(
                                name = tag.name,
                                accentKey = tag.color,
                                onRemoveClick = {
                                    val newTags = task.tagIds.filter { it != tag.id }
                                    viewModel.upsertTask(task.copy(tagIds = newTags))
                                }
                            )
                        }
                        // Dashed add tag
                        InputChip(
                            selected = false,
                            onClick = { activeSheet = DetailSheetType.TagPicker },
                            label = { Text(stringResource(R.string.task_detail_tag)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (inheritedTags.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.task_detail_tags_sync_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section toggle chips — show/hide Subtasks/Notes/Comments independently, each
            // section animating open/closed instead of just popping in and out.
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SectionToggleChip(stringResource(R.string.new_task_subtasks), showSubtasks, { showSubtasks = !showSubtasks }, Modifier.weight(1f), count = task.subtasks.size)
                    SectionToggleChip(stringResource(R.string.new_task_notes), showNotes, { showNotes = !showNotes }, Modifier.weight(1f), count = if (!task.notes.isNullOrBlank()) 1 else 0)
                    SectionToggleChip(stringResource(R.string.task_detail_comments), showComments, { showComments = !showComments; userToggledComments = true }, Modifier.weight(1f), count = comments.size)
                }
            }

            // 5. Subtasks section
            item {
                AnimatedVisibility(
                    visible = showSubtasks,
                    enter = expandVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeIn(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)),
                    exit = shrinkVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeOut(animationSpec = tween(com.mj.yata.ui.theme.YataDur.micro))
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val subtasks = task.subtasks
                    val subTotal = subtasks.size
                    val subDone = subtasks.count { it.done }
                    val subProgress = if (subTotal > 0) subDone.toFloat() / subTotal else 0f
                    val animatedSubProgress by animateFloatAsState(
                        targetValue = subProgress,
                        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
                        label = "subtaskProgressAnimation"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.new_task_subtasks),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.task_detail_subtasks_progress, subDone, subTotal),
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    if (subTotal > 0) {
                        LinearProgressIndicator(
                            progress = { animatedSubProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    val topLevelSubtasks = remember(subtasks) { subtasks.filter { it.parentSubtaskId == null }.sortedBy { it.sortOrder } }
                    val childrenByParent = remember(subtasks) { subtasks.filter { it.parentSubtaskId != null }.groupBy { it.parentSubtaskId } }

                    fun toggleSubtask(id: String, done: Boolean) {
                        val wasAllDone = subtasks.isNotEmpty() && subtasks.all { it.done }
                        val updated = subtasks.map { if (it.id == id) it.copy(done = done) else it }
                        val isAllDone = updated.isNotEmpty() && updated.all { it.done }
                        if (done && !task.done && !wasAllDone && isAllDone) {
                            when (subtaskCompletionAction) {
                                SubtaskCompletionAction.AUTO_COMPLETE -> {
                                    viewModel.completeTaskAfterSavingSubtasks(task, updated) {}
                                }
                                SubtaskCompletionAction.ASK -> {
                                    viewModel.upsertTask(task.copy(subtasks = updated))
                                    pendingParentCompletionSubtasks = updated
                                }
                                SubtaskCompletionAction.NOTHING -> {
                                    viewModel.upsertTask(task.copy(subtasks = updated))
                                }
                            }
                        } else {
                            viewModel.upsertTask(task.copy(subtasks = updated))
                        }
                    }

                    fun deleteSubtask(id: String) {
                        // Cascade: dropping a parent also drops its (depth-1) children.
                        val updated = subtasks.filter { it.id != id && it.parentSubtaskId != id }
                        viewModel.upsertTask(task.copy(subtasks = updated))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        topLevelSubtasks.forEach { sub ->
                            var addingChild by remember(sub.id) { mutableStateOf(false) }
                            var childTitle by remember(sub.id) { mutableStateOf("") }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleSubtask(sub.id, !sub.done) }
                                    .padding(vertical = 6.dp)
                            ) {
                                SpringyCheck(checked = sub.done, onCheckedChange = { toggleSubtask(sub.id, it) }, color = listColor, size = 20.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sub.title,
                                    color = if (sub.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { addingChild = !addingChild }) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.task_detail_add_sub_item), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { deleteSubtask(sub.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.task_detail_delete_subtask), modifier = Modifier.size(16.dp))
                                }
                            }

                            // Depth-1 children — no further nesting allowed, so no "add child" here.
                            childrenByParent[sub.id]?.sortedBy { it.sortOrder }?.forEach { child ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp)
                                        .clickable { toggleSubtask(child.id, !child.done) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    SpringyCheck(checked = child.done, onCheckedChange = { toggleSubtask(child.id, it) }, color = listColor, size = 18.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = child.title,
                                        color = if (child.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (child.done) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { deleteSubtask(child.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.task_detail_delete_sub_item), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            if (addingChild) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    TextField(
                                        value = childTitle,
                                        onValueChange = { childTitle = it },
                                        placeholder = { Text(stringResource(R.string.task_detail_add_a_sub_item)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                                        colors = com.mj.yata.ui.widgets.yataFieldColors()
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (childTitle.isNotBlank()) {
                                                val siblingCount = childrenByParent[sub.id]?.size ?: 0
                                                val newChild = Subtask(
                                                    id = "sub_" + UUID.randomUUID().toString(),
                                                    title = childTitle.trim(),
                                                    done = false,
                                                    parentSubtaskId = sub.id,
                                                    sortOrder = siblingCount
                                                )
                                                viewModel.upsertTask(task.copy(subtasks = subtasks + newChild))
                                                childTitle = ""
                                                addingChild = false
                                            }
                                        },
                                        enabled = childTitle.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.task_detail_add_sub_item))
                                    }
                                }
                            }
                        }

                        // Add subtask input field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            TextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text(stringResource(R.string.action_add_a_subtask)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = com.mj.yata.ui.widgets.YataCompactFieldShape,
                                colors = com.mj.yata.ui.widgets.yataFieldColors()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        val newSub = Subtask(
                                            id = "sub_" + UUID.randomUUID().toString(),
                                            title = newSubtaskTitle.trim(),
                                            done = false,
                                            sortOrder = topLevelSubtasks.size
                                        )
                                        viewModel.upsertTask(task.copy(subtasks = subtasks + newSub))
                                        newSubtaskTitle = ""
                                    }
                                },
                                enabled = newSubtaskTitle.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_subtask))
                            }
                        }
                    }
                }
                }
            }

            // 6. Notes card — tap to edit raw text, tap away to render as markdown.
            item {
                AnimatedVisibility(
                    visible = showNotes,
                    enter = expandVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeIn(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)),
                    exit = shrinkVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeOut(animationSpec = tween(com.mj.yata.ui.theme.YataDur.micro))
                ) {
                var isEditingNotes by remember(task.id) { mutableStateOf(false) }
                // onFocusChanged fires once immediately on mount reporting isFocused=false (before
                // anything has actually requested focus) — without this guard, that spurious first
                // callback closed edit mode the instant it opened, so tapping notes appeared to do
                // nothing. Only a real loss of focus (after having genuinely gained it) should close it.
                var hasFocusedOnce by remember(task.id) { mutableStateOf(false) }
                val notesFocusRequester = remember(task.id) { FocusRequester() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.new_task_notes),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isEditingNotes || task.notes.isNullOrBlank()) {
                        // Same local-buffer fix as the title field — task.notes only updates after
                        // a round trip through the DB, and fast typing outraces that otherwise.
                        var notesBuffer by remember(task.id) { mutableStateOf(task.notes ?: "") }
                        LaunchedEffect(isEditingNotes) {
                            if (isEditingNotes) {
                                hasFocusedOnce = false
                                notesFocusRequester.requestFocus()
                            }
                        }
                        TextField(
                            value = notesBuffer,
                            onValueChange = {
                                notesBuffer = it
                                viewModel.upsertTask(task.copy(notes = it))
                            },
                            placeholder = { Text(stringResource(R.string.task_detail_notes_placeholder)) },
                            supportingText = { Text(stringResource(R.string.task_detail_notes_supporting)) },
                            minLines = 3,
                            // Bounded so a long note scrolls inside the field instead of pushing
                            // the comments section and everything below it off the screen.
                            maxLines = 12,
                            colors = com.mj.yata.ui.widgets.yataFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(notesFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        // Also flips isEditingNotes true here, not just on the
                                        // explicit "tap to edit" path below — this field is also
                                        // shown by default whenever notes are blank (isEditingNotes
                                        // still false in that case). Without this, typing the
                                        // first character flips task.notes from blank to non-blank,
                                        // the outer `isEditingNotes || task.notes.isNullOrBlank()`
                                        // check goes false, and the field is yanked out from under
                                        // the user mid-keystroke and replaced with the read-only
                                        // markdown view.
                                        hasFocusedOnce = true
                                        isEditingNotes = true
                                    } else if (hasFocusedOnce) {
                                        isEditingNotes = false
                                    }
                                },
                            shape = com.mj.yata.ui.widgets.YataFieldShape
                        )
                    } else {
                        MarkdownText(
                            markdown = task.notes ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { isEditingNotes = true }
                                .padding(12.dp)
                        )
                    }
                }
                }
            }

            // 7. Comments card
            item {
                AnimatedVisibility(
                    visible = showComments,
                    enter = expandVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeIn(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)),
                    exit = shrinkVertically(animationSpec = tween(com.mj.yata.ui.theme.YataDur.fade)) + fadeOut(animationSpec = tween(com.mj.yata.ui.theme.YataDur.micro))
                ) {
                var newComment by remember { mutableStateOf("") }
                val peopleById = remember(people) { people.associateBy { it.id } }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.task_detail_comments),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Send lives in the field's trailingIcon slot rather than as a sibling button
                    // outside it. That's the M3 pattern for a field's own submit action, and it
                    // also fixes the alignment: as a sibling, the button centred against a
                    // growing multi-line field and drifted away from the text baseline.
                    val postComment = {
                        if (newComment.isNotBlank()) {
                            viewModel.addComment(task.id, newComment.trim())
                            newComment = ""
                        }
                    }
                    comments.forEach { comment ->
                        val author = comment.authorId?.let { peopleById[it] }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = comment.body,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            author?.let { if (it.isMe) "You" else it.name },
                                            com.mj.yata.util.TaskScheduleUtils.formatDueDate(
                                                java.time.Instant.ofEpochMilli(comment.createdAt)
                                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                                            )
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Compact tonal pill instead of a full 48dp IconButton — the default
                                // touch target was taller than this row's two lines of text, which
                                // padded the card out with visible empty space beneath the content.
                                FilledTonalIconButton(
                                    onClick = { viewModel.deleteComment(comment) },
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(28.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.task_detail_delete_comment),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    TextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        placeholder = { Text(stringResource(R.string.task_detail_add_a_comment)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.mj.yata.ui.widgets.YataFieldShape,
                        colors = com.mj.yata.ui.widgets.yataFieldColors(),
                        maxLines = 6,
                        trailingIcon = {
                            IconButton(onClick = postComment, enabled = newComment.isNotBlank()) {
                                Icon(
                                    Icons.AutoMirrored.Default.Send,
                                    contentDescription = stringResource(R.string.task_detail_post_comment)
                                )
                            }
                        }
                    )
                }
                }
            }
        }
        }
    }

    // Sheets Router
    if (activeSheet != DetailSheetType.None) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = DetailSheetType.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetMaxWidth = rememberAdaptiveSheetMaxWidth()
        ) {
            when (activeSheet) {
                DetailSheetType.ScheduleEditor -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.task_detail_due_date_and_time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LocalScheduleChip("Today", task.due == java.time.LocalDate.now().toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().toString()))
                            }
                            LocalScheduleChip("Tomorrow", task.due == java.time.LocalDate.now().plusDays(1).toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusDays(1).toString()))
                            }
                            LocalScheduleChip("Next week", task.due == java.time.LocalDate.now().plusWeeks(1).toString()) {
                                viewModel.upsertTask(task.copy(due = java.time.LocalDate.now().plusWeeks(1).toString()))
                            }
                            LocalScheduleChip("No due date", task.due == null) {
                                viewModel.upsertTask(task.copy(due = null, time = null, reminder = null))
                                activeSheet = DetailSheetType.None
                            }
                            LocalScheduleChip("Pick date", false) {
                                showDatePicker = true
                            }
                        }

                        // Start date — its own row rather than mixed in with the due-date chips
                        // above, since the two mean opposite ends of the same window and sharing
                        // a row invites setting one when you meant the other.
                        Text(
                            text = stringResource(R.string.task_start_date),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LocalScheduleChip("Tomorrow", task.startDate == java.time.LocalDate.now().plusDays(1).toString()) {
                                viewModel.upsertTask(task.copy(startDate = java.time.LocalDate.now().plusDays(1).toString()))
                            }
                            LocalScheduleChip("Next week", task.startDate == java.time.LocalDate.now().plusWeeks(1).toString()) {
                                viewModel.upsertTask(task.copy(startDate = java.time.LocalDate.now().plusWeeks(1).toString()))
                            }
                            LocalScheduleChip("Next month", task.startDate == java.time.LocalDate.now().plusMonths(1).toString()) {
                                viewModel.upsertTask(task.copy(startDate = java.time.LocalDate.now().plusMonths(1).toString()))
                            }
                            LocalScheduleChip(stringResource(R.string.task_start_date_none), task.startDate == null) {
                                viewModel.upsertTask(task.copy(startDate = null))
                            }
                            LocalScheduleChip("Pick date", false) {
                                showStartDatePicker = true
                            }
                        }
                        LocalPanelHint(stringResource(R.string.task_start_date_hint))

                        if (task.due == null) {
                            LocalPanelHint("Pick a due date to unlock time and reminder options.")
                        } else {
                            Text(
                                text = stringResource(R.string.new_task_time),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // The string written to the task stays canonical 12-hour; only the
                                // chip's label follows the clock preference. Comparing against the
                                // canonical value is what keeps the selected state right in both.
                                listOf("9:00 AM", "12:00 PM", "6:00 PM").forEach { canonical ->
                                    val label = com.mj.yata.util.TaskScheduleUtils.displayTime(canonical) ?: canonical
                                    LocalScheduleChip(label, task.time == canonical) {
                                        viewModel.upsertTask(task.copy(time = canonical))
                                    }
                                }
                                LocalScheduleChip("Custom time", false) { showTimePicker = true }
                                LocalScheduleChip("Clear", task.time == null) { viewModel.upsertTask(task.copy(time = null)) }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { activeSheet = DetailSheetType.None }) {
                                Text(stringResource(R.string.action_done))
                            }
                        }
                    }
                }
                DetailSheetType.ReminderPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.task_detail_reminder), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (task.due == null) {
                            LocalPanelHint("Pick a due date before setting a reminder.")
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LocalScheduleChip("None", task.reminder == null) {
                                    viewModel.upsertTask(task.copy(reminder = null))
                                    activeSheet = DetailSheetType.None
                                }
                                com.mj.yata.util.TaskScheduleUtils.reminderOptions.forEach { option ->
                                    LocalScheduleChip(option, task.reminder == option) {
                                        if (!com.mj.yata.util.TaskScheduleUtils.isPresetReminderInFuture(task.due, task.time, option)) {
                                            scope.launch { snackbarHostState.showError(context.getString(R.string.reminder_in_past_error)) }
                                        } else {
                                            viewModel.upsertTask(task.copy(reminder = option))
                                            activeSheet = DetailSheetType.None
                                        }
                                    }
                                }
                                val customReminderSelected = task.reminder != null &&
                                    com.mj.yata.util.TaskScheduleUtils.parseTime(task.reminder) != null
                                LocalScheduleChip("Custom time", customReminderSelected) {
                                    showReminderTimePicker = true
                                }
                            }
                        }
                    }
                }
                DetailSheetType.RecurrenceBuilder -> RecurrenceSheet(
                    initialRecurrence = task.recurrence,
                    onSave = { rec ->
                        viewModel.upsertTask(task.copy(recurrence = rec))
                        activeSheet = DetailSheetType.None
                    },
                    onDismiss = { activeSheet = DetailSheetType.None }
                )
                DetailSheetType.ListPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.task_detail_select_list), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            YataSelectChip(
                                label = stringResource(R.string.task_detail_none),
                                selected = task.listId == null,
                                onClick = {
                                    viewModel.upsertTask(task.copy(listId = null))
                                    activeSheet = DetailSheetType.None
                                }
                            )
                            lists.forEach { list ->
                                val color = accents.getAccent(list.color)
                                YataSelectChip(
                                    label = list.name,
                                    selected = list.id == task.listId,
                                    onClick = {
                                        viewModel.upsertTask(task.copy(listId = list.id))
                                        activeSheet = DetailSheetType.None
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.ProjectPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.task_detail_select_project), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            YataSelectChip(
                                label = stringResource(R.string.task_detail_none),
                                selected = task.projectId == null,
                                onClick = {
                                    viewModel.upsertTask(task.copy(projectId = null))
                                    activeSheet = DetailSheetType.None
                                }
                            )
                            projects.forEach { pr ->
                                val color = accents.getAccent(pr.color)
                                YataSelectChip(
                                    label = pr.name,
                                    selected = pr.id == task.projectId,
                                    onClick = {
                                        // Matches NewTaskSheet's project-selection behavior — a
                                        // project with its own due date carries it onto the task,
                                        // same as when the project is picked at creation time.
                                        val updated = if (pr.due != null) task.copy(projectId = pr.id, due = pr.due) else task.copy(projectId = pr.id)
                                        viewModel.upsertTask(updated)
                                        activeSheet = DetailSheetType.None
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.AssigneePicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.task_detail_assign_people), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            people.sortedBy { it.name.lowercase() }.forEach { person ->
                                val isAssigned = task.assigneeIds.contains(person.id)
                                YataSelectChip(
                                    label = if (person.isMe) "You" else person.name,
                                    selected = isAssigned,
                                    onClick = {
                                        val newAss = if (isAssigned) task.assigneeIds - person.id else task.assigneeIds + person.id
                                        viewModel.upsertTask(task.copy(assigneeIds = newAss))
                                    },
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    leading = {
                                        com.mj.yata.ui.widgets.PersonAvatar(
                                            initials = person.initials,
                                            accentKey = person.color,
                                            size = 22.dp,
                                            photoUri = person.photoUri
                                        )
                                    },
                                    height = 38.dp
                                )
                            }
                        }
                    }
                }
                DetailSheetType.TagPicker -> {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.action_select_tags), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                                val isSelected = task.tagIds.contains(tag.id)
                                val color = accents.getAccent(tag.color)
                                YataSelectChip(
                                    label = tag.name,
                                    selected = isSelected,
                                    onClick = {
                                        val newTags = if (isSelected) task.tagIds - tag.id else task.tagIds + tag.id
                                        viewModel.upsertTask(task.copy(tagIds = newTags))
                                    },
                                    tint = color,
                                    dotColor = color
                                )
                            }
                        }
                    }
                }
                DetailSheetType.None -> Unit
            }
        }
    }
    if (showDatePicker) {
        YataDatePickerDialog(
            initialDate = task.due,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.upsertTask(task.copy(due = it))
                showDatePicker = false
            }
        )
    }

    if (showStartDatePicker) {
        YataDatePickerDialog(
            initialDate = task.startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = {
                viewModel.upsertTask(task.copy(startDate = it))
                showStartDatePicker = false
            }
        )
    }

    if (showFollowUpPicker) {
        val initialFollowUpDate = task.followUpAt?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        }
        YataDatePickerDialog(
            initialDate = initialFollowUpDate,
            onDismiss = { showFollowUpPicker = false },
            onConfirm = { dateStr ->
                val millis = java.time.LocalDate.parse(dateStr)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                viewModel.setTaskFollowUp(task.id, millis)
                showFollowUpPicker = false
            }
        )
    }

    exportFormatPending?.let { format ->
        val hasNotes = !task.notes.isNullOrBlank()
        val hasComments = comments.isNotEmpty()
        val hasSubtasks = task.subtasks.isNotEmpty()
        val hasScheduleDetails = task.recurrence != null || task.reminder != null
        val exportAccentColor = project?.let { accents.getAccent(it.color) } ?: listColor
        val exportOverdue = task.due != null && !task.done &&
            com.mj.yata.util.TaskScheduleUtils.parseDate(task.due)?.isBefore(java.time.LocalDate.now()) == true
        val exportTagChips = (inheritedTags + ownTags).distinctBy { it.id }.map { tag ->
            com.mj.yata.util.export.ExportTagChip(
                tag.name,
                if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
            )
        }
        val peopleById = remember(people) { people.associateBy { it.id } }
        // The People-tab "me" row is literally named "You" by default (see
        // YataRepositoryImpl.seedInitialDataIfNeeded) — that reads fine on-screen but is
        // meaningless to whoever receives the shared image/PDF. Prefer the Settings > Profile
        // name when it's been filled in, since that's the field actually meant to be your name.
        fun exportNameFor(person: com.mj.yata.domain.model.Person): String =
            if (person.isMe && profileUserName.isNotBlank()) profileUserName else person.name
        val exportComments = remember(comments, people, profileUserName) {
            comments.map { comment ->
                val author = comment.authorId?.let { peopleById[it] }
                com.mj.yata.util.export.ExportCommentRow(
                    authorLabel = author?.let { exportNameFor(it) },
                    timestampLabel = com.mj.yata.util.TaskScheduleUtils.formatDueDate(
                        java.time.Instant.ofEpochMilli(comment.createdAt)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    ),
                    body = comment.body,
                    authorInitials = author?.initials,
                    authorAccentKey = author?.color,
                    authorPhotoUri = author?.photoUri
                )
            }
        }

        val exportSubtasks = remember(task.subtasks) { task.subtasks.toExportSubtaskRows() }
        val exportedBy = remember(people) { people.firstOrNull { it.isMe } }
        val exportDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        fun runExport(options: com.mj.yata.util.export.TaskExportOptions) {
            val transferLink = if (options.includeImportLink) {
                com.mj.yata.util.export.buildTaskTransferLink(
                    title = task.title,
                    tasks = listOf(task),
                    listsById = listsById,
                    projectsById = projectsById,
                    tagsById = tagsById,
                    peopleById = peopleById,
                    includeStructure = !options.privacyMode,
                    includeNotes = !options.privacyMode && options.includeNotes
                )
            } else {
                null
            }

            fun startExport() {
                exportFormatPending = null
                scope.launch {
                exportInProgress = true
                val exportResult = runCatching {
                    com.mj.yata.util.export.exportTaskReport(
                        context = context,
                        format = format,
                        title = task.title,
                        done = task.done,
                        priority = task.priority,
                        flagged = task.flag,
                        dueLabel = task.due?.let { com.mj.yata.util.TaskScheduleUtils.formatDueDateTime(task.due, task.time) },
                        overdue = exportOverdue,
                        completedAtLabel = if (task.done) com.mj.yata.util.TaskScheduleUtils.formatCompletedAt(task.completedAt) else null,
                        projectName = project?.name,
                        listName = taskList?.name,
                        assignees = if (options.privacyMode) {
                            emptyList()
                        } else {
                            taskAssignees.map { person ->
                                com.mj.yata.util.export.ExportPersonChip(
                                    name = exportNameFor(person),
                                    initials = person.initials,
                                    accentKey = person.color,
                                    photoUri = person.photoUri
                                )
                            }
                        },
                        tagChips = if (options.privacyMode) emptyList() else exportTagChips,
                        notes = task.notes,
                        includeNotes = options.includeNotes,
                        comments = exportComments,
                        includeComments = options.includeComments,
                        recurrenceLabel = task.recurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) },
                        reminderLabel = task.reminder?.let { com.mj.yata.util.TaskScheduleUtils.formatReminder(it) },
                        subtasks = exportSubtasks,
                        includeSubtasks = options.includeSubtasks,
                        includeScheduleDetails = options.includeScheduleDetails,
                        sharedByName = if (options.privacyMode) {
                            "You"
                        } else {
                            exportedBy?.let { exportNameFor(it) } ?: profileUserName.ifBlank { "You" }
                        },
                        sharedByInitials = exportedBy?.initials ?: "Y",
                        sharedByAccentKey = exportedBy?.color ?: "accentA",
                        sharedByPhotoUri = if (options.privacyMode) null else exportedBy?.photoUri,
                        darkTheme = options.imageDarkTheme,
                        accentColor = exportAccentColor,
                        showMadeWithFooter = options.showMadeWithFooter,
                        destination = options.destination,
                        fileNameBase = options.fileNameBase,
                        pdfPageSize = options.pdfPageSize,
                        imageScale = options.imageScale,
                        transferText = transferLink?.asShareText(task.title, 1)
                    )
                }
                exportInProgress = false
                exportResult.onSuccess { outcome ->
                    snackbarHostState.showSuccess(outcome.userMessage())
                }.onFailure { error ->
                    snackbarHostState.showError(error.message ?: context.getString(R.string.export_failed))
                }
            }
            }

            if (options.destination == com.mj.yata.util.export.ExportDestination.SHARE) {
                longTaskLinkWarningGate.runOrConfirm(transferLink, taskCount = 1, action = ::startExport)
            } else {
                startExport()
            }
        }

        com.mj.yata.util.export.TaskExportOptionsDialog(
            taskTitle = task.title,
            format = format,
            hasNotes = hasNotes,
            hasComments = hasComments,
            hasSubtasks = hasSubtasks,
            hasScheduleDetails = hasScheduleDetails,
            systemDarkTheme = exportDarkTheme,
            onDismiss = { exportFormatPending = null },
            onConfirm = { options ->
                runExport(options)
            }
        )
    }
    if (exportInProgress) {
        com.mj.yata.util.export.ExportProgressDialog()
    }
    com.mj.yata.util.export.LongTaskLinkWarningDialog(longTaskLinkWarningGate)

    pendingParentCompletionSubtasks?.let { completedSubtasks ->
        AlertDialog(
            onDismissRequest = { pendingParentCompletionSubtasks = null },
            title = { Text(stringResource(R.string.task_detail_all_subtasks_done_title)) },
            text = { Text(stringResource(R.string.task_detail_all_subtasks_done_body, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingParentCompletionSubtasks = null
                        viewModel.completeTaskAfterSavingSubtasks(task, completedSubtasks) {}
                    }
                ) {
                    Text(stringResource(R.string.task_detail_mark_task_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingParentCompletionSubtasks = null }) {
                    Text(stringResource(R.string.action_not_now))
                }
            }
        )
    }

    YataTimePickerLauncher(
        show = showTimePicker,
        initialTime = task.time,
        onDismiss = { showTimePicker = false },
        onConfirm = {
            viewModel.upsertTask(task.copy(time = it))
            showTimePicker = false
        }
    )

    YataTimePickerLauncher(
        show = showReminderTimePicker,
        initialTime = task.reminder,
        onDismiss = { showReminderTimePicker = false },
        onConfirm = {
            showReminderTimePicker = false
            when {
                !com.mj.yata.util.TaskScheduleUtils.isCustomReminderBeforeDue(it, task.time) -> {
                    scope.launch { snackbarHostState.showError(context.getString(R.string.reminder_before_due_error)) }
                }
                !com.mj.yata.util.TaskScheduleUtils.isReminderTimeInFuture(task.due, it) -> {
                    scope.launch { snackbarHostState.showError(context.getString(R.string.reminder_in_past_error)) }
                }
                else -> {
                    viewModel.upsertTask(task.copy(reminder = it))
                    activeSheet = DetailSheetType.None
                }
            }
        }
    )
}

@Composable
private fun LocalScheduleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun LocalPanelHint(text: String) {
    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Handoff's MetaRow: own surfaceContainerLow card per row, 32dp icon tile, uppercase label over value. */
@Composable
fun MetaRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    swatchColor: Color? = null,
    rightContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (swatchColor != null) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(swatchColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        color = accentColor ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (rightContent != null) {
                rightContent()
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
