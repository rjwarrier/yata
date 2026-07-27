package com.mj.yata.ui.screen.archive

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import kotlinx.coroutines.launch

/**
 * Archived tasks — shelved but fully intact, unlike Trash. Nothing here expires or is purged;
 * the only way out is unarchiving (or deleting from the task's own detail screen).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val archivedTasks by viewModel.archivedTasks.collectAsState()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openArchivedTasks = remember(archivedTasks) { archivedTasks.filter { !it.done } }
    val completedArchivedTasks = remember(archivedTasks) { archivedTasks.filter { it.done } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        stringResource(R.string.archive_title),
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
        if (archivedTasks.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.archive_empty_state),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.archive_empty_state_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onNavigateToTab(0) }) {
                        Text(stringResource(R.string.archive_empty_state_action))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.archive_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (openArchivedTasks.isNotEmpty()) {
                    item { ArchiveSectionHeader("Shelved active tasks", openArchivedTasks.size) }
                    items(openArchivedTasks, key = { it.id }) { task ->
                        ArchiveTaskRow(
                            task = task,
                            onClick = { onNavigateToTaskDetail(task.id) },
                            onUnarchive = {
                                viewModel.setTaskArchived(task.id, false)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Unarchived \"${task.title}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.setTaskArchived(task.id, true)
                                    }
                                }
                            }
                        )
                    }
                }
                if (completedArchivedTasks.isNotEmpty()) {
                    item { ArchiveSectionHeader("Shelved completed tasks", completedArchivedTasks.size) }
                    items(completedArchivedTasks, key = { it.id }) { task ->
                        ArchiveTaskRow(
                            task = task,
                            onClick = { onNavigateToTaskDetail(task.id) },
                            onUnarchive = {
                                viewModel.setTaskArchived(task.id, false)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Unarchived \"${task.title}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.setTaskArchived(task.id, true)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveSectionHeader(label: String, count: Int) {
    Text(
        text = "$label - $count",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ArchiveTaskRow(
    task: com.mj.yata.domain.model.Task,
    onClick: () -> Unit,
    onUnarchive: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2
                )
                Text(
                    text = listOfNotNull(task.due, task.recurrence?.let { com.mj.yata.util.RecurrenceEvaluator.recurrenceSummary(it) }).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onUnarchive) {
                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.cd_archive_unarchive))
            }
        }
    }
}
