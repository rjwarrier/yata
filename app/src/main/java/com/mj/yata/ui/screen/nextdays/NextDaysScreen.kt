package com.mj.yata.ui.screen.nextdays

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
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

    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun deleteTaskWithUndo(task: Task) {
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "Task deleted", undoWindowSeconds)
            if (!result) {
                viewModel.deleteTask(task)
            }
        }
    }

    val today = com.mj.yata.util.AppClock.today
    val todayStr = remember(today) { today.toString() }
    val endStr = remember(today) { today.plusDays((WINDOW_DAYS - 1).toLong()).toString() }

    val upcomingTasks = remember(tasks, todayStr, endStr) {
        tasks.filter { it.due != null && it.due >= todayStr && it.due <= endStr }
            .sortedWith(compareBy({ it.due }, { it.sortOrder }))
    }
    val groupedByDate = remember(upcomingTasks) { upcomingTasks.groupBy { it.due!! } }
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }

    fun dateLabel(dateStr: String): String {
        // The window filter above is a lexicographic string compare, so a malformed due date
        // (restored from a hand-edited backup, say) can pass it and still fail to parse. Falling
        // back to the raw string keeps one bad row from taking down the whole screen.
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(com.mj.yata.util.AppFormats.headerDateFormatter())
        }
    }

    val listsById = remember(lists) { lists.associateBy { it.id } }
    val peopleById = remember(people) { people.associateBy { it.id } }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
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
                    title = stringResource(R.string.next_days_all_clear),
                    subtitle = stringResource(R.string.next_days_nothing_due, WINDOW_DAYS)
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
                                text = pluralStringResource(R.plurals.task_count_lower, dateTasks.size, dateTasks.size),
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
                        val taskTags = remember(task, projectsById, tagsById, tagsFeatureEnabled) {
                            if (tagsFeatureEnabled) task.effectiveTags(projectsById, tagsById) else emptyList()
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
                            modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                            )
                        )
                    }
                }
            }
        }
    }
}
