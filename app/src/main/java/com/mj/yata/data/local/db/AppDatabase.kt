package com.mj.yata.data.local.db

import android.content.ContentValues
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mj.yata.data.local.db.dao.*
import com.mj.yata.data.local.db.entity.*
import org.json.JSONArray

@Database(
    entities = [
        PersonEntity::class,
        ProjectEntity::class,
        ListEntity::class,
        TagEntity::class,
        TaskEntity::class,
        TaskPersonCrossRef::class,
        TaskTagCrossRef::class,
        TagGroupEntity::class,
        PersonGroupEntity::class,
        SubtaskEntity::class,
        TaskCommentEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun projectDao(): ProjectDao
    abstract fun listDao(): ListDao
    abstract fun tagDao(): TagDao
    abstract fun taskDao(): TaskDao
    abstract fun tagGroupDao(): TagGroupDao
    abstract fun personGroupDao(): PersonGroupDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun taskCommentDao(): TaskCommentDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN commonTagIds TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tag_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `person_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE tags ADD COLUMN groupId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE people ADD COLUMN groupId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE people ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite can't drop a NOT NULL constraint in place — rebuild the table so
                // projectId can be null (a list no longer has to belong to a project).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lists_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `projectId` TEXT, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("INSERT INTO `lists_new` SELECT `id`, `name`, `color`, `icon`, `projectId`, `starred` FROM `lists`")
                db.execSQL("DROP TABLE `lists`")
                db.execSQL("ALTER TABLE `lists_new` RENAME TO `lists`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lists_projectId` ON `lists` (`projectId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN hideCompletedByDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN defaultReminder TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Projects no longer contain Lists — tasks link to a project directly instead.
                // Backfill tasks.projectId from the task's (old) list's projectId before that
                // link disappears, so existing tasks keep their project association.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `listId` TEXT, `projectId` TEXT, `section` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, `reminder` TEXT, `priority` TEXT NOT NULL, `flag` INTEGER NOT NULL, `done` INTEGER NOT NULL, `notes` TEXT, `recurrenceJson` TEXT, `subtasksJson` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`subtasksJson`) " +
                        "SELECT t.`id`, t.`title`, t.`listId`, l.`projectId`, t.`section`, t.`dueDate`, t.`dueTime`, t.`reminder`, t.`priority`, t.`flag`, t.`done`, t.`notes`, t.`recurrenceJson`, t.`subtasksJson` " +
                        "FROM `tasks` t LEFT JOIN `lists` l ON t.`listId` = l.`id`"
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")

                // Lists become fully standalone.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lists_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("INSERT INTO `lists_new` SELECT `id`, `name`, `color`, `icon`, `starred` FROM `lists`")
                db.execSQL("DROP TABLE `lists`")
                db.execSQL("ALTER TABLE `lists_new` RENAME TO `lists`")

                // Projects no longer track an ordered list of lists.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projects_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL, `dueDate` TEXT, `starred` INTEGER NOT NULL, `commonTagIds` TEXT NOT NULL, `defaultReminder` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("INSERT INTO `projects_new` SELECT `id`, `name`, `color`, `icon`, `dueDate`, `starred`, `commonTagIds`, `defaultReminder` FROM `projects`")
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Manual drag-and-drop reorder needs a persistent order per task. Seed it from
                // rowid so existing tasks keep their current (insertion-order) display order.
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET sortOrder = rowid")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `parentSubtaskId` TEXT, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`parentSubtaskId`) REFERENCES `subtasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_parentSubtaskId` ON `subtasks` (`parentSubtaskId`)")

                // Convert each task's subtasksJson blob into real rows (top-level only —
                // the old flat blob never had nesting), then drop the column.
                db.query("SELECT id, subtasksJson FROM tasks WHERE subtasksJson IS NOT NULL").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val jsonIndex = cursor.getColumnIndexOrThrow("subtasksJson")
                    while (cursor.moveToNext()) {
                        val taskId = cursor.getString(idIndex)
                        val json = cursor.getString(jsonIndex) ?: continue
                        try {
                            val arr = JSONArray(json)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val values = ContentValues().apply {
                                    put("id", obj.getString("id"))
                                    put("taskId", taskId)
                                    putNull("parentSubtaskId")
                                    put("title", obj.getString("title"))
                                    put("done", if (obj.getBoolean("done")) 1 else 0)
                                    put("sortOrder", i)
                                }
                                db.insert("subtasks", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
                            }
                        } catch (e: Exception) {
                            // Malformed blob for this task — skip it, don't fail the migration.
                        }
                    }
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `listId` TEXT, `projectId` TEXT, `section` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, `reminder` TEXT, `priority` TEXT NOT NULL, `flag` INTEGER NOT NULL, `done` INTEGER NOT NULL, `notes` TEXT, `recurrenceJson` TEXT, `sortOrder` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (`id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`sortOrder`) " +
                        "SELECT `id`,`title`,`listId`,`projectId`,`section`,`dueDate`,`dueTime`,`reminder`,`priority`,`flag`,`done`,`notes`,`recurrenceJson`,`sortOrder` FROM `tasks`"
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_comments` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `authorId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_comments_taskId` ON `task_comments` (`taskId`)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tasks already marked done before this migration have no real completion
                // timestamp to backfill — left NULL rather than guessing, so analytics that
                // depend on this column simply treat them as "completed at an unknown time".
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Deleting a task now soft-deletes it (Trash) instead of removing the row —
                // NULL means "not deleted", a timestamp means "moved to trash at this time".
                db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN description TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // people.groupId / tags.groupId previously referenced person_groups/tag_groups
                // by convention only, with no FK constraint — correctness rested entirely on
                // every group-delete call site remembering to null out that column first. Adding
                // a real FK (ON DELETE SET NULL) makes that a DB-level guarantee instead.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `initials` TEXT NOT NULL, `color` TEXT NOT NULL, `photoUri` TEXT, `isMe` INTEGER NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`groupId`) REFERENCES `person_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("INSERT INTO `people_new` SELECT `id`, `name`, `initials`, `color`, `photoUri`, `isMe`, `groupId`, `starred` FROM `people`")
                db.execSQL("DROP TABLE `people`")
                db.execSQL("ALTER TABLE `people_new` RENAME TO `people`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_people_groupId` ON `people` (`groupId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `groupId` TEXT, `starred` INTEGER NOT NULL, `hideCompletedByDefault` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`groupId`) REFERENCES `tag_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("INSERT INTO `tags_new` SELECT `id`, `name`, `color`, `groupId`, `starred`, `hideCompletedByDefault` FROM `tags`")
                db.execSQL("DROP TABLE `tags`")
                db.execSQL("ALTER TABLE `tags_new` RENAME TO `tags`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_groupId` ON `tags` (`groupId`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // "Exclude from Today" — a project/list of tasks meant for later (a backlog with
                // no fixed date yet) that should never leak into Today even if a task inside it
                // ends up with a due date that's today or overdue.
                db.execSQL("ALTER TABLE projects ADD COLUMN excludeFromToday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lists ADD COLUMN excludeFromToday INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Manual drag-and-drop reorder for Projects/Lists/People, mirroring tasks.sortOrder —
                // seed from rowid so existing rows keep their current (insertion-order) display order.
                db.execSQL("ALTER TABLE projects ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE projects SET sortOrder = rowid")
                db.execSQL("ALTER TABLE lists ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE lists SET sortOrder = rowid")
                db.execSQL("ALTER TABLE people ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE people SET sortOrder = rowid")
            }
        }

        // Composite index for the three hottest task queries (active-tasks, trash, Today) —
        // all of them filter on deletedAt, and the Today query adds done + dueDate. Previously
        // only listId/projectId (the FK columns) were indexed, so these did full table scans.
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_deletedAt_done_dueDate ON tasks(deletedAt, done, dueDate)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE people ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
