package com.mj.yata.ui.screen.recurring

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.effectiveTags
import com.mj.yata.ui.screen.main.AdaptiveBottomNav
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.widgets.TabEmptyState
import com.mj.yata.ui.widgets.TaskRow
import com.mj.yata.ui.widgets.YataSelectChip
import com.mj.yata.ui.widgets.YataSnackbar
import com.mj.yata.util.AppClock
import com.mj.yata.util.RecurrenceEvaluator
import com.mj.yata.util.TaskScheduleUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTasksScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val taskRowDensity by viewModel.taskRowDensity.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var clearRepeatTask by remember { mutableStateOf<Task?>(null) }

    val recurringTasks = remember(tasks) {
        tasks.filter { !it.done && it.recurrence != null }
            .sortedWith(compareBy<Task> { it.due ?: "9999-99-99" }.thenBy { it.title.lowercase() })
    }
    val listsById = remember(lists) { lists.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    val todayIso = remember { AppClock.today.toString() }
    val nextWeekIso = remember { AppClock.today.plusDays(7).toString() }
    val dueSoonCount = remember(recurringTasks, todayIso, nextWeekIso) {
        recurringTasks.count { dueSoonTask -> dueSoonTask.due?.let { it >= todayIso && it <= nextWeekIso } == true }
    }
    val noDueCount = remember(recurringTasks) { recurringTasks.count { it.due == null } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> YataSnackbar(data) } },
        bottomBar = {
            AdaptiveBottomNav(
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
                    Text(
                        text = stringResource(R.string.recurring_tasks_title),
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
            if (recurringTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TabEmptyState(
                        icon = Icons.Default.EventRepeat,
                        title = stringResource(R.string.recurring_empty_title),
                        subtitle = stringResource(R.string.recurring_empty_body),
                        actionLabel = stringResource(R.string.recurring_empty_action),
                        onAction = { onNavigateToTab(0) }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "recurring_summary") {
                        RecurringSummaryCard(
                            taskCount = recurringTasks.size,
                            dueSoonCount = dueSoonCount,
                            noDueCount = noDueCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                                .animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                        )
                    }
                    items(recurringTasks, key = { it.id }, contentType = { "recurring_task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById, peopleFeatureEnabled) {
                            if (peopleFeatureEnabled) task.assigneeIds.mapNotNull { peopleById[it] } else emptyList()
                        }
                        val taskTags = remember(task, projectsById, tagsById, tagsFeatureEnabled) {
                            if (tagsFeatureEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
                        }
                        RecurringTaskCard(
                            task = task,
                            list = taskList,
                            assignees = taskAssignees,
                            tags = taskTags,
                            onTaskClick = { onNavigateToTaskDetail(task.id) },
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRename = { viewModel.renameTask(task.id, it) },
                            onSkipNext = {
                                viewModel.skipTaskOccurrence(task.id)
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.task_occurrence_skipped)) }
                            },
                            onClearRepeat = { clearRepeatTask = task },
                            density = taskRowDensity,
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                        )
                    }
                }
            }
        }
    }

    clearRepeatTask?.let { task ->
        AlertDialog(
            onDismissRequest = { clearRepeatTask = null },
            title = { Text(stringResource(R.string.recurring_clear_repeat_title)) },
            text = { Text(stringResource(R.string.recurring_clear_repeat_body, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.upsertTask(task.copy(recurrence = null))
                        clearRepeatTask = null
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.recurring_repeat_cleared)) }
                    }
                ) {
                    Text(stringResource(R.string.recurring_clear_repeat_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearRepeatTask = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringSummaryCard(
    taskCount: Int,
    dueSoonCount: Int,
    noDueCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
        )
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
                        imageVector = Icons.Default.EventRepeat,
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
                        text = stringResource(R.string.recurring_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurringMetricChip(dueSoonCount, stringResource(R.string.tab_upcoming), MaterialTheme.colorScheme.primary)
                RecurringMetricChip(noDueCount, stringResource(R.string.date_no_due), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun RecurringMetricChip(
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
private fun RecurringTaskCard(
    task: Task,
    list: YataList?,
    assignees: List<Person>,
    tags: List<Tag>,
    onTaskClick: () -> Unit,
    onToggleDone: () -> Unit,
    onQuickSnooze: (com.mj.yata.domain.model.QuickSnoozePreset) -> Unit,
    onRename: (String) -> Unit,
    onSkipNext: () -> Unit,
    onClearRepeat: () -> Unit,
    density: com.mj.yata.domain.model.TaskRowDensity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized))
    ) {
        TaskRow(
            task = task,
            list = list,
            assignees = assignees,
            tags = tags,
            onToggleDone = onToggleDone,
            onTaskClick = onTaskClick,
            onQuickSnooze = onQuickSnooze,
            onRenameTask = onRename,
            density = density,
            showDueDate = true
        )
        AnimatedVisibility(
            visible = true,
            enter = expandVertically(animationSpec = tween(YataDur.sheet, easing = YataEase.emphDecel)) +
                fadeIn(animationSpec = tween(YataDur.fade, easing = YataEase.emphDecel)),
            exit = shrinkVertically(animationSpec = tween(YataDur.fade, easing = YataEase.emphAccel)) +
                fadeOut(animationSpec = tween(YataDur.fade, easing = YataEase.emphAccel))
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(18.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.recurring_next_due,
                                    task.due?.let { TaskScheduleUtils.formatDueDate(it) } ?: stringResource(R.string.date_no_due)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = RecurrenceEvaluator.recurrenceSummary(task.recurrence),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        YataSelectChip(
                            label = stringResource(R.string.recurring_skip_next),
                            selected = true,
                            showCheck = false,
                            tint = MaterialTheme.colorScheme.primary,
                            leading = { RecurringChipIcon(Icons.Default.SkipNext, MaterialTheme.colorScheme.primary) },
                            onClick = onSkipNext
                        )
                        YataSelectChip(
                            label = stringResource(R.string.recurring_clear_repeat_action),
                            selected = true,
                            showCheck = false,
                            tint = MaterialTheme.colorScheme.error,
                            leading = { RecurringChipIcon(Icons.Default.Clear, MaterialTheme.colorScheme.error) },
                            onClick = onClearRepeat
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun RecurringChipIcon(
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
