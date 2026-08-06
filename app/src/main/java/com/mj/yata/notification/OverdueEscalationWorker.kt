package com.mj.yata.notification

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.widget.resolveNotificationAccentColor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily check for the partner/owner: which teammates have tasks that are overdue by at least
 * [ESCALATION_THRESHOLD_DAYS] days. A single grouped notification, not per-task — the due-date
 * reminder (see [ReminderReceiver]) already fires on the day a task is due, so this is
 * specifically for work that's slipped past that point and needs the owner's attention.
 */
@HiltWorker
class OverdueEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: YataRepository,
    private val operationHistoryStore: OperationHistoryStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runOperationSafely(
        operationHistoryStore = operationHistoryStore,
        operationId = OperationHistoryStore.REMINDERS_OVERDUE_ESCALATION,
        tag = TAG,
        runReason = "Overdue escalation worker started"
    ) {
        val tasks = repository.getTasks().first()
        val people = repository.getPeople().first()
        val today = LocalDate.now()

        val lines = people.mapNotNull { person ->
            val overdueCount = tasks.count { task ->
                if (task.done || person.id !in task.assigneeIds) return@count false
                val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
                due.isBefore(today.minusDays(ESCALATION_THRESHOLD_DAYS - 1))
            }
            if (overdueCount > 0) "${person.name}: $overdueCount overdue" else null
        }

        if (lines.isEmpty()) {
            operationHistoryStore.recordSuccess(OperationHistoryStore.REMINDERS_OVERDUE_ESCALATION, "No overdue escalations")
            return@runOperationSafely Result.success()
        }

        NotificationHelper.createChannels(applicationContext)
        if (!NotificationPermissionUtils.areNotificationsEnabled(applicationContext)) {
            operationHistoryStore.recordSkipped(
                OperationHistoryStore.REMINDERS_OVERDUE_ESCALATION,
                "Notification permission is disabled"
            )
            return@runOperationSafely Result.success()
        }
        val accentColor = resolveNotificationAccentColor(applicationContext)
        val notification = NotificationHelper.buildEscalationNotification(applicationContext, accentColor, lines)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationHelper.ESCALATION_NOTIFICATION_ID, notification)

        operationHistoryStore.recordSuccess(
            OperationHistoryStore.REMINDERS_OVERDUE_ESCALATION,
            "Posted ${lines.size} overdue escalation line(s)"
        )
        Result.success()
    }

    companion object {
        private const val TAG = "OverdueEscalationWorker"
        private const val WORK_NAME = "overdue_escalation_daily"
        const val ESCALATION_THRESHOLD_DAYS = 2L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OverdueEscalationWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
