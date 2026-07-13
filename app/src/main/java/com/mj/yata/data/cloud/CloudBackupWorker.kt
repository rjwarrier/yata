package com.mj.yata.data.cloud

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.mj.yata.data.local.datastore.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic fallback for [CloudBackupManager.scheduleDebouncedBackup] — the debounce lives in an
 * in-memory job that dies with the process, so this catches "app never sat idle long enough for
 * the debounce to fire" and "app was killed mid-debounce." Cadence is user-configurable (Settings
 * → Cloud Backup → Backup frequency); WorkManager enforces a 15-minute floor on periodic work.
 */
@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudBackupManager: CloudBackupManager,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!userPreferences.cloudBackupEnabledFlow.first()) return Result.success()

        val result = cloudBackupManager.backupNow()
        return if (result.isSuccess) {
            Result.success()
        } else {
            when (result.exceptionOrNull()) {
                // Not signed in / needs re-auth isn't something retrying will fix on its own —
                // surfaced in Settings instead of burning retry attempts.
                CloudBackupError.NotSignedIn, CloudBackupError.NeedsReauth -> Result.success()
                else -> Result.retry()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "cloud_backup_periodic"
        const val DEFAULT_INTERVAL_MINUTES = 24 * 60L

        /** [policy] defaults to KEEP so the app-start call in YataApplication only establishes
         * an initial schedule when none exists yet, without stomping a user-configured interval.
         * Settings passes UPDATE explicitly when the user actually changes the frequency. */
        fun schedule(
            context: Context,
            intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            wifiOnly: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
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
