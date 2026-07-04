package com.mj.yata.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.domain.repository.YataRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Handles notification action buttons:
 *   - "Complete" → marks the task as completed.
 *   - "Snooze 1hr" → reschedules the reminder 1 hour from now.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionReceiverEntryPoint {
        fun appDatabase(): AppDatabase
        fun yataRepository(): YataRepository
        fun reminderScheduler(): ReminderScheduler
    }

    companion object {
        const val ACTION_COMPLETE_TASK    = "com.mj.yata.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK      = "com.mj.yata.ACTION_SNOOZE_TASK"
        const val ACTION_SNOOZE_15M       = "com.mj.yata.ACTION_SNOOZE_15M"
        const val ACTION_SNOOZE_TOMORROW  = "com.mj.yata.ACTION_SNOOZE_TOMORROW"
        const val EXTRA_TASK_ID        = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ActionReceiverEntryPoint::class.java
        )
        val db = entryPoint.appDatabase()
        val repository = entryPoint.yataRepository()
        val scheduler = entryPoint.reminderScheduler()
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE_TASK -> {
                        repository.toggleTaskDone(taskId)
                        notifManager.cancel(notifId)
                    }
                    ACTION_SNOOZE_TASK -> {
                        val task = db.taskDao().getByIdDirect(taskId)
                        if (task != null) {
                            scheduler.scheduleReminderDelayed(task, 60 * 60 * 1000L) // +1 hour
                            notifManager.cancel(notifId)
                        }
                    }
                    ACTION_SNOOZE_15M -> {
                        val task = db.taskDao().getByIdDirect(taskId)
                        if (task != null) {
                            scheduler.scheduleReminderDelayed(task, 15 * 60 * 1000L) // +15 min
                            notifManager.cancel(notifId)
                        }
                    }
                    ACTION_SNOOZE_TOMORROW -> {
                        val task = db.taskDao().getByIdDirect(taskId)
                        if (task != null) {
                            val tomorrow9am = LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val delay = (tomorrow9am - System.currentTimeMillis()).coerceAtLeast(0L)
                            scheduler.scheduleReminderDelayed(task, delay)
                            notifManager.cancel(notifId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
