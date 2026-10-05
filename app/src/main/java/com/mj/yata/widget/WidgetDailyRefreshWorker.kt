package com.mj.yata.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.notification.runOperationSafely
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Repaints every placed home-screen widget once a day, independent of any task write.
 * [WidgetRefresher.refreshAll] is otherwise only called from [WidgetUpdaterImpl.notifyTasksChanged]
 * and a couple of UI actions — all "something changed" triggers. None of those fire on their own
 * across a plain midnight rollover with the process dead, so a widget showing "Today" content
 * (due-today counts, the progress ring) could sit a full day stale until the next write happens to
 * land after it. Scheduled a few minutes past midnight rather than exactly at it so it doesn't race
 * [com.mj.yata.util.AppClock]'s own midnight loop for who observes the new day first — irrelevant
 * here since this worker reads the system clock directly, not [com.mj.yata.util.AppClock].
 */
@HiltWorker
class WidgetDailyRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val operationHistoryStore: OperationHistoryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runOperationSafely(
        operationHistoryStore = operationHistoryStore,
        operationId = OperationHistoryStore.WIDGETS_REFRESH,
        tag = TAG,
        runReason = "Daily widget refresh worker started"
    ) {
        WidgetRefresher.refreshAll(applicationContext)
        operationHistoryStore.recordSuccess(OperationHistoryStore.WIDGETS_REFRESH, "Daily refresh completed")
        Result.success()
    }

    companion object {
        private const val TAG = "WidgetDailyRefreshWorker"
        private const val WORK_NAME = "widget_daily_refresh"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(0, 5))
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun)

            val request = PeriodicWorkRequestBuilder<WidgetDailyRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
