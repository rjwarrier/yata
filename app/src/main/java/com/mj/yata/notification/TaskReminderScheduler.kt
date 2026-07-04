package com.mj.yata.notification

import com.mj.yata.data.local.db.entity.TaskEntity

interface TaskReminderScheduler {
    fun scheduleReminder(task: TaskEntity)
    fun scheduleReminderDelayed(task: TaskEntity, delayMillis: Long)
    fun cancelReminder(task: TaskEntity)
}
