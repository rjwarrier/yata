package com.mj.yata.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_preservesTasks_andAddsFeeTables() {
        context.deleteDatabase(TEST_DB)
        createVersion4Database().apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `projects` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `colorHex` TEXT NOT NULL,
                    `icon` TEXT,
                    `isArchived` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT,
                    `isCompleted` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `dueDate` INTEGER,
                    `dueTime` INTEGER,
                    `reminderAt` INTEGER,
                    `priority` TEXT NOT NULL,
                    `isStarred` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `projectId` INTEGER,
                    `assignedTo` TEXT,
                    `parentTaskId` INTEGER,
                    `completedBy` TEXT,
                    `estimatedPomodoros` INTEGER,
                    `completedPomodoros` INTEGER NOT NULL,
                    `repeat_frequency` TEXT,
                    `repeat_interval` INTEGER,
                    `repeat_daysOfWeek` TEXT,
                    `repeat_endDate` INTEGER,
                    `repeat_endAfterOccurrences` INTEGER,
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")

            execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `tasks_fts` USING FTS4(`title` TEXT NOT NULL, `description` TEXT, content=`tasks`)")
            execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tasks_fts_BEFORE_UPDATE BEFORE UPDATE ON `tasks` BEGIN DELETE FROM `tasks_fts` WHERE `docid`=OLD.`rowid`; END")
            execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tasks_fts_BEFORE_DELETE BEFORE DELETE ON `tasks` BEGIN DELETE FROM `tasks_fts` WHERE `docid`=OLD.`rowid`; END")
            execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tasks_fts_AFTER_UPDATE AFTER UPDATE ON `tasks` BEGIN INSERT INTO `tasks_fts`(`docid`, `title`, `description`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`); END")
            execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_tasks_fts_AFTER_INSERT AFTER INSERT ON `tasks` BEGIN INSERT INTO `tasks_fts`(`docid`, `title`, `description`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`); END")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `labels` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `icon` TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `task_label_cross_ref` (
                    `taskId` INTEGER NOT NULL,
                    `labelId` INTEGER NOT NULL,
                    PRIMARY KEY(`taskId`, `labelId`),
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_task_label_cross_ref_labelId` ON `task_label_cross_ref` (`labelId`)")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `task_updates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `message` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_task_updates_taskId` ON `task_updates` (`taskId`)")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pomodoro_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER,
                    `durationMinutes` INTEGER NOT NULL,
                    `actualDurationSeconds` INTEGER,
                    `type` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `note` TEXT,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_pomodoro_sessions_taskId` ON `pomodoro_sessions` (`taskId`)")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_taskId` ON `attachments` (`taskId`)")

            execSQL(
                """
                INSERT INTO `projects` (`id`, `name`, `description`, `colorHex`, `icon`, `isArchived`, `createdAt`, `sortOrder`)
                VALUES (1, 'Inbox', NULL, '#4C662B', NULL, 0, 1710000000000, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `tasks` (
                    `id`, `title`, `description`, `isCompleted`, `completedAt`, `createdAt`, `updatedAt`,
                    `dueDate`, `dueTime`, `reminderAt`, `priority`, `isStarred`, `sortOrder`,
                    `projectId`, `assignedTo`, `parentTaskId`, `completedBy`, `estimatedPomodoros`,
                    `completedPomodoros`, `repeat_frequency`, `repeat_interval`, `repeat_daysOfWeek`,
                    `repeat_endDate`, `repeat_endAfterOccurrences`
                ) VALUES (
                    7, 'Survive migration', 'seed task', 0, NULL, 1710000000000, 1710000005000,
                    1710086400000, NULL, NULL, 'HIGH', 1, 3,
                    1, 'Ada', NULL, NULL, 4,
                    1, NULL, NULL, NULL,
                    NULL, NULL
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).apply {
            query("SELECT `title`, `projectId`, `clientId`, `feeInvoiceId` FROM `tasks` WHERE `id` = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Survive migration", cursor.getString(0))
                assertEquals(1L, cursor.getLong(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }

            query("PRAGMA table_info(`tasks`)").use { cursor ->
                val columnNames = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columnNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertTrue(columnNames.contains("clientId"))
                assertTrue(columnNames.contains("feeInvoiceId"))
            }

            val expectedFeeTables = setOf(
                "firm_profile",
                "fee_groups",
                "clients",
                "invoices",
                "invoice_line_items",
                "payments",
                "invoice_counter"
            )
            query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                val actualTables = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    actualTables += cursor.getString(0)
                }
                assertTrue(actualTables.containsAll(expectedFeeTables))
            }
            close()
        }
    }

    // Note: this project builds with exportSchema = false, so no schema JSON assets exist for
    // MigrationTestHelper.runMigrationsAndValidate() to diff against (it throws
    // FileNotFoundException for <version>.json regardless of which migration is under test —
    // that's a pre-existing gap, not specific to this migration). Exercise the migration
    // directly against a raw SQLite callback instead of relying on schema-file validation.
    @Test
    fun migrate9To10_addsSortOrder_andBackfillsFromRowid() {
        context.deleteDatabase(TEST_DB)
        createVersion9Database().apply {
            execSQL(
                "INSERT INTO `tasks` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`subtasksJson`) " +
                    "VALUES ('t1','First',NULL,NULL,'Morning',NULL,NULL,NULL,'none',0,0,NULL,NULL,NULL)"
            )
            execSQL(
                "INSERT INTO `tasks` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`subtasksJson`) " +
                    "VALUES ('t2','Second',NULL,NULL,'Morning',NULL,NULL,NULL,'none',0,0,NULL,NULL,NULL)"
            )
            close()
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_9_10.migrate(db)
                }
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.apply {
            query("PRAGMA table_info(`tasks`)").use { cursor ->
                val columnNames = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columnNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertTrue(columnNames.contains("sortOrder"))
            }
            query("SELECT `id`, `sortOrder` FROM `tasks` ORDER BY `id`").use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    assertTrue(cursor.getInt(1) >= 0)
                    count++
                }
                assertEquals(2, count)
            }
            close()
        }
    }

    @Test
    fun migrate10To11_convertsSubtasksJsonToRows_andDropsColumn() {
        context.deleteDatabase(TEST_DB)
        createVersion10Database().apply {
            execSQL(
                "INSERT INTO `tasks` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`subtasksJson`,`sortOrder`) " +
                    "VALUES ('t1','Has subtasks',NULL,NULL,'Morning',NULL,NULL,NULL,'none',0,0,NULL,NULL," +
                    "'[{\"id\":\"s1\",\"title\":\"Step one\",\"done\":false},{\"id\":\"s2\",\"title\":\"Step two\",\"done\":true}]',0)"
            )
            execSQL(
                "INSERT INTO `tasks` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`subtasksJson`,`sortOrder`) " +
                    "VALUES ('t2','No subtasks',NULL,NULL,'Morning',NULL,NULL,NULL,'none',0,0,NULL,NULL,NULL,1)"
            )
            close()
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    AppDatabase.MIGRATION_10_11.migrate(db)
                }
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.apply {
            query("PRAGMA table_info(`tasks`)").use { cursor ->
                val columnNames = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columnNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertTrue(!columnNames.contains("subtasksJson"))
                assertTrue(columnNames.contains("sortOrder"))
            }
            query("SELECT `id`, `title`, `done`, `sortOrder`, `parentSubtaskId` FROM `subtasks` WHERE `taskId` = 't1' ORDER BY `sortOrder`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("s1", cursor.getString(0))
                assertEquals("Step one", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.moveToNext())
                assertEquals("s2", cursor.getString(0))
                assertEquals("Step two", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertTrue(!cursor.moveToNext())
            }
            query("SELECT COUNT(*) FROM `subtasks` WHERE `taskId` = 't2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }

    private fun createVersion4Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .writableDatabase
    }

    private fun createVersion9Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `initials` TEXT NOT NULL, `color` TEXT NOT NULL, `photoUri` TEXT, `isMe` INTEGER NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `dueDate` TEXT, `starred` INTEGER NOT NULL, `commonTagIds` TEXT NOT NULL, `defaultReminder` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, `hideCompletedByDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `tag_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `person_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `listId` TEXT, `projectId` TEXT, `section` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, `reminder` TEXT, `priority` TEXT NOT NULL, `flag` INTEGER NOT NULL, `done` INTEGER NOT NULL, `notes` TEXT, `recurrenceJson` TEXT, `subtasksJson` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `task_person_cross_ref` (`taskId` TEXT NOT NULL, `personId` TEXT NOT NULL, PRIMARY KEY(`taskId`, `personId`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_person_cross_ref_taskId` ON `task_person_cross_ref` (`taskId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_person_cross_ref_personId` ON `task_person_cross_ref` (`personId`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (`taskId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`taskId`, `tagId`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_taskId` ON `task_tag_cross_ref` (`taskId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_tagId` ON `task_tag_cross_ref` (`tagId`)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .writableDatabase
    }

    private fun createVersion10Database(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `initials` TEXT NOT NULL, `color` TEXT NOT NULL, `photoUri` TEXT, `isMe` INTEGER NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `dueDate` TEXT, `starred` INTEGER NOT NULL, `commonTagIds` TEXT NOT NULL, `defaultReminder` TEXT, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, `hideCompletedByDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `tag_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `person_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `listId` TEXT, `projectId` TEXT, `section` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, `reminder` TEXT, `priority` TEXT NOT NULL, `flag` INTEGER NOT NULL, `done` INTEGER NOT NULL, `notes` TEXT, `recurrenceJson` TEXT, `subtasksJson` TEXT, `sortOrder` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `task_person_cross_ref` (`taskId` TEXT NOT NULL, `personId` TEXT NOT NULL, PRIMARY KEY(`taskId`, `personId`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_person_cross_ref_taskId` ON `task_person_cross_ref` (`taskId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_person_cross_ref_personId` ON `task_person_cross_ref` (`personId`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (`taskId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`taskId`, `tagId`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_taskId` ON `task_tag_cross_ref` (`taskId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_tagId` ON `task_tag_cross_ref` (`tagId`)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory()
            .create(configuration)
            .writableDatabase
    }
}
