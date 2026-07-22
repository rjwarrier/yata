package com.mj.yata.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.db.entity.TaskEntity
import com.mj.yata.util.TaskScheduleUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) : TaskReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun scheduleReminder(task: TaskEntity) {
        val defaultTime = LocalTime.of(
            userPreferences.defaultReminderHourFlow.first(),
            userPreferences.defaultReminderMinuteFlow.first()
        )
        scheduleReminder(task, defaultTime)
    }

    /**
     * Reschedules many tasks at once (e.g. every active reminder after a device reboot), reading
     * the default reminder time from DataStore once for the whole batch instead of once per task
     * — the per-task version used to do 2 DataStore reads per task, which for a user with
     * hundreds of reminders risked not finishing before BootReceiver's tight goAsync() execution
     * window runs out, silently leaving the rest unscheduled.
     */
    suspend fun scheduleReminders(tasks: List<TaskEntity>) {
        val defaultTime = LocalTime.of(
            userPreferences.defaultReminderHourFlow.first(),
            userPreferences.defaultReminderMinuteFlow.first()
        )
        tasks.forEach { task -> scheduleReminder(task, defaultTime) }
    }

    override suspend fun syncReminders(tasks: List<TaskEntity>) {
        val defaultTime = LocalTime.of(
            userPreferences.defaultReminderHourFlow.first(),
            userPreferences.defaultReminderMinuteFlow.first()
        )
        tasks.forEach { task ->
            if (task.done || task.dueDate == null || task.reminder.isNullOrBlank()) {
                cancelReminder(task)
            } else {
                scheduleReminder(task, defaultTime)
            }
        }
    }

    private fun scheduleReminder(task: TaskEntity, defaultTime: LocalTime) {
        if (task.dueDate == null || task.done || task.reminder.isNullOrBlank()) return

        val localDate = try {
            LocalDate.parse(task.dueDate)
        } catch (e: Exception) {
            return
        }

        val localTime = TaskScheduleUtils.parseTime(task.dueTime) ?: defaultTime
        val dueAtMillis = localDate.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // A custom reminder is stored as a literal clock time (e.g. "4:45 PM") rather than one
        // of the relative-offset presets — fire at that exact time on the due date instead of
        // computing an offset from the due time.
        val customReminderTime = TaskScheduleUtils.parseTime(task.reminder)
        val triggerAtMillis = if (customReminderTime != null) {
            localDate.atTime(customReminderTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            dueAtMillis - TaskScheduleUtils.reminderOffsetMillis(task.reminder)
        }

        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancelReminder(task)
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("EXTRA_TASK_ID", task.id)
            putExtra("EXTRA_TASK_TITLE", task.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    override fun scheduleReminderDelayed(task: TaskEntity, delayMillis: Long) {
        val triggerAtMillis = System.currentTimeMillis() + delayMillis

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("EXTRA_TASK_ID", task.id)
            putExtra("EXTRA_TASK_TITLE", task.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    /** Checks exact-alarm permission up front instead of relying on catching the
     * `SecurityException` `setExactAndAllowWhileIdle` throws when it's been revoked — that
     * still worked, but every single reminder scheduled after the user turns exact alarms off
     * paid the cost (and logging noise) of a thrown-and-caught exception on every call. */
    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (NotificationPermissionUtils.canScheduleExactAlarms(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (e: SecurityException) {
                // Permission was revoked between the check above and this call — fall through.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    override fun cancelReminder(task: TaskEntity) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
