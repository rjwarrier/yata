package com.mj.yata.ui.screen.archive

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.widgets.LocalUndoWindowSeconds
import com.mj.yata.ui.widgets.showUndoSnackbar
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
    val archivedTasks by viewModel.archivedTasks.collectAsStateWithLifecycle()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoWindowSeconds = LocalUndoWindowSeconds.current
    val openArchivedTasks = remember(archivedTasks) { archivedTasks.filter { !it.done } }
    val completedArchivedTasks = remember(archivedTasks) { archivedTasks.filter { it.done } }

    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn

    // Serves both entry points: a long-press on a row (which enters selection mode on its own)
    // and a tap while already selecting - same function either way, mirroring TagsTab/PeopleTab.
    fun toggleSelect(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) selectModeOn = false
        } else {
            selectedIds.add(id)
            selectModeOn = true
        }
    }

    // Named per-task rather than by count when it's a single explicit row action - "Unarchived
    // 'Buy groceries'" is more useful than "Unarchived 1 task" when there's a specific title to
    // name. The bulk selection path below has no single title to point to, so it counts instead.
    fun unarchiveOneWithUndo(task: com.mj.yata.domain.model.Task) {
        viewModel.setTaskArchived(task.id, false)
        scope.launch {
            val message = context.getString(R.string.archive_unarchived_snackbar, task.title)
            val result = showUndoSnackbar(snackbarHostState, message, undoWindowSeconds)
            if (result) viewModel.setTaskArchived(task.id, true)
        }
    }

    fun unarchiveSelectedWithUndo() {
        val taskIds = selectedIds.toList()
        if (taskIds.isEmpty()) return
        viewModel.bulkArchiveTasks(taskIds, false)
        selectedIds.clear()
        selectModeOn = false
        scope.launch {
            val message = context.resources.getQuantityString(R.plurals.archive_tasks_unarchived, taskIds.size, taskIds.size)
            val result = showUndoSnackbar(snackbarHostState, message, undoWindowSeconds)
            if (result) viewModel.bulkArchiveTasks(taskIds, true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) } },
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
            if (selectionMode) {
                com.mj.yata.ui.widgets.TabSelectionTopBar(
                    selectedCount = selectedIds.size,
                    onCancel = { selectedIds.clear(); selectModeOn = false }
                ) {
                    IconButton(
                        onClick = { unarchiveSelectedWithUndo() },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.cd_archive_unarchive))
                    }
                }
            } else {
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
        }
    ) { innerPadding ->
        AdaptiveContentBox(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
        if (archivedTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
            val activeLabel = stringResource(R.string.archive_section_shelved_active)
            val completedLabel = stringResource(R.string.archive_section_shelved_completed)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                    item { ArchiveSectionHeader(activeLabel, openArchivedTasks.size) }
                    items(openArchivedTasks, key = { it.id }) { task ->
                        ArchiveTaskRow(
                            task = task,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(task.id),
                            onClick = { onNavigateToTaskDetail(task.id) },
                            onToggleSelect = { toggleSelect(task.id) },
                            onUnarchive = { unarchiveOneWithUndo(task) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = com.mj.yata.ui.theme.yataItemFade,
                                placementSpec = com.mj.yata.ui.theme.yataItemPlacement,
                                fadeOutSpec = com.mj.yata.ui.theme.yataItemFade
                            )
                        )
                    }
                }
                if (completedArchivedTasks.isNotEmpty()) {
                    item { ArchiveSectionHeader(completedLabel, completedArchivedTasks.size) }
                    items(completedArchivedTasks, key = { it.id }) { task ->
                        ArchiveTaskRow(
                            task = task,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(task.id),
                            onClick = { onNavigateToTaskDetail(task.id) },
                            onToggleSelect = { toggleSelect(task.id) },
                            onUnarchive = { unarchiveOneWithUndo(task) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = com.mj.yata.ui.theme.yataItemFade,
                                placementSpec = com.mj.yata.ui.theme.yataItemPlacement,
                                fadeOutSpec = com.mj.yata.ui.theme.yataItemFade
                            )
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ArchiveSectionHeader(label: String, count: Int) {
    Text(
        text = stringResource(R.string.archive_section_header, label, count),
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
    onUnarchive: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onClick() },
                    onLongClick = onToggleSelect
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(modifier = Modifier.width(4.dp))
            }
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
            if (!selectionMode) {
                IconButton(onClick = onUnarchive) {
                    Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.cd_archive_unarchive))
                }
            }
        }
    }
}
