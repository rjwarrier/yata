package com.mj.yata.ui.screen.synchistory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncCommitMessage
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.util.localized
import com.mj.yata.util.modelSyncDeviceLabel
import com.mj.yata.util.syncDeviceLabel
import com.mj.yata.ui.widgets.showError
import com.mj.yata.ui.widgets.showSuccess
import kotlinx.coroutines.launch

/**
 * The full GitHub sync history — every snapshot the current transport reports, one card each.
 * Split out from RemoteSyncScreen because that screen embedding the whole list inline made an
 * already-long config screen scroll forever; here the list is the entire point.
 *
 * Per-card detail (device/task counts read from *inside* the snapshot, not guessed from the
 * commit message) is fetched lazily on expand, not eagerly for every row — inspecting a snapshot
 * downloads and decrypts the full blob, and doing that for every entry on screen open would turn
 * "browse history" into "download everything in history".
 */
/** Matches GitHubApi's commit page size, so a fetch here is always exactly one API call. */
private const val SYNC_HISTORY_PAGE_SIZE = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<RestorePoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // How many restore points the next fetch asks for. GitHubSyncManager has no page cursor to
    // resume from - listRestorePoints(limit) always re-walks from the newest commit - so "load
    // more" means re-fetching everything up to a bigger limit and replacing the list, not
    // appending a new page. Wasteful only in the sense of re-downloading commit metadata (cheap,
    // and still just one GitHub API page per SYNC_HISTORY_PAGE_SIZE) already shown, not the
    // per-card snapshot blobs, which stay cached in `summaries` regardless.
    var requestedLimit by remember { mutableStateOf(SYNC_HISTORY_PAGE_SIZE) }
    // If the last fetch returned fewer than it asked for, that's every restore point there is.
    val hasMore = loadError == null && entries.size >= requestedLimit

    fun load(limit: Int, forceRefresh: Boolean, onDone: () -> Unit) {
        loadError = null
        viewModel.listRemoteRestorePoints(limit = limit, forceRefresh = forceRefresh) { result ->
            onDone()
            result.fold(
                onSuccess = { entries = it },
                onFailure = { error ->
                    entries = emptyList()
                    loadError = error.message ?: context.getString(R.string.export_failed)
                }
            )
        }
    }

    // forceRefresh = false on the initial screen-open load: it's the common case of arriving
    // here right after RemoteSyncScreen's own compact-row fetch, and there's no reason to pay for
    // a second GitHub API call for data that's still fresh from the first. The toolbar's explicit
    // refresh action forces past that, since "refresh" tapped by hand means "I want current data,
    // not what happened to already be cached."
    fun loadFirstPage(forceRefresh: Boolean) {
        requestedLimit = SYNC_HISTORY_PAGE_SIZE
        isLoading = true
        load(requestedLimit, forceRefresh) { isLoading = false }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        requestedLimit += SYNC_HISTORY_PAGE_SIZE
        load(requestedLimit, forceRefresh = false) { isLoadingMore = false }
    }

    LaunchedEffect(Unit) { loadFirstPage(forceRefresh = false) }

    // Single-expand accordion — keeps the list scannable, and caches each fetched summary so
    // collapsing and re-expanding a card doesn't re-download the snapshot.
    var expandedId by remember { mutableStateOf<String?>(null) }
    val summaries = remember { mutableStateMapOf<String, BackupSummary>() }
    val summaryErrors = remember { mutableStateMapOf<String, String>() }
    var inspectingId by remember { mutableStateOf<String?>(null) }

    fun toggleExpand(id: String) {
        expandedId = if (expandedId == id) null else id
        // inspectingId != id guards against a second concurrent fetch of the same snapshot: the
        // cache only gets populated when the *first* fetch resolves, so collapsing and
        // re-expanding the same card before that happens would otherwise pass this check twice
        // and download+decrypt the same blob twice in parallel.
        if (expandedId == id && id !in summaries && id !in summaryErrors && inspectingId != id) {
            inspectingId = id
            viewModel.inspectRemoteSnapshot(id) { result ->
                if (inspectingId == id) inspectingId = null
                result.fold(
                    onSuccess = { summaries[id] = it },
                    onFailure = { error -> summaryErrors[id] = error.message ?: context.getString(R.string.export_failed) }
                )
            }
        }
    }

    var pendingRestore by remember { mutableStateOf<RestorePoint?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    val thisDeviceLabels = remember(context) {
        setOf(context.syncDeviceLabel(), modelSyncDeviceLabel())
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) } },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_sync_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { loadFirstPage(forceRefresh = true) }, enabled = !isLoading) {
                        Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContentBox(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading && entries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = loadError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                entries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.remote_sync_activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            val parsed = remember(entry.label) { SyncCommitMessage.parse(entry.label) }
                            SyncHistoryCard(
                                device = parsed.device,
                                summaryText = parsed.summary,
                                timestamp = entry.createdAt,
                                isThisDevice = parsed.device != null &&
                                    thisDeviceLabels.any { it.equals(parsed.device, ignoreCase = true) },
                                isExpanded = expandedId == entry.id,
                                onToggleExpand = { toggleExpand(entry.id) },
                                isInspecting = inspectingId == entry.id,
                                inspected = summaries[entry.id],
                                inspectError = summaryErrors[entry.id],
                                onRestore = { pendingRestore = entry }
                            )
                        }
                        if (hasMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        TextButton(onClick = ::loadMore) {
                                            Text(stringResource(R.string.remote_sync_load_more_activity))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { restorePoint ->
        val summary = summaries[restorePoint.id]
        val error = summaryErrors[restorePoint.id]
        AlertDialog(
            onDismissRequest = { if (!isRestoring) pendingRestore = null },
            title = { Text(stringResource(R.string.settings_sftp_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        summary != null -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        stringResource(
                                            R.string.settings_backup_summary_device,
                                            summary.createdByDevice ?: stringResource(R.string.settings_backup_summary_device_unknown)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(stringResource(R.string.settings_backup_summary_tasks, summary.totalTasks), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.settings_backup_summary_open, summary.openTasks), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.settings_backup_summary_projects, summary.totalProjects), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        error != null -> {
                            Text(
                                stringResource(R.string.settings_backup_summary_failed, error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.settings_backup_summary_loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(stringResource(R.string.settings_sftp_restore_confirm_body))
                }
            },
            confirmButton = {
                TextButton(
                    // Blocked both on a read failure AND on still-loading (summary == null,
                    // error == null is *also* true mid-fetch) - a snapshot whose contents haven't
                    // been shown yet is one the user hasn't actually had the chance to check
                    // before confirming, which defeats the reason this dialog shows them at all.
                    enabled = !isRestoring && error == null && summary != null,
                    onClick = {
                        isRestoring = true
                        viewModel.restoreRemoteSnapshot(restorePoint.id) { result ->
                            isRestoring = false
                            pendingRestore = null
                            scope.launch {
                                if (result.isSuccess) {
                                    snackbarHostState.showSuccess(context.getString(R.string.settings_sftp_connection_ok))
                                } else {
                                    snackbarHostState.showError(result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed))
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.cd_trash_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }, enabled = !isRestoring) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SyncHistoryCard(
    device: String?,
    summaryText: String?,
    timestamp: java.time.Instant?,
    isThisDevice: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isInspecting: Boolean,
    inspected: BackupSummary?,
    inspectError: String?,
    onRestore: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (isThisDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = device ?: stringResource(R.string.remote_sync_activity_unknown_device),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (device == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        if (isThisDevice) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.remote_sync_activity_this_device),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val subtitle = listOfNotNull(summaryText, timestamp?.localized()).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    when {
                        isInspecting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_backup_summary_loading), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        inspected != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(
                                        R.string.settings_backup_summary_device,
                                        inspected.createdByDevice ?: stringResource(R.string.settings_backup_summary_device_unknown)
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(stringResource(R.string.settings_backup_summary_tasks, inspected.totalTasks), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.settings_backup_summary_open, inspected.openTasks), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.settings_backup_summary_projects, inspected.totalProjects), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        inspectError != null -> {
                            Text(
                                stringResource(R.string.settings_backup_summary_failed, inspectError),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.cd_trash_restore))
                    }
                }
            }
        }
    }
}
