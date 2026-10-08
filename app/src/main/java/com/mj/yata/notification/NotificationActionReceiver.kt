package com.mj.yata.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.capitalizeTaskSentence
import com.mj.yata.util.withParsedQuickAdd
import java.util.UUID
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Handles notification action buttons:
 *   - "Complete" → marks the task as completed.
 *   - "Snooze 1hr" → reschedules the reminder 1 hour from now.
 *   - "Add task" (inline reply on the daily agenda) → creates a task from the typed text.
 *   - "Stop timer" (focus timer notification) → stops the timer and logs its minutes.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionReceiverEntryPoint {
        fun appDatabase(): AppDatabase
        fun yataRepository(): YataRepository
        fun reminderScheduler(): ReminderScheduler
        fun userPreferences(): UserPreferences
        fun focusTimerOperations(): com.mj.yata.domain.usecase.FocusTimerOperations
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_COMPLETE_TASK    = "com.mj.yata.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK      = "com.mj.yata.ACTION_SNOOZE_TASK"
        const val ACTION_SNOOZE_15M       = "com.mj.yata.ACTION_SNOOZE_15M"
        const val ACTION_SNOOZE_TOMORROW  = "com.mj.yata.ACTION_SNOOZE_TOMORROW"
        const val ACTION_QUICK_ADD        = "com.mj.yata.ACTION_QUICK_ADD"
        const val ACTION_STOP_FOCUS_TIMER = "com.mj.yata.ACTION_STOP_FOCUS_TIMER"
        const val KEY_QUICK_ADD_TEXT   = "quick_add_text"
        const val EXTRA_TASK_ID        = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) = onReceiveSafely(
        context = context,
        tag = TAG,
        operationId = OperationHistoryStore.REMINDERS_TASK
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ActionReceiverEntryPoint::class.java
        )

        if (intent.action == ACTION_STOP_FOCUS_TIMER) {
            goAsyncSafely(context, TAG, OperationHistoryStore.REMINDERS_TASK) {
                entryPoint.focusTimerOperations().stop()
            }
            return@onReceiveSafely
        }

        if (intent.action == ACTION_QUICK_ADD) {
            val text = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(KEY_QUICK_ADD_TEXT)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@onReceiveSafely
            goAsyncSafely(context, TAG, OperationHistoryStore.REMINDERS_TASK) {
                addTaskFromReply(entryPoint.yataRepository(), entryPoint.userPreferences(), text)
                showReplyInNotification(context, text)
            }
            return@onReceiveSafely
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return@onReceiveSafely
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())
        val db = entryPoint.appDatabase()
        val repository = entryPoint.yataRepository()
        val scheduler = entryPoint.reminderScheduler()
        val userPreferences = entryPoint.userPreferences()
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        goAsyncSafely(context, TAG, OperationHistoryStore.REMINDERS_TASK) {
            when (intent.action) {
                ACTION_COMPLETE_TASK -> {
                    repository.toggleTaskDone(taskId)
                    notifManager.cancel(notifId)
                }
                ACTION_SNOOZE_TASK -> {
                    val task = db.taskDao().getByIdDirect(taskId)
                    if (task != null) {
                        scheduler.scheduleReminderDelayed(task, 60 * 60 * 1000L) // +1 hour
                        notifManager.cancel(notifId)
                    }
                }
                ACTION_SNOOZE_15M -> {
                    val task = db.taskDao().getByIdDirect(taskId)
                    if (task != null) {
                        scheduler.scheduleReminderDelayed(task, 15 * 60 * 1000L) // +15 min
                        notifManager.cancel(notifId)
                    }
                }
                ACTION_SNOOZE_TOMORROW -> {
                    val task = db.taskDao().getByIdDirect(taskId)
                    if (task != null) {
                        val tomorrowTime = LocalTime.of(
                            userPreferences.snoozeTomorrowHourFlow.first(),
                            userPreferences.snoozeTomorrowMinuteFlow.first()
                        )
                        val tomorrow = LocalDate.now().plusDays(1).atTime(tomorrowTime)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val delay = (tomorrow - System.currentTimeMillis()).coerceAtLeast(0L)
                        scheduler.scheduleReminderDelayed(task, delay)
                        notifManager.cancel(notifId)
                    }
                }
            }
        }
    }

    /**
     * A reply typed into the daily agenda notification, created the way the Quick Add widget
     * creates one with no preset destination: parsed by NaturalLanguageParser, auto-assigned if
     * that setting is on, and a typed project/list with no date lands on today. No duplicate
     * prompt — there's nowhere to show one, and the text was typed on purpose.
     */
    private suspend fun addTaskFromReply(repository: YataRepository, userPreferences: UserPreferences, text: String) {
        // This can run in a cold process, before MainViewModel has configured the user's aliases.
        NaturalLanguageParser.configureDateAliases(userPreferences.dateAliasDefinitionsFlow.first())
        val parsed = NaturalLanguageParser.parse(text)
        val people = repository.getPeople().first()
        val task = Task(
            id = "t_" + UUID.randomUUID().toString(),
            title = capitalizeTaskSentence(parsed.title.ifBlank { text }),
            listId = null,
            projectId = null,
            section = "",
            due = null,
            time = null,
            reminder = null,
            priority = "none",
            flag = false,
            done = false,
            assigneeIds = if (userPreferences.autoAssignToMeFlow.first()) listOfNotNull(people.find { it.isMe }?.id) else emptyList(),
            tagIds = emptyList(),
            recurrence = null,
            subtasks = emptyList(),
            notes = null
        ).withParsedQuickAdd(
            quickAdd = parsed,
            lists = repository.getLists().first(),
            projects = repository.getProjects().first(),
            people = people,
            tags = repository.getTags().first(),
            projectsEnabled = userPreferences.projectsFeatureEnabledFlow.first(),
            tagsEnabled = userPreferences.tagsFeatureEnabledFlow.first(),
            peopleEnabled = userPreferences.peopleFeatureEnabledFlow.first()
        )
        val hasDestination = task.listId != null || task.projectId != null
        repository.upsertTask(if (task.due == null && hasDestination) task.copy(due = LocalDate.now().toString()) else task)
    }

    /** The system shows a spinner on the reply field until the notification is updated; adding
     * the reply to its history both stops that and shows what was added. */
    private fun showReplyInNotification(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = manager.activeNotifications
            .firstOrNull { it.id == NotificationHelper.DAILY_AGENDA_NOTIFICATION_ID } ?: return
        val updated = Notification.Builder.recoverBuilder(context, active.notification)
            .setRemoteInputHistory(arrayOf<CharSequence>(text))
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(active.id, updated)
    }
}
