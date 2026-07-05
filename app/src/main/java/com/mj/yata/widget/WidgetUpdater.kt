package com.mj.yata.widget

import android.content.Context
import com.mj.yata.wear.WearSyncUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface WidgetUpdater {
    /** Refreshes every placed instance of every home-screen widget. Called after any task write. */
    fun notifyTasksChanged()
}

@Singleton
class WidgetUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearSyncUpdater: WearSyncUpdater
) : WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun notifyTasksChanged() {
        scope.launch { WidgetRefresher.refreshAll(context) }
        // Piggybacks on the same "something changed" signal — the paired watch's complication
        // needs the same refresh the home-screen widgets do.
        wearSyncUpdater.notifyTasksChanged()
    }
}
