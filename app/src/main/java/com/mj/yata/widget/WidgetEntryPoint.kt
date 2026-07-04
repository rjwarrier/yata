package com.mj.yata.widget

import com.mj.yata.domain.repository.YataRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Glance widgets are instantiated directly (new YataAppWidget()), not through an
 * Android/Hilt-managed component, so they can't get constructor injection like ViewModels do.
 * This is the entry point they use instead to reach the same repository the rest of the app uses.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): YataRepository
}
