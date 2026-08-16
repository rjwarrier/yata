package com.mj.yata.ui.screen.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.screen.main.AdaptiveBottomNav
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.sheets.TaskMoveToPickerSheet
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.widgets.LocalUndoWindowSeconds
import com.mj.yata.ui.widgets.TabEmptyState
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.YataSelectChip
import com.mj.yata.ui.widgets.YataSnackbar
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.util.AppClock
import kotlinx.coroutines.launch

private const val LIGHTWEIGHT_SCREEN_ANIMATION_ROW_LIMIT = 80

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.inboxUiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = LocalUndoWindowSeconds.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var moveTask by remember { mutableStateOf<Task?>(null) }
    val animateListChanges = uiState.rows.size <= LIGHTWEIGHT_SCREEN_ANIMATION_ROW_LIMIT

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, context.getString(R.string.task_deleted), undoWindowSeconds)
            if (!result) viewModel.deleteTask(task)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> YataSnackbar(data) } },
        bottomBar = {
            AdaptiveBottomNav(
                selectedTab = -1,
                todayBadgeCount = uiState.todayRemainingCount,
                peopleEnabled = uiState.peopleFeatureEnabled,
                tagsEnabled = uiState.tagsFeatureEnabled,
                projectsEnabled = uiState.projectsFeatureEnabled,
                todayEnabled = uiState.todayTabEnabled,
                upcomingEnabled = uiState.upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.inbox_title),
                        style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSynthesis = FontSynthesis.All)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
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
            if (uiState.rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TabEmptyState(
                        icon = Icons.Default.Inbox,
                        title = stringResource(R.string.inbox_empty_title),
                        subtitle = stringResource(R.string.inbox_empty_body),
                        actionLabel = stringResource(R.string.inbox_empty_action),
                        onAction = { onNavigateToTab(0) }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "inbox_summary") {
                        InboxSummaryCard(
                            taskCount = uiState.rows.size,
                            missingDueCount = uiState.missingDueCount,
                            missingEstimateCount = uiState.missingEstimateCount,
                            missingHomeCount = uiState.missingHomeCount,
                            missingOwnerCount = uiState.missingOwnerCount,
                            projectsEnabled = uiState.projectsFeatureEnabled,
                            peopleEnabled = uiState.peopleFeatureEnabled,
                            animateSize = animateListChanges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                                .let {
                                    if (animateListChanges) {
                                        it.animateItem(
                                            fadeInSpec = yataItemFade,
                                            placementSpec = yataItemPlacement,
                                            fadeOutSpec = yataItemFade
                                        )
                                    } else {
                                        it
                                    }
                                }
                        )
                    }
                    items(uiState.rows, key = { it.task.id }, contentType = { "inbox_task" }) { row ->
                        val task = row.task
                        InboxTaskCard(
                            task = task,
                            list = row.list,
                            assignees = row.assignees,
                            tags = row.tags,
                            projectsEnabled = uiState.projectsFeatureEnabled,
                            peopleEnabled = uiState.peopleFeatureEnabled,
                            myPerson = uiState.myPerson,
                            onTaskClick = { onNavigateToTaskDetail(task.id) },
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onDelete = { deleteTaskWithUndo(task) },
                            onSetDue = { due -> viewModel.upsertTask(task.copy(due = due)) },
                            onSetEstimate = { minutes -> viewModel.setTaskEstimate(task.id, minutes) },
                            onAssignMe = { person -> viewModel.upsertTask(task.copy(assigneeIds = listOf(person.id) + task.assigneeIds.filterNot { it == person.id })) },
                            onMove = { moveTask = task },
                            density = uiState.taskRowDensity,
                            animateSize = animateListChanges,
                            animateDetails = animateListChanges,
                            modifier = Modifier.let {
                                if (animateListChanges) {
                                    it.animateItem(
                                        fadeInSpec = yataItemFade,
                                        placementSpec = yataItemPlacement,
                                        fadeOutSpec = yataItemFade
                                    )
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    moveTask?.let { task ->
        ModalBottomSheet(
            onDismissRequest = { moveTask = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TaskMoveToPickerSheet(
                lists = uiState.lists,
                projects = uiState.projects,
                onSelectList = {
                    viewModel.moveTaskToList(task.id, it)
                    moveTask = null
                },
                onSelectProject = {
                    viewModel.moveTaskToList(task.id, targetListId = null, targetProjectId = it)
                    moveTask = null
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxSummaryCard(
    taskCount: Int,
    missingDueCount: Int,
    missingEstimateCount: Int,
    missingHomeCount: Int,
    missingOwnerCount: Int,
    projectsEnabled: Boolean,
    peopleEnabled: Boolean,
    animateSize: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.let {
            if (animateSize) {
                it.animateContentSize(
                    animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                )
            } else {
                it
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = pluralStringResource(R.plurals.task_count_lower, taskCount, taskCount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.inbox_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TriageMetricChip(missingDueCount, stringResource(R.string.date_no_due), MaterialTheme.colorScheme.primary)
                TriageMetricChip(missingEstimateCount, stringResource(R.string.task_estimate_none), MaterialTheme.colorScheme.tertiary)
                if (projectsEnabled) {
                    TriageMetricChip(missingHomeCount, stringResource(R.string.settings_default_project), MaterialTheme.colorScheme.secondary)
                }
                if (peopleEnabled) {
                    TriageMetricChip(missingOwnerCount, stringResource(R.string.analytics_unassigned), MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TriageMetricChip(
    count: Int,
    label: String,
    tint: Color
) {
    Surface(
        color = tint.copy(alpha = if (count > 0) 0.14f else 0.08f),
        contentColor = tint,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxTaskCard(
    task: Task,
    list: YataList?,
    assignees: List<Person>,
    tags: List<Tag>,
    projectsEnabled: Boolean,
    peopleEnabled: Boolean,
    myPerson: Person?,
    onTaskClick: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onSetDue: (String?) -> Unit,
    onSetEstimate: (Int?) -> Unit,
    onAssignMe: (Person) -> Unit,
    onMove: () -> Unit,
    density: com.mj.yata.domain.model.TaskRowDensity,
    animateSize: Boolean,
    animateDetails: Boolean,
    modifier: Modifier = Modifier
) {
    val hasTriageActions = task.due == null ||
        task.estimateMinutes == null ||
        (projectsEnabled && task.projectId == null && task.listId == null) ||
        (peopleEnabled && task.assigneeIds.isEmpty() && myPerson != null)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (animateSize) {
                    it.animateContentSize(animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized))
                } else {
                    it
                }
            }
    ) {
        TaskRow(
            task = task,
            list = list,
            assignees = assignees,
            tags = tags,
            onToggleDone = onToggleDone,
            onTaskClick = onTaskClick,
            onSwipeToDelete = onDelete,
            density = density,
            showDueDate = true
        )
        if (animateDetails) {
            AnimatedVisibility(
                visible = hasTriageActions,
                enter = expandVertically(animationSpec = tween(YataDur.sheet, easing = YataEase.emphDecel)) +
                    fadeIn(animationSpec = tween(YataDur.fade, easing = YataEase.emphDecel)),
                exit = shrinkVertically(animationSpec = tween(YataDur.fade, easing = YataEase.emphAccel)) +
                    fadeOut(animationSpec = tween(YataDur.fade, easing = YataEase.emphAccel))
            ) {
                InboxTriageActions(
                    task = task,
                    projectsEnabled = projectsEnabled,
                    peopleEnabled = peopleEnabled,
                    myPerson = myPerson,
                    onSetDue = onSetDue,
                    onSetEstimate = onSetEstimate,
                    onAssignMe = onAssignMe,
                    onMove = onMove
                )
            }
        } else if (hasTriageActions) {
            InboxTriageActions(
                task = task,
                projectsEnabled = projectsEnabled,
                peopleEnabled = peopleEnabled,
                myPerson = myPerson,
                onSetDue = onSetDue,
                onSetEstimate = onSetEstimate,
                onAssignMe = onAssignMe,
                onMove = onMove
            )
        }
        if (!hasTriageActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChipIcon(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.inbox_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxTriageActions(
    task: Task,
    projectsEnabled: Boolean,
    peopleEnabled: Boolean,
    myPerson: Person?,
    onSetDue: (String?) -> Unit,
    onSetEstimate: (Int?) -> Unit,
    onAssignMe: (Person) -> Unit,
    onMove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (task.due == null) {
                val today = AppClock.today
                YataSelectChip(
                    label = stringResource(R.string.inbox_due_today),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.primary,
                    leading = { ChipIcon(Icons.Default.Today, MaterialTheme.colorScheme.primary) },
                    onClick = { onSetDue(today.toString()) }
                )
                YataSelectChip(
                    label = stringResource(R.string.inbox_due_tomorrow),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.primary,
                    leading = { ChipIcon(Icons.Default.Today, MaterialTheme.colorScheme.primary) },
                    onClick = { onSetDue(today.plusDays(1).toString()) }
                )
                YataSelectChip(
                    label = stringResource(R.string.inbox_due_next_week),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.primary,
                    leading = { ChipIcon(Icons.Default.Today, MaterialTheme.colorScheme.primary) },
                    onClick = { onSetDue(today.plusWeeks(1).toString()) }
                )
            }
            if (task.estimateMinutes == null) {
                YataSelectChip(
                    label = stringResource(R.string.inbox_estimate_15m),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.tertiary,
                    leading = { ChipIcon(Icons.Default.AccessTime, MaterialTheme.colorScheme.tertiary) },
                    onClick = { onSetEstimate(15) }
                )
                YataSelectChip(
                    label = stringResource(R.string.inbox_estimate_30m),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.tertiary,
                    leading = { ChipIcon(Icons.Default.AccessTime, MaterialTheme.colorScheme.tertiary) },
                    onClick = { onSetEstimate(30) }
                )
                YataSelectChip(
                    label = stringResource(R.string.inbox_estimate_1h),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.tertiary,
                    leading = { ChipIcon(Icons.Default.AccessTime, MaterialTheme.colorScheme.tertiary) },
                    onClick = { onSetEstimate(60) }
                )
            }
            if (projectsEnabled && task.projectId == null && task.listId == null) {
                YataSelectChip(
                    label = stringResource(R.string.inbox_move),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.secondary,
                    leading = { ChipIcon(Icons.Default.DriveFileMove, MaterialTheme.colorScheme.secondary) },
                    onClick = onMove
                )
            }
            if (peopleEnabled && task.assigneeIds.isEmpty() && myPerson != null) {
                YataSelectChip(
                    label = stringResource(R.string.inbox_assign_me),
                    selected = true,
                    showCheck = false,
                    tint = MaterialTheme.colorScheme.error,
                    leading = { ChipIcon(Icons.Default.PersonAdd, MaterialTheme.colorScheme.error) },
                    onClick = { onAssignMe(myPerson) }
                )
            }
        }
    }
}

@Composable
private fun ChipIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(15.dp)
    )
}
