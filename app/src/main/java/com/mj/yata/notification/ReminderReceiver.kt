package com.mj.yata.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.widget.resolveNotificationAccentColor

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) = onReceiveSafely(
        context = context,
        tag = TAG,
        operationId = OperationHistoryStore.REMINDERS_TASK
    ) {
        val taskId = intent.getStringExtra("EXTRA_TASK_ID") ?: return@onReceiveSafely
        val taskTitle = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Task Reminder"

        NotificationHelper.createChannels(context)

        // Resolving the effective M3 color needs a DataStore read, so keep the receiver alive
        // with goAsync() and treat failures as handled Diagnostics entries.
        goAsyncSafely(context, TAG, OperationHistoryStore.REMINDERS_TASK) {
            if (!NotificationPermissionUtils.areNotificationsEnabled(context)) {
                OperationHistoryStore(context.applicationContext).recordSkipped(
                    OperationHistoryStore.REMINDERS_TASK,
                    "Notification permission is disabled"
                )
                return@goAsyncSafely
            }
            val accentColor = resolveNotificationAccentColor(context)
            val notification = NotificationHelper.buildReminderNotification(
                context = context,
                taskId = taskId,
                taskTitle = taskTitle,
                accentColor = accentColor
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(taskId.hashCode(), notification)
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
