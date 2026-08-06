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
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.model.RemoteBackupProtocol
import com.mj.yata.notification.runOperationSafely
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Legacy periodic worker for SFTP server backups. A network constraint is needed
 * (unlike the on-device worker), but it is always "any network" because a self-hosted server is
 * often reached over a local network or VPN that a metered-connection check would not distinguish
 * correctly anyway.
 *
 * Scheduled unconditionally alongside [com.mj.yata.data.ftp.FtpBackupWorker] at app start -- see
 * that class's doc comment for why both run and only the currently-selected protocol's actually
 * does anything. */
@HiltWorker
class SftpBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sftpBackupManager: SftpBackupManager,
    private val userPreferences: UserPreferences,
    private val operationHistoryStore: OperationHistoryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runOperationSafely(
        operationHistoryStore = operationHistoryStore,
        operationId = OperationHistoryStore.SYNC_SFTP_LEGACY,
        tag = TAG,
        runReason = "Legacy SFTP worker started"
    ) {
            if (!userPreferences.sftpBackupEnabledFlow.first()) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.SYNC_SFTP_LEGACY, "Self-hosted backup is disabled")
                return@runOperationSafely Result.success()
            }
            if (userPreferences.remoteBackupProtocolFlow.first() != RemoteBackupProtocol.SFTP) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.SYNC_SFTP_LEGACY, "SFTP is not the selected protocol")
                return@runOperationSafely Result.success()
            }

            val result = sftpBackupManager.syncNow()
            if (result.isSuccess) {
                operationHistoryStore.recordSuccess(OperationHistoryStore.SYNC_SFTP_LEGACY, "SFTP sync completed")
                Result.success()
            } else {
                when (result.exceptionOrNull()) {
                    // Nothing configured, or a rejected/missing host key -- retrying on the same
                    // schedule won't fix either, and would just burn WorkManager's retry budget
                    // silently. Both are surfaced in Settings instead.
                    is SftpNotConfiguredException -> {
                        operationHistoryStore.recordSkipped(
                            OperationHistoryStore.SYNC_SFTP_LEGACY,
                            result.exceptionOrNull()?.message ?: "SFTP is not fully configured"
                        )
                        Result.success()
                    }
                    else -> {
                        operationHistoryStore.recordFailure(OperationHistoryStore.SYNC_SFTP_LEGACY, result.exceptionOrNull())
                        Result.retry()
                    }
                }
            }
    }

    companion object {
        private const val TAG = "SftpBackupWorker"
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
