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
        .addMigrations(*AppDatabase.MIGRATIONS)
        // Missing migrations and downgrades fail without deleting user data.
        .build()
    }

    @Provides fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()
    @Provides fun provideProjectGroupDao(db: AppDatabase): ProjectGroupDao = db.projectGroupDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideListDao(db: AppDatabase): ListDao = db.listDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTagGroupDao(db: AppDatabase): TagGroupDao = db.tagGroupDao()
    @Provides fun providePersonGroupDao(db: AppDatabase): PersonGroupDao = db.personGroupDao()
    @Provides fun provideSubtaskDao(db: AppDatabase): SubtaskDao = db.subtaskDao()
    @Provides fun provideTaskCommentDao(db: AppDatabase): TaskCommentDao = db.taskCommentDao()
}
