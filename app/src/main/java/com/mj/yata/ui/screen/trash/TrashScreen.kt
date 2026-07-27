package com.mj.yata.ui.screen.trash

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.screen.main.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val deletedTasks by viewModel.deletedTasks.collectAsState()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsState()

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var pendingPermanentDelete by remember { mutableStateOf<Task?>(null) }

    Scaffold(
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
    ) { innerPadding ->
        if (deletedTasks.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
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
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
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
                        onRestore = { viewModel.restoreTask(task.id) },
                        onDeleteForever = { pendingPermanentDelete = task },
                        modifier = Modifier.animateItem(placementSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                        )
                    )
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
}

@Composable
private fun TrashTaskRow(
    task: Task,
    retentionDays: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

private fun deletedLabel(deletedAt: Long?, retentionDays: Int): String {
    if (deletedAt == null) return "Deleted"
    val deletedDate = Instant.ofEpochMilli(deletedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val daysAgo = ChronoUnit.DAYS.between(deletedDate, today)
    val whenText = when (daysAgo) {
        0L -> "today"
        1L -> "yesterday"
        else -> "on " + deletedDate.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    if (retentionDays <= 0) return "Deleted $whenText · kept until removed"
    val daysLeft = (retentionDays - daysAgo).coerceAtLeast(0)
    return "Deleted $whenText · $daysLeft days left"
}
