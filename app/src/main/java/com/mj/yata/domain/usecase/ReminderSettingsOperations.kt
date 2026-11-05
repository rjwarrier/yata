package com.mj.yata.domain.usecase

import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.mapper.toEntity
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.notification.TaskReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Persist each setting before reconciling already-scheduled active alarms. */
@Singleton
class ReminderSettingsOperations @Inject constructor(
    private val preferences: UserPreferences,
    private val repository: YataRepository,
    private val scheduler: TaskReminderScheduler
) {
    private val mutex = Mutex()
    private suspend fun update(write: suspend () -> Unit) = mutex.withLock {
        write()
        scheduler.syncReminders(repository.getTasks().first().map { it.toEntity() })
    }
    suspend fun setQuietHoursEnabled(value: Boolean) = update { preferences.setQuietHoursEnabled(value) }
    suspend fun setQuietHoursStart(hour: Int, minute: Int) = update { preferences.setQuietHoursStart(hour, minute) }
    suspend fun setQuietHoursEnd(hour: Int, minute: Int) = update { preferences.setQuietHoursEnd(hour, minute) }
    suspend fun setDefaultReminderTime(hour: Int, minute: Int) = update { preferences.setDefaultReminderTime(hour, minute) }
}
