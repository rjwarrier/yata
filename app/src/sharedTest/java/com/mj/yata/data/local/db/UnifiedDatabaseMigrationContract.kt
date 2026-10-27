package com.mj.yata.data.local.db

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Executes the production migrations with foreign keys enabled and Room's full validation. */
abstract class UnifiedDatabaseMigrationContract {
    protected abstract val context: Context
    protected abstract fun fixture(name: String): String

    @Test fun common32FollowsRegisteredMigrationChain() = verifyUpgrade("common-32", false, null)
    @Test fun common33Upgrades() = verifyUpgrade("common-33", false, null)
    @Test fun master34PreservesTrackedTime() = verifyUpgrade("master-34", false, 42)
    @Test fun groups34PreservesGroups() = verifyUpgrade("groups-34", true, null)
    @Test fun groups35NormalizesNullTime() = verifyUpgrade("groups-35", true, null)
    @Test fun groups35PreservesTrackedTime() = verifyUpgrade("groups-35", true, 42)
    @Test fun groups35PreservesRelationshipsWithForeignKeysEnabled() =
        verifyUpgrade("groups-35", true, 42, migrateWithForeignKeys = true)

    @Test fun freshInstallCreatesUnifiedSchema() {
        val name = "unified-fresh-test.db"
        context.deleteDatabase(name)
        try {
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().closeAfter { room ->
                val db = room.openHelper.writableDatabase
                assertEquals(36, db.version)
                assertTrue(hasColumn(db, "projects", "groupId"))
                assertTrue(hasColumn(db, "tasks", "trackedMinutes"))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun verifyUpgrade(name: String, grouped: Boolean, tracked: Int?, migrateWithForeignKeys: Boolean = false) {
        val databaseName = "unified-$name-test.db"
        context.deleteDatabase(databaseName)
        val schema = JSONObject(fixture(name)).getJSONObject("database")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(schema.getInt("version")) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        entity.optJSONArray("indices")?.let { indices ->
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                    }
                    for (i in 0 until entities.length()) {
                        entities.getJSONObject(i).optJSONArray("contentSyncTriggers")?.let { triggers ->
                            for (j in 0 until triggers.length()) db.execSQL(triggers.getString(j))
                        }
                    }
                    schema.getJSONArray("setupQueries").let { queries ->
                        for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        try {
            FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
                val db = helper.writableDatabase
                insert(db, "people", mapOf("id" to "me", "isMe" to 1))
                insert(db, "tags", mapOf("id" to "tag"))
                insert(db, "lists", mapOf("id" to "list"))
                if (grouped) insert(db, "project_groups", mapOf("id" to "group", "name" to "Work"))
                insert(db, "projects", buildMap {
                    put("id", "project")
                    if (grouped) put("groupId", "group")
                })
                insert(db, "tasks", buildMap {
                    put("id", "task"); put("title", "Keep me searchable")
                    put("projectId", "project"); put("listId", "list")
                    put("ownerId", "me")
                    if (hasColumn(db, "tasks", "postponementCount")) put("postponementCount", 3)
                    if (hasColumn(db, "tasks", "trackedMinutes")) put("trackedMinutes", tracked)
                })
                insert(db, "task_person_cross_ref", mapOf("taskId" to "task", "personId" to "me"))
                insert(db, "task_tag_cross_ref", mapOf("taskId" to "task", "tagId" to "tag"))
                insert(db, "subtasks", mapOf("id" to "parent", "taskId" to "task"))
                insert(db, "subtasks", mapOf("id" to "child", "taskId" to "task", "parentSubtaskId" to "parent"))
                insert(db, "task_comments", mapOf("id" to "comment", "taskId" to "task", "body" to "Preserve this"))
                db.execSQL("UPDATE tasks SET rowid=41 WHERE id='task'")
                db.execSQL("INSERT INTO tasks_fts(tasks_fts) VALUES ('rebuild')")
                if (migrateWithForeignKeys) {
                    db.query("PRAGMA foreign_keys").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                    db.beginTransaction()
                    try {
                        AppDatabase.MIGRATION_35_36.migrate(db)
                        // Room revalidates the migrated schema on open when its identity row is absent.
                        db.execSQL("DROP TABLE room_master_table")
                        db.version = 36
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .addMigrations(*AppDatabase.MIGRATIONS)
                .build().closeAfter { room ->
                    val db = room.openHelper.writableDatabase // Runs migration AND Room schema validation.
                    assertEquals(36, db.version)
                    db.query("SELECT trackedMinutes, postponementCount, projectId, ownerId FROM tasks WHERE id='task'").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(tracked ?: 0, it.getInt(0))
                        assertEquals(if (name == "common-32") 0 else 3, it.getInt(1)); assertEquals("project", it.getString(2)); assertEquals("me", it.getString(3))
                    }
                    db.query("SELECT groupId FROM projects WHERE id='project'").use {
                        assertTrue(it.moveToFirst())
                        if (grouped) assertEquals("group", it.getString(0)) else assertTrue(it.isNull(0))
                    }
                    for ((table, count) in mapOf("task_person_cross_ref" to 1, "task_tag_cross_ref" to 1, "subtasks" to 2, "task_comments" to 1)) {
                        db.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(count, it.getInt(0)) }
                    }
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    db.query("SELECT docid FROM tasks_fts WHERE tasks_fts MATCH 'searchable'").use { assertTrue(it.moveToFirst()); assertEquals(41L, it.getLong(0)) }
                    // New writes must still maintain the FTS mirror after a table rebuild.
                    db.execSQL("UPDATE tasks SET title='Changedsearchterm' WHERE id='task'")
                    db.query("SELECT docid FROM tasks_fts WHERE tasks_fts MATCH 'Changedsearchterm'").use { assertTrue(it.moveToFirst()) }
                    if (grouped) {
                        db.execSQL("DELETE FROM project_groups WHERE id='group'")
                        db.query("SELECT groupId FROM projects WHERE id='project'").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
                        db.query("SELECT COUNT(*) FROM tasks").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                    }
                }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private inline fun <T> AppDatabase.closeAfter(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(1) == column) found = true
            found
        }

    private fun insert(db: SupportSQLiteDatabase, table: String, fields: Map<String, Any?>) {
        val values = ContentValues()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                if (cursor.getInt(3) != 0) {
                    if (cursor.getString(2) == "INTEGER") values.put(column, 0) else values.put(column, "")
                }
            }
        }
        fields.forEach { (key, value) ->
            when (value) {
                null -> values.putNull(key)
                is Int -> values.put(key, value)
                else -> values.put(key, value.toString())
            }
        }
        db.insert(table, 0, values)
    }
}
