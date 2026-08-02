package com.mj.yata.data.sftp

import android.content.Context
import android.util.Log
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.PublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of [SftpBackupManager.testConnection] — always carries the server's host key
 * fingerprint when the transport got far enough to see one, success or not, so the caller can
 * offer to pin it (first connection) or warn that it changed (host key mismatch). */
data class SftpConnectionTestResult(
    val success: Boolean,
    val fingerprint: String?,
    val error: Throwable?
)

/** Thrown by [SftpBackupManager] when a connection is attempted with required fields unset. */
class SftpNotConfiguredException(message: String) : Exception(message)

/**
 * On-device counterpart to [com.mj.yata.data.cloud.CloudBackupManager] and
 * [com.mj.yata.data.local.backup.LocalBackupManager] — same JSON payload (via [JsonExporter]),
 * uploaded over SFTP to a server the user supplies, instead of Google Drive or app-private
 * storage. No third-party account, no vendor lock-in to a specific host — anything that speaks
 * SFTP works.
 *
 * Host key verification is TOFU (trust-on-first-use), matching how every mainstream SSH client
 * behaves: the fingerprint observed on the first successful connection is pinned in
 * [UserPreferences.sftpHostKeyFingerprintFlow], and every later connection must present the exact
 * same key or the connection is refused outright — never silently downgraded to "accept
 * anything," which is what would make this a real man-in-the-middle hole rather than a
 * self-hosted convenience. [testConnection] is the only entry point that connects when nothing is
 * pinned yet, specifically so the UI can show the fingerprint and ask the user to confirm it
 * before [backupNow]/[restoreBackup] (used by the scheduled worker too) will pin anything.
 */
