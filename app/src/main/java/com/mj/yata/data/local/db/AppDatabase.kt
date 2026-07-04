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
    version = 6,
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
    }
}
