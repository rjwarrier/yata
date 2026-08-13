package com.mj.yata.ui.screen.trash

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.screen.main.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import androidx.compose.foundation.ExperimentalFoundationApi
import com.mj.yata.ui.util.AdaptiveContentBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val deletedTasks by viewModel.deletedTasks.collectAsStateWithLifecycle()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var pendingPermanentDelete by remember { mutableStateOf<Task?>(null) }
    var pendingBulkPermanentDelete by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn

    fun toggleSelect(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) selectModeOn = false
        } else {
            selectedIds.add(id)
            selectModeOn = true
        }
    }

    fun restoreOneWithView(task: Task) {
        viewModel.restoreTask(task.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.trash_restored_snackbar, task.title),
                actionLabel = context.getString(R.string.action_view),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToTaskDetail(task.id)
            }
        }
    }

    fun restoreSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        viewModel.bulkRestoreTasks(ids)
        selectedIds.clear()
        selectModeOn = false
        scope.launch {
            val message = context.resources.getQuantityString(R.plurals.trash_tasks_restored, ids.size, ids.size)
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
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
                    IconButton(onClick = { restoreSelected() }, enabled = selectedIds.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = stringResource(R.string.cd_trash_restore)
                        )
                    }
                    IconButton(
                        onClick = { pendingBulkPermanentDelete = true },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.cd_trash_delete_forever),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.trash_title),
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
                    },
                    actions = {
                        if (deletedTasks.isNotEmpty()) {
                            IconButton(onClick = { showEmptyTrashDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.cd_trash_empty),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
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
        if (deletedTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.trash_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = if (trashRetentionDays <= 0) {
                            stringResource(R.string.trash_retention_notice_forever)
                        } else {
                            pluralStringResource(
                                R.plurals.trash_retention_notice_days,
                                trashRetentionDays,
                                trashRetentionDays
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(deletedTasks, key = { it.id }) { task ->
                    TrashTaskRow(
                        task = task,
                        retentionDays = trashRetentionDays,
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(task.id),
                        onToggleSelect = { toggleSelect(task.id) },
                        onClick = { if (selectionMode) toggleSelect(task.id) else onNavigateToTaskDetail(task.id) },
                        onRestore = { restoreOneWithView(task) },
                        onDeleteForever = { pendingPermanentDelete = task },
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

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.trash_empty_confirm_body, deletedTasks.size, deletedTasks.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyTrashDialog = false
                    viewModel.emptyTrash()
                }) {
                    Text(stringResource(R.string.trash_empty_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingPermanentDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text(stringResource(R.string.trash_delete_forever_title)) },
            text = { Text(stringResource(R.string.trash_delete_forever_body, task.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingPermanentDelete = null
                    viewModel.permanentlyDeleteTask(task)
                }) {
                    Text(stringResource(R.string.trash_delete_forever_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingBulkPermanentDelete) {
        val idsSnapshot = selectedIds.toList()
        AlertDialog(
            onDismissRequest = { pendingBulkPermanentDelete = false },
            title = { Text(stringResource(R.string.trash_delete_forever_title)) },
            text = { Text(pluralStringResource(R.plurals.trash_empty_confirm_body, idsSnapshot.size, idsSnapshot.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkPermanentDelete = false
                    val tasksToDelete = deletedTasks.filter { it.id in idsSnapshot }
                    selectedIds.clear()
                    selectModeOn = false
                    viewModel.bulkPermanentlyDeleteTasks(tasksToDelete)
                }) {
                    Text(stringResource(R.string.trash_delete_forever_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkPermanentDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrashTaskRow(
    task: Task,
    retentionDays: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onClick: () -> Unit,
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
                    onClick = onClick,
                    onLongClick = onToggleSelect
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = deletedLabel(task.deletedAt, retentionDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = stringResource(R.string.cd_trash_restore),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.cd_trash_delete_forever),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun deletedLabel(deletedAt: Long?, retentionDays: Int): String {
    // Defensive fallback - deletedAt should always be set for a row that made it into Trash.
    if (deletedAt == null) return stringResource(R.string.task_deleted)
    val deletedDate = Instant.ofEpochMilli(deletedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val daysAgo = ChronoUnit.DAYS.between(deletedDate, today)
    val whenText = when (daysAgo) {
        0L -> stringResource(R.string.trash_deleted_today)
        1L -> stringResource(R.string.trash_deleted_yesterday)
        else -> stringResource(R.string.trash_deleted_on, deletedDate.format(com.mj.yata.util.AppFormats.dayMonthFormatter()))
    }
    val suffix = if (retentionDays <= 0) {
        stringResource(R.string.trash_kept_forever_suffix)
    } else {
        val daysLeft = (retentionDays - daysAgo).coerceAtLeast(0).toInt()
        pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft)
    }
    return "$whenText · $suffix"
}
