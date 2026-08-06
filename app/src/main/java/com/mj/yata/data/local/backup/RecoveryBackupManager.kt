package com.mj.yata.data.local.backup

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private recovery ring used before destructive restore/sync steps. Unlike the user-visible local
 * backup toggle, this always runs: if a remote snapshot, import, or exact sync replace goes wrong,
 * the pre-change state is still available inside app storage.
 */
@Singleton
class RecoveryBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter
) {
    companion object {
        private const val TAG = "RecoveryBackupManager"
        private const val KEEP_RECOVERY_BACKUPS = 25
        private const val FILENAME_PREFIX = "yata_recovery_"
        private const val FILENAME_SUFFIX = ".json.enc"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private fun recoveryDir(): File =
        File(context.filesDir, "recovery_backups").apply { mkdirs() }

    suspend fun saveCurrent(reason: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            saveBytes(jsonExporter.exportToBytes(), reason)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create recovery backup", e)
            Result.failure(e)
        }
    }

    suspend fun restoreLatest(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var latest: File? = null
            var bytes: ByteArray? = null
            recoveryFiles().forEach { candidate ->
                if (bytes == null) {
                    bytes = runCatching { readEncrypted(candidate) }
                        .onFailure { Log.w(TAG, "Skipping unreadable recovery backup ${candidate.name}", it) }
                        .getOrNull()
                    if (bytes == null) candidate.delete() else latest = candidate
                }
            }
            val recoveryBytes = bytes
                ?: return@withContext Result.failure(IllegalStateException("No recovery backup found"))
            jsonExporter.dryRunRestoreBytes(recoveryBytes)
            if (jsonExporter.importBytes(recoveryBytes)) {
                Result.success(Unit)
            } else {
                latest?.delete()
                Result.failure(IllegalStateException("Recovery backup is unreadable"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore recovery backup", e)
            Result.failure(e)
        }
    }

    private fun saveBytes(bytes: ByteArray, reason: String): Result<File> {
        jsonExporter.summarise(bytes)
        val safeReason = reason.replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .ifEmpty { "snapshot" }
            .take(48)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val finalFile = File(recoveryDir(), "$FILENAME_PREFIX${timestamp}_$safeReason$FILENAME_SUFFIX")
        if (finalFile.exists()) finalFile.delete()

        val encryptedFile = EncryptedFile.Builder(
            context,
            finalFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        try {
            encryptedFile.openFileOutput().use { it.write(bytes) }
            check(readEncrypted(finalFile).contentEquals(bytes)) {
                "Recovery backup verification failed"
            }
        } catch (t: Throwable) {
            finalFile.delete()
            throw t
        }

        pruneOldBackups()
        return Result.success(finalFile)
    }

    private fun readEncrypted(file: File): ByteArray =
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileInput().use { it.readBytes() }

    private fun recoveryFiles(): List<File> =
        recoveryDir().listFiles()
            ?.filter { it.name.startsWith(FILENAME_PREFIX) && it.name.endsWith(FILENAME_SUFFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    private fun pruneOldBackups() {
        recoveryFiles().drop(KEEP_RECOVERY_BACKUPS).forEach { file ->
            if (!file.delete()) Log.w(TAG, "Could not prune old recovery backup ${file.name}")
        }
    }
}
