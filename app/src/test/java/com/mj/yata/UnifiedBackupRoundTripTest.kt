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
class UnifiedBackupRoundTripTest {
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

    @Test fun demoRejectsExportsImportsAndAllBackupTransportsWithoutChangingRealData() = runBlocking {
        store().use { target ->
            target.repository.upsertPerson(Person("me", "Real user", "R", "accentA", isMe = true))
            target.repository.upsertProject(Project("real-project", "Real project", "accentA", "work"))
            target.preferences.setUserName("Real user")
            val before = target.exporter.exportToBytes()
            val recovery = RecoveryBackupManager(context, target.exporter)
            val engine = SnapshotSyncEngine(context, target.exporter, recovery)
            val credentials = com.mj.yata.data.sftp.RemoteBackupCredentialsStore(context)
            val transports = listOf<com.mj.yata.domain.sync.SyncTransport>(
                com.mj.yata.data.github.GitHubSyncManager(context, target.preferences, credentials, engine, recovery, target.exporter),
                com.mj.yata.data.sftp.SftpBackupManager(context, target.exporter, target.preferences, credentials, engine, recovery),
                com.mj.yata.data.ftp.FtpBackupManager(context, target.exporter, target.preferences, credentials, engine, recovery)
            )
            val local = com.mj.yata.data.local.backup.LocalBackupManager(context, target.exporter, target.preferences, recovery)
            val operations = com.mj.yata.domain.usecase.BackupOperations(
                target.repository, target.exporter, local,
                transports[1] as com.mj.yata.data.sftp.SftpBackupManager,
                transports[2] as com.mj.yata.data.ftp.FtpBackupManager,
                transports[0] as com.mj.yata.data.github.GitHubSyncManager,
                engine, target.preferences,
                com.mj.yata.data.local.operationhistory.OperationHistoryStore(context), context
            )
            target.preferences.setLocalBackupEnabled(true)
            target.preferences.setSftpBackupEnabled(true)
            val fingerprintBefore = target.preferences.autoBackupLastFingerprintFlow.first()
            target.preferences.setDemoModeEnabled(true)
            try {
                try {
                    target.exporter.exportToBytes()
                    org.junit.Assert.fail("Demo data could be exported")
                } catch (_: com.mj.yata.data.demo.DemoModeDataAccessException) { }
                assertFalse(target.exporter.importBytes(before))
                assertFalse(target.exporter.replaceBytesForSync(before))
                assertTrue(local.backupNow().exceptionOrNull() is com.mj.yata.data.demo.DemoModeDataAccessException)
                assertTrue(local.restoreLatest().exceptionOrNull() is com.mj.yata.data.demo.DemoModeDataAccessException)
                assertTrue(operations.backupAllConfigured().isEmpty())
                assertTrue(operations.runScheduledBackupIfNeeded("demo test") == null)
                assertTrue(operations.syncSelfHostedIfConfigured() == null)
                operations.scheduleDebouncedBackup()
                kotlinx.coroutines.withTimeout(5_000) { operations.syncPendingOrInProgress.first { !it } }
                assertEquals(fingerprintBefore, target.preferences.autoBackupLastFingerprintFlow.first())
                for (transport in transports) {
                    assertTrue(transport.syncNow().exceptionOrNull() is com.mj.yata.data.demo.DemoModeDataAccessException)
                    assertTrue(transport.restore("unused").exceptionOrNull() is com.mj.yata.data.demo.DemoModeDataAccessException)
                }
                target.preferences.importPortableSettings(listOf(
                    com.mj.yata.data.local.datastore.PortableSetting("demo_mode_enabled", "bool", false)
                ))
                assertTrue(target.preferences.demoModeEnabledFlow.first())
                assertTrue(target.preferences.exportPortableSettings().none { it.name == "demo_mode_enabled" })
                assertEquals("Real project", target.repository.getProjects().first().single().name)
                assertEquals("Real user", target.preferences.userNameFlow.first())
            } finally {
                operations.cancelDebouncedBackup()
                target.preferences.setDemoModeEnabled(false)
            }
            val after = target.exporter.exportToBytes()
            assertTrue(SnapshotMerger.equivalent(
                SnapshotMerger.normalizeForSync(JSONObject(String(before))),
                SnapshotMerger.normalizeForSync(JSONObject(String(after)))
            ))
        }
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

    @Test fun repeatedLegacySyncKeepsAssignmentsAndRequestsCloudRepair() = runBlocking {
        store().use { target ->
            target.repository.upsertPerson(Person("me", "You", "Y", "accentA", isMe = true))
            target.repository.upsertProjectGroup(ProjectGroup("group", "Apps", "accentB"))
            target.repository.upsertProject(Project("project", "Project", "accentA", "work", groupId = "group"))
            val canonical = target.exporter.exportToBytes()
            val legacy = JSONObject(String(canonical)).put("version", 4).apply { remove("projectGroups") }
            legacy.getJSONArray("projects").getJSONObject(0).remove("groupId")
            val engine = SnapshotSyncEngine(context, target.exporter, RecoveryBackupManager(context, target.exporter))
            val scope = "project-group-legacy-regression"
            engine.commitRestoredBaseline(scope, canonical)
            repeat(2) {
                val prepared = engine.prepare(legacy.toString().toByteArray(), scope)
                assertTrue(prepared.remoteNeedsPublish)
                assertEquals("group", prepared.canonical.getJSONArray("projects").getJSONObject(0).getString("groupId"))
                engine.commit(prepared)
                assertEquals("group", target.repository.getProjects().first().single().groupId)
                assertEquals(1, target.repository.getProjectGroups().first().size)
            }
        }
    }

    @Test fun legacyProjectImportPreservesMembershipButExplicitNullClearsIt() = runBlocking {
        store().use { target ->
            target.repository.upsertProjectGroup(ProjectGroup("group", "Apps", "accentB"))
            target.repository.upsertProject(Project("project", "Project", "accentA", "work", groupId = "group"))
            val project = JSONObject().put("id", "project").put("name", "Imported name")
                .put("color", "accentA").put("icon", "work")
            val legacy = JSONObject().put("version", 4).put("tasks", JSONArray())
                .put("projects", JSONArray().put(project))
            assertTrue(target.exporter.importBytes(legacy.toString().toByteArray()))
            assertEquals("Imported name", target.repository.getProjects().first().single().name)
            assertEquals("group", target.repository.getProjects().first().single().groupId)
            project.put("groupId", JSONObject.NULL)
            assertTrue(target.exporter.importBytes(legacy.toString().toByteArray()))
            assertTrue(target.repository.getProjects().first().single().groupId == null)
            assertEquals(1, target.repository.getProjectGroups().first().size)
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
