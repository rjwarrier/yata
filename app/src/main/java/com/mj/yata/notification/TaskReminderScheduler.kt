package com.mj.yata.notification

import com.mj.yata.data.local.db.entity.TaskEntity

interface TaskReminderScheduler {
    suspend fun scheduleReminder(task: TaskEntity)
    fun scheduleReminderDelayed(task: TaskEntity, delayMillis: Long)
    fun cancelReminder(task: TaskEntity)
    fun cancelReminder(taskId: String)

    /** Cancels or (re)schedules each task's reminder as appropriate, reading the default
     * reminder time from DataStore once for the whole batch instead of once per task — for
     * bulk writes (import, bulk reschedule) this avoids O(n) DataStore reads. */
    suspend fun syncReminders(tasks: List<TaskEntity>)
}
