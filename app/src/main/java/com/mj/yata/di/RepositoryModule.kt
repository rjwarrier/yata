package com.mj.yata.di

import com.mj.yata.data.repository.YataRepositoryImpl
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.notification.ReminderScheduler
import com.mj.yata.notification.TaskReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindYataRepository(impl: YataRepositoryImpl): YataRepository

    @Binds @Singleton
    abstract fun bindTaskReminderScheduler(impl: ReminderScheduler): TaskReminderScheduler
}
