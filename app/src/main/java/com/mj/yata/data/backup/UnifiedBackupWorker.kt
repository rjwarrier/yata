package com.mj.yata.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.usecase.BackupOperations
import com.mj.yata.notification.runOperationSafely
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnifiedBackupWorkerEntryPoint {
    fun backupOperations(): BackupOperations
    fun operationHistoryStore(): OperationHistoryStore
}

/**
 * The one scheduled backup job, covering every destination the user has enabled.
 *
 * Replaces the old per-destination workers that each ran on their
 * own interval. Separate schedules meant a trigger only ever refreshed one copy, so a second
 * destination configured for redundancy could silently fall days behind the first — and four
 * independent jobs made "when did this last actually run" nearly impossible to answer. One job on
 * one interval keeps every copy the same age.
 *
 * [cancelLegacyWorkers] retires the old unique work names; without it their already-enqueued
 * periodic jobs would keep firing alongside this one, backing everything up several times a day.
 */
class UnifiedBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val backupOperations: BackupOperations by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            UnifiedBackupWorkerEntryPoint::class.java
        ).backupOperations()
    }
    private val operationHistoryStore: OperationHistoryStore by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            UnifiedBackupWorkerEntryPoint::class.java
        ).operationHistoryStore()
    }

    override suspend fun doWork(): Result = runOperationSafely(
        operationHistoryStore = operationHistoryStore,
        operationId = OperationHistoryStore.BACKUP_UNIFIED,
        tag = TAG,
        runReason = "Scheduled backup started"
    ) {
            val results = backupOperations.backupAllConfigured()
            if (results.isEmpty()) {
                operationHistoryStore.recordSkipped(OperationHistoryStore.BACKUP_UNIFIED, "No backup destinations are enabled")
                return@runOperationSafely Result.success()
            }

            val failed = results.filter { !it.isSuccess }
            failed.forEach { Log.w(TAG, "Scheduled backup to ${it.destination} failed", it.error) }

            // Every configured destination is an independent safety copy. A local success must not
            // suppress a transient server failure until the next periodic interval. Successful
            // destinations may receive another rotated copy on retry, which is preferable to leaving
            // a requested destination stale and is bounded by each manager's retention policy.
            if (failed.isNotEmpty()) {
                val reason = failed.joinToString { result ->
                    "${result.destination}: ${result.error?.message ?: result.error?.javaClass?.simpleName ?: "failed"}"
                }
                operationHistoryStore.recordFailure(OperationHistoryStore.BACKUP_UNIFIED, null, reason)
                Result.retry()
            } else {
                operationHistoryStore.recordSuccess(
                    OperationHistoryStore.BACKUP_UNIFIED,
                    "Backed up ${results.size} destination(s)"
                )
                Result.success()
            }
    }

    companion object {
        private const val TAG = "UnifiedBackupWorker"
        private const val WORK_NAME = "unified_backup_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 24 * 60L

        private val LEGACY_WORK_NAMES = listOf(
            "cloud_backup_periodic",
            "local_backup_periodic",
            "sftp_backup_periodic",
            "ftp_backup_periodic"
        )

        fun schedule(
            context: Context,
            intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
        ) {
            val request = PeriodicWorkRequestBuilder<UnifiedBackupWorker>(
                intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
            )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }

        /** Idempotent — safe to call on every launch, which is how upgrades get cleaned up. */
        fun cancelLegacyWorkers(context: Context) {
            val workManager = WorkManager.getInstance(context)
            LEGACY_WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
