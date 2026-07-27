package com.mj.yata.ui.screen.nextdays

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.TaskRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val WINDOW_DAYS = 10

/** Flat, date-grouped agenda of everything due from today through the next [WINDOW_DAYS] days —
 * unlike the Upcoming tab (one day/month at a time), this is a single scroll sorted by due date,
 * reachable from the drawer for a quick "what's coming up" scan. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NextDaysScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Task deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                viewModel.deleteTask(task)
            }
        }
    }

    val today = remember { LocalDate.now() }
    val todayStr = remember { today.toString() }
    val endStr = remember { today.plusDays((WINDOW_DAYS - 1).toLong()).toString() }

    val upcomingTasks = remember(tasks, todayStr, endStr) {
        tasks.filter { it.due != null && it.due >= todayStr && it.due <= endStr }
            .sortedWith(compareBy({ it.due }, { it.sortOrder }))
    }
    val groupedByDate = remember(upcomingTasks) { upcomingTasks.groupBy { it.due!! } }

    fun dateLabel(dateStr: String): String {
        val date = LocalDate.parse(dateStr)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }
    }

    val listsById = remember(lists) { lists.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                if (data.visuals.actionLabel == "Undo") {
                    com.mj.yata.ui.widgets.DeleteUndoSnackbar(data)
                } else {
                    Snackbar(data)
                }
            }
        },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
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
                        pluralStringResource(R.plurals.next_days_title, WINDOW_DAYS, WINDOW_DAYS),
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
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
        if (upcomingTasks.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                com.mj.yata.ui.widgets.TabEmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = "All clear",
                    subtitle = "Nothing due in the next $WINDOW_DAYS days."
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                groupedByDate.forEach { (dateStr, dateTasks) ->
                    item(key = "header_$dateStr") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateLabel(dateStr),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${dateTasks.size} ${if (dateTasks.size == 1) "task" else "tasks"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(dateTasks, key = { it.id }, contentType = { "task" }) { task ->
                        val taskList = remember(task.listId, listsById) { listsById[task.listId] }
                        val taskAssignees = remember(task.assigneeIds, peopleById, peopleFeatureEnabled) {
                            if (peopleFeatureEnabled) task.assigneeIds.mapNotNull { pid -> peopleById[pid] } else emptyList()
                        }
                        val taskTags = remember(task, projects, tags, tagsFeatureEnabled) {
                            if (tagsFeatureEnabled) task.effectiveTags(projects, tags) else emptyList()
                        }

                        TaskRow(
                            task = task,
                            list = taskList,
                            assignees = taskAssignees,
                            tags = taskTags,
                            onToggleDone = { viewModel.toggleTaskDone(task.id) {} },
                            onTaskClick = { onNavigateToTaskDetail(task.id) },
                            onSwipeToDelete = { deleteTaskWithUndo(task) },
                            onQuickSnooze = { viewModel.quickSnoozeTask(task.id, it) },
                            onRenameTask = { viewModel.renameTask(task.id, it) },
                            density = taskRowDensity,
                            modifier = Modifier.animateItem(placementSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                            )
                        )
                    }
                }
            }
        }
    }
}
