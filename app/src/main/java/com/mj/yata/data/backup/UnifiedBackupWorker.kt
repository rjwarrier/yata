package com.mj.yata.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.domain.usecase.BackupOperations
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * The one scheduled backup job, covering every destination the user has enabled.
 *
 * Replaces the four per-destination workers (Drive, on-device, SFTP, FTP) that each ran on their
 * own interval. Separate schedules meant a trigger only ever refreshed one copy, so a second
 * destination configured for redundancy could silently fall days behind the first — and four
 * independent jobs made "when did this last actually run" nearly impossible to answer. One job on
 * one interval keeps every copy the same age.
 *
 * [cancelLegacyWorkers] retires the old unique work names; without it their already-enqueued
 * periodic jobs would keep firing alongside this one, backing everything up several times a day.
 */
@HiltWorker
class UnifiedBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupOperations: BackupOperations
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val results = backupOperations.backupAllConfigured()
        if (results.isEmpty()) return Result.success()

        val failed = results.filter { !it.isSuccess }
        failed.forEach { Log.w(TAG, "Scheduled backup to ${it.destination} failed", it.error) }

        // Retry only when nothing at all got through — that's the signature of something transient
        // and shared (no network, airplane mode) which a retry can actually fix. A partial failure
        // is usually specific to one destination (wrong password, server down); retrying would
        // re-upload to the destinations that already succeeded, and the next scheduled run covers
        // it anyway.
        return if (failed.size == results.size) Result.retry() else Result.success()
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
            // Any network rather than unmetered: a self-hosted destination is often reached over a
            // LAN or VPN that a metered check misreads, and the Wi-Fi-only preference is applied
            // per-destination by the managers themselves.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UnifiedBackupWorker>(
                intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
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
