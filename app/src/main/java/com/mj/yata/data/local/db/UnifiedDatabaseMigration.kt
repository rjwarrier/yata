package com.mj.yata.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Converges both shipped v34 layouts and v35 without dropping user relationships. */
internal object UnifiedDatabaseMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `project_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
        if (!columns(db, "projects").contains("groupId")) {
            // SQLite permits a nullable REFERENCES column via ALTER TABLE. Rebuilding projects
            // would fire its ON DELETE CASCADE and remove the project's tasks.
            db.execSQL("ALTER TABLE `projects` ADD COLUMN `groupId` TEXT REFERENCES `project_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_groupId` ON `projects` (`groupId`)")

        var nullable = false
        val hasTracked = db.query("PRAGMA table_info(`tasks`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "trackedMinutes") {
                    found = true
                    nullable = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 0
                }
            }
            found
        }
        if (!hasTracked) {
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `trackedMinutes` INTEGER NOT NULL DEFAULT 0")
        } else if (nullable) {
            rebuildTasks(db)
        }
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Database upgrade left an invalid relationship" }
        }
    }

    private fun rebuildTasks(db: SupportSQLiteDatabase) {
        // DROP TABLE fires foreign-key cascades even within a Room migration. Copy every direct
        // dependent first, including nested subtasks, then restore their original rowids.
        val dependents = listOf("task_person_cross_ref", "task_tag_cross_ref", "subtasks", "task_comments")
        val savedColumns = dependents.associateWith { table ->
            val names = columns(db, table)
            db.execSQL("CREATE TEMP TABLE `db36_$table` AS SELECT rowid AS migration_rowid, * FROM `$table`")
            names
        }
        // Room may enable foreign keys only after onUpgrade. Empty the saved tables explicitly
        // so restoration works both with cascades enabled and with them temporarily disabled.
        dependents.forEach { db.execSQL("DELETE FROM `$it`") }
        val indicesAndTriggers = db.query(
            "SELECT sql FROM sqlite_master WHERE tbl_name = 'tasks' AND type IN ('index', 'trigger') AND sql IS NOT NULL"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        db.execSQL(TASKS_SQL)
        val names = columns(db, "tasks")
        val quoted = names.joinToString(",") { "`$it`" }
        val values = names.joinToString(",") { if (it == "trackedMinutes") "COALESCE(`trackedMinutes`, 0)" else "`$it`" }
        db.execSQL("INSERT INTO `tasks_unified` (rowid,$quoted) SELECT rowid,$values FROM `tasks`")
        db.execSQL("DROP TABLE `tasks`")
        db.execSQL("ALTER TABLE `tasks_unified` RENAME TO `tasks`")
        indicesAndTriggers.forEach(db::execSQL)
        savedColumns.forEach { (table, fields) ->
            val fieldsSql = fields.joinToString(",") { "`$it`" }
            db.execSQL("INSERT INTO `$table` (rowid,$fieldsSql) SELECT migration_rowid,$fieldsSql FROM `db36_$table`")
            db.execSQL("DROP TABLE `db36_$table`")
        }
        db.execSQL("INSERT INTO `tasks_fts` (`tasks_fts`) VALUES ('rebuild')")
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private const val TASKS_SQL = """CREATE TABLE IF NOT EXISTS `tasks_unified` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `listId` TEXT, `projectId` TEXT, `section` TEXT NOT NULL, `dueDate` TEXT, `startDate` TEXT, `dueTime` TEXT, `reminder` TEXT, `priority` TEXT NOT NULL, `flag` INTEGER NOT NULL, `done` INTEGER NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER, `deletedAt` INTEGER, `notes` TEXT, `recurrenceJson` TEXT, `sortOrder` INTEGER NOT NULL, `seriesId` TEXT, `archived` INTEGER NOT NULL, `followUpAt` INTEGER, `estimateMinutes` INTEGER, `ownerId` TEXT, `postponementCount` INTEGER NOT NULL, `trackedMinutes` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
}
