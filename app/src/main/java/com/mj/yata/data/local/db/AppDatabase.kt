package com.mj.yata.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mj.yata.data.local.db.dao.*
import com.mj.yata.data.local.db.entity.*

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
        PersonGroupEntity::class
    ],
    version = 9,
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
    }
}
