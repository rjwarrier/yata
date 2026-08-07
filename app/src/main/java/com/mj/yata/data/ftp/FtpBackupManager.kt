package com.mj.yata.data.ftp

import android.content.Context
import android.os.Build
import android.util.Log
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sftp.SftpNotConfiguredException
import com.mj.yata.data.sync.SnapshotSyncEngine
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.model.SyncLockBusyException
import com.mj.yata.domain.model.SyncLockInfo
import com.mj.yata.domain.sync.LockableSyncTransport
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncRunReport
import com.mj.yata.domain.sync.restorePointFromHistoryName
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
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * FTP/FTPS counterpart to [com.mj.yata.data.sftp.SftpBackupManager] — same JSON payload, same
 * host/port/username/remote-dir config (shared between the two protocols; see
 * [UserPreferences]'s doc comment on `SFTP_BACKUP_ENABLED` and neighbours), different transport.
 *
 * Unlike SFTP, there's no host-key/TOFU concept here. FTPS still protects the control channel
 * and, when possible, the data channel, but a lot of self-hosted FTP servers use legacy
 * certificates without proper subjectAltName entries. Enforcing browser-style hostname checks
 * would break those existing setups, so this client validates the certificate chain without
 * requiring a hostname/SAN match.
 */
@Singleton
class FtpBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val credentialsStore: RemoteBackupCredentialsStore,
    private val snapshotSyncEngine: SnapshotSyncEngine,
    private val recoveryBackupManager: RecoveryBackupManager
) : LockableSyncTransport {
    companion object {
        private const val TAG = "FtpBackupManager"
        private const val FILENAME_PREFIX = "yata_backup_"
        // Zipped rather than raw JSON: a backup carrying base64 photo bytes is large, and the
        // less time the data connection is open the less there is to go wrong on it.
        private const val FILENAME_SUFFIX = ".zip"
        private const val ENCRYPTED_SUFFIX = ".zip.enc"
        private const val ZIP_ENTRY_NAME = "backup.json"
        private const val SYNC_FILENAME = "yata_sync_v1.data"
        private const val SYNC_PREVIOUS_FILENAME = ".yata_sync_v1.previous"
        private const val SYNC_LOCK_DIR = ".yata_sync_v1.lock"
        private const val SYNC_LEASE_FILE = "lease"
        private const val SYNC_LOCK_STALE_MILLIS = 60 * 60 * 1000L
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

    override suspend fun clearSyncLock(): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val remoteDir = userPreferences.sftpRemoteDirFlow.first()
                val client = connect()
                try {
                    ensureRemoteDir(client, remoteDir)
                    check(client.changeWorkingDirectory(remoteDir)) {
                        "Could not open remote folder $remoteDir"
                    }
                    clearSyncLockDirectory(client)
                    Result.success(Unit)
                } finally {
                    disconnectQuietly(client)
                }
            } catch (e: Exception) {
                Log.w(TAG, "clearSyncLock failed", e)
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
                        val temporaryFilename = ".$filename.part"
                        upload(client, temporaryFilename, bytes)
                        if (!client.rename(temporaryFilename, filename)) {
                            deleteQuietly(client, temporaryFilename)
                            throw IllegalStateException(
                                "Could not publish completed backup: ${client.replyString.trim()}"
                            )
                        }
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

    /** Full two-way sync using only generic FTP/FTPS file operations. */
    override suspend fun syncNow(progress: (Int, String) -> Unit): Result<SyncRunReport> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                var conflictsResolved = 0
                val remoteDir = userPreferences.sftpRemoteDirFlow.first()
                val keepCount = userPreferences.sftpKeepCountFlow.first()
                val host = userPreferences.sftpHostFlow.first()
                val port = userPreferences.sftpPortFlow.first()
                val username = userPreferences.sftpUsernameFlow.first()
                val normalizedHost = host.trim().trimEnd('.').lowercase(Locale.ROOT)

                withContext(NonCancellable) {
                    progress(12, "Connecting to server")
                    val client = connect()
                    try {
                        progress(24, "Opening remote folder")
                        ensureRemoteDir(client, remoteDir)
                        check(client.changeWorkingDirectory(remoteDir)) {
                            "Could not open remote folder $remoteDir"
                        }
                        val canonicalRemoteDir = client.printWorkingDirectory()
                            ?.trim()
                            ?.trimEnd('/')
                            ?.ifEmpty { "/" }
                            ?: error("The FTP server did not report its current folder")
                        val scopeKey =
                            "ftp|$username@$normalizedHost:$port|$canonicalRemoteDir"
                        progress(34, "Checking sync lock")
                        val lease = acquireSyncLock(client)
                        try {
                            progress(46, "Reading server changes")
                            val remote = readValidSyncSource(client)
                            progress(60, "Merging changes")
                            val prepared = snapshotSyncEngine.prepare(
                                remoteBytes = remote.jsonBytes,
                                scopeKey = scopeKey,
                                remoteIsRecovery =
                                    remote.jsonBytes != null && !remote.canonicalHeadValid
                            )
                            val publish =
                                prepared.remoteNeedsPublish || !remote.canonicalHeadValid
                            val encoded = if (publish) {
                                encodePayload(prepared.canonicalBytes)
                            } else {
                                null
                            }
                            if (encoded != null) {
                                progress(76, "Uploading changes")
                                publishCanonicalSnapshot(
                                    client = client,
                                    bytes = encoded,
                                    previousBytes = remote.encodedBytes,
                                    hadCurrent = remote.hadCanonical,
                                    lease = lease
                                )
                            } else {
                                progress(76, "Already up to date")
                            }
                            progress(86, "Applying updates")
                            conflictsResolved = snapshotSyncEngine.commit(prepared)

                            if (encoded != null) {
                                progress(92, "Saving backup copy")
                                val encrypted = credentialsStore.backupPassphrase != null
                                val backupName = FILENAME_PREFIX +
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                                    (if (encrypted) ENCRYPTED_SUFFIX else FILENAME_SUFFIX)
                                publishNewFile(client, backupName, encoded)
                                pruneOldBackups(client, remoteDir, keepCount)
                            }
                        } finally {
                            releaseSyncLock(client, lease)
                        }
                    } finally {
                        disconnectQuietly(client)
                    }
                }

                userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
                Result.success(SyncRunReport(conflictsResolved = conflictsResolved))
            } catch (e: Exception) {
                Log.w(TAG, "syncNow failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Uploads and then verifies. A successful write alone does not prove the server received the
     * complete object; anything short of a byte-exact match is deleted and reported as failure.
     */
    private fun upload(client: FTPClient, filename: String, bytes: ByteArray) {
        var commandCompleted = false
        try {
            val stream = openStoreFileStream(client, filename)
            // Flush before close so the payload is on the wire ahead of the TLS shutdown, then let
            // completePendingCommand read the server's real verdict (226 transferred vs 451 aborted).
            stream.use {
                it.write(bytes)
                it.flush()
            }
            val completed = client.completePendingCommand()
            commandCompleted = true
            if (!completed) {
                throw IllegalStateException("Transfer aborted by server: ${client.replyString.trim()}")
            }
            val uploaded = remoteSize(client, filename)
            if (uploaded != null && uploaded != bytes.size.toLong()) {
                throw IllegalStateException(
                    "Upload truncated — $uploaded of ${bytes.size} bytes reached the server"
                )
            }
        } catch (t: Throwable) {
            // A control command cannot be issued until the pending STOR reply is consumed. Do this
            // best-effort after a write/close failure, then remove the unpublished temporary file.
            if (!commandCompleted) runCatching { client.completePendingCommand() }
            deleteQuietly(client, filename)
            throw t
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

    private fun encodePayload(jsonBytes: ByteArray): ByteArray {
        val zipped = zip(jsonBytes)
        val passphrase = credentialsStore.backupPassphrase
        return if (passphrase == null) zipped else BackupCrypto.encrypt(zipped, passphrase)
    }

    private fun decodePayload(bytes: ByteArray): ByteArray {
        val decrypted = if (BackupCrypto.isEncrypted(bytes)) {
            val passphrase = credentialsStore.backupPassphrase
                ?: throw IllegalStateException(
                    "This sync snapshot is encrypted — set the backup passphrase first"
                )
            try {
                BackupCrypto.decrypt(bytes, passphrase)
            } catch (e: Exception) {
                throw IllegalStateException("Wrong passphrase, or the sync snapshot is damaged", e)
            }
        } else {
            bytes
        }
        return if (isZip(decrypted)) unzip(decrypted) else decrypted
    }

    private data class RemoteSyncSource(
        val jsonBytes: ByteArray?,
        val encodedBytes: ByteArray?,
        val hadCanonical: Boolean,
        val canonicalHeadValid: Boolean
    )

    /**
     * Tries recovery candidates in durability order. A damaged head must not hide a usable
     * previous/history snapshot, but finding remote files and validating none of them must also
     * never be mistaken for a brand-new empty server.
     */
    private fun readValidSyncSource(client: FTPClient): RemoteSyncSource {
        var sawCandidate = false
        val currentPayload = readRemoteBytesOrNull(client, SYNC_FILENAME)

        fun acceptIfValid(label: String, payload: ByteArray?): RemoteSyncSource? {
            if (payload == null) return null
            sawCandidate = true
            val decoded = try {
                decodePayload(payload)
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring unreadable FTP sync candidate $label", e)
                return null
            }
            if (!snapshotSyncEngine.isValidRemoteSnapshot(decoded)) {
                Log.w(TAG, "Ignoring invalid FTP sync candidate $label")
                return null
            }
            return RemoteSyncSource(
                jsonBytes = decoded,
                encodedBytes = payload,
                hadCanonical = currentPayload != null,
                canonicalHeadValid = label == SYNC_FILENAME
            )
        }

        acceptIfValid(SYNC_FILENAME, currentPayload)?.let { return it }

        listHistoryBackupNames(client).forEach { filename ->
            // The listing itself observed a candidate. If it disappears before RETR, fail closed
            // rather than reclassifying a raced/unstable remote as a brand-new empty server.
            sawCandidate = true
            val payload = readRemoteBytesOrNull(client, filename)
            acceptIfValid(filename, payload)?.let { return it }
        }

        val previousPayload = readRemoteBytesOrNull(client, SYNC_PREVIOUS_FILENAME)
        acceptIfValid(SYNC_PREVIOUS_FILENAME, previousPayload)?.let { return it }

        check(!sawCandidate) {
            "The server contains sync/backup files, but none is a valid YATA snapshot"
        }
        return RemoteSyncSource(
            jsonBytes = null,
            encodedBytes = null,
            hadCanonical = currentPayload != null,
            canonicalHeadValid = false
        )
    }

    private fun readRemoteBytesOrNull(client: FTPClient, filename: String): ByteArray? {
        val expected = remoteSize(client, filename)
        val out = ByteArrayOutputStream()
        if (!retrieveFileWithPassiveFallback(client, filename, out)) {
            if (client.replyCode == 550 && isDefinitelyAbsent(client, filename)) return null
            throw IllegalStateException("Download failed: ${client.replyString.trim()}")
        }
        return out.toByteArray().also { downloaded ->
            if (expected != null && expected != downloaded.size.toLong()) {
                throw IllegalStateException(
                    "Download truncated — got ${downloaded.size} of $expected bytes"
                )
            }
        }
    }

    /** A 550 can also mean permission denied; only a successful parent listing proves absence. */
    private fun isDefinitelyAbsent(client: FTPClient, filename: String): Boolean {
        val normalized = filename.trimEnd('/')
        val separator = normalized.lastIndexOf('/')
        val parent = if (separator < 0) "." else normalized.substring(0, separator).ifBlank { "/" }
        val leaf = normalized.substring(separator + 1)
        val entries = client.listFiles(parent)
        check(FTPReply.isPositiveCompletion(client.replyCode)) {
            "Could not verify whether $filename is absent: ${client.replyString.trim()}"
        }
        return entries.none { entry ->
            entry.name.trimEnd('/').substringAfterLast('/') == leaf
        }
    }

    private fun publishCanonicalSnapshot(
        client: FTPClient,
        bytes: ByteArray,
        previousBytes: ByteArray?,
        hadCurrent: Boolean,
        lease: SyncLease
    ) {
        val temporary = ".$SYNC_FILENAME.${UUID.randomUUID()}.part"
        val previousTemporary = ".$SYNC_PREVIOUS_FILENAME.${UUID.randomUUID()}.part"
        upload(client, temporary, bytes)
        try {
            if (previousBytes != null) upload(client, previousTemporary, previousBytes)

            // Confirm nobody recovered our lock immediately before canonical names are mutated.
            verifySyncLockOwnership(client, lease)

            if (previousBytes != null) {
                client.deleteFile(SYNC_PREVIOUS_FILENAME)
                if (!client.rename(previousTemporary, SYNC_PREVIOUS_FILENAME)) {
                    throw IllegalStateException(
                        "Could not publish the previous sync snapshot: ${client.replyString.trim()}"
                    )
                }
            }
            verifySyncLockOwnership(client, lease)
            if (hadCurrent && !client.deleteFile(SYNC_FILENAME)) {
                throw IllegalStateException(
                    "Could not replace the old sync snapshot: ${client.replyString.trim()}"
                )
            }
            if (!client.rename(temporary, SYNC_FILENAME)) {
                throw IllegalStateException(
                    "Could not publish the sync snapshot: ${client.replyString.trim()}"
                )
            }
        } catch (t: Throwable) {
            deleteQuietly(client, temporary)
            deleteQuietly(client, previousTemporary)
            throw t
        }
    }

    private fun publishNewFile(client: FTPClient, filename: String, bytes: ByteArray) {
        val temporary = ".$filename.${UUID.randomUUID()}.part"
        upload(client, temporary, bytes)
        if (!client.rename(temporary, filename)) {
            // Two serialized devices can still finish within the same timestamp second. Replacing
            // that one history slot is harmless; the canonical sync state is already published.
            client.deleteFile(filename)
            if (!client.rename(temporary, filename)) {
                deleteQuietly(client, temporary)
                throw IllegalStateException(
                    "Could not publish completed backup: ${client.replyString.trim()}"
                )
            }
        }
    }

    private data class SyncLease(val token: String)

    private fun acquireSyncLock(client: FTPClient): SyncLease {
        if (!client.makeDirectory(SYNC_LOCK_DIR)) {
            val leaseInfo = readLeaseInfo(client)
            val directoryMillis = runCatching {
                client.mlistFile(SYNC_LOCK_DIR)?.timestamp?.timeInMillis
            }.getOrNull()
            val lockedAt = leaseInfo.lockedAt ?: directoryMillis
            val isStale = lockedAt != null &&
                System.currentTimeMillis() - lockedAt > SYNC_LOCK_STALE_MILLIS
            if (!isStale) {
                throw syncLockBusyException(lockedAt, leaseInfo.ownerDevice)
            }
            client.deleteFile("$SYNC_LOCK_DIR/$SYNC_LEASE_FILE")
            check(client.removeDirectory(SYNC_LOCK_DIR) && client.makeDirectory(SYNC_LOCK_DIR)) {
                "Could not recover a stale sync lock: ${client.replyString.trim()}"
            }
        }
        val lease = SyncLease(UUID.randomUUID().toString())
        try {
            upload(
                client,
                "$SYNC_LOCK_DIR/$SYNC_LEASE_FILE",
                leasePayload(lease)
            )
        } catch (t: Throwable) {
            client.removeDirectory(SYNC_LOCK_DIR)
            throw t
        }
        return lease
    }

    private fun clearSyncLockDirectory(client: FTPClient) {
        deleteQuietly(client, "$SYNC_LOCK_DIR/$SYNC_LEASE_FILE")
        runCatching {
            client.listFiles(SYNC_LOCK_DIR)
                .filterNot { it.name == "." || it.name == ".." }
                .forEach { entry ->
                    val child = "$SYNC_LOCK_DIR/${entry.name}"
                    if (entry.isDirectory) {
                        client.removeDirectory(child)
                    } else {
                        client.deleteFile(child)
                    }
                }
        }
        client.removeDirectory(SYNC_LOCK_DIR)
        if (client.makeDirectory(SYNC_LOCK_DIR)) {
            client.removeDirectory(SYNC_LOCK_DIR)
        } else {
            throw IllegalStateException("Could not clear the remote sync lock: ${client.replyString.trim()}")
        }
    }

    private fun verifySyncLockOwnership(client: FTPClient, lease: SyncLease) {
        check(readLeaseToken(client) == lease.token) {
            "FTP sync lock ownership was lost before publication"
        }
    }

    private fun leasePayload(lease: SyncLease): ByteArray =
        "${System.currentTimeMillis()}\n${lease.token}\n${deviceLabel()}".toByteArray(Charsets.UTF_8)

    private fun readLeaseToken(client: FTPClient): String? =
        readLeaseInfo(client).token

    private fun readLeaseInfo(client: FTPClient): RemoteLeaseInfo =
        readRemoteBytesOrNull(client, "$SYNC_LOCK_DIR/$SYNC_LEASE_FILE")
            ?.toString(Charsets.UTF_8)
            ?.let(::parseLeaseInfo)
            ?: RemoteLeaseInfo()

    private fun releaseSyncLock(client: FTPClient, lease: SyncLease) {
        try {
            if (readLeaseToken(client) != lease.token) {
                Log.w(TAG, "FTP sync lock ownership changed before release; leaving it intact")
                return
            }
            if (!client.deleteFile("$SYNC_LOCK_DIR/$SYNC_LEASE_FILE")) {
                Log.w(TAG, "Could not remove FTP sync lease: ${client.replyString.trim()}")
                return
            }
            if (!client.removeDirectory(SYNC_LOCK_DIR)) {
                Log.w(TAG, "Could not release sync lock: ${client.replyString.trim()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release sync lock", e)
        }
    }

    private fun syncLockBusyException(lockedAt: Long?, ownerDevice: String?): SyncLockBusyException {
        val age = lockedAt?.let { formatLockAge(System.currentTimeMillis() - it) } ?: "unknown age"
        return SyncLockBusyException(
            SyncLockInfo(
                lockedAt = lockedAt,
                ageText = age,
                ownerDevice = ownerDevice
            )
        )
    }

    private data class RemoteLeaseInfo(
        val lockedAt: Long? = null,
        val token: String? = null,
        val ownerDevice: String? = null
    )

    private fun parseLeaseInfo(payload: String): RemoteLeaseInfo {
        val lines = payload.lineSequence().map { it.trim() }.toList()
        return RemoteLeaseInfo(
            lockedAt = lines.getOrNull(0)?.toLongOrNull(),
            token = lines.getOrNull(1)?.takeIf { it.isNotBlank() },
            ownerDevice = lines.getOrNull(2)?.takeIf { it.isNotBlank() }
        )
    }

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val cleanedModel = if (
            manufacturer.isNotBlank() &&
            model.startsWith(manufacturer, ignoreCase = true)
        ) {
            model
        } else {
            listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
        }
        return cleanedModel.ifBlank { "Unknown Android device" }
    }

    private fun formatLockAge(ageMillis: Long): String {
        val clampedSeconds = (ageMillis.coerceAtLeast(0L) / 1000L)
        val minutes = clampedSeconds / 60L
        val seconds = clampedSeconds % 60L
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return when {
            hours > 0 -> "${hours}h ${remainingMinutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun isHistoryBackupName(name: String): Boolean =
        name.startsWith(FILENAME_PREFIX) &&
            (name.endsWith(FILENAME_SUFFIX) || name.endsWith(ENCRYPTED_SUFFIX))

    private fun listHistoryBackupNames(client: FTPClient): List<String> {
        val files = client.listFiles()
        check(FTPReply.isPositiveCompletion(client.replyCode)) {
            "Could not list FTP backup history: ${client.replyString.trim()}"
        }
        return files.asSequence()
            .filter { it.isFile && isHistoryBackupName(it.name) }
            .map { it.name }
            .sortedDescending()
            .toList()
    }

    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val client = connect()
            val names = try {
                client.changeWorkingDirectory(remoteDir)
                client.listNames()
                    ?.filter(::isHistoryBackupName)
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

    override suspend fun listRestorePoints(): Result<List<RestorePoint>> =
        listBackups().map { names -> names.map(::restorePointFromHistoryName) }

    suspend fun restoreBackup(filename: String): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val jsonBytes = fetchBackupJson(filename)
                jsonExporter.dryRunRestoreBytes(jsonBytes)
                recoveryBackupManager.saveCurrent("pre_ftp_restore").getOrElse { e ->
                    throw IllegalStateException(
                        "Could not create a recovery backup before restore; local data was not changed",
                        e
                    )
                }
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

    override suspend fun restore(id: String): Result<Unit> = restoreBackup(id)

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

    override suspend fun inspect(id: String): Result<BackupSummary> = inspectBackup(id)

    /** Read-only access for comparing a server backup with current local data. */
    suspend fun readBackupJson(filename: String): Result<ByteArray> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                Result.success(fetchBackupJson(filename))
            } catch (e: Exception) {
                Log.w(TAG, "readBackupJson failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun readSnapshot(id: String): Result<ByteArray> = readBackupJson(id)

    override suspend fun isConfigured(): Boolean =
        userPreferences.sftpHostFlow.first().isNotBlank()

    /** Downloads, verifies length, decrypts if needed, unzips if needed. */
    private suspend fun fetchBackupJson(filename: String): ByteArray {
        val remoteDir = userPreferences.sftpRemoteDirFlow.first()
        val client = connect()
        val bytes = try {
            client.changeWorkingDirectory(remoteDir)
            val expected = remoteSize(client, filename)
            val out = ByteArrayOutputStream()
            check(retrieveFileWithPassiveFallback(client, filename, out)) {
                "Download failed: ${client.replyString}"
            }
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
        val client: FTPClient = if (useTls) {
            FTPSClient(false).apply {
                // Commons Net's default trust manager only checks certificate dates. Install the
                // platform CA trust manager explicitly, but do not enable endpoint identification:
                // many self-hosted FTPS servers have certificates with no matching SAN, and the
                // Settings screen exposes only "use FTPS" rather than a separate strict TLS mode.
                setTrustManager(platformTrustManager())
            }
        } else {
            FTPClient()
        }
        client.connectTimeout = CONNECT_TIMEOUT_MS
        connectPreferIpv4(client, host, port)
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
            configurePassiveDataMode(client)
            client.setFileType(FTP.BINARY_FILE_TYPE)
        } catch (e: Exception) {
            disconnectQuietly(client)
            throw e
        }
        return client
    }

    private fun openStoreFileStream(client: FTPClient, filename: String): OutputStream {
        client.storeFileStream(filename)?.let { return it }

        val firstReply = client.replyString.trim()
        if (isExtendedPassiveDataFailure(firstReply)) {
            Log.i(TAG, "Retrying FTP upload with regular passive mode after: $firstReply")
            configurePassiveDataMode(client)
            client.storeFileStream(filename)?.let { return it }
            throw IllegalStateException(
                "Could not open data connection after passive fallback: ${client.replyString.trim()}"
            )
        }

        throw IllegalStateException("Could not open data connection: $firstReply")
    }

    private fun retrieveFileWithPassiveFallback(
        client: FTPClient,
        filename: String,
        out: ByteArrayOutputStream
    ): Boolean {
        if (client.retrieveFile(filename, out)) return true

        val firstReply = client.replyString.trim()
        if (!isExtendedPassiveDataFailure(firstReply)) return false

        Log.i(TAG, "Retrying FTP download with regular passive mode after: $firstReply")
        out.reset()
        configurePassiveDataMode(client)
        if (client.retrieveFile(filename, out)) return true

        throw IllegalStateException(
            "Download failed after passive fallback: ${client.replyString.trim()}"
        )
    }

    private fun configurePassiveDataMode(client: FTPClient) {
        client.setUseEPSVwithIPv4(false)
        client.enterLocalPassiveMode()
    }

    private fun isExtendedPassiveDataFailure(reply: String): Boolean {
        return reply.startsWith("229") ||
            reply.contains("Extended Passive Mode", ignoreCase = true) ||
            reply.contains("EPSV", ignoreCase = true)
    }

    private fun connectPreferIpv4(client: FTPClient, host: String, port: Int) {
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve $host manually; falling back to FTPClient resolver", e)
            client.connect(host, port)
            return
        }
        val ordered = (addresses.filterIsInstance<Inet4Address>() +
            addresses.filterNot { it is Inet4Address })
            .distinctBy { it.hostAddress }

        var lastError: Exception? = null
        for (address in ordered) {
            try {
                client.connect(address, port)
                return
            } catch (e: Exception) {
                lastError = e
                disconnectQuietly(client)
            }
        }
        throw lastError ?: IOException("Could not connect to $host:$port")
    }

    private fun platformTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().singleOrNull()
            ?: throw IllegalStateException("Platform X.509 trust manager is unavailable")
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
        val names = client.listNames()
            ?.filter(::isHistoryBackupName)
            ?.sortedByDescending { it }
            ?: return
        names.drop(keepCount).forEach { name ->
            try {
                client.deleteFile(name)
            } catch (e: Exception) {
                Log.w(TAG, "pruneOldBackups: failed to delete $name", e)
            }
        }
    }

}
