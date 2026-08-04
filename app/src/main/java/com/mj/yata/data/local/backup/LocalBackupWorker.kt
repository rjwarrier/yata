package com.mj.yata.data.local.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.datastore.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Legacy periodic on-device backup worker. No network constraint is needed because this never
 * leaves the device. */
@HiltWorker
class LocalBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localBackupManager: LocalBackupManager,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!userPreferences.localBackupEnabledFlow.first()) return Result.success()

        val result = localBackupManager.backupNow()
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "local_backup_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 24 * 60L

        fun schedule(
            context: Context,
            intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(
                intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
