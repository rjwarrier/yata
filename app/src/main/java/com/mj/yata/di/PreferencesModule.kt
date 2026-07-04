package com.mj.yata.di

import com.mj.yata.data.local.datastore.TaskListPreferences
import com.mj.yata.data.local.datastore.UserPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindTaskListPreferences(impl: UserPreferences): TaskListPreferences
}
