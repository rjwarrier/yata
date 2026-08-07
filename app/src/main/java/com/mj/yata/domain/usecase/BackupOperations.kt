package com.mj.yata.domain.usecase

import com.mj.yata.data.backup.BackupDiff
import com.mj.yata.data.backup.compareBackupJsonWithTasks
import com.mj.yata.data.ftp.FtpBackupManager
import com.mj.yata.data.github.GitHubSyncManager
import com.mj.yata.data.local.backup.LocalBackupManager
import com.mj.yata.data.sftp.SftpBackupManager
import com.mj.yata.data.sftp.SftpConnectionTestResult
import android.content.Context
import android.util.Log
import com.mj.yata.data.backup.UnifiedBackupWorker
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.model.RemoteBackupProtocol
import com.mj.yata.domain.model.SyncProgressState
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.sync.LockableSyncTransport
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncRunOptions
import com.mj.yata.domain.sync.SyncRunReport
import com.mj.yata.domain.sync.SyncTransport
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
    private val gitHubSyncManager: GitHubSyncManager,
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
    private val syncStateLock = Any()
    private var activeSyncs = 0
    private var activeSyncFailed = false
    private val _syncInProgress = MutableStateFlow(false)
    val syncInProgress: StateFlow<Boolean> = _syncInProgress.asStateFlow()
    private val _syncPendingOrInProgress = MutableStateFlow(false)
    val syncPendingOrInProgress: StateFlow<Boolean> = _syncPendingOrInProgress.asStateFlow()
    private val _lastSyncSucceeded = MutableStateFlow<Boolean?>(null)
    val lastSyncSucceeded: StateFlow<Boolean?> = _lastSyncSucceeded.asStateFlow()
    private val _syncProgress = MutableStateFlow<SyncProgressState?>(null)
    val syncProgress: StateFlow<SyncProgressState?> = _syncProgress.asStateFlow()

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
    suspend fun backupAllConfigured(
        allowInitialJoinMerge: Boolean = false
    ): List<BackupRunResult> = buildList {
        // Host check as well as the toggle: the switch can be on with the server dialog never
        // filled in, and an attempt that can only fail would report a backup failure for something
        // the user never actually set up. Sync first so any pulled changes are included in the
        // local safety copy produced by the same run.
        if (userPreferences.sftpBackupEnabledFlow.first() &&
            currentTransport().isConfigured()
        ) {
            add(
                attempt(BackupDestination.SELF_HOSTED) {
                    syncSelfHostedWithProgress("Syncing before scheduled backup") { progress ->
                        currentTransport().syncNow(
                            progress,
                            SyncRunOptions(allowInitialJoinMerge = allowInitialJoinMerge)
                        )
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
            !currentTransport().isConfigured()
        ) return null
        return syncSelfHostedWithProgress("Syncing after app launch") { progress ->
            currentTransport().syncNow(progress)
        }.map { }
    }

    private suspend fun syncSelfHostedWithProgress(
        runReason: String,
        block: suspend ((Int, String) -> Unit) -> Result<SyncRunReport>
    ): Result<SyncRunReport> {
        val protocol = userPreferences.remoteBackupProtocolFlow.first()
        val operationId = syncOperationId(protocol)
        val labels = syncLabels(protocol)
        operationHistoryStore.recordRun(operationId, runReason)
        beginSyncFeedback(labels.preparing)
        return try {
            val result = block(::updateSyncProgress)
            updateSyncProgress(96, labels.finishing)
            finishSyncFeedback(success = result.isSuccess)
            result.fold(
                onSuccess = { report -> operationHistoryStore.recordSuccess(operationId, labels.completed.withConflictCount(report)) },
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

    private fun syncOperationId(protocol: RemoteBackupProtocol): String =
        when (protocol) {
            RemoteBackupProtocol.FTP -> OperationHistoryStore.SYNC_FTP_LEGACY
            RemoteBackupProtocol.GITHUB -> OperationHistoryStore.SYNC_GITHUB
            RemoteBackupProtocol.SFTP -> OperationHistoryStore.SYNC_SFTP_LEGACY
        }

    private fun syncLabels(protocol: RemoteBackupProtocol): RemoteSyncLabels =
        when (protocol) {
            RemoteBackupProtocol.GITHUB -> RemoteSyncLabels(
                preparing = "Preparing GitHub sync",
                finishing = "Finishing GitHub sync",
                completed = "GitHub sync completed"
            )
            RemoteBackupProtocol.FTP -> RemoteSyncLabels(
                preparing = "Preparing FTP sync",
                finishing = "Finishing FTP sync",
                completed = "FTP sync completed"
            )
            RemoteBackupProtocol.SFTP -> RemoteSyncLabels(
                preparing = "Preparing SFTP sync",
                finishing = "Finishing SFTP sync",
                completed = "SFTP sync completed"
            )
        }

    private suspend fun currentTransport(): SyncTransport =
        when (userPreferences.remoteBackupProtocolFlow.first()) {
            RemoteBackupProtocol.FTP -> ftpBackupManager
            RemoteBackupProtocol.SFTP -> sftpBackupManager
            RemoteBackupProtocol.GITHUB -> gitHubSyncManager
        }

    private fun beginSyncFeedback(initialLabel: String) {
        synchronized(syncStateLock) {
            activeSyncs++
            if (activeSyncs == 1) {
                activeSyncFailed = false
                _syncInProgress.value = true
                _syncPendingOrInProgress.value = true
                _syncProgress.value = SyncProgressState(4, initialLabel)
            }
        }
    }

    private fun finishSyncFeedback(success: Boolean) {
        synchronized(syncStateLock) {
            if (!success) activeSyncFailed = true
            activeSyncs = (activeSyncs - 1).coerceAtLeast(0)
            if (activeSyncs == 0) {
                _syncInProgress.value = false
                if (debounceJob?.isActive != true) _syncPendingOrInProgress.value = false
                _lastSyncSucceeded.value = !activeSyncFailed
                _syncProgress.value = null
            }
        }
    }

    private fun updateSyncProgress(percent: Int, label: String) {
        synchronized(syncStateLock) {
            if (activeSyncs > 0) {
                _syncProgress.value = SyncProgressState(percent.coerceIn(0, 100), label)
            }
        }
    }

    /** Catches so a thrown failure is reported like a returned one and the run continues. */
    private suspend fun <T> attempt(
        destination: BackupDestination,
        block: suspend () -> Result<T>
    ): BackupRunResult = try {
        BackupRunResult(destination, block().exceptionOrNull())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w("BackupOperations", "Backup to $destination failed", t)
        BackupRunResult(destination, t)
    }

    private data class RemoteSyncLabels(
        val preparing: String,
        val finishing: String,
        val completed: String
    )

    private fun String.withConflictCount(report: SyncRunReport): String =
        if (report.conflictsResolved > 0) {
            "$this; ${report.conflictsResolved} conflict(s) resolved"
        } else {
            this
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
        val transport = currentTransport()
        val restorePoints = transport.listRestorePoints().getOrElse { return Result.failure(it) }
        val latest = restorePoints.firstOrNull()
            ?: return Result.failure(IllegalStateException("No server backups found yet"))
        val bytes = transport.readSnapshot(latest.id).getOrElse { return Result.failure(it) }
        return try {
            Result.success(
                compareBackupJsonWithTasks(
                    backupJsonBytes = bytes,
                    currentTasks = tasks,
                    backupCreatedTime = latest.createdAt?.toString() ?: latest.label
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
        syncSelfHostedWithProgress("Manual SFTP sync started") { progress ->
            sftpBackupManager.syncNow(progress)
        }.map { }

    suspend fun listSftpBackups(): Result<List<String>> = sftpBackupManager.listBackups()

    suspend fun restoreSftpBackup(filename: String): Result<Unit> = sftpBackupManager.restoreBackup(filename)

    suspend fun inspectSftpBackup(filename: String): Result<BackupSummary> =
        sftpBackupManager.inspectBackup(filename)

    suspend fun listRemoteRestorePoints(): Result<List<RestorePoint>> =
        currentTransport().listRestorePoints()

    suspend fun restoreRemoteSnapshot(id: String): Result<Unit> =
        currentTransport().restore(id)

    suspend fun inspectRemoteSnapshot(id: String): Result<BackupSummary> =
        currentTransport().inspect(id)

    suspend fun testFtpConnection(): Result<Unit> = ftpBackupManager.testConnection()

    suspend fun clearSelfHostedSyncLock(): Result<Unit> =
        currentTransport()
            .let { transport ->
                val lockable = transport as? LockableSyncTransport
                    ?: return Result.failure(IllegalStateException("This sync provider does not use a clearable lock"))
                val selectedProtocol = userPreferences.remoteBackupProtocolFlow.first()
                val operationId = syncOperationId(selectedProtocol)
                val protocol = selectedProtocol.name
                operationHistoryStore.recordRun(operationId, "Clearing remote $protocol sync lock")
                lockable.clearSyncLock()
                    .also { result ->
                        result.fold(
                            onSuccess = { operationHistoryStore.recordSkipped(operationId, "Remote $protocol sync lock cleared") },
                            onFailure = { operationHistoryStore.recordFailure(operationId, it) }
                        )
                    }
            }

    suspend fun ftpBackupNow(): Result<Unit> =
        syncSelfHostedWithProgress("Manual FTP sync started") { progress ->
            ftpBackupManager.syncNow(progress)
        }.map { }

    suspend fun listFtpBackups(): Result<List<String>> = ftpBackupManager.listBackups()

    suspend fun restoreFtpBackup(filename: String): Result<Unit> = ftpBackupManager.restoreBackup(filename)

    suspend fun inspectFtpBackup(filename: String): Result<BackupSummary> =
        ftpBackupManager.inspectBackup(filename)
}
