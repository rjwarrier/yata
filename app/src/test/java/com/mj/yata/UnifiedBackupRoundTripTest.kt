package com.mj.yata

import android.app.Application
import androidx.room.Room
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.entity.TaskEntity
import com.mj.yata.data.repository.YataRepositoryImpl
import com.mj.yata.data.sync.SnapshotMerger
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
class UnifiedBackupRoundTripTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val scheduler = object : TaskReminderScheduler {
        override suspend fun scheduleReminder(task: TaskEntity) = Unit
        override fun scheduleReminderDelayed(task: TaskEntity, delayMillis: Long) = Unit
        override fun cancelReminder(task: TaskEntity) = Unit
        override fun cancelReminder(taskId: String) = Unit
        override suspend fun syncReminders(tasks: List<TaskEntity>) = Unit
    }
    private class Store(val db: AppDatabase, val repository: YataRepositoryImpl, val exporter: JsonExporter) : AutoCloseable {
        override fun close() = db.close()
    }
    private fun store(): Store {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val preferences = UserPreferences(context)
        val repository = YataRepositoryImpl(db, scheduler, object : WidgetUpdater {
            override fun notifyTasksChanged() = Unit
        }, preferences)
        return Store(db, repository, JsonExporter(context, db, repository, preferences, scheduler))
    }

    @Test fun backupImportAndRepeatedSyncPreserveGroupsAndTrackedTime() = runBlocking {
        store().use { source ->
            source.repository.upsertPerson(Person("me", "You", "Y", "accentA", isMe = true))
            source.repository.upsertProjectGroup(ProjectGroup("group", "Work", "accentB"))
            source.repository.upsertProject(Project("project", "Project", "accentA", "work", groupId = "group"))
            source.repository.upsertTask(Task(
                id = "task", title = "Tracked", listId = null, projectId = "project", section = "",
                due = null, time = null, reminder = null, priority = "none", flag = false, done = false,
                assigneeIds = listOf("me"), tagIds = emptyList(), recurrence = null,
                subtasks = emptyList(), notes = null, trackedMinutes = 42
            ))
            val bytes = source.exporter.exportToBytes()
            val original = SnapshotMerger.normalizeForSync(JSONObject(String(bytes)))
            assertEquals(5, original.getInt("version"))
            store().use { target ->
                assertTrue(target.exporter.importBytes(bytes))
                assertEquals("Work", target.repository.getProjectGroups().first().single().name)
                assertEquals("group", target.repository.getProjects().first().single().groupId)
                assertEquals(42, target.repository.getTasks().first().single().trackedMinutes)
                repeat(2) {
                    assertTrue(target.exporter.replaceBytesForSync(original.toString().toByteArray()))
                    val restored = SnapshotMerger.normalizeForSync(JSONObject(String(target.exporter.exportToBytes())))
                    assertTrue(SnapshotMerger.equivalent(original, restored))
                }
                // Invalid foreign keys are rejected before existing data can change.
                val invalid = JSONObject(original.toString()).put("projectGroups", JSONArray())
                assertFalse(target.exporter.replaceBytesForSync(invalid.toString().toByteArray()))
                assertEquals("group", target.repository.getProjects().first().single().groupId)
            }
        }
    }

    @Test fun oldBackupsImportWithMissingOrNullTrackedTime() = runBlocking {
        for (explicitNull in listOf(false, true)) {
            store().use { target ->
                val task = JSONObject().apply {
                    put("id", "old"); put("title", "Old task"); put("section", ""); put("priority", "none")
                    put("assigneeIds", JSONArray()); put("tagIds", JSONArray()); put("subtasks", JSONArray())
                    if (explicitNull) put("trackedMinutes", JSONObject.NULL)
                }
                val backup = JSONObject().put("version", 4).put("tasks", JSONArray().put(task))
                assertTrue(target.exporter.importBytes(backup.toString().toByteArray()))
                assertEquals(0, target.repository.getTasks().first().single().trackedMinutes)
            }
        }
    }
}
