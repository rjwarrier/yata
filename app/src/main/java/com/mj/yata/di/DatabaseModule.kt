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
        .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
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
}
