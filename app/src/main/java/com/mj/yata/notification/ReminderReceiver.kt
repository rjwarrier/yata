package com.mj.yata.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId    = intent.getStringExtra("EXTRA_TASK_ID") ?: return
        val taskTitle = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Task Reminder"

        NotificationHelper.createChannels(context)

        val notification = NotificationHelper.buildReminderNotification(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(taskId.hashCode(), notification)
    }
}
