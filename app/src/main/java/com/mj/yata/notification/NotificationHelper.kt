package com.mj.yata.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mj.yata.MainActivity

object NotificationHelper {
    const val REMINDER_CHANNEL_ID = "task_reminders_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for your due tasks"
                enableVibration(true)
            }

            nm.createNotificationChannels(listOf(reminderChannel))
        }
    }

    /** Build a high-priority reminder notification with Complete and Snooze actions. */
    fun buildReminderNotification(
        context: Context,
        taskId: String,
        taskTitle: String
    ): Notification {
        val notifId = taskId.hashCode()

        val openIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "task_detail")
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, NotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setContentTitle("Reminder: $taskTitle")
            .setContentText("This task is due")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Complete",
                actionPendingIntent(NotificationActionReceiver.ACTION_COMPLETE_TASK, notifId + 1000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "Snooze 15m",
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_15M, notifId + 2000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "Snooze 1hr",
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_TASK, notifId + 3000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "Snooze tomorrow",
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_TOMORROW, notifId + 4000)
            )
            .build()
    }
}
