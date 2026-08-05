package com.mj.yata.ui.screen.person

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.TaskRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonDetailScreen(
    viewModel: MainViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val assignedTasks by remember(personId) { viewModel.getTasksForPerson(personId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val allTasks by viewModel.tasks.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val personGroups by viewModel.personGroups.collectAsStateWithLifecycle()
    val taskRowDensity by viewModel.taskRowDensity.collectAsStateWithLifecycle()

    val person = remember(people, personId) { people.find { it.id == personId } }
    val accents = LocalYataAccents.current

    var openExpanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(true) }
    var isEditSheetOpen by remember { mutableStateOf(false) }
    var isNewTaskSheetOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hideCompleted by viewModel.hideCompletedPerson.collectAsStateWithLifecycle()
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
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val listsById = remember(lists) { lists.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }

    // Project name takes priority over list name for the export's subheading — a task's
    // projectId/listId are mutually exclusive containers in this app, so this just picks
    // whichever one the task actually has.
    fun exportGroupLabel(task: Task): String =
        projectsById[task.projectId]?.name?.let { "Project - $it" }
            ?: listsById[task.listId]?.name?.let { "List - $it" }
            ?: ""

    val exportTagErrorColor = MaterialTheme.colorScheme.error
    fun exportTagChips(task: Task): List<com.mj.yata.util.export.ExportTagChip> =
        task.effectiveTagIds(projectsById).mapNotNull { tagId ->
            tagsById[tagId]?.let { t ->
                val color = if (t.color == "error") exportTagErrorColor else accents.getAccent(t.color)
                com.mj.yata.util.export.ExportTagChip(t.name, color)
            }
        }

    fun exportAssigneeNames(task: Task): List<String> =
        task.assigneeIds.mapNotNull { id -> peopleById[id]?.name }

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "Task deleted", undoWindowSeconds)
            if (!result) {
                viewModel.deleteTask(task)
            }
        }
    }

    val openChevronRotation by animateFloatAsState(
        targetValue = if (openExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "openChevronRotation"
    )
    val completedChevronRotation by animateFloatAsState(
        targetValue = if (completedExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "completedChevronRotation"
    )

    val showMissingPerson = com.mj.yata.ui.widgets.rememberMissingContentVisible(personId, person == null)
    if (person == null) {
        if (showMissingPerson) {
            com.mj.yata.ui.widgets.MissingContentState(
                itemName = stringResource(R.string.entity_person),
                onNavigateBack = onNavigateBack
            )
        } else {
            com.mj.yata.ui.widgets.ListDetailShimmer()
        }
        return
    }

    val personColor = accents.getAccent(person.color)
    com.mj.yata.ui.theme.StatusBarColor(
        personColor.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.background)
    )
    val sortMode by viewModel.sortModePerson.collectAsStateWithLifecycle()
    var activeStatFilter by remember { mutableStateOf<com.mj.yata.ui.widgets.HeroStatKind?>(null) }
    val today = com.mj.yata.util.AppClock.today
    // Unfiltered-by-stat count, used by the hero's own primary text/progress so those always
    // reflect the true totals — only the rendered list below is narrowed by activeStatFilter.
    val openTasks = remember(assignedTasks, searchQuery, sortMode) {
        assignedTasks.filter { !it.done && taskMatchesQuery(it, searchQuery) }.sortedByMode(sortMode)
    }
    val displayedOpenTasks = remember(openTasks, activeStatFilter) {
        openTasks.filter { activeStatFilter == null || activeStatFilter!!.matches(it, today) }
    }
    val completedTasks = remember(assignedTasks, searchQuery) { assignedTasks.filter { it.done && taskMatchesQuery(it, searchQuery) } }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()

    val selectedIds = remember { mutableStateListOf<String>() }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkTagSheet by remember { mutableStateOf(false) }
    var showBulkMoveSheet by remember { mutableStateOf(false) }
    var showBulkAssignSheet by remember { mutableStateOf(false) }
    var showBulkRescheduleSheet by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
        },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 2,
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
            if (selectionMode) {
                TaskSelectionTopBar(
                    selectedCount = selectedIds.size,
                    onCancel = { selectedIds.clear() },
                    onComplete = { viewModel.bulkCompleteTasks(selectedIds.toList()); selectedIds.clear() },
                    onAddTag = { showBulkTagSheet = true },
                    onMove = { showBulkMoveSheet = true },
                    onReschedule = { showBulkRescheduleSheet = true },
                    onDuplicate = { viewModel.bulkDuplicateTasks(selectedIds.toList()); selectedIds.clear() },
                    onDelete = { showBulkDeleteDialog = true },
                    onAssign = { showBulkAssignSheet = true },
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled,
                    modifier = Modifier.statusBarsPadding()
                )
            } else {
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
                            placeholder = { Text(stringResource(R.string.search_person_tasks, person.name)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            person.name,
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
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.person_detail_search_this_person_s_tasks))
                        }
                        com.mj.yata.ui.widgets.TaskSortMenuButton(
                            current = sortMode,
                            onSelect = { viewModel.setSortModePerson(it) },
                            filledContainer = true
                        )
                        com.mj.yata.ui.widgets.YataTopBarIconToggleButton(
                            checked = hideCompleted,
                            onCheckedChange = { viewModel.setHideCompletedPerson(it) }
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
                                text = { Text(stringResource(R.string.person_detail_edit_person)) },
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
                                    com.mj.yata.util.shareTasksAsMarkdown(exportContext, person.name, assignedTasks)
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
                            if (!person.isMe) {
                                DropdownMenuItem(
                                    text = { Text(if (person.archived) "Unarchive person" else "Archive person") },
                                    onClick = {
                                        showMenu = false
                                        if (person.archived) {
                                            viewModel.setPersonArchived(person, false)
                                        } else {
                                            showDeleteDialog = true
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                        }
                    }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = personColor.copy(alpha = 0.16f)
                )
            )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { isEditSheetOpen = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.person_detail_edit_person))
                }
                FloatingActionButton(
                    onClick = { isNewTaskSheetOpen = true },
                    containerColor = personColor,
                    contentColor = accents.onAccentFor(personColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_task_for_person, person.name))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Hero section — bigger avatar + progress ring, plus overdue/high-priority/due-today
            // stats in addition to the open/completed count, all derived from assignedTasks (not
            // search-filtered, unlike openTasks/completedTasks below) so these always reflect the
            // person's real workload regardless of an active search query.
            item {
                val overdueCount = remember(assignedTasks) {
                    com.mj.yata.util.AnalyticsUtils.overdueCount(assignedTasks)
                }
                val highPriorityCount = remember(assignedTasks) {
                    assignedTasks.count { !it.done && it.priority == "high" }
                }
                val todayStr = com.mj.yata.util.AppClock.todayString
                val dueTodayCount = remember(assignedTasks, todayStr) {
                    assignedTasks.count { !it.done && it.due == todayStr }
                }
                val totalCount = assignedTasks.size
                val doneCount = assignedTasks.count { it.done }
                val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

                com.mj.yata.ui.widgets.EntityHeroSection(
                    accentColor = personColor,
                    progress = progress,
                    primaryText = "${openTasks.size} open · ${completedTasks.size} completed",
                    overdueCount = overdueCount,
                    highPriorityCount = highPriorityCount,
                    dueTodayCount = dueTodayCount,
                    leadingContent = {
                        PersonAvatar(
                            initials = person.initials,
                            accentKey = person.color,
                            photoUri = person.photoUri,
                            size = 72.dp
                        )
                    },
                    activeFilter = activeStatFilter,
                    onStatClick = { activeStatFilter = if (activeStatFilter == it) null else it }
                )
            }

            // 1.5 Workload trend — overdue count as it stood at the end of each of the last 7
            // days, so a manager can see whether this person's backlog is growing or shrinking,
            // not just the live snapshot above.
            item {
                val trend = remember(assignedTasks) {
                    val today = java.time.LocalDate.now()
                    (6 downTo 0).map { offset ->
                        val day = today.minusDays(offset.toLong())
                        val overdueCount = assignedTasks.count { task ->
                            val due = task.due?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@count false
                            val completedDay = task.completedAt?.let {
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }
                            due.isBefore(day) && (completedDay == null || completedDay.isAfter(day))
                        }
                        day to overdueCount
                    }
                }
                val maxOverdue = (trend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "OVERDUE TREND (7 DAYS)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trend.forEach { (day, count) ->
                            val heightFraction = count.toFloat() / maxOverdue
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(if (count == 0) 0.06f else heightFraction.coerceAtLeast(0.06f))
                                    .background(
                                        if (count > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // 2. Open Tasks Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openExpanded = !openExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(
                                text = displayedOpenTasks.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.person_detail_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(openChevronRotation)
                    )
                }
            }

            if (activeStatFilter != null) {
                item {
                    com.mj.yata.ui.widgets.ActiveFilterBanner(
                        kind = activeStatFilter!!,
                        onClear = { activeStatFilter = null }
                    )
                }
            }

            if (openExpanded) {
                if (displayedOpenTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No open tasks assigned.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(displayedOpenTasks, key = { "open_" + it.id }, contentType = { "task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById) {
                            task.assigneeIds.mapNotNull { pid -> peopleById[pid] }
                        }
                        val taskTags = remember(task, projectsById, tagsById, tagsFeatureEnabled) {
                            if (tagsFeatureEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
                        }

                        TaskRow(
                            task = task,
                            list = taskList,
                            assignees = taskAssignees,
                            tags = taskTags,
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onTaskClick = {
                                if (selectionMode) {
                                    if (selectedIds.contains(task.id)) selectedIds.remove(task.id) else selectedIds.add(task.id)
                                } else {
                                    onNavigateToTaskDetail(task.id)
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(task.id),
                            onLongClick = { if (!selectedIds.contains(task.id)) selectedIds.add(task.id) },
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                            ),
                            onCommentClick = { pendingCommentTask = task },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRenameTask = { viewModel.renameTask(task.id, it) },
                            density = taskRowDensity,
                            onSwipeToDelete = { if (!selectionMode) deleteTaskWithUndo(task) },
                            swipeEnabled = !selectionMode,
                            showDueDate = true
                        )
                    }
                }
            }

            if (!hideCompleted) {
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            // 3. Completed Tasks Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { completedExpanded = !completedExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Completed Tasks",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(9999.dp)
                        ) {
                            Text(
                                text = completedTasks.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.person_detail_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(completedChevronRotation)
                    )
                }
            }

            if (completedExpanded) {
                if (completedTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No completed tasks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(completedTasks, key = { "completed_" + it.id }, contentType = { "task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById) {
                            task.assigneeIds.mapNotNull { pid -> peopleById[pid] }
                        }
                        val taskTags = remember(task, projectsById, tagsById, tagsFeatureEnabled) {
                            if (tagsFeatureEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
                        }

                        TaskRow(
                            task = task,
                            list = taskList,
                            assignees = taskAssignees,
                            tags = taskTags,
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onTaskClick = {
                                if (selectionMode) {
                                    if (selectedIds.contains(task.id)) selectedIds.remove(task.id) else selectedIds.add(task.id)
                                } else {
                                    onNavigateToTaskDetail(task.id)
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(task.id),
                            onLongClick = { if (!selectedIds.contains(task.id)) selectedIds.add(task.id) },
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                            ),
                            onCommentClick = { pendingCommentTask = task },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRenameTask = { viewModel.renameTask(task.id, it) },
                            density = taskRowDensity,
                            onSwipeToDelete = { if (!selectionMode) deleteTaskWithUndo(task) },
                            swipeEnabled = !selectionMode,
                            showDueDate = true
                        )
                    }
                }
            }
            } // !hideCompleted
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
                tasks = allTasks,
                initialAssigneeId = person.id,
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
            PersonEditorSheet(
                initialName = person.name,
                initialColor = person.color,
                initialGroupId = person.groupId,
                initialPhotoUri = person.photoUri,
                groups = personGroups,
                onSave = { newName, newColor, newGroupId, newPhotoUri ->
                    val initials = newName.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                    val updatedInitials = if (initials.isEmpty()) "P" else initials
                    viewModel.upsertPerson(person.copy(name = newName, color = newColor, initials = updatedInitials, groupId = newGroupId, photoUri = newPhotoUri))
                    isEditSheetOpen = false
                },
                onCreateGroup = { id, name, color ->
                    viewModel.upsertPersonGroup(com.mj.yata.domain.model.PersonGroup(id = id, name = name, color = color))
                },
                onDismiss = { isEditSheetOpen = false }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.person_detail_archive_person)) },
            text = { Text(stringResource(R.string.person_detail_their_past_assigned_tasks_and_stats_stay_t)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.setPersonArchived(person, true)
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.archive_title), color = MaterialTheme.colorScheme.error)
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

    if (showBulkTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TaskBulkTagPickerSheet(
                tags = tags,
                onSelectTag = { tagId ->
                    viewModel.bulkAddTag(selectedIds.toList(), tagId)
                    selectedIds.clear()
                    showBulkTagSheet = false
                },
                onDismiss = { showBulkTagSheet = false }
            )
        }
    }

    if (showBulkAssignSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkAssignSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TaskBulkAssignPersonSheet(
                people = people,
                tasks = allTasks,
                todayStr = com.mj.yata.util.AppClock.todayString,
                onSelectPerson = { targetPersonId ->
                    viewModel.bulkAssignPerson(selectedIds.toList(), targetPersonId)
                    selectedIds.clear()
                    showBulkAssignSheet = false
                },
                onDismiss = { showBulkAssignSheet = false }
            )
        }
    }

    if (showBulkMoveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkMoveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TaskBulkMoveSheet(
                projects = projects,
                lists = lists,
                onSelectProject = { targetProjectId ->
                    viewModel.bulkSetProject(selectedIds.toList(), targetProjectId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onSelectList = { targetListId ->
                    viewModel.bulkSetList(selectedIds.toList(), targetListId)
                    selectedIds.clear()
                    showBulkMoveSheet = false
                },
                onDismiss = { showBulkMoveSheet = false },
                projectsEnabled = projectsFeatureEnabled
            )
        }
    }

    if (showBulkRescheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBulkRescheduleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            TaskBulkRescheduleSheet(
                onSelectPreset = { preset ->
                    viewModel.bulkRescheduleTasks(selectedIds.toList(), preset)
                    selectedIds.clear()
                    showBulkRescheduleSheet = false
                },
                onDismiss = { showBulkRescheduleSheet = false }
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.confirm_delete_tasks_title, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.action_this_can_t_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bulkDeleteTasks(selectedIds.toList())
                    selectedIds.clear()
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    exportFormatPending?.let { format ->
        com.mj.yata.util.export.ExportOptionsDialog(
            entityName = person.name,
            format = format,
            itemPreviews = assignedTasks.map { com.mj.yata.util.export.ExportItemPreview(it.done, it.completedAt) },
            onDismiss = { exportFormatPending = null },
            onConfirm = { options ->
                exportFormatPending = null
                val cutoffMillis = options.excludeCompletedOlderThanDays?.takeIf { it > 0 }?.let {
                    System.currentTimeMillis() - it.toLong() * 24 * 60 * 60 * 1000
                }
                val exportTasks = assignedTasks.filter { task ->
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
                            entityKind = "Person",
                            entityName = person.name,
                            accentColor = personColor,
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
