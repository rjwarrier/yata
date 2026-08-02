package com.mj.yata.data.ftp

import android.content.Context
import android.util.Log
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sftp.SftpNotConfiguredException
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP/FTPS counterpart to [com.mj.yata.data.sftp.SftpBackupManager] — same JSON payload, same
 * host/port/username/remote-dir config (shared between the two protocols; see
 * [UserPreferences]'s doc comment on `SFTP_BACKUP_ENABLED` and neighbours), different transport.
 *
 * Unlike SFTP, there's no host-key/TOFU concept here: FTPS certificate validation goes through
 * the platform's own trust store via [FTPSClient], the same as any HTTPS connection this app
 * makes — no custom pinning, no bypass. A self-signed certificate on the user's own server will
 * fail to validate, same as it would in a browser; that's a real limitation of this
 * implementation (documented, not silently worked around by disabling validation) rather than a
 * gap in what FTPS itself supports.
 */
@Singleton
class FtpBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val credentialsStore: RemoteBackupCredentialsStore
) {
    companion object {
        private const val TAG = "FtpBackupManager"
        private const val KEEP_BACKUPS = 5
        private const val FILENAME_PREFIX = "yata_backup_"
        private const val FILENAME_SUFFIX = ".json"
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connect().disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "testConnection failed", e)
            Result.failure(e)
        }
    }

    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val (primaryJson, _) = jsonExporter.buildSplitBackupJson(archiveMonths = 0)
            val bytes = primaryJson.toString(2).toByteArray(Charsets.UTF_8)
            val filename = FILENAME_PREFIX +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                FILENAME_SUFFIX

            val client = connect()
            try {
                ensureRemoteDir(client, remoteDir)
                check(client.changeWorkingDirectory(remoteDir)) { "Could not open remote folder $remoteDir" }
                ByteArrayInputStream(bytes).use { input ->
                    check(client.storeFile(filename, input)) { "Upload failed: ${client.replyString}" }
                }
                pruneOldBackups(client, remoteDir)
            } finally {
                disconnectQuietly(client)
            }

            userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "backupNow failed", e)
            Result.failure(e)
        }
    }

    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val client = connect()
            val names = try {
                client.changeWorkingDirectory(remoteDir)
                client.listNames()
                    ?.filter { it.startsWith(FILENAME_PREFIX) }
                    ?: emptyList()
            } finally {
                disconnectQuietly(client)
            }
            Result.success(names.sortedDescending())
        } catch (e: Exception) {
            Log.w(TAG, "listBackups failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val client = connect()
            val bytes = try {
                client.changeWorkingDirectory(remoteDir)
                val out = ByteArrayOutputStream()
                check(client.retrieveFile(filename, out)) { "Download failed: ${client.replyString}" }
                out.toByteArray()
            } finally {
                disconnectQuietly(client)
            }
            if (jsonExporter.importBytes(bytes)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Restore failed - backup file unreadable"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreBackup failed", e)
            Result.failure(e)
        }
    }

    private suspend fun connect(): FTPClient {
        val host = userPreferences.sftpHostFlow.first()
        val port = userPreferences.sftpPortFlow.first()
        val username = userPreferences.sftpUsernameFlow.first()
        val useTls = userPreferences.ftpUseTlsFlow.first()

        if (host.isBlank() || username.isBlank()) {
            throw SftpNotConfiguredException("FTP host and username must be set")
        }
        val password = credentialsStore.password
            ?: throw SftpNotConfiguredException("No FTP password saved")

        // Explicit FTPS (AUTH TLS on the plain control port) rather than implicit FTPS (a
        // dedicated TLS-from-the-start port) -- explicit is what virtually every FTPS server
        // actually expects on the standard port 21, implicit is a legacy convention tied to a
        // different port that's uncommon on self-hosted setups.
        val client: FTPClient = if (useTls) FTPSClient(false) else FTPClient()
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.connect(host, port)
        val connectReply = client.replyCode
        if (!FTPReply.isPositiveCompletion(connectReply)) {
            client.disconnect()
            throw IllegalStateException("Server refused connection: ${client.replyString}")
        }

        try {
            if (client is FTPSClient) {
                // Protects the data channel too, not just the login -- without this, credentials
                // are encrypted but the actual file contents (the backup itself) would upload and
                // download in the clear.
                client.execPBSZ(0)
                client.execPROT("P")
            }
            if (!client.login(username, password)) {
                throw IllegalStateException("Login failed: ${client.replyString}")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
        } catch (e: Exception) {
            disconnectQuietly(client)
            throw e
        }
        return client
    }

    private fun disconnectQuietly(client: FTPClient) {
        try {
            if (client.isConnected) {
                client.logout()
                client.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "disconnect failed", e)
        }
    }

    private fun ensureRemoteDir(client: FTPClient, dir: String) {
        if (client.changeWorkingDirectory(dir)) return
        // makeDirectory only creates one level -- build it up segment by segment for a nested
        // path, same as the SFTP side's mkdirs does natively.
        var path = ""
        for (segment in dir.trim('/').split("/")) {
            if (segment.isEmpty()) continue
            path += "/$segment"
            client.makeDirectory(path)
        }
    }

    private fun pruneOldBackups(client: FTPClient, dir: String) {
        val names = client.listNames()?.filter { it.startsWith(FILENAME_PREFIX) }?.sortedByDescending { it } ?: return
        names.drop(KEEP_BACKUPS).forEach { name ->
            try {
                client.deleteFile(name)
            } catch (e: Exception) {
                Log.w(TAG, "pruneOldBackups: failed to delete $name", e)
            }
        }
    }

    /** Reschedules the periodic upload job — not suspend, WorkManager enqueue is sync. */
    fun updateBackupInterval(intervalMinutes: Long) {
        FtpBackupWorker.schedule(context, intervalMinutes, androidx.work.ExistingPeriodicWorkPolicy.UPDATE)
    }
}
