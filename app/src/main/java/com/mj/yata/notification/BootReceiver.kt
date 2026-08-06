package com.mj.yata.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint

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

    override fun onReceive(context: Context, intent: Intent) = onReceiveSafely(
        context = context,
        tag = TAG,
        operationId = OperationHistoryStore.REMINDERS_TASK
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return@onReceiveSafely

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootReceiverEntryPoint::class.java
        )
        val db = entryPoint.appDatabase()
        val scheduler = entryPoint.reminderScheduler()

        goAsyncSafely(context, TAG, OperationHistoryStore.REMINDERS_TASK) {
            val tasks = db.taskDao().getActiveReminderTasksDirect()
            scheduler.scheduleReminders(tasks)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
