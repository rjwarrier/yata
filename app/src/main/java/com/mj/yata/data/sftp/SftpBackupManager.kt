package com.mj.yata.data.sftp

import android.content.Context
import android.os.Build
import android.util.Log
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sync.SnapshotSyncEngine
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.PublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of [SftpBackupManager.testConnection] — always carries the server's host key
 * fingerprint when the transport got far enough to see one, success or not. An unpinned first
 * test intentionally stops before authentication and therefore returns `success = false`. */
data class SftpConnectionTestResult(
    val success: Boolean,
    val fingerprint: String?,
    val error: Throwable?
)

/** Thrown by [SftpBackupManager] when a connection is attempted with required fields unset. */
class SftpNotConfiguredException(message: String) : Exception(message)

/** No authentication is attempted until the user has confirmed the observed host key. */
class SftpHostKeyNotTrustedException(message: String) : Exception(message)

/**
 * Server-backup counterpart to [com.mj.yata.data.local.backup.LocalBackupManager] — same JSON
 * payload (via [JsonExporter]), uploaded over SFTP to a server the user supplies. No third-party
 * account, no vendor lock-in to a specific host — anything that speaks SFTP works.
 *
 * Host key verification is TOFU (trust-on-first-use), matching how every mainstream SSH client
 * behaves: the fingerprint observed during the first unauthenticated key exchange is pinned in
 * [UserPreferences.sftpHostKeyFingerprintFlow], and every later connection must present the exact
 * same key or the connection is refused outright — never silently downgraded to "accept
 * anything," which is what would make this a real man-in-the-middle hole rather than a
 * self-hosted convenience. [testConnection] is the only entry point that reaches an unpinned
 * server, and it rejects the key after observing its fingerprint so no credential is sent. The UI
 * asks the user to confirm it before a second, authenticated test or any backup/restore can run.
 */
