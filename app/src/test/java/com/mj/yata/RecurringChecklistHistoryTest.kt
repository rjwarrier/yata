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
class RecurringChecklistHistoryTest {
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

    @Test fun completionAndSkipRetainHistoryHierarchyAndResetNextChecklist() = runBlocking {
        for (skip in listOf(false, true)) {
            store().use { target ->
                val task = Task("recurring", "Repeat", null, null, "", "2020-01-01",
                    time = null, reminder = null, priority = "none", flag = false, done = false,
                    assigneeIds = emptyList(), tagIds = emptyList(), recurrence = com.mj.yata.domain.model.Recurrence("daily", 1),
                    subtasks = listOf(com.mj.yata.domain.model.Subtask("parent", "Parent", true),
                        com.mj.yata.domain.model.Subtask("child", "Child", true, "parent", 1)), notes = null)
                target.repository.upsertTask(task)
                if (skip) target.repository.skipTaskOccurrence(task.id) else target.repository.toggleTaskDone(task.id)
                val live = target.repository.getTasks().first()
                val next = live.single { it.id == task.id }
                assertEquals("2020-01-02", next.due)
                assertTrue(next.subtasks.none { it.done })
                val history = if (skip) target.repository.getArchivedTasks().first().single() else live.single { it.id != task.id }
                assertEquals(!skip, history.done)
                assertEquals(skip, history.archived)
                assertEquals(history.subtasks[0].id, history.subtasks[1].parentSubtaskId)
                assertTrue(history.subtasks.all { it.done })
                assertTrue(history.subtasks.none { h -> next.subtasks.any { it.id == h.id } })
                if (skip) assertEquals(1, live.size)
            }
        }
    }
}
