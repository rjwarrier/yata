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
class ContainerDeletionSafetyTest {
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

    @Test fun deletingContainersPreservesPreviouslyTrashedTaskGraphs() = runBlocking {
        for (projectContainer in listOf(true, false)) {
            store().use { target ->
                val project = Project("project", "Project", "accentA", "work")
                val list = com.mj.yata.domain.model.YataList("list", "List", "accentA", "list")
                target.repository.upsertProject(project)
                target.repository.upsertList(list)
                val task = Task(
                    id = "trashed", title = "Keep", listId = if (projectContainer) null else list.id,
                    projectId = if (projectContainer) project.id else null, section = "", due = null,
                    time = null, reminder = null, priority = "none", flag = false, done = false,
                    deletedAt = 123L, assigneeIds = emptyList(), tagIds = emptyList(), recurrence = null,
                    subtasks = listOf(com.mj.yata.domain.model.Subtask("parent", "Parent", false),
                        com.mj.yata.domain.model.Subtask("child", "Child", true, "parent", sortOrder = 1)), notes = "Keep notes"
                )
                target.repository.upsertTask(task)
                if (projectContainer) target.repository.deleteProject(project) else target.repository.deleteList(list)
                val kept = target.repository.getDeletedTasks().first().single()
                assertEquals(123L, kept.deletedAt)
                assertEquals(null, kept.projectId)
                assertEquals(null, kept.listId)
                assertEquals(task.subtasks, kept.subtasks)
            }
        }
    }
}
