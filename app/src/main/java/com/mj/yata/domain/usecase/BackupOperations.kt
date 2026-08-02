package com.mj.yata.domain.usecase

import com.mj.yata.data.cloud.CloudBackupDiff
import com.mj.yata.data.cloud.CloudBackupEntry
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.data.ftp.FtpBackupManager
import com.mj.yata.data.local.backup.LocalBackupManager
import com.mj.yata.data.sftp.SftpBackupManager
import com.mj.yata.data.sftp.SftpConnectionTestResult
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.JsonExporter
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
    private val ftpBackupManager: FtpBackupManager
) {

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

    /** Reschedules the periodic Drive upload job. Not suspend — WorkManager enqueue is sync. */
    fun updateCloudBackupInterval(minutes: Long) = cloudBackupManager.updateBackupInterval(minutes)

    suspend fun backupLocalNow() = localBackupManager.backupNow()

    suspend fun restoreLatestLocalBackup(): Boolean = localBackupManager.restoreLatest().isSuccess

    suspend fun testSftpConnection(): SftpConnectionTestResult = sftpBackupManager.testConnection()

    fun updateSftpBackupInterval(minutes: Long) = sftpBackupManager.updateBackupInterval(minutes)

    suspend fun pinSftpHostKey(fingerprint: String) = sftpBackupManager.pinHostKey(fingerprint)

    suspend fun sftpBackupNow(): Result<Unit> = sftpBackupManager.backupNow()

    suspend fun listSftpBackups(): Result<List<String>> = sftpBackupManager.listBackups()

    suspend fun restoreSftpBackup(filename: String): Result<Unit> = sftpBackupManager.restoreBackup(filename)

    suspend fun testFtpConnection(): Result<Unit> = ftpBackupManager.testConnection()

    fun updateFtpBackupInterval(minutes: Long) = ftpBackupManager.updateBackupInterval(minutes)

    suspend fun ftpBackupNow(): Result<Unit> = ftpBackupManager.backupNow()

    suspend fun listFtpBackups(): Result<List<String>> = ftpBackupManager.listBackups()

    suspend fun restoreFtpBackup(filename: String): Result<Unit> = ftpBackupManager.restoreBackup(filename)
}
