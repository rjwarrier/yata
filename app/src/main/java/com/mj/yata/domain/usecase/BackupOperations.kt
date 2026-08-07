package com.mj.yata.domain.usecase

import com.mj.yata.data.backup.BackupDiff
import com.mj.yata.data.backup.compareBackupJsonWithTasks
import com.mj.yata.data.ftp.FtpBackupManager
import com.mj.yata.data.local.backup.LocalBackupManager
import com.mj.yata.data.sftp.SftpBackupManager
import com.mj.yata.data.sftp.SftpConnectionTestResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.mj.yata.R
import com.mj.yata.data.backup.UnifiedBackupWorker
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.model.RemoteBackupProtocol
import com.mj.yata.domain.model.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup triggers extracted from MainViewModel — self-hosted sync, on-device backup, and the
 * export-then-wipe path. Only the "do it now" actions live here; the enabled/interval
 * *preferences* stay on the ViewModel with the rest of the DataStore setters, since those are
 * plain writes with no orchestration.
 */
@Singleton
class BackupOperations @Inject constructor(
    private val repository: YataRepository,
    private val jsonExporter: JsonExporter,
    private val localBackupManager: LocalBackupManager,
    private val sftpBackupManager: SftpBackupManager,
    private val ftpBackupManager: FtpBackupManager,
    private val userPreferences: UserPreferences,
    private val operationHistoryStore: OperationHistoryStore,
    @ApplicationContext private val context: Context
) {

    private companion object {
        /** Keeps edit bursts from producing a backup for every individual keystroke. */
        const val DEBOUNCE_MILLIS = 15 * 1000L
    }

    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var debounceGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncStateLock = Any()
    private var activeSyncs = 0
    private var activeSyncFailed = false
    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress.asStateFlow()
    private val _syncPendingOrInProgress = MutableStateFlow(false)
    val syncPendingOrInProgress: StateFlow<Boolean> = _syncPendingOrInProgress.asStateFlow()
    private val _lastSyncSucceeded = MutableStateFlow<Boolean?>(null)
    val lastSyncSucceeded: StateFlow<Boolean?> = _lastSyncSucceeded.asStateFlow()

    /**
     * Backs up to every destination the user has switched on, whatever triggered it — the manual
     * button, the periodic worker, or the debounced run after a task changes. Configuring two
     * destinations is a statement that you want two copies; a trigger that only refreshed one of
     * them would leave the other quietly rotting until the day it was needed.
     *
     * Runs sequentially and never lets one destination's failure stop the next: the whole value of
     * a second destination is that it still works when the first doesn't. Returns one result per
     * *attempted* destination, so callers can report "local ok, server failed" rather than a single
     * verdict that's wrong for at least one of them. Destinations that are switched off aren't
     * attempted and don't appear in the list.
     */
    suspend fun backupAllConfigured(): List<BackupRunResult> = buildList {
        // Host check as well as the toggle: the switch can be on with the server dialog never
        // filled in, and an attempt that can only fail would report a backup failure for something
        // the user never actually set up. Sync first so any pulled changes are included in the
        // local safety copy produced by the same run.
        if (userPreferences.sftpBackupEnabledFlow.first() &&
            userPreferences.sftpHostFlow.first().isNotBlank()
        ) {
            val useFtp = userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP
            add(
                attempt(BackupDestination.SELF_HOSTED) {
                    syncSelfHostedWithToast("Syncing before scheduled backup") {
                        if (useFtp) ftpBackupManager.syncNow() else sftpBackupManager.syncNow()
                    }
                }
            )
        }
        if (userPreferences.localBackupEnabledFlow.first()) {
            add(attempt(BackupDestination.LOCAL) { localBackupManager.backupNow() })
        }
    }

    /**
     * Coalesces a burst of edits into one backup of every destination shortly after the last
     * change, so self-hosted sync stays fresh between scheduled runs without firing once per
     * keystroke.
     *
     * Scoped to the process rather than any caller: the point is to still be running shortly after
     * the screen that triggered it went away.
     */
    fun scheduleDebouncedBackup() {
        val generation = synchronized(syncStateLock) {
            debounceGeneration += 1
            debounceJob?.cancel()
            _syncPendingOrInProgress.value = true
            debounceGeneration
        }
        val nextJob = debounceScope.launch {
            try {
                delay(DEBOUNCE_MILLIS)
                backupAllConfigured()
            } finally {
                finishDebouncedBackup(generation)
            }
        }
        synchronized(syncStateLock) {
            if (generation == debounceGeneration) {
                debounceJob = nextJob
            } else {
                nextJob.cancel()
            }
        }
    }

    fun cancelDebouncedBackup() {
        val jobToCancel = synchronized(syncStateLock) {
            debounceGeneration += 1
            val job = debounceJob
            debounceJob = null
            if (activeSyncs == 0) _syncPendingOrInProgress.value = false
            job
        }
        jobToCancel?.cancel()
    }

    private fun finishDebouncedBackup(generation: Int) {
        synchronized(syncStateLock) {
            if (generation == debounceGeneration) {
                debounceJob = null
                if (activeSyncs == 0) _syncPendingOrInProgress.value = false
            }
        }
    }

    /** Pulls remote changes whenever the main app is opened, without touching other backups. */
    suspend fun syncSelfHostedIfConfigured(): Result<Unit>? {
        if (!userPreferences.sftpBackupEnabledFlow.first() ||
            userPreferences.sftpHostFlow.first().isBlank()
        ) return null
        return syncSelfHostedWithToast("Syncing after app launch") {
            if (userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP) {
                ftpBackupManager.syncNow()
            } else {
                sftpBackupManager.syncNow()
            }
        }
    }

    private suspend fun syncSelfHostedWithToast(
        runReason: String,
        block: suspend () -> Result<Unit>
    ): Result<Unit> {
        val operationId = currentSelfHostedSyncOperationId()
        operationHistoryStore.recordRun(operationId, runReason)
        beginSyncFeedback()
        return try {
            val result = block()
            finishSyncFeedback(success = result.isSuccess)
            result.fold(
                onSuccess = { operationHistoryStore.recordSuccess(operationId, "Self-hosted sync completed") },
                onFailure = { operationHistoryStore.recordFailure(operationId, it) }
            )
            result
        } catch (e: CancellationException) {
            finishSyncFeedback(success = false)
            throw e
        } catch (t: Throwable) {
            finishSyncFeedback(success = false)
            operationHistoryStore.recordFailure(operationId, t)
            Result.failure(t)
        }
    }

    private suspend fun currentSelfHostedSyncOperationId(): String =
        if (userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP) {
            OperationHistoryStore.SYNC_FTP_LEGACY
        } else {
            OperationHistoryStore.SYNC_SFTP_LEGACY
        }

    private fun beginSyncFeedback() {
        val shouldToast = synchronized(syncStateLock) {
            activeSyncs++
            if (activeSyncs == 1) {
                activeSyncFailed = false
                _syncInProgress.value = true
                _syncPendingOrInProgress.value = true
                true
            } else {
                false
            }
        }
        if (shouldToast) showSyncToast(R.string.sync_toast_started)
    }

    private fun finishSyncFeedback(success: Boolean) {
        val finalToast = synchronized(syncStateLock) {
            if (!success) activeSyncFailed = true
            activeSyncs = (activeSyncs - 1).coerceAtLeast(0)
            if (activeSyncs == 0) {
                _syncInProgress.value = false
                if (debounceJob?.isActive != true) _syncPendingOrInProgress.value = false
                _lastSyncSucceeded.value = !activeSyncFailed
                if (activeSyncFailed) R.string.sync_toast_failed else R.string.sync_toast_finished
            } else {
                null
            }
        }
        finalToast?.let(::showSyncToast)
    }

    private fun showSyncToast(messageRes: Int) {
        mainHandler.post {
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    /** Catches so a thrown failure is reported like a returned one and the run continues. */
    private suspend fun attempt(
        destination: BackupDestination,
        block: suspend () -> Result<Unit>
    ): BackupRunResult = try {
        BackupRunResult(destination, block().exceptionOrNull())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w("BackupOperations", "Backup to $destination failed", t)
        BackupRunResult(destination, t)
    }

    /**
     * Backs up everything to Downloads first, and only wipes the database if that backup
     * actually succeeded — never delete without a safety copy landing on disk. Returns the
     * backup filename, or null if the export failed (in which case nothing was deleted).
     */
    suspend fun backupThenDeleteAllData(): String? {
        val filename = jsonExporter.exportToDownloads()
        if (filename != null) {
            repository.deleteAllData()
        }
        return filename
    }

    suspend fun compareWithLastSelfHostedBackup(tasks: List<Task>): Result<BackupDiff> {
        val useFtp = userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP
        val backups = (if (useFtp) {
            ftpBackupManager.listBackups()
        } else {
            sftpBackupManager.listBackups()
        }).getOrElse { return Result.failure(it) }
        val latest = backups.firstOrNull()
            ?: return Result.failure(IllegalStateException("No server backups found yet"))
        val bytes = (if (useFtp) {
            ftpBackupManager.readBackupJson(latest)
        } else {
            sftpBackupManager.readBackupJson(latest)
        }).getOrElse { return Result.failure(it) }
        return try {
            Result.success(
                compareBackupJsonWithTasks(
                    backupJsonBytes = bytes,
                    currentTasks = tasks,
                    backupCreatedTime = backupCreatedTimeFromFilename(latest)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reschedules the single periodic backup job that covers every destination. */
    fun updateBackupInterval(minutes: Long) =
        UnifiedBackupWorker.schedule(context, minutes, androidx.work.ExistingPeriodicWorkPolicy.UPDATE)

    /** Triggers an immediate encrypted on-device backup. */
    suspend fun backupLocalNow() = localBackupManager.backupNow()

    suspend fun restoreLatestLocalBackup(): Boolean = localBackupManager.restoreLatest().isSuccess

    suspend fun testSftpConnection(): SftpConnectionTestResult = sftpBackupManager.testConnection()

    suspend fun pinSftpHostKey(fingerprint: String) = sftpBackupManager.pinHostKey(fingerprint)

    suspend fun sftpBackupNow(): Result<Unit> =
        syncSelfHostedWithToast("Manual SFTP sync started") { sftpBackupManager.syncNow() }

    suspend fun listSftpBackups(): Result<List<String>> = sftpBackupManager.listBackups()

    suspend fun restoreSftpBackup(filename: String): Result<Unit> = sftpBackupManager.restoreBackup(filename)

    suspend fun inspectSftpBackup(filename: String): Result<BackupSummary> =
        sftpBackupManager.inspectBackup(filename)

    suspend fun testFtpConnection(): Result<Unit> = ftpBackupManager.testConnection()

    suspend fun clearSelfHostedSyncLock(): Result<Unit> =
        if (userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP) {
            val operationId = OperationHistoryStore.SYNC_FTP_LEGACY
            operationHistoryStore.recordRun(operationId, "Clearing remote FTP sync lock")
            ftpBackupManager.clearSyncLock()
                .also { result ->
                    result.fold(
                        onSuccess = { operationHistoryStore.recordSkipped(operationId, "Remote FTP sync lock cleared") },
                        onFailure = { operationHistoryStore.recordFailure(operationId, it) }
                    )
                }
        } else {
            val operationId = OperationHistoryStore.SYNC_SFTP_LEGACY
            operationHistoryStore.recordRun(operationId, "Clearing remote SFTP sync lock")
            sftpBackupManager.clearSyncLock()
                .also { result ->
                    result.fold(
                        onSuccess = { operationHistoryStore.recordSkipped(operationId, "Remote SFTP sync lock cleared") },
                        onFailure = { operationHistoryStore.recordFailure(operationId, it) }
                    )
                }
        }

    suspend fun ftpBackupNow(): Result<Unit> =
        syncSelfHostedWithToast("Manual FTP sync started") { ftpBackupManager.syncNow() }

    suspend fun listFtpBackups(): Result<List<String>> = ftpBackupManager.listBackups()

    suspend fun restoreFtpBackup(filename: String): Result<Unit> = ftpBackupManager.restoreBackup(filename)

    suspend fun inspectFtpBackup(filename: String): Result<BackupSummary> =
        ftpBackupManager.inspectBackup(filename)

    private fun backupCreatedTimeFromFilename(filename: String): String {
        val name = filename.substringAfterLast('/')
        val match = Regex("""yata_backup_(\d{8})_(\d{6})\.(json|zip)(\.enc)?""").matchEntire(name)
            ?: return filename
        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss", java.util.Locale.US)
            val localDateTime = java.time.LocalDateTime.parse(
                match.groupValues[1] + match.groupValues[2],
                formatter
            )
            localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
        } catch (e: Exception) {
            filename
        }
    }
}
