package com.mj.yata.ui.screen.crashlog

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationCompat
import com.mj.yata.R
import com.mj.yata.data.local.crash.CrashLogCluster
import com.mj.yata.data.local.crash.CrashLogEntry
import com.mj.yata.data.local.operationhistory.OperationHistoryEntry
import com.mj.yata.data.local.operationhistory.OperationStatus
import com.mj.yata.notification.NotificationHelper
import com.mj.yata.notification.NotificationPermissionUtils
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.widget.resolveNotificationAccentColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEST_REMINDER_NOTIFICATION_ID = 907001

/**
 * Saved crash reports, newest first. Two kinds land here: an uncaught exception that killed the
 * process, and a failure that was caught on a write path and reported to the user instead of
 * crashing (see MainViewModel.safeLaunch). The second kind matters precisely because it is
 * invisible otherwise — the app carries on, so without a record the only trace is in logcat.
 *
 * Reports never leave the device unless shared deliberately; they aren't uploaded and aren't part
 * of backups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.crashLogs.collectAsStateWithLifecycle()
    val crashClusters by viewModel.crashClusters.collectAsStateWithLifecycle()
    val operationHistory by viewModel.operationHistory.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedBody by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var reminderChecks by remember { mutableStateOf(readReminderHealthChecks(context)) }

    // Files, not a Flow — nothing pushes an update, so the list is pulled when the screen opens.
    LaunchedEffect(Unit) {
        viewModel.refreshCrashLogs()
        viewModel.refreshOperationHistory()
    }

    // Bodies are read lazily: a stack trace is far bigger than the row that summarises it, and
    // most reports are never opened.
    LaunchedEffect(expandedId) {
        expandedBody = expandedId?.let { viewModel.readCrashLog(it) }.orEmpty()
    }

    val copiedMessage = stringResource(R.string.crash_log_copied)
    val shareSubject = stringResource(R.string.crash_log_share_subject)
    val testSentMessage = stringResource(R.string.diagnostics_test_reminder_sent)
    val testFailedMessage = stringResource(R.string.diagnostics_test_reminder_failed)
    val testReminderTitle = stringResource(R.string.diagnostics_test_reminder_title)
    val testReminderBody = stringResource(R.string.notification_helper_this_task_is_due)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) } },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.crash_log_clear_all))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ReminderHealthCard(
                    checks = reminderChecks,
                    onRefresh = { reminderChecks = readReminderHealthChecks(context) },
                    onOpenNotifications = { NotificationPermissionUtils.openNotificationSettings(context) },
                    onOpenExactAlarm = { NotificationPermissionUtils.openExactAlarmSettings(context) },
                    onOpenBattery = { NotificationPermissionUtils.requestIgnoreBatteryOptimizations(context) },
                    onSendTest = {
                        scope.launch {
                            runCatching {
                                sendTestReminderNotification(context, testReminderTitle, testReminderBody)
                            }.onSuccess {
                                snackbarHostState.showSnackbar(testSentMessage)
                                reminderChecks = readReminderHealthChecks(context)
                            }.onFailure {
                                snackbarHostState.showSnackbar(testFailedMessage)
                            }
                        }
                    }
                )
            }
            item {
                BackupHealthCard(entries = operationHistory.filter { it.category == "Backup" || it.category == "Sync" })
            }
            item {
                Text(
                    text = stringResource(R.string.diagnostics_operation_history),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(operationHistory, key = { it.id }) { entry ->
                OperationHistoryCard(entry = entry)
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.diagnostics_crash_reports),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (logs.isEmpty()) {
                        stringResource(R.string.crash_log_empty_body)
                    } else {
                        pluralStringResource(R.plurals.crash_log_count, logs.size, logs.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (crashClusters.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diagnostics_crash_clusters),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                items(crashClusters.take(5), key = { it.fingerprint }) { cluster ->
                    CrashClusterCard(cluster = cluster)
                }
            }
            items(logs, key = { it.id }) { entry ->
                CrashLogCard(
                    entry = entry,
                    expanded = expandedId == entry.id,
                    body = if (expandedId == entry.id) expandedBody else "",
                    onToggle = { expandedId = if (expandedId == entry.id) null else entry.id },
                    onCopy = {
                        clipboard.setText(AnnotatedString(expandedBody))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, expandedBody)
                        }
                        context.startActivity(Intent.createChooser(intent, shareSubject))
                    },
                    onDelete = {
                        if (expandedId == entry.id) expandedId = null
                        viewModel.deleteCrashLog(entry.id)
                    }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.crash_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.crash_log_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    expandedId = null
                    viewModel.clearCrashLogs()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.crash_log_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CrashClusterCard(cluster: CrashLogCluster) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_crash_cluster_count, cluster.count),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = cluster.fingerprint,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(cluster.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperationMetric(
                    label = stringResource(R.string.diagnostics_first_seen),
                    value = formatTimestamp(cluster.firstSeenAt),
                    modifier = Modifier.weight(1f)
                )
                OperationMetric(
                    label = stringResource(R.string.diagnostics_last_seen),
                    value = formatTimestamp(cluster.lastSeenAt),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class ReminderHealthChecks(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean
) {
    val ready: Boolean = notificationsEnabled && exactAlarmsAllowed && batteryUnrestricted
}

private fun readReminderHealthChecks(context: Context): ReminderHealthChecks = ReminderHealthChecks(
    notificationsEnabled = NotificationPermissionUtils.areNotificationsEnabled(context),
    exactAlarmsAllowed = NotificationPermissionUtils.canScheduleExactAlarms(context),
    batteryUnrestricted = NotificationPermissionUtils.isIgnoringBatteryOptimizations(context)
)

@Composable
private fun ReminderHealthCard(
    checks: ReminderHealthChecks,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenBattery: () -> Unit,
    onSendTest: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diagnostics_reminder_health),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(
                            if (checks.ready) R.string.diagnostics_reminder_health_ready
                            else R.string.diagnostics_reminder_health_attention
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.diagnostics_refresh_checks))
                }
            }
            HealthStatusRow(
                label = stringResource(R.string.diagnostics_notification_access),
                value = stringResource(if (checks.notificationsEnabled) R.string.diagnostics_enabled else R.string.diagnostics_disabled_tap_to_fix),
                ok = checks.notificationsEnabled,
                onClick = onOpenNotifications
            )
            HealthStatusRow(
                label = stringResource(R.string.diagnostics_exact_alarm_timing),
                value = stringResource(if (checks.exactAlarmsAllowed) R.string.diagnostics_allowed else R.string.diagnostics_not_allowed_tap_to_fix),
                ok = checks.exactAlarmsAllowed,
                onClick = onOpenExactAlarm
            )
            HealthStatusRow(
                label = stringResource(R.string.diagnostics_background_delivery),
                value = stringResource(if (checks.batteryUnrestricted) R.string.diagnostics_unrestricted else R.string.diagnostics_optimized_tap_to_fix),
                ok = checks.batteryUnrestricted,
                onClick = onOpenBattery
            )
            OutlinedButton(onClick = onSendTest, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.diagnostics_send_test_reminder))
            }
        }
    }
}

@Composable
private fun HealthStatusRow(label: String, value: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .clickable(enabled = !ok, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringResource(if (ok) R.string.diagnostics_status_ok else R.string.diagnostics_status_attention),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BackupHealthCard(entries: List<OperationHistoryEntry>) {
    val latestSuccess = entries.mapNotNull { entry -> entry.lastSuccessAt?.let { it to entry } }.maxByOrNull { it.first }?.second
    val latestFailure = entries.mapNotNull { entry -> entry.lastFailureAt?.let { it to entry } }.maxByOrNull { it.first }?.second
    val retrying = entries.sumOf { it.retryCount }
    val destinationsSeen = entries.count { it.lastRunAt != null }
    val hasFailureAfterSuccess = latestFailure?.let { failure ->
        val failureAt = failure.lastFailureAt ?: 0L
        val successAt = latestSuccess?.lastSuccessAt ?: 0L
        failureAt > successAt
    } == true

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.diagnostics_backup_health),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                hasFailureAfterSuccess -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                latestSuccess != null -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(
                            when {
                                hasFailureAfterSuccess -> R.string.diagnostics_status_attention
                                latestSuccess != null -> R.string.diagnostics_status_ok
                                else -> R.string.diagnostics_status_unknown
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = when {
                            hasFailureAfterSuccess -> MaterialTheme.colorScheme.error
                            latestSuccess != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (entries.none { it.lastRunAt != null }) {
                Text(
                    text = stringResource(R.string.diagnostics_backup_health_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OperationMetric(
                        label = stringResource(R.string.diagnostics_latest_success),
                        value = latestSuccess?.let { "${it.title} - ${formatNullableTimestamp(it.lastSuccessAt)}" }
                            ?: stringResource(R.string.operation_history_never),
                        modifier = Modifier.weight(1f)
                    )
                    OperationMetric(
                        label = stringResource(R.string.diagnostics_latest_failure),
                        value = latestFailure?.let { "${it.title} - ${formatNullableTimestamp(it.lastFailureAt)}" }
                            ?: stringResource(R.string.operation_history_never),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OperationMetric(
                        label = stringResource(R.string.diagnostics_retrying),
                        value = retrying.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    OperationMetric(
                        label = stringResource(R.string.diagnostics_destinations_seen),
                        value = destinationsSeen.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun sendTestReminderNotification(context: Context, title: String, body: String) {
    NotificationHelper.createChannels(context)
    val accentColor = resolveNotificationAccentColor(context)
    val notification = NotificationCompat.Builder(context, NotificationHelper.REMINDER_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(body)
        .setSmallIcon(R.drawable.ic_notification_small)
        .setColor(accentColor)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(TEST_REMINDER_NOTIFICATION_ID, notification)
}

@Composable
private fun OperationHistoryCard(entry: OperationHistoryEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusContainerColor(entry.lastStatus))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText(entry.lastStatus),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = statusContentColor(entry.lastStatus)
                    )
                }
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            entry.lastReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.operation_history_reason, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OperationMetric(
                    label = stringResource(R.string.operation_history_last_run),
                    value = formatNullableTimestamp(entry.lastRunAt),
                    modifier = Modifier.weight(1f)
                )
                OperationMetric(
                    label = stringResource(R.string.operation_history_last_success),
                    value = formatNullableTimestamp(entry.lastSuccessAt),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OperationMetric(
                    label = stringResource(R.string.operation_history_last_failure),
                    value = formatNullableTimestamp(entry.lastFailureAt),
                    modifier = Modifier.weight(1f)
                )
                OperationMetric(
                    label = stringResource(R.string.operation_history_retry_count),
                    value = entry.retryCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OperationMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun statusText(status: OperationStatus): String = stringResource(
    when (status) {
        OperationStatus.NEVER_RUN -> R.string.operation_status_never_run
        OperationStatus.RUNNING -> R.string.operation_status_running
        OperationStatus.SUCCESS -> R.string.operation_status_success
        OperationStatus.FAILURE -> R.string.operation_status_failure
        OperationStatus.SKIPPED -> R.string.operation_status_skipped
    }
)

@Composable
private fun statusContainerColor(status: OperationStatus) = when (status) {
    OperationStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    OperationStatus.FAILURE -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    OperationStatus.RUNNING -> MaterialTheme.colorScheme.secondaryContainer
    OperationStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceContainerHighest
    OperationStatus.NEVER_RUN -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun statusContentColor(status: OperationStatus) = when (status) {
    OperationStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    OperationStatus.FAILURE -> MaterialTheme.colorScheme.error
    OperationStatus.RUNNING -> MaterialTheme.colorScheme.onSecondaryContainer
    OperationStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    OperationStatus.NEVER_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun CrashLogCard(
    entry: CrashLogEntry,
    expanded: Boolean,
    body: String,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // A fatal report and a handled one need telling apart at a glance: one
                        // took the app down, the other is a bug the user may not have noticed.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (entry.fatal) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(if (entry.fatal) R.string.crash_log_fatal else R.string.crash_log_handled),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                color = if (entry.fatal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatTimestamp(entry.timestampMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_crash_log),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                // Monospace and horizontally scrollable: a stack trace wrapped at the screen edge
                // is far harder to read than one that keeps its original line structure.
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.crash_log_copy))
                    }
                    TextButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.crash_log_share))
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(millis))

@Composable
private fun formatNullableTimestamp(millis: Long?): String =
    millis?.let(::formatTimestamp) ?: stringResource(R.string.operation_history_never)
