package com.mj.yata.domain.usecase

import com.mj.yata.data.cloud.CloudBackupDiff
import com.mj.yata.data.cloud.CloudBackupEntry
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.data.ftp.FtpBackupManager
import com.mj.yata.data.local.backup.LocalBackupManager
import com.mj.yata.data.sftp.SftpBackupManager
import com.mj.yata.data.sftp.SftpConnectionTestResult
import android.content.Context
import android.util.Log
import com.mj.yata.data.backup.UnifiedBackupWorker
import com.mj.yata.data.local.datastore.UserPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup triggers extracted from MainViewModel — Drive (cloud), on-device (local), and the
 * export-then-wipe path. Only the "do it now" actions live here; the enabled/interval/wifi-only
 * *preferences* stay on the ViewModel with the rest of the DataStore setters, since those are
 * plain writes with no orchestration.
 */
@Singleton
class BackupOperations @Inject constructor(
    private val repository: YataRepository,
    private val jsonExporter: JsonExporter,
    private val cloudBackupManager: CloudBackupManager,
    private val localBackupManager: LocalBackupManager,
    private val sftpBackupManager: SftpBackupManager,
    private val ftpBackupManager: FtpBackupManager,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) {

    private companion object {
        /** Matches the window the Drive-only debounce used, so edit bursts behave as before. */
        const val DEBOUNCE_MILLIS = 2 * 60 * 1000L
    }

    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    /**
     * Backs up to every destination the user has switched on, whatever triggered it — the manual
     * button, the periodic worker, or the debounced run after a task changes. Configuring two
     * destinations is a statement that you want two copies; a trigger that only refreshed one of
     * them would leave the other quietly rotting until the day it was needed.
     *
     * Runs sequentially and never lets one destination's failure stop the next: the whole value of
     * a second destination is that it still works when the first doesn't. Returns one result per
     * *attempted* destination, so callers can report "Drive ok, server failed" rather than a single
     * verdict that's wrong for at least one of them. Destinations that are switched off aren't
     * attempted and don't appear in the list.
     */
    suspend fun backupAllConfigured(): List<BackupRunResult> = buildList {
        if (userPreferences.cloudBackupEnabledFlow.first()) {
            add(attempt(BackupDestination.CLOUD) { cloudBackupManager.backupNow() })
        }
        if (userPreferences.localBackupEnabledFlow.first()) {
            add(attempt(BackupDestination.LOCAL) { localBackupManager.backupNow() })
        }
        // Host check as well as the toggle: the switch can be on with the server dialog never
        // filled in, and an attempt that can only fail would report a backup failure for something
        // the user never actually set up.
        if (userPreferences.sftpBackupEnabledFlow.first() &&
            userPreferences.sftpHostFlow.first().isNotBlank()
        ) {
            val useFtp = userPreferences.remoteBackupProtocolFlow.first() == RemoteBackupProtocol.FTP
            add(
                attempt(BackupDestination.SELF_HOSTED) {
                    if (useFtp) ftpBackupManager.backupNow() else sftpBackupManager.backupNow()
                }
            )
        }
    }

    /**
     * Coalesces a burst of edits into one backup of every destination, [DEBOUNCE_MILLIS] after the
     * last change. Replaces the Drive-only debounce this used to call, which left the other
     * destinations only as fresh as their next scheduled run.
     *
     * Scoped to the process rather than any caller: the point is to still be running two minutes
     * after the screen that triggered it went away.
     */
    fun scheduleDebouncedBackup() {
        debounceJob?.cancel()
        debounceJob = debounceScope.launch {
            delay(DEBOUNCE_MILLIS)
            backupAllConfigured()
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

    suspend fun cloudSignOut() = cloudBackupManager.signOut()

    suspend fun cloudBackupNow(): Result<Unit> = cloudBackupManager.backupNow()

    suspend fun listCloudBackups(): Result<List<CloudBackupEntry>> = cloudBackupManager.listBackups()

    suspend fun restoreCloudBackup(fileId: String): Result<Unit> = cloudBackupManager.restoreBackup(fileId)

    suspend fun compareWithLastBackup(tasks: List<Task>): Result<CloudBackupDiff> =
        cloudBackupManager.compareWithLatestBackup(tasks)

    /** Reschedules the single periodic backup job that covers every destination. */
    fun updateBackupInterval(minutes: Long) =
        UnifiedBackupWorker.schedule(context, minutes, androidx.work.ExistingPeriodicWorkPolicy.UPDATE)

    /** Reschedules the periodic Drive upload job. Not suspend — WorkManager enqueue is sync. */
    fun updateCloudBackupInterval(minutes: Long) = cloudBackupManager.updateBackupInterval(minutes)

    suspend fun backupLocalNow() = localBackupManager.backupNow()

    suspend fun restoreLatestLocalBackup(): Boolean = localBackupManager.restoreLatest().isSuccess

    suspend fun testSftpConnection(): SftpConnectionTestResult = sftpBackupManager.testConnection()

    suspend fun pinSftpHostKey(fingerprint: String) = sftpBackupManager.pinHostKey(fingerprint)

    suspend fun sftpBackupNow(): Result<Unit> = sftpBackupManager.backupNow()

    suspend fun listSftpBackups(): Result<List<String>> = sftpBackupManager.listBackups()

    suspend fun restoreSftpBackup(filename: String): Result<Unit> = sftpBackupManager.restoreBackup(filename)

    suspend fun inspectSftpBackup(filename: String): Result<BackupSummary> =
        sftpBackupManager.inspectBackup(filename)

    suspend fun testFtpConnection(): Result<Unit> = ftpBackupManager.testConnection()

    suspend fun ftpBackupNow(): Result<Unit> = ftpBackupManager.backupNow()

    suspend fun listFtpBackups(): Result<List<String>> = ftpBackupManager.listBackups()

    suspend fun restoreFtpBackup(filename: String): Result<Unit> = ftpBackupManager.restoreBackup(filename)

    suspend fun inspectFtpBackup(filename: String): Result<BackupSummary> =
        ftpBackupManager.inspectBackup(filename)
}
