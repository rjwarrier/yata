package com.mj.yata.data.local.backup

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.mj.yata.data.local.datastore.UserPreferences
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
        private const val KEEP_BACKUPS = 5
        private const val FILENAME_PREFIX = "yata_local_"
        private const val FILENAME_SUFFIX = ".json.enc"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private fun backupDir(): File =
        File(context.getExternalFilesDir(null), "local_backups").apply { mkdirs() }

    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (primaryJson, _) = jsonExporter.buildSplitBackupJson(archiveMonths = 0)
            val bytes = primaryJson.toString(2).toByteArray(Charsets.UTF_8)

            val filename = FILENAME_PREFIX +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                FILENAME_SUFFIX
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
            val latest = backupDir().listFiles()
                ?.filter { it.name.startsWith(FILENAME_PREFIX) }
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
        val files = backupDir().listFiles()
            ?.filter { it.name.startsWith(FILENAME_PREFIX) }
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(KEEP_BACKUPS).forEach { it.delete() }
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
