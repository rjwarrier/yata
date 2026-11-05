package com.mj.yata.domain.usecase

import android.content.Context
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.FocusTimer
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.focusSessionMinutes
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.notification.NotificationHelper
import com.mj.yata.widget.resolveNotificationAccentColor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The focus timer: its device-local running state, the ongoing notification that shows it, and
 * logging its minutes onto [Task.trackedMinutes]. Shared by MainViewModel, the notification's Stop
 * action and app start, so all three go through the same steps.
 */
@Singleton
class FocusTimerOperations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YataRepository,
    private val userPreferences: UserPreferences
) {

    /** Starts the timer on [task]. Only one runs at a time, so a timer already running on another
     * task is stopped and its time logged first. */
    suspend fun start(task: Task) = userPreferences.focusTimerMutex.withLock {
        stopLocked()
        val timer = FocusTimer(task.id, System.currentTimeMillis())
        userPreferences.setFocusTimer(timer)
        NotificationHelper.showFocusTimer(context, timer, task.title, resolveNotificationAccentColor(context))
    }

    /** Serialize competing actions; retain the running session if logging fails. */
    suspend fun stop() = userPreferences.focusTimerMutex.withLock { stopLocked() }

    private suspend fun stopLocked() {
        settleFocusTimer(userPreferences) { timer, minutes ->
            val task = repository.getTasksByIds(listOf(timer.taskId)).firstOrNull()
            if (task != null && minutes > 0) {
                repository.upsertTask(task.copy(trackedMinutes = task.trackedMinutes + minutes), resyncReminder = false)
            }
        }
    }

    /** Re-posts the notification for a timer still running from before the process died. Called on
     * every app start; notifications don't survive a reboot, but the timer state does. */
    suspend fun restoreNotification() {
        val timer = userPreferences.focusTimerFlow.first() ?: return
        val task = repository.getTasksByIds(listOf(timer.taskId)).firstOrNull() ?: return
        NotificationHelper.showFocusTimer(context, timer, task.title, resolveNotificationAccentColor(context))
    }
}

/** Called under preferences.focusTimerMutex so logging and clearing cannot race another stop. */
internal suspend fun settleFocusTimer(
    preferences: UserPreferences,
    taskId: String? = null,
    log: suspend (FocusTimer, Int) -> Unit
) {
    val timer = preferences.focusTimerFlow.first() ?: return
    if (taskId != null && timer.taskId != taskId) return
    log(timer, focusSessionMinutes(timer.startedAt, System.currentTimeMillis()))
    preferences.setFocusTimer(null)
}
