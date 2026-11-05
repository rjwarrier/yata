package com.mj.yata

import android.app.Application
import androidx.room.Room
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.entity.TaskEntity
import com.mj.yata.data.repository.YataRepositoryImpl
import com.mj.yata.data.sync.SnapshotMerger
import com.mj.yata.data.sync.SnapshotSyncEngine
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.ProjectGroup
import com.mj.yata.domain.model.Task
import com.mj.yata.notification.TaskReminderScheduler
import com.mj.yata.util.JsonExporter
import com.mj.yata.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FocusTimerSettlementTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val scheduler = object : TaskReminderScheduler {
        override suspend fun scheduleReminder(task: TaskEntity) = Unit
        override fun scheduleReminderDelayed(task: TaskEntity, delayMillis: Long) = Unit
        override fun cancelReminder(task: TaskEntity) = Unit
        override fun cancelReminder(taskId: String) = Unit
        override suspend fun syncReminders(tasks: List<TaskEntity>) = Unit
    }
    private class Store(val db: AppDatabase, val repository: YataRepositoryImpl, val exporter: JsonExporter, val preferences: UserPreferences) : AutoCloseable {
        override fun close() = db.close()
    }
    private fun store(): Store {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        // These JVM tests don't package Android resources. Supply the one backend error string
        // while keeping the real application's DataStore and filesystem for the integration test.
        val preferenceContext = object : android.content.ContextWrapper(context) {
            override fun getResources(): android.content.res.Resources {
                val base = baseContext.resources
                return object : android.content.res.Resources(base.assets, base.displayMetrics, base.configuration) {
                    override fun getString(id: Int): String = if (id == R.string.demo_mode_sync_disabled) {
                        "Sync and backups are paused in demo mode."
                    } else base.getString(id)
                }
            }
        }
        val preferences = UserPreferences(preferenceContext)
        val repository = YataRepositoryImpl(db, scheduler, object : WidgetUpdater {
            override fun notifyTasksChanged() = Unit
        }, preferences)
        return Store(db, repository, JsonExporter(context, db, repository, preferences, scheduler), preferences)
    }

    private fun task() = Task("timer-task", "Repeat", null, null, "", "2026-11-05",
        time = null, reminder = null, priority = "none", flag = false, done = false,
        assigneeIds = emptyList(), tagIds = emptyList(), recurrence = com.mj.yata.domain.model.Recurrence("daily", 1),
        subtasks = emptyList(), notes = null, trackedMinutes = 2)

    @Test fun completionAndSkipSettleTimerIntoHistoryBeforeNextOccurrence() = runBlocking {
        for (skip in listOf(false, true)) {
            store().use { target ->
                val task = task()
                target.repository.upsertTask(task)
                target.preferences.setFocusTimer(com.mj.yata.domain.model.FocusTimer(task.id, System.currentTimeMillis() - 300_000))
                if (skip) target.repository.skipTaskOccurrence(task.id) else target.repository.toggleTaskDone(task.id)
                val tasks = target.repository.getTasks().first()
                assertEquals(0, tasks.single { it.id == task.id }.trackedMinutes)
                val history = if (skip) target.repository.getArchivedTasks().first().single() else tasks.single { it.id != task.id }
                assertEquals(7, history.trackedMinutes)
                assertEquals(null, target.preferences.focusTimerFlow.first())
            }
        }
    }

    @Test fun failedLoggingRetainsSessionForRetryAndCompetingStopsOnlyLogOnce() = runBlocking {
        store().use { target ->
            val task = task().copy(recurrence = null)
            target.repository.upsertTask(task)
            val timer = com.mj.yata.domain.model.FocusTimer(task.id, System.currentTimeMillis() - 300_000)
            target.preferences.setFocusTimer(timer)
            try {
                target.preferences.focusTimerMutex.withLock {
                    com.mj.yata.domain.usecase.settleFocusTimer(target.preferences) { _, _ -> error("write failed") }
                }
                org.junit.Assert.fail("Expected write failure")
            } catch (_: IllegalStateException) { }
            assertEquals(timer, target.preferences.focusTimerFlow.first())
            val operations = com.mj.yata.domain.usecase.FocusTimerOperations(context, target.repository, target.preferences)
            listOf(async(kotlinx.coroutines.Dispatchers.IO) { operations.stop() },
                async(kotlinx.coroutines.Dispatchers.IO) { operations.stop() }).awaitAll()
            assertEquals(7, target.repository.getTasks().first().single().trackedMinutes)
            assertEquals(null, target.preferences.focusTimerFlow.first())
        }
    }
}