@Singleton
class SftpBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val credentialsStore: SftpCredentialsStore
) {
    companion object {
        private const val TAG = "SftpBackupManager"
        private const val KEEP_BACKUPS = 5
        private const val FILENAME_PREFIX = "yata_backup_"
        private const val FILENAME_SUFFIX = ".json"
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    /** Pins [fingerprint] as the trusted host key — called only after the user has explicitly
     * confirmed it (first connection) or deliberately chosen to trust a changed key. */
    suspend fun pinHostKey(fingerprint: String) {
        userPreferences.setSftpHostKeyFingerprint(fingerprint)
    }

    /** Reschedules the periodic upload job — not suspend, WorkManager enqueue is sync, matching
     * [com.mj.yata.data.cloud.CloudBackupManager.updateBackupInterval]'s shape. */
    fun updateBackupInterval(intervalMinutes: Long) {
        SftpBackupWorker.schedule(context, intervalMinutes, androidx.work.ExistingPeriodicWorkPolicy.UPDATE)
    }

    suspend fun testConnection(): SftpConnectionTestResult = withContext(Dispatchers.IO) {
        var observed: String? = null
        try {
            val ssh = buildClient { observed = it }
            ssh.disconnect()
            SftpConnectionTestResult(success = true, fingerprint = observed, error = null)
        } catch (e: Exception) {
            Log.w(TAG, "testConnection failed", e)
            SftpConnectionTestResult(success = false, fingerprint = observed, error = e)
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

            val tempFile = File.createTempFile("sftp_upload", ".json", context.cacheDir)
            try {
                tempFile.writeBytes(bytes)
                buildClient().use { ssh ->
                    ssh.newSFTPClient().use { sftp ->
                        ensureRemoteDir(sftp, remoteDir)
                        sftp.put(tempFile.absolutePath, "$remoteDir/$filename")
                        pruneOldBackups(sftp, remoteDir)
                    }
                }
            } finally {
                tempFile.delete()
            }

            userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "backupNow failed", e)
            Result.failure(e)
        }
    }

    /** Filenames only, newest first — the same ordering [pruneOldBackups] relies on
     * (timestamp-embedded names sort correctly as plain strings). */
    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val names = buildClient().use { ssh ->
                ssh.newSFTPClient().use { sftp ->
                    sftp.ls(remoteDir)
                        .filter { it.name.startsWith(FILENAME_PREFIX) }
                        .map { it.name }
                }
            }
            Result.success(names.sortedDescending())
        } catch (e: Exception) {
            Log.w(TAG, "listBackups failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("sftp_download", ".json", context.cacheDir)
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            buildClient().use { ssh ->
                ssh.newSFTPClient().use { sftp -> sftp.get("$remoteDir/$filename", tempFile.absolutePath) }
            }
            val bytes = tempFile.readBytes()
            if (jsonExporter.importBytes(bytes)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Restore failed - backup file unreadable"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "restoreBackup failed", e)
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    /** [onHostKeyObserved] fires the moment a host key is presented, before verification decides
     * whether to accept it — so callers can capture the fingerprint even when verification then
     * rejects it (a changed key) or auth afterward fails (wrong password), neither of which is
     * the caller finding out what key the server actually offered. */
    private suspend fun buildClient(onHostKeyObserved: (String) -> Unit = {}): SSHClient {
        val host = userPreferences.sftpHostFlow.first()
        val port = userPreferences.sftpPortFlow.first()
        val username = userPreferences.sftpUsernameFlow.first()
        val authMethod = userPreferences.sftpAuthMethodFlow.first()
        val pinnedFingerprint = userPreferences.sftpHostKeyFingerprintFlow.first()

        if (host.isBlank() || username.isBlank()) {
            throw SftpNotConfiguredException("SFTP host and username must be set")
        }

        val ssh = SSHClient()
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = fingerprintOf(key)
                onHostKeyObserved(fingerprint)
                return pinnedFingerprint == null || pinnedFingerprint == fingerprint
            }

            // Used by sshj to prefer already-known key algorithms during negotiation (the way an
            // OpenSSH known_hosts file would). We only ever pin one fingerprint, not a per-algorithm
            // set, so there's nothing to report -- doesn't affect correctness, only negotiation order.
            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })

        try {
            ssh.connect(host, port)
            when (authMethod) {
                "PRIVATE_KEY" -> authWithPrivateKey(ssh, username)
                else -> authWithPassword(ssh, username)
            }
        } catch (e: Exception) {
            runCatching { ssh.disconnect() }
            throw e
        }
        return ssh
    }

    private fun authWithPassword(ssh: SSHClient, username: String) {
        val password = credentialsStore.password
            ?: throw SftpNotConfiguredException("No SFTP password saved")
        ssh.authPassword(username, password)
    }

    private fun authWithPrivateKey(ssh: SSHClient, username: String) {
        val pem = credentialsStore.privateKeyPem
            ?: throw SftpNotConfiguredException("No SFTP private key saved")
        // sshj's string-content key loading goes through a temp file rather than a raw-PEM
        // overload -- written to the app's own cache dir (never external storage) and deleted
        // immediately after, whether auth succeeds or not.
        val tempKeyFile = File.createTempFile("sftp_key", ".pem", context.cacheDir)
        try {
            tempKeyFile.writeText(pem)
            val passphrase = credentialsStore.passphrase
            val keyProvider = if (passphrase.isNullOrEmpty()) {
                ssh.loadKeys(tempKeyFile.absolutePath)
            } else {
                ssh.loadKeys(tempKeyFile.absolutePath, passphrase)
            }
            ssh.authPublickey(username, keyProvider)
        } finally {
            tempKeyFile.delete()
        }
    }

    private fun fingerprintOf(key: PublicKey): String = SecurityUtils.getFingerprint(key)

    private fun ensureRemoteDir(sftp: SFTPClient, dir: String) {
        try {
            sftp.stat(dir)
        } catch (e: net.schmizz.sshj.sftp.SFTPException) {
            sftp.mkdirs(dir)
        }
    }

    private fun pruneOldBackups(sftp: SFTPClient, dir: String) {
        val files = sftp.ls(dir)
            .filter { it.name.startsWith(FILENAME_PREFIX) }
            .sortedByDescending { it.name }
        files.drop(KEEP_BACKUPS).forEach { entry ->
            try {
                sftp.rm("$dir/${entry.name}")
            } catch (e: Exception) {
                Log.w(TAG, "pruneOldBackups: failed to delete ${entry.name}", e)
            }
        }
    }
}