@Singleton
class SftpBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val credentialsStore: RemoteBackupCredentialsStore,
    private val snapshotSyncEngine: SnapshotSyncEngine,
    private val recoveryBackupManager: RecoveryBackupManager
) : LockableSyncTransport {
    companion object {
        private const val TAG = "SftpBackupManager"
        private const val FILENAME_PREFIX = "yata_backup_"
        private const val FILENAME_SUFFIX = ".json"
        private const val ENCRYPTED_SUFFIX = ".json.enc"
        private const val SYNC_FILENAME = "yata_sync_v1.json"
        private const val SYNC_PREVIOUS_FILENAME = ".yata_sync_v1.previous"
        private const val SYNC_LOCK_DIR = ".yata_sync_v1.lock"
        private const val SYNC_LEASE_FILE = "lease"
        private const val SYNC_LOCK_STALE_MILLIS = 60 * 60 * 1000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val SOCKET_TIMEOUT_MS = 60_000
    }

    private val sessionMutex = Mutex()

    /** Pins [fingerprint] as the trusted host key — called only after the user has explicitly
     * confirmed it (first connection) or deliberately chosen to trust a changed key. */
    suspend fun pinHostKey(fingerprint: String) {
        userPreferences.setSftpHostKeyFingerprint(fingerprint)
    }

    override suspend fun clearSyncLock(): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val remoteDir = userPreferences.sftpRemoteDirFlow.first()
                buildClient().use { ssh ->
                    ssh.newSFTPClient().use { sftp ->
                        ensureRemoteDir(sftp, remoteDir)
                        val lockPath = remotePath(remoteDir, SYNC_LOCK_DIR)
                        clearSyncLockDirectory(sftp, lockPath)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.w(TAG, "clearSyncLock failed", e)
                Result.failure(e)
            }
        }
    }


    suspend fun testConnection(): SftpConnectionTestResult = withContext(Dispatchers.IO) {
        var observed: String? = null
        try {
            val ssh = buildClient(allowUnpinnedProbe = true) { observed = it }
            ssh.disconnect()
            SftpConnectionTestResult(success = true, fingerprint = observed, error = null)
        } catch (e: Exception) {
            Log.w(TAG, "testConnection failed", e)
            SftpConnectionTestResult(success = false, fingerprint = observed, error = e)
        }
    }

    suspend fun backupNow(): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val remoteDir = userPreferences.sftpRemoteDirFlow.first()
                val keepCount = userPreferences.sftpKeepCountFlow.first()
                val (primaryJson, _) = jsonExporter.buildSplitBackupJson(archiveMonths = 0)
                val bytes = encodePayload(primaryJson.toString(2).toByteArray(Charsets.UTF_8))
                val encrypted = credentialsStore.backupPassphrase != null
                val filename = FILENAME_PREFIX +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                    (if (encrypted) ENCRYPTED_SUFFIX else FILENAME_SUFFIX)

                val tempFile = File.createTempFile("sftp_upload", ".json", context.cacheDir)
                try {
                    tempFile.writeBytes(bytes)
                    // Once STOR begins, finish publishing or remove the temporary remote object.
                    // Cancellation between put and rename must never expose a partial backup.
                    withContext(NonCancellable) {
                        buildClient().use { ssh ->
                            ssh.newSFTPClient().use { sftp ->
                                ensureRemoteDir(sftp, remoteDir)
                                val finalPath = remotePath(remoteDir, filename)
                                val temporaryPath = remotePath(remoteDir, ".$filename.part")
                                try {
                                    sftp.put(tempFile.absolutePath, temporaryPath)
                                    val uploadedSize = sftp.stat(temporaryPath).size
                                    check(uploadedSize == bytes.size.toLong()) {
                                        "Upload truncated — $uploadedSize of ${bytes.size} bytes reached the server"
                                    }
                                    sftp.rename(temporaryPath, finalPath)
                                } catch (t: Throwable) {
                                    runCatching { sftp.rm(temporaryPath) }
                                    throw t
                                }
                                pruneOldBackups(sftp, remoteDir, keepCount)
                            }
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
    }

    /**
     * Pulls the canonical server snapshot, merges it with this device, and publishes the merged
     * result while holding a cross-device lease. Timestamped backups remain recovery points; the
     * fixed canonical file is the only input devices normally synchronize against.
     */
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
                    buildClient().use { ssh ->
                        ssh.newSFTPClient().use { sftp ->
                            progress(24, "Opening remote folder")
                            ensureRemoteDir(sftp, remoteDir)
                            val canonicalRemoteDir =
                                sftp.canonicalize(remoteDir).trimEnd('/').ifEmpty { "/" }
                            val scopeKey =
                                "sftp|$username@$normalizedHost:$port|$canonicalRemoteDir"
                            progress(34, "Checking sync lock")
                            val lease = acquireSyncLease(sftp, remoteDir)
                            try {
                                progress(46, "Reading server changes")
                                val remote = readSyncSource(sftp, remoteDir)
                                progress(60, "Merging changes")
                                val prepared = snapshotSyncEngine.prepare(
                                    remoteBytes = remote.jsonBytes,
                                    scopeKey = scopeKey,
                                    remoteIsRecovery =
                                        remote.jsonBytes != null && !remote.canonicalHeadValid
                                )
                                val publish = prepared.remoteNeedsPublish || !remote.canonicalHeadValid
                                val canonicalBytes = if (publish) {
                                    encodePayload(prepared.canonicalBytes)
                                } else {
                                    null
                                }
                                if (canonicalBytes != null) {
                                    progress(76, "Uploading changes")
                                    publishCanonical(
                                        sftp = sftp,
                                        remoteDir = remoteDir,
                                        bytes = canonicalBytes,
                                        previousBytes = remote.encodedBytes,
                                        lease = lease
                                    )
                                } else {
                                    progress(76, "Already up to date")
                                }
                                progress(86, "Applying updates")
                                conflictsResolved = snapshotSyncEngine.commit(prepared)
                                if (canonicalBytes != null) {
                                    progress(92, "Saving backup copy")
                                    writeTimestampedBackup(sftp, remoteDir, canonicalBytes)
                                    pruneOldBackups(sftp, remoteDir, keepCount)
                                }
                            } finally {
                                releaseSyncLease(sftp, lease)
                            }
                        }
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

    /** Filenames only, newest first — the same ordering [pruneOldBackups] relies on
     * (timestamp-embedded names sort correctly as plain strings). */
    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val remoteDir = userPreferences.sftpRemoteDirFlow.first()
            val names = buildClient().use { ssh ->
                ssh.newSFTPClient().use { sftp ->
                    sftp.ls(remoteDir)
                        .filter {
                            it.isRegularFile && isHistoryBackupName(it.name)
                        }
                        .map { it.name }
                }
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
                val bytes = download(filename)
                jsonExporter.dryRunRestoreBytes(bytes)
                recoveryBackupManager.saveCurrent("pre_sftp_restore").getOrElse { e ->
                    throw IllegalStateException(
                        "Could not create a recovery backup before restore; local data was not changed",
                        e
                    )
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
    }

    override suspend fun restore(id: String): Result<Unit> = restoreBackup(id)

    /** Counterpart to [com.mj.yata.data.ftp.FtpBackupManager.inspectBackup] — see it for why the
     * confirm dialog reads a backup's contents before restoring it. */
    suspend fun inspectBackup(filename: String): Result<com.mj.yata.domain.model.BackupSummary> = withContext(Dispatchers.IO) {
        try {
            Result.success(jsonExporter.summarise(download(filename)))
        } catch (e: Exception) {
            Log.w(TAG, "inspectBackup failed", e)
            Result.failure(e)
        }
    }

    override suspend fun inspect(id: String): Result<com.mj.yata.domain.model.BackupSummary> =
        inspectBackup(id)

    /** Read-only access for comparing a server backup with current local data. */
    suspend fun readBackupJson(filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Result.success(download(filename))
        } catch (e: Exception) {
            Log.w(TAG, "readBackupJson failed", e)
            Result.failure(e)
        }
    }

    override suspend fun readSnapshot(id: String): Result<ByteArray> = readBackupJson(id)

    override suspend fun isConfigured(): Boolean =
        userPreferences.sftpHostFlow.first().isNotBlank()

    private suspend fun download(filename: String): ByteArray {
        val remoteDir = userPreferences.sftpRemoteDirFlow.first()
        buildClient().use { ssh ->
            ssh.newSFTPClient().use { sftp ->
                val path = remotePath(remoteDir, filename)
                val bytes = readRemoteBytes(sftp, path)
                    ?: throw IllegalStateException("Backup file no longer exists: $filename")
                return decodePayload(bytes)
            }
        }
    }

    /** [onHostKeyObserved] fires the moment a host key is presented, before verification decides
     * whether to accept it — so callers can capture the fingerprint even when verification then
     * rejects it (a changed key) or auth afterward fails (wrong password), neither of which is
     * the caller finding out what key the server actually offered. */
    private data class SyncLease(
        val lockPath: String,
        val leasePath: String,
        val token: String
    )

    private data class RemoteSyncSource(
        val jsonBytes: ByteArray?,
        val encodedBytes: ByteArray?,
        val canonicalHeadValid: Boolean
    )

    private fun encodePayload(jsonBytes: ByteArray): ByteArray {
        val passphrase = credentialsStore.backupPassphrase
        return if (passphrase == null) jsonBytes else BackupCrypto.encrypt(jsonBytes, passphrase)
    }

    private fun decodePayload(bytes: ByteArray): ByteArray {
        if (!BackupCrypto.isEncrypted(bytes)) return bytes
        val passphrase = credentialsStore.backupPassphrase
            ?: throw IllegalStateException(
                "This backup is encrypted - set the backup passphrase before syncing or restoring"
            )
        return try {
            BackupCrypto.decrypt(bytes, passphrase)
        } catch (e: Exception) {
            throw IllegalStateException("Wrong passphrase, or the backup is damaged", e)
        }
    }

    private fun readSyncSource(sftp: SFTPClient, remoteDir: String): RemoteSyncSource {
        val canonicalPath = remotePath(remoteDir, SYNC_FILENAME)
        var sawExistingCandidate = false

        fun acceptIfValid(candidate: String, bytes: ByteArray): RemoteSyncSource? {
            sawExistingCandidate = true
            val decoded = try {
                decodePayload(bytes)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable sync candidate $candidate", e)
                return null
            }
            if (!snapshotSyncEngine.isValidRemoteSnapshot(decoded)) {
                Log.w(TAG, "Skipping invalid sync candidate $candidate")
                return null
            }
            return RemoteSyncSource(
                jsonBytes = decoded,
                encodedBytes = bytes,
                canonicalHeadValid = candidate == canonicalPath
            )
        }

        val canonicalBytes = try {
            readRemoteBytes(sftp, canonicalPath)
        } catch (e: IllegalStateException) {
            sawExistingCandidate = true
            Log.w(TAG, "Skipping damaged sync candidate $canonicalPath", e)
            null
        }
        if (canonicalBytes != null) {
            acceptIfValid(canonicalPath, canonicalBytes)?.let { return it }
        }

        val historyPaths = sftp.ls(remoteDir)
            .asSequence()
            .filter { it.isRegularFile && isHistoryBackupName(it.name) }
            .map { remotePath(remoteDir, it.name) }
            .sortedDescending()
            .toList()
        val recoveryCandidates = historyPaths.asSequence() +
            sequenceOf(remotePath(remoteDir, SYNC_PREVIOUS_FILENAME))

        for (candidate in recoveryCandidates) {
            val bytes = try {
                readRemoteBytes(sftp, candidate)
            } catch (e: IllegalStateException) {
                sawExistingCandidate = true
                Log.w(TAG, "Skipping damaged sync candidate $candidate", e)
                continue
            } ?: continue
            acceptIfValid(candidate, bytes)?.let { return it }
        }
        check(!sawExistingCandidate) {
            "Remote sync files exist, but none contains a valid YATA snapshot"
        }
        return RemoteSyncSource(jsonBytes = null, encodedBytes = null, canonicalHeadValid = false)
    }

    private fun readRemoteBytes(sftp: SFTPClient, remotePath: String): ByteArray? {
        val attributes = sftp.statExistence(remotePath) ?: return null
        val temporary = File.createTempFile("sftp_sync_read", ".tmp", context.cacheDir)
        return try {
            sftp.get(remotePath, temporary.absolutePath)
            val bytes = temporary.readBytes()
            check(bytes.size.toLong() == attributes.size) {
                "Download truncated - got ${bytes.size} of ${attributes.size} bytes"
            }
            bytes
        } finally {
            temporary.delete()
        }
    }

    private fun writeRemoteBytes(sftp: SFTPClient, remotePath: String, bytes: ByteArray) {
        val temporary = File.createTempFile("sftp_sync_write", ".tmp", context.cacheDir)
        try {
            temporary.writeBytes(bytes)
            sftp.put(temporary.absolutePath, remotePath)
            val uploadedSize = sftp.stat(remotePath).size
            check(uploadedSize == bytes.size.toLong()) {
                "Upload truncated - $uploadedSize of ${bytes.size} bytes reached the server"
            }
        } finally {
            temporary.delete()
        }
    }

    private fun publishCanonical(
        sftp: SFTPClient,
        remoteDir: String,
        bytes: ByteArray,
        previousBytes: ByteArray?,
        lease: SyncLease
    ) {
        val canonicalPath = remotePath(remoteDir, SYNC_FILENAME)
        val previousPath = remotePath(remoteDir, SYNC_PREVIOUS_FILENAME)
        val temporaryPath = remotePath(remoteDir, ".$SYNC_FILENAME.${UUID.randomUUID()}.part")
        val previousTemporaryPath =
            remotePath(remoteDir, ".$SYNC_PREVIOUS_FILENAME.${UUID.randomUUID()}.part")
        try {
            writeRemoteBytes(sftp, temporaryPath, bytes)
            if (previousBytes != null) {
                writeRemoteBytes(sftp, previousTemporaryPath, previousBytes)
            }
            verifySyncLeaseOwnership(sftp, lease)
            if (previousBytes != null) {
                removeRemoteFileIfPresent(sftp, previousPath)
                sftp.rename(previousTemporaryPath, previousPath)
            }
            verifySyncLeaseOwnership(sftp, lease)
            removeRemoteFileIfPresent(sftp, canonicalPath)
            sftp.rename(temporaryPath, canonicalPath)
        } catch (t: Throwable) {
            runCatching { removeRemoteFileIfPresent(sftp, temporaryPath) }
            runCatching { removeRemoteFileIfPresent(sftp, previousTemporaryPath) }
            throw t
        }
    }

    private fun writeTimestampedBackup(sftp: SFTPClient, remoteDir: String, bytes: ByteArray) {
        val encrypted = credentialsStore.backupPassphrase != null
        val filename = FILENAME_PREFIX +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            (if (encrypted) ENCRYPTED_SUFFIX else FILENAME_SUFFIX)
        val finalPath = remotePath(remoteDir, filename)
        val temporaryPath = remotePath(remoteDir, ".$filename.${UUID.randomUUID()}.part")
        try {
            writeRemoteBytes(sftp, temporaryPath, bytes)
            removeRemoteFileIfPresent(sftp, finalPath)
            sftp.rename(temporaryPath, finalPath)
        } catch (t: Throwable) {
            runCatching { removeRemoteFileIfPresent(sftp, temporaryPath) }
            throw t
        }
    }

    private fun acquireSyncLease(sftp: SFTPClient, remoteDir: String): SyncLease {
        val lockPath = remotePath(remoteDir, SYNC_LOCK_DIR)
        val leasePath = remotePath(lockPath, SYNC_LEASE_FILE)
        for (attempt in 0 until 3) {
            val mkdirFailure = try {
                sftp.mkdir(lockPath)
                null
            } catch (e: Exception) {
                e
            }

            if (mkdirFailure == null) {
                val token = UUID.randomUUID().toString()
                try {
                    writeRemoteBytes(sftp, leasePath, syncLeasePayload(token))
                    return SyncLease(lockPath, leasePath, token)
                } catch (t: Throwable) {
                    runCatching { removeRemoteFileIfPresent(sftp, leasePath) }
                    runCatching { sftp.rmdir(lockPath) }
                    throw t
                }
            }

            val lockAttributes = sftp.statExistence(lockPath) ?: throw mkdirFailure
            val leaseInfo = readSyncLeaseInfo(sftp, leasePath)
            val observedAt = leaseInfo.lockedAt ?: lockAttributes.mtime * 1000L
            val ageMillis = System.currentTimeMillis() - observedAt
            if (ageMillis < SYNC_LOCK_STALE_MILLIS) {
                throw syncLockBusyException(observedAt, leaseInfo.ownerDevice, mkdirFailure)
            }

            Log.w(TAG, "Breaking stale SFTP sync lease after ${ageMillis / 1000L}s")
            removeRemoteFileIfPresent(sftp, leasePath)
            try {
                sftp.rmdir(lockPath)
            } catch (e: Exception) {
                if (attempt == 2) throw e
            }
        }
        throw IllegalStateException("Could not acquire the SFTP sync lease")
    }

    private fun clearSyncLockDirectory(sftp: SFTPClient, lockPath: String) {
        removeRemoteFileIfPresent(sftp, remotePath(lockPath, SYNC_LEASE_FILE))
        runCatching {
            sftp.ls(lockPath)
                .filterNot { it.name == "." || it.name == ".." }
                .forEach { entry ->
                    val child = remotePath(lockPath, entry.name)
                    if (entry.isDirectory) {
                        runCatching { sftp.rmdir(child) }
                    } else {
                        removeRemoteFileIfPresent(sftp, child)
                    }
                }
        }
        runCatching { sftp.rmdir(lockPath) }
        try {
            sftp.mkdir(lockPath)
            sftp.rmdir(lockPath)
        } catch (e: Exception) {
            throw IllegalStateException("Could not clear the remote sync lock", e)
        }
    }

    private fun verifySyncLeaseOwnership(sftp: SFTPClient, lease: SyncLease) {
        check(readSyncLeaseToken(sftp, lease.leasePath) == lease.token) {
            "The SFTP sync lease expired or changed ownership before publish"
        }
    }

    private fun releaseSyncLease(sftp: SFTPClient, lease: SyncLease) {
        try {
            val ownerToken = readSyncLeaseToken(sftp, lease.leasePath)
            if (ownerToken != lease.token) {
                Log.w(TAG, "SFTP sync lease ownership changed before release; leaving it intact")
                return
            }
            removeRemoteFileIfPresent(sftp, lease.leasePath)
            sftp.rmdir(lease.lockPath)
        } catch (e: Exception) {
            Log.w(TAG, "Could not release SFTP sync lease; it will expire automatically", e)
        }
    }

    private fun syncLockBusyException(
        lockedAt: Long?,
        ownerDevice: String?,
        cause: Throwable? = null
    ): SyncLockBusyException {
        val age = lockedAt?.let { formatLockAge(System.currentTimeMillis() - it) } ?: "unknown age"
        return SyncLockBusyException(
            lockInfo = SyncLockInfo(
                lockedAt = lockedAt,
                ageText = age,
                ownerDevice = ownerDevice
            ),
            cause = cause
        )
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

    private fun syncLeasePayload(token: String): ByteArray =
        "${System.currentTimeMillis()}\n$token\n${deviceLabel()}".toByteArray(Charsets.UTF_8)

    private fun readSyncLeaseToken(sftp: SFTPClient, leasePath: String): String? =
        readSyncLeaseInfo(sftp, leasePath).token

    private fun readSyncLeaseInfo(sftp: SFTPClient, leasePath: String): RemoteLeaseInfo =
        readRemoteBytes(sftp, leasePath)
            ?.toString(Charsets.UTF_8)
            ?.let(::parseLeaseInfo)
            ?: RemoteLeaseInfo()

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

    private fun removeRemoteFileIfPresent(sftp: SFTPClient, path: String) {
        if (sftp.statExistence(path) != null) sftp.rm(path)
    }

    private fun remotePath(directory: String, name: String): String =
        if (directory == "/") "/$name" else "${directory.trimEnd('/')}/$name"

    private fun isHistoryBackupName(name: String): Boolean =
        name.startsWith(FILENAME_PREFIX) &&
            (name.endsWith(FILENAME_SUFFIX) || name.endsWith(ENCRYPTED_SUFFIX))

    private suspend fun buildClient(
        allowUnpinnedProbe: Boolean = false,
        onHostKeyObserved: (String) -> Unit = {}
    ): SSHClient {
        val host = userPreferences.sftpHostFlow.first()
        val port = userPreferences.sftpPortFlow.first()
        val username = userPreferences.sftpUsernameFlow.first()
        val authMethod = userPreferences.sftpAuthMethodFlow.first()
        val pinnedFingerprint = userPreferences.sftpHostKeyFingerprintFlow.first()

        if (host.isBlank() || username.isBlank()) {
            throw SftpNotConfiguredException("SFTP host and username must be set")
        }
        if (pinnedFingerprint == null && !allowUnpinnedProbe) {
            throw SftpHostKeyNotTrustedException(
                "SFTP server is not trusted yet — test the connection and confirm its host key"
            )
        }

        val ssh = SSHClient()
        ssh.connectTimeout = CONNECT_TIMEOUT_MS
        ssh.timeout = SOCKET_TIMEOUT_MS
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = fingerprintOf(key)
                onHostKeyObserved(fingerprint)
                // An unpinned test connection deliberately fails immediately after key exchange.
                // The fingerprint is returned to the UI, but no password/private key is sent.
                return pinnedFingerprint != null && pinnedFingerprint == fingerprint
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

    private fun pruneOldBackups(sftp: SFTPClient, dir: String, keepCount: Int) {
        val files = sftp.ls(dir)
            .filter {
                it.isRegularFile && isHistoryBackupName(it.name)
            }
            .sortedByDescending { it.name }
        files.drop(keepCount).forEach { entry ->
            try {
                sftp.rm(remotePath(dir, entry.name))
            } catch (e: Exception) {
                Log.w(TAG, "pruneOldBackups: failed to delete ${entry.name}", e)
            }
        }
    }
}
