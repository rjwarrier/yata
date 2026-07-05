package com.mj.yata.di

import com.mj.yata.wear.WearSyncUpdater
import com.mj.yata.wear.WearSyncUpdaterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {

    @Binds @Singleton
    abstract fun bindWearSyncUpdater(impl: WearSyncUpdaterImpl): WearSyncUpdater
}
