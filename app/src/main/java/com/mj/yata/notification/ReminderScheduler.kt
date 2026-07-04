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
        if (task.dueDate == null || task.done || task.reminder.isNullOrBlank()) return

        val localDate = try {
            LocalDate.parse(task.dueDate)
        } catch (e: Exception) {
            return
        }

        val defaultTime = LocalTime.of(
            userPreferences.defaultReminderHourFlow.first(),
            userPreferences.defaultReminderMinuteFlow.first()
        )
        val localTime = TaskScheduleUtils.parseTime(task.dueTime) ?: defaultTime
        val reminderOffset = TaskScheduleUtils.reminderOffsetMillis(task.reminder)
        val dueAtMillis = localDate.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val triggerAtMillis = dueAtMillis - reminderOffset

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

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
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

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
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
