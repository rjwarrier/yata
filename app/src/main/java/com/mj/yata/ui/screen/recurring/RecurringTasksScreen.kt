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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTasksScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.recurringTasksUiState.collectAsStateWithLifecycle()

    Scaffold(
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
            if (uiState.rows.isEmpty()) {
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
                            taskCount = uiState.rows.size,
                            dueSoonCount = uiState.dueSoonCount,
                            noDueCount = uiState.noDueCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                                .animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                        )
                    }
                    items(uiState.rows, key = { it.task.id }, contentType = { "recurring_task" }) { row ->
                        val task = row.task
                        RecurringTaskCard(
                            task = task,
                            list = row.list,
                            assignees = row.assignees,
                            tags = row.tags,
                            formattedDueDate = row.formattedDueDate,
                            recurrenceSummary = row.recurrenceSummary,
                            onTaskClick = { onNavigateToTaskDetail(task.id) },
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onEditTask = { onNavigateToTaskDetail(task.id) },
                            density = uiState.taskRowDensity,
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade)
                        )
                    }
                }
            }
        }
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
                        text = stringResource(R.string.recurring_empty_body),
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
    formattedDueDate: String?,
    recurrenceSummary: String,
    onTaskClick: () -> Unit,
    onToggleDone: () -> Unit,
    onEditTask: () -> Unit,
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
                                    formattedDueDate ?: stringResource(R.string.date_no_due)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = recurrenceSummary,
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
                            label = stringResource(R.string.task_row_edit_title),
                            selected = true,
                            showCheck = false,
                            tint = MaterialTheme.colorScheme.primary,
                            leading = { RecurringChipIcon(Icons.Default.Edit, MaterialTheme.colorScheme.primary) },
                            onClick = onEditTask
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
