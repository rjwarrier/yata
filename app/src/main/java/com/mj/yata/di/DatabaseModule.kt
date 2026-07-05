package com.mj.yata.di

import android.content.Context
import androidx.room.Room
import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "yata_expressive.db"
        )
        .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideListDao(db: AppDatabase): ListDao = db.listDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTagGroupDao(db: AppDatabase): TagGroupDao = db.tagGroupDao()
    @Provides fun providePersonGroupDao(db: AppDatabase): PersonGroupDao = db.personGroupDao()
    @Provides fun provideSubtaskDao(db: AppDatabase): SubtaskDao = db.subtaskDao()
    @Provides fun provideTaskCommentDao(db: AppDatabase): TaskCommentDao = db.taskCommentDao()
}
