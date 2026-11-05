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
import kotlinx.coroutines.cancel
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
class SyncCommitConcurrencyTest {
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

    @Test fun editsAndAdditionsAtFinalApplyBoundarySurvive() = runBlocking {
        store().use { target ->
            target.repository.upsertPerson(Person("me", "You", "Y", "accentA", isMe = true))
            val original = Task("original", "Original", null, null, "", "2026-11-01",
                time = null, reminder = null, priority = "none", flag = false, done = false,
                assigneeIds = emptyList(), tagIds = emptyList(), recurrence = null, subtasks = emptyList(), notes = null)
            target.repository.upsertTask(original)
            val recovery = RecoveryBackupManager(context, target.exporter)
            val engine = SnapshotSyncEngine(context, target.exporter, recovery)
            val scope = "concurrent-test"
            val baseline = JSONObject(String(target.exporter.exportToBytes()))
            engine.commitRestoredBaseline(scope, baseline.toString().toByteArray())
            val remote = JSONObject(baseline.toString())
            // An unchanged canonical snapshot exercises the same final transaction without
            // requiring AndroidKeyStore, which is unavailable in JVM tests.
            val prepared = engine.prepare(remote.toString().toByteArray(), scope)
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val writerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            var writer: kotlinx.coroutines.Deferred<Unit>? = null
            try {
                engine.commit(prepared) {
                    writer = writerScope.async {
                        started.complete(Unit)
                        target.repository.upsertTasks(listOf(original.copy(title = "Local edit"), original.copy(id = "added", title = "Local addition")))
                    }
                    started.await()
                    assertEquals(null, kotlinx.coroutines.withTimeoutOrNull(250) { writer!!.await(); true })
                }
                writer!!.await()
                val tasks = target.repository.getTasks().first().associateBy { it.id }
                assertEquals("Local edit", tasks.getValue("original").title)
                assertEquals("Local addition", tasks.getValue("added").title)
            } finally { writerScope.cancel() }
        }
    }
}
