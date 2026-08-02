package com.mj.yata.data.sftp

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.datastore.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodic counterpart to [com.mj.yata.data.cloud.CloudBackupWorker] and
 * [com.mj.yata.data.local.backup.LocalBackupWorker] for SFTP. A network constraint is needed
 * (unlike the on-device one) but it's always "any network," not Wi-Fi-only like cloud backup can
 * be configured for -- a self-hosted server is often reached over a local network or a VPN a
 * metered-connection check wouldn't distinguish correctly anyway. */
@HiltWorker
class SftpBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sftpBackupManager: SftpBackupManager,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!userPreferences.sftpBackupEnabledFlow.first()) return Result.success()

        val result = sftpBackupManager.backupNow()
        return if (result.isSuccess) {
            Result.success()
        } else {
            when (result.exceptionOrNull()) {
                // Nothing configured, or a rejected/missing host key -- retrying on the same
                // schedule won't fix either, and would just burn WorkManager's retry budget
                // silently. Both are surfaced in Settings instead.
                is SftpNotConfiguredException -> Result.success()
                else -> Result.retry()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "sftp_backup_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 24 * 60L

        fun schedule(
            context: Context,
            intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SftpBackupWorker>(
                intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
