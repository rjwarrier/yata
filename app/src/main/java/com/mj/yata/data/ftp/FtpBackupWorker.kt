package com.mj.yata.data.ftp

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
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.data.sftp.SftpNotConfiguredException
import com.mj.yata.domain.model.RemoteBackupProtocol
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodic counterpart to [com.mj.yata.data.sftp.SftpBackupWorker] for FTP/FTPS. Both workers
 * are scheduled unconditionally at app start (see YataApplication) and each no-ops unless its own
 * protocol is the one currently selected -- simpler than tearing one down and standing the other
 * up every time the user flips the protocol picker, and no less correct: an unwanted run is a
 * single cheap DataStore read followed immediately by `Result.success()`. */
@HiltWorker
class FtpBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ftpBackupManager: FtpBackupManager,
    private val userPreferences: UserPreferences,
    private val operationHistoryStore: OperationHistoryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        operationHistoryStore.recordRun(OperationHistoryStore.SYNC_FTP_LEGACY, "Legacy FTP worker started")
        try {
            if (!userPreferences.sftpBackupEnabledFlow.first()) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.SYNC_FTP_LEGACY, "Self-hosted backup is disabled")
                return Result.success()
            }
            if (userPreferences.remoteBackupProtocolFlow.first() != RemoteBackupProtocol.FTP) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.SYNC_FTP_LEGACY, "FTP is not the selected protocol")
                return Result.success()
            }

            val result = ftpBackupManager.syncNow()
            return if (result.isSuccess) {
                operationHistoryStore.recordSuccess(OperationHistoryStore.SYNC_FTP_LEGACY, "FTP sync completed")
                Result.success()
            } else {
                when (result.exceptionOrNull()) {
                    is SftpNotConfiguredException -> {
                        operationHistoryStore.recordSkipped(
                            OperationHistoryStore.SYNC_FTP_LEGACY,
                            result.exceptionOrNull()?.message ?: "FTP is not fully configured"
                        )
                        Result.success()
                    }
                    else -> {
                        operationHistoryStore.recordFailure(OperationHistoryStore.SYNC_FTP_LEGACY, result.exceptionOrNull())
                        Result.retry()
                    }
                }
            }
        } catch (t: Throwable) {
            operationHistoryStore.recordFailure(OperationHistoryStore.SYNC_FTP_LEGACY, t)
            throw t
        }
    }

    companion object {
        private const val WORK_NAME = "ftp_backup_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 24 * 60L

        fun schedule(
            context: Context,
            intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<FtpBackupWorker>(
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
