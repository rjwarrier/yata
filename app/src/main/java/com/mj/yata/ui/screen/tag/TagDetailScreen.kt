package com.mj.yata.ui.screen.tag

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.ui.widgets.showSuccess
import com.mj.yata.ui.widgets.showError
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.TaskSectionHeader
import com.mj.yata.ui.sheets.*
import com.mj.yata.util.taskMatchesQuery
import com.mj.yata.util.sortedByMode
import com.mj.yata.util.export.toExportRow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.theme.YataEase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagDetailScreen(
    viewModel: MainViewModel,
    tagId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tagGroups by viewModel.tagGroups.collectAsStateWithLifecycle()
    val taskRowDensity by viewModel.taskRowDensity.collectAsStateWithLifecycle()

    val tag = remember(tags, tagId) { tags.find { it.id == tagId } }
    val accents = LocalYataAccents.current

    var isEditSheetOpen by remember { mutableStateOf(false) }
    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingCommentTask by remember { mutableStateOf<Task?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val defaultDueDate by viewModel.defaultDueDate.collectAsStateWithLifecycle()
    val defaultPriority by viewModel.defaultPriority.collectAsStateWithLifecycle()
    val exportContext = androidx.compose.ui.platform.LocalContext.current
    var exportFormatPending by remember { mutableStateOf<com.mj.yata.util.export.ExportFormat?>(null) }
    var exportInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Project name takes priority over list name for the export's subheading — a task's
    // projectId/listId are mutually exclusive containers in this app, so this just picks
    // whichever one the task actually has.
    fun exportGroupLabel(task: Task): String =
        projects.find { it.id == task.projectId }?.name?.let { "Project - $it" }
            ?: lists.find { it.id == task.listId }?.name?.let { "List - $it" }
            ?: ""

    val exportTagErrorColor = MaterialTheme.colorScheme.error
    fun exportTagChips(task: Task): List<com.mj.yata.util.export.ExportTagChip> =
        task.effectiveTagIds(projects).mapNotNull { tagId ->
            tags.find { it.id == tagId }?.let { t ->
                val color = if (t.color == "error") exportTagErrorColor else accents.getAccent(t.color)
                com.mj.yata.util.export.ExportTagChip(t.name, color)
            }
        }

    fun exportAssigneeNames(task: Task): List<String> =
        task.assigneeIds.mapNotNull { id -> people.find { it.id == id }?.name }

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "Task deleted", undoWindowSeconds)
            if (!result) {
                viewModel.deleteTask(task)
            }
        }
    }

    val showMissingTag = com.mj.yata.ui.widgets.rememberMissingContentVisible(tagId, tag == null)
    if (tag == null) {
        if (showMissingTag) {
            com.mj.yata.ui.widgets.MissingContentState(
                itemName = stringResource(R.string.entity_tag),
                onNavigateBack = onNavigateBack
            )
        } else {
            com.mj.yata.ui.widgets.ListDetailShimmer()
        }
        return
    }

    val tagColor = if (tag.color == "error") {
        MaterialTheme.colorScheme.error
    } else {
        accents.getAccent(tag.color)
    }
    com.mj.yata.ui.theme.StatusBarColor(
        tagColor.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.background)
    )

    val allTaggedTasks = remember(tasks, lists, projects, tag.id) {
        tasks.filter { it.effectiveTagIds(projects).contains(tag.id) }.sortedBy { it.sortOrder }
    }
    val doneTasks = allTaggedTasks.count { it.done }
    val openTasks = allTaggedTasks.size - doneTasks
    val progress = if (allTaggedTasks.isNotEmpty()) doneTasks.toFloat() / allTaggedTasks.size else 0f
    val overdueCount = remember(allTaggedTasks) { com.mj.yata.util.AnalyticsUtils.overdueCount(allTaggedTasks) }
    val highPriorityCount = remember(allTaggedTasks) { allTaggedTasks.count { !it.done && it.priority == "high" } }
    val todayStr = com.mj.yata.util.AppClock.todayString
    val dueTodayCount = remember(allTaggedTasks, todayStr) { allTaggedTasks.count { !it.done && it.due == todayStr } }

    var hideCompleted by remember(tag.id) { mutableStateOf(tag.hideCompletedByDefault) }
    val sortMode by viewModel.sortModeTagDetail.collectAsStateWithLifecycle()
    var activeStatFilter by remember { mutableStateOf<com.mj.yata.ui.widgets.HeroStatKind?>(null) }
    val heroToday = com.mj.yata.util.AppClock.today
    val pendingTaggedTasks = remember(allTaggedTasks, searchQuery, sortMode) {
        allTaggedTasks.filter { !it.done && taskMatchesQuery(it, searchQuery) }.sortedByMode(sortMode)
    }
    val displayedPendingTaggedTasks = remember(pendingTaggedTasks, activeStatFilter, heroToday) {
        pendingTaggedTasks.filter { activeStatFilter == null || activeStatFilter!!.matches(it, heroToday) }
    }
    val completedTaggedTasks = remember(allTaggedTasks, hideCompleted, searchQuery) {
        if (hideCompleted) emptyList() else allTaggedTasks.filter { it.done && taskMatchesQuery(it, searchQuery) }
    }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
        },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 3,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        floatingActionButton = {
            com.mj.yata.ui.widgets.PressableScaleBox(
                onClick = { isNewTaskSheetOpen = true }
            ) {
                Surface(
                    color = tagColor,
                    contentColor = accents.onAccentFor(tagColor),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_task_tagged, tag.name))
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_in_tag, tag.name)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            "#" + tag.name,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                            )
                        )
                    }
                },
                navigationIcon = {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            searchQuery = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = if (searchActive) "Close search" else "Back")
                    }
                },
                actions = {
                    // Wrapped so the circular containers get the same 8dp gap they have on the
                    // main tabs — the actions slot packs its children flush, which reads as one
                    // long pill once the buttons are filled rather than plain.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                    if (searchActive) {
                        if (searchQuery.isNotEmpty()) {
                            com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    } else {
                        com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tag_detail_search_in_tag))
                        }
                        com.mj.yata.ui.widgets.TaskSortMenuButton(
                            current = sortMode,
                            onSelect = { viewModel.setSortModeTagDetail(it) },
                            filledContainer = true
                        )
                        com.mj.yata.ui.widgets.YataTopBarIconToggleButton(
                            checked = hideCompleted,
                            onCheckedChange = { checked ->
                                hideCompleted = checked
                                viewModel.upsertTag(tag.copy(hideCompletedByDefault = checked))
                            }
                        ) {
                            Icon(
                                imageVector = if (hideCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (hideCompleted) "Show completed tasks" else "Hide completed tasks"
                            )
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                        com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tag_detail_edit_tag)) },
                                onClick = {
                                    showMenu = false
                                    isEditSheetOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_as_markdown)) },
                                onClick = {
                                    showMenu = false
                                    com.mj.yata.util.shareTasksAsMarkdown(exportContext, tag.name, allTaggedTasks)
                                },
                                leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_as_image)) },
                                onClick = {
                                    showMenu = false
                                    exportFormatPending = com.mj.yata.util.export.ExportFormat.IMAGE
                                },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_as_pdf)) },
                                onClick = {
                                    showMenu = false
                                    exportFormatPending = com.mj.yata.util.export.ExportFormat.PDF
                                },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tag_detail_delete_tag)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                        }
                    }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tagColor.copy(alpha = 0.16f)
                )
            )
        }
    ) { innerPadding ->
        val listsById = remember(lists) { lists.associateBy { it.id } }
        val peopleById = remember(people) { people.associateBy { it.id } }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Hero section — icon tile, progress ring, and overdue/high-priority/due-today
            // stats, matching Person/Project/List's hero (this screen previously had neither).
            item {
                com.mj.yata.ui.widgets.EntityHeroSection(
                    accentColor = tagColor,
                    progress = progress,
                    primaryText = "$openTasks open · $doneTasks completed",
                    overdueCount = overdueCount,
                    highPriorityCount = highPriorityCount,
                    dueTodayCount = dueTodayCount,
                    leadingContent = {
                        com.mj.yata.ui.widgets.TagMonogram(
                            name = tag.name,
                            tagColor = tagColor,
                            size = 44.dp,
                            shape = RoundedCornerShape(14.dp)
                        )
                    },
                    activeFilter = activeStatFilter,
                    onStatClick = { activeStatFilter = if (activeStatFilter == it) null else it }
                )
            }

            if (activeStatFilter != null) {
                item {
                    com.mj.yata.ui.widgets.ActiveFilterBanner(
                        kind = activeStatFilter!!,
                        onClear = { activeStatFilter = null }
                    )
                }
            }

            // 2. Tasks list — split into Pending/Completed; headers vanish while hiding completed.
            if (pendingTaggedTasks.isEmpty() && completedTaggedTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching tasks." else "No tasks carrying this tag.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                @Composable
                fun taskRowFor(task: Task, modifier: Modifier = Modifier) {
                    val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                    val taskAssignees = remember(task.assigneeIds, peopleById, peopleFeatureEnabled) {
                        if (peopleFeatureEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                    }
                    val taskTags = remember(task, projects, tags) { task.effectiveTags(projects, tags) }

                    TaskRow(
                        task = task,
                        list = taskList,
                        assignees = taskAssignees,
                        tags = taskTags,
                        onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                        onTaskClick = { onNavigateToTaskDetail(task.id) },
                        modifier = modifier,
                        onCommentClick = { pendingCommentTask = task },
                        onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                        onRenameTask = { viewModel.renameTask(task.id, it) },
                        density = taskRowDensity,
                        onSwipeToDelete = { deleteTaskWithUndo(task) },
                        showDueDate = true
                    )
                }

                if (!hideCompleted && displayedPendingTaggedTasks.isNotEmpty()) {
                    item(key = "pending_header") { TaskSectionHeader("PENDING", displayedPendingTaggedTasks.size) }
                }
                if (activeStatFilter != null && displayedPendingTaggedTasks.isEmpty()) {
                    item(key = "no_filter_match") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tasks match this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                items(displayedPendingTaggedTasks, key = { "pending_" + it.id }, contentType = { "task" }) { task ->
                    taskRowFor(
                        task = task,
                        modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                        )
                    )
                }
                if (!hideCompleted && completedTaggedTasks.isNotEmpty()) {
                    item(key = "completed_header") { TaskSectionHeader("COMPLETED", completedTaggedTasks.size) }
                    items(completedTaggedTasks, key = { "completed_" + it.id }, contentType = { "task" }) { task ->
                        taskRowFor(
                            task = task,
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                            )
                        )
                    }
                }
            }
        }
    }

    if (isNewTaskSheetOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isNewTaskSheetOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            NewTaskSheet(
                lists = lists,
                projects = projects,
                people = people,
                tags = tags,
                tasks = tasks,
                initialTagId = tag.id,
                onAddTask = { draft ->
                    viewModel.addTask(draft)
                    isNewTaskSheetOpen = false
                },
                onGoToExistingTask = { id ->
                    isNewTaskSheetOpen = false
                    onNavigateToTaskDetail(id)
                },
                autoAssignToMe = autoAssignToMe,
                onCreateTag = { id, name, color ->
                    viewModel.upsertTag(Tag(id = id, name = name, color = color))
                },
                onCreatePerson = { id, name, color ->
                    viewModel.upsertPerson(Person(id = id, name = name, initials = initialsFor(name), color = color, isMe = false))
                },
                onDismiss = { isNewTaskSheetOpen = false },
                projectsEnabled = projectsFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                peopleEnabled = peopleFeatureEnabled,
                defaultDueDate = defaultDueDate,
                defaultPriority = defaultPriority
            )
        }
    }

    if (isEditSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isEditSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TagEditorSheet(
                initialName = tag.name,
                initialColor = tag.color,
                initialGroupId = tag.groupId,
                initialHideCompletedByDefault = tag.hideCompletedByDefault,
                groups = tagGroups,
                onSave = { newName, newColor, newGroupId, newHideCompletedByDefault ->
                    viewModel.upsertTag(tag.copy(name = newName.lowercase().trim(), color = newColor, groupId = newGroupId, hideCompletedByDefault = newHideCompletedByDefault))
                    isEditSheetOpen = false
                },
                onCreateGroup = { id, name, color ->
                    viewModel.upsertTagGroup(com.mj.yata.domain.model.TagGroup(id = id, name = name, color = color))
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.tag_detail_delete_tag_2)) },
            text = { Text(stringResource(R.string.tag_detail_this_tag_will_be_removed_from_all_tasks)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTag(tag)
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingCommentTask?.let { task ->
        com.mj.yata.ui.widgets.QuickCommentDialog(
            taskTitle = task.title,
            onSubmit = { body ->
                viewModel.addComment(task.id, body)
                pendingCommentTask = null
            },
            onDismiss = { pendingCommentTask = null }
        )
    }

    exportFormatPending?.let { format ->
        com.mj.yata.util.export.ExportOptionsDialog(
            entityName = tag.name,
            format = format,
            itemPreviews = allTaggedTasks.map { com.mj.yata.util.export.ExportItemPreview(it.done, it.completedAt) },
            onDismiss = { exportFormatPending = null },
            onConfirm = { options ->
                exportFormatPending = null
                val cutoffMillis = options.excludeCompletedOlderThanDays?.takeIf { it > 0 }?.let {
                    System.currentTimeMillis() - it.toLong() * 24 * 60 * 60 * 1000
                }
                val exportTasks = allTaggedTasks.filter { task ->
                    if (!task.done) return@filter true
                    if (!options.includeCompleted) return@filter false
                    cutoffMillis == null || (task.completedAt != null && task.completedAt >= cutoffMillis)
                }
                scope.launch {
                    exportInProgress = true
                    val exportResult = runCatching {
                        com.mj.yata.util.export.exportEntityReport(
                            context = exportContext,
                            format = format,
                            entityKind = "Tag",
                            entityName = tag.name,
                            accentColor = tagColor,
                            doneCount = exportTasks.count { it.done },
                            totalCount = exportTasks.size,
                            overdueCount = com.mj.yata.util.AnalyticsUtils.overdueCount(exportTasks),
                            tasks = exportTasks.map { task ->
                                task.toExportRow(
                                    exportGroupLabel(task),
                                    if (options.showTags) exportTagChips(task) else emptyList(),
                                    if (options.showAssignees) exportAssigneeNames(task) else emptyList()
                                )
                            },
                            layoutDensity = options.density,
                            strikeThroughCompleted = options.strikeThroughCompleted,
                            showTags = options.showTags,
                            showAssignees = options.showAssignees,
                            showMadeWithFooter = options.showMadeWithFooter,
                            destination = options.destination,
                            fileNameBase = options.fileNameBase,
                            pdfPageSize = options.pdfPageSize,
                            imageScale = options.imageScale
                        )
                    }
                    exportInProgress = false
                    exportResult.onSuccess { outcome ->
                        snackbarHostState.showSuccess(outcome.userMessage())
                    }.onFailure { error ->
                        snackbarHostState.showError(error.message ?: exportContext.getString(R.string.export_failed))
                    }
                }
            }
        )
    }
    if (exportInProgress) {
        com.mj.yata.util.export.ExportProgressDialog()
    }
}
