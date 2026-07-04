package com.mj.yata.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mj.yata.data.local.db.AppDatabase
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-schedules all pending task reminders after a device reboot,
 * since AlarmManager alarms are cleared on reboot.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun appDatabase(): AppDatabase
        fun reminderScheduler(): ReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootReceiverEntryPoint::class.java
        )
        val db = entryPoint.appDatabase()
        val scheduler = entryPoint.reminderScheduler()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = db.taskDao().getActiveReminderTasksDirect()
                tasks.forEach { task -> scheduler.scheduleReminder(task) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
