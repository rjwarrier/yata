package com.mj.yata.data.ftp

import android.content.Context
import android.util.Log
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sftp.SftpNotConfiguredException
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.util.BackupCrypto
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
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
        private const val FILENAME_PREFIX = "yata_backup_"
        // Zipped rather than raw JSON: a backup carrying base64 photo bytes is large, and the
        // less time the data connection is open the less there is to go wrong on it.
        private const val FILENAME_SUFFIX = ".zip"
        private const val ENCRYPTED_SUFFIX = ".zip.enc"
        private const val ZIP_ENTRY_NAME = "backup.json"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val DATA_TIMEOUT_MS = 60_000
        private const val UPLOAD_BUFFER_BYTES = 64 * 1024
    }

    /**
     * One FTP session at a time. Tapping "Back up now" twice used to open two simultaneous
     * sessions from the same IP, and shared hosting caps concurrent FTP connections per account --
     * the server resolves that by killing a session mid-transfer, which is exactly how a
     * half-written backup ends up on the server.
     */
    private val sessionMutex = Mutex()

    suspend fun testConnection(): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // Log out with QUIT rather than dropping the socket -- some servers (vsftpd's
                // max_per_ip, fail2ban) treat an abrupt disconnect as abusive and briefly block
                // the client IP, which then fails the very next real connection attempt.
                disconnectQuietly(connect())
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "testConnection failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun backupNow(): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val remoteDir = userPreferences.sftpRemoteDirFlow.first()
                val keepCount = userPreferences.sftpKeepCountFlow.first()
                val (primaryJson, _) = jsonExporter.buildSplitBackupJson(archiveMonths = 0)
                val jsonBytes = primaryJson.toString(2).toByteArray(Charsets.UTF_8)
                val zipped = zip(jsonBytes)
                val backupPassphrase = credentialsStore.backupPassphrase
                val bytes = if (backupPassphrase != null) {
                    BackupCrypto.encrypt(zipped, backupPassphrase)
                } else {
                    zipped
                }
                val filename = FILENAME_PREFIX +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                    (if (backupPassphrase != null) ENCRYPTED_SUFFIX else FILENAME_SUFFIX)

                // The transfer runs NonCancellable once it has started. Cancelling mid-upload
                // (screen closed, scope torn down) drops the socket without a TLS shutdown, and
                // the server keeps whatever arrived -- a truncated file that still looks like a
                // backup. Finishing or failing loudly are the only two acceptable outcomes.
                withContext(NonCancellable) {
                    val client = connect()
                    try {
                        ensureRemoteDir(client, remoteDir)
                        check(client.changeWorkingDirectory(remoteDir)) { "Could not open remote folder $remoteDir" }
                        upload(client, filename, bytes)
                        pruneOldBackups(client, remoteDir, keepCount)
                    } finally {
                        disconnectQuietly(client)
                    }
                }

                userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "backupNow failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Uploads and then *verifies*. `storeFile`-style success only means the client finished
     * writing; a data connection that dies mid-stream can still leave a partial file behind, and
     * a backup that restores to a fraction of your data is worse than one that never happened.
     * Anything short of a byte-exact match is deleted and reported as a failure.
     */
    private fun upload(client: FTPClient, filename: String, bytes: ByteArray) {
        val stream = client.storeFileStream(filename)
        if (stream == null) {
            // The server accepts STOR and creates the (empty) file before the data connection is
            // established, so a data-connection failure still leaves a 0-byte file behind that
            // looks like a backup. Clean it up rather than leave that lying around.
            deleteQuietly(client, filename)
            throw IllegalStateException("Could not open data connection: ${client.replyString.trim()}")
        }
        // Flush before close so the payload is on the wire ahead of the TLS shutdown, then let
        // completePendingCommand read the server's real verdict (226 transferred vs 451 aborted).
        stream.use {
            it.write(bytes)
            it.flush()
        }
        if (!client.completePendingCommand()) {
            deleteQuietly(client, filename)
            throw IllegalStateException("Transfer aborted by server: ${client.replyString.trim()}")
        }
        val uploaded = remoteSize(client, filename)
        if (uploaded != null && uploaded != bytes.size.toLong()) {
            deleteQuietly(client, filename)
            throw IllegalStateException(
                "Upload truncated — $uploaded of ${bytes.size} bytes reached the server"
            )
        }
    }

    /** SIZE on the just-uploaded file; null when the server doesn't support it. */
    private fun remoteSize(client: FTPClient, filename: String): Long? = try {
        if (FTPReply.isPositiveCompletion(client.sendCommand("SIZE", filename))) {
            client.replyString.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "SIZE check failed for $filename", e)
        null
    }

    private fun deleteQuietly(client: FTPClient, filename: String) {
        try {
            client.deleteFile(filename)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove partial upload $filename", e)
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

    suspend fun restoreBackup(filename: String): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val jsonBytes = fetchBackupJson(filename)
                if (jsonExporter.importBytes(jsonBytes)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Restore failed - backup file unreadable"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "restoreBackup failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Reads a backup's contents without importing it, so the confirm dialog can say what restoring
     * would actually bring back. Same download-and-decode path as [restoreBackup] — a backup that
     * can't be summarised is one that couldn't have been restored either, and the user finds that
     * out before the destructive step rather than during it.
     */
    suspend fun inspectBackup(filename: String): Result<BackupSummary> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                Result.success(jsonExporter.summarise(fetchBackupJson(filename)))
            } catch (e: Exception) {
                Log.w(TAG, "inspectBackup failed", e)
                Result.failure(e)
            }
        }
    }

    /** Downloads, verifies length, decrypts if needed, unzips if needed. */
    private suspend fun fetchBackupJson(filename: String): ByteArray {
        val remoteDir = userPreferences.sftpRemoteDirFlow.first()
        val client = connect()
        val bytes = try {
            client.changeWorkingDirectory(remoteDir)
            val expected = remoteSize(client, filename)
            val out = ByteArrayOutputStream()
            check(client.retrieveFile(filename, out)) { "Download failed: ${client.replyString}" }
            val downloaded = out.toByteArray()
            // Same reasoning as the upload check, in reverse: importing a half-downloaded
            // backup would overwrite live data with a fragment of itself.
            if (expected != null && expected != downloaded.size.toLong()) {
                throw IllegalStateException(
                    "Download truncated — got ${downloaded.size} of $expected bytes"
                )
            }
            downloaded
        } finally {
            disconnectQuietly(client)
        }

        // Decided by the file's own header rather than its name, so a backup stays restorable
        // even if it gets renamed on the server.
        val decrypted = if (BackupCrypto.isEncrypted(bytes)) {
            val passphrase = credentialsStore.backupPassphrase
                ?: throw IllegalStateException(
                    "This backup is encrypted — set the backup passphrase before restoring"
                )
            try {
                BackupCrypto.decrypt(bytes, passphrase)
            } catch (e: Exception) {
                throw IllegalStateException("Wrong passphrase, or the backup is damaged", e)
            }
        } else {
            bytes
        }
        return if (isZip(decrypted)) unzip(decrypted) else decrypted
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
            // Bounded reads on both channels: without these a half-dead connection hangs the
            // backup indefinitely instead of failing.
            client.soTimeout = DATA_TIMEOUT_MS
            client.setDataTimeout(Duration.ofMillis(DATA_TIMEOUT_MS.toLong()))
            client.bufferSize = UPLOAD_BUFFER_BYTES

            if (!client.login(username, password)) {
                throw IllegalStateException("Login failed: ${client.replyString}")
            }
            if (client is FTPSClient) {
                // PBSZ/PROT are issued *after* login: that's the order RFC 4217 clients
                // conventionally use, and some servers quietly drop a protection level negotiated
                // before authentication, leaving client and server disagreeing about whether the
                // data channel is encrypted.
                client.execPBSZ(0)
                // "P" encrypts the data channel as well as the control channel, and is what we ask
                // for whenever the payload itself isn't already encrypted.
                //
                // "C" (clear data channel, encrypted control channel) is used once the user has set
                // a backup passphrase, because many FTPS servers -- Pure-FTPd and ProFTPd on shared
                // cPanel hosting among them -- require the data connection to *resume the control
                // connection's TLS session*. Android's TLS stack can't do that resumption (desktop
                // clients built on OpenSSL can, which is why FileZilla succeeds where this fails),
                // so a PROT P data connection is refused outright and the upload lands as a 0-byte
                // file. With an encrypted payload the file is protected in transit and at rest by
                // its own AES-GCM layer, so the data channel carries ciphertext either way; the
                // login still goes over TLS on the control channel.
                client.execPROT(if (credentialsStore.backupPassphrase != null) "C" else "P")
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
        } catch (e: Exception) {
            disconnectQuietly(client)
            throw e
        }
        return client
    }

    private fun zip(jsonBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(ZIP_ENTRY_NAME))
            zip.write(jsonBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** PK — the local file header every zip starts with. */
    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun unzip(zipBytes: ByteArray): ByteArray {
        java.util.zip.ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            zip.nextEntry ?: throw IllegalStateException("Backup archive is empty")
            return zip.readBytes()
        }
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

    private fun pruneOldBackups(client: FTPClient, dir: String, keepCount: Int) {
        val names = client.listNames()?.filter { it.startsWith(FILENAME_PREFIX) }?.sortedByDescending { it } ?: return
        names.drop(keepCount).forEach { name ->
            try {
                client.deleteFile(name)
            } catch (e: Exception) {
                Log.w(TAG, "pruneOldBackups: failed to delete $name", e)
            }
        }
    }

}
