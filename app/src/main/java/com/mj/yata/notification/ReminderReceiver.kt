package com.mj.yata.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import com.mj.yata.widget.resolveNotificationAccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId    = intent.getStringExtra("EXTRA_TASK_ID") ?: return
        val taskTitle = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Task Reminder"

        NotificationHelper.createChannels(context)

        // Resolving the app's effective M3 color (light/dark/dynamic) needs a DataStore read,
        // hence goAsync() — a plain onReceive() can't suspend and would otherwise risk the
        // process being killed before the read completes.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accentColor = resolveNotificationAccentColor(context)
                val notification = NotificationHelper.buildReminderNotification(
                    context = context,
                    taskId = taskId,
                    taskTitle = taskTitle,
                    accentColor = accentColor
                )
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(taskId.hashCode(), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
