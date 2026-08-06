package com.mj.yata.data.local.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
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
    private val userPreferences: UserPreferences,
    private val operationHistoryStore: OperationHistoryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        operationHistoryStore.recordRun(OperationHistoryStore.BACKUP_LOCAL_LEGACY, "Legacy local backup worker started")
        try {
            if (!userPreferences.localBackupEnabledFlow.first()) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.BACKUP_LOCAL_LEGACY, "Local backup is disabled")
                return Result.success()
            }

            val result = localBackupManager.backupNow()
            return if (result.isSuccess) {
                operationHistoryStore.recordSuccess(OperationHistoryStore.BACKUP_LOCAL_LEGACY, "Local backup completed")
                Result.success()
            } else {
                operationHistoryStore.recordFailure(OperationHistoryStore.BACKUP_LOCAL_LEGACY, result.exceptionOrNull())
                Result.retry()
            }
        } catch (t: Throwable) {
            operationHistoryStore.recordFailure(OperationHistoryStore.BACKUP_LOCAL_LEGACY, t)
            throw t
        }
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
