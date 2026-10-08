package com.mj.yata.data.local.backup

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sync.SnapshotMerger
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val FILENAME_PREFIX = "yata_local_"
private const val FILENAME_SUFFIX = ".json.enc"
private const val KEEP_RECENT = 3
private const val KEEP_DAYS = 7

/**
 * Which local backups to keep: the [KEEP_RECENT] newest, plus the newest of each of the last
 * [KEEP_DAYS] days that have one. A plain "newest N" let a few edits in one afternoon — each
 * triggers a backup — push out every copy from before them, including the one from before a
 * mistake. Names sort chronologically: `yyyyMMdd_HHmmss` comes right after the prefix.
 */
internal fun localBackupsToKeep(names: List<String>): Set<String> {
    val newestFirst = names.sortedDescending()
    val newestPerDay = newestFirst.distinctBy { it.removePrefix(FILENAME_PREFIX).take(8) }.take(KEEP_DAYS)
    return (newestFirst.take(KEEP_RECENT) + newestPerDay).toSet()
}

/** The content fingerprint embedded in a backup's name, or null for one written before names
 * carried it. */
internal fun localBackupFingerprint(name: String): String? =
    name.removePrefix(FILENAME_PREFIX).removeSuffix(FILENAME_SUFFIX)
        .split('_').getOrNull(2)

/** On-device backup manager: same JSON payload (via [JsonExporter]), but written encrypted to
 * app-specific external storage so a user gets automated backups without a network round-trip. */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val recoveryBackupManager: RecoveryBackupManager
) {
    companion object {
        private const val TAG = "LocalBackupManager"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private fun backupDir(): File =
        File(context.getExternalFilesDir(null), "local_backups").apply { mkdirs() }

    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (primaryJson, _) = jsonExporter.buildSplitBackupJson(archiveMonths = 0)
            // Fingerprinted the way sync compares snapshots, so the per-run timestamp and
            // device-local settings (this backup's own last-run time among them) don't count
            // as a change.
            val fingerprint = SnapshotMerger.canonicalHash(SnapshotMerger.normalizeForSync(primaryJson)).take(16)
            val newest = backupDir().listFiles()
                ?.filter { it.name.startsWith(FILENAME_PREFIX) && it.name.endsWith(FILENAME_SUFFIX) }
                ?.maxByOrNull { it.name }
            if (newest != null && localBackupFingerprint(newest.name) == fingerprint) {
                // Nothing changed since the newest backup, which still matches the data exactly.
                // Writing another copy would only push an older, different one out sooner.
                userPreferences.setLocalBackupLastAt(System.currentTimeMillis())
                return@withContext Result.success(Unit)
            }
            // Compact rather than indented: only the app reads these, and indentation was ~30% of
            // the file.
            val bytes = primaryJson.toString().toByteArray(Charsets.UTF_8)

            val filename = FILENAME_PREFIX +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                "_" + fingerprint + FILENAME_SUFFIX
            val file = File(backupDir(), filename)
            val tempFile = File(backupDir(), "$filename.tmp")
            if (file.exists()) file.delete()
            if (tempFile.exists()) tempFile.delete()

            val encryptedFile = EncryptedFile.Builder(
                context, tempFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use {
                it.write(bytes)
                it.fd.sync()
            }
            val verified = EncryptedFile.Builder(
                context, tempFile, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build().openFileInput().use { it.readBytes() }
            check(verified.contentEquals(bytes)) { "Local backup verification failed" }
            moveIntoPlace(tempFile, file)

            userPreferences.setLocalBackupLastAt(System.currentTimeMillis())
            pruneOldBackups()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "backupNow failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreLatest(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Suffix too: a .tmp left by an interrupted backup also starts with the prefix and
            // sorts newest, and restoring it would load a half-written file.
            val latest = backupDir().listFiles()
                ?.filter { it.name.startsWith(FILENAME_PREFIX) && it.name.endsWith(FILENAME_SUFFIX) }
                ?.maxByOrNull { it.name }
                ?: return@withContext recoveryBackupManager.restoreLatest()

            val encryptedFile = EncryptedFile.Builder(
                context, latest, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            val bytes = encryptedFile.openFileInput().use { it.readBytes() }
            jsonExporter.dryRunRestoreBytes(bytes)

            recoveryBackupManager.saveCurrent("pre_local_restore").getOrElse { e ->
                throw IllegalStateException(
                    "Could not create a recovery backup before restore; local data was not changed",
                    e
                )
            }
            if (jsonExporter.importBytes(bytes)) Result.success(Unit)
            else Result.failure(IllegalStateException("Restore failed - backup file unreadable"))
        } catch (e: Exception) {
            Log.w(TAG, "restoreLatest failed", e)
            Result.failure(e)
        }
    }

    private fun pruneOldBackups() {
        // Runs after the new file is in place, so any .tmp left is from an interrupted earlier run.
        backupDir().listFiles()?.filter { it.name.startsWith(FILENAME_PREFIX) && it.name.endsWith(".tmp") }
            ?.forEach { it.delete() }
        val files = backupDir().listFiles()
            ?.filter { it.name.startsWith(FILENAME_PREFIX) && it.name.endsWith(FILENAME_SUFFIX) }
            ?: return
        val keep = localBackupsToKeep(files.map { it.name })
        files.filterNot { it.name in keep }.forEach { it.delete() }
    }

    private fun moveIntoPlace(tempFile: File, finalFile: File) {
        try {
            Files.move(
                tempFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
