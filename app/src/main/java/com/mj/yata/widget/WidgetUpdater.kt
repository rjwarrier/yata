package com.mj.yata.widget

import android.content.Context
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.wear.WearSyncUpdater
import dagger.Lazy
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
    private val wearSyncUpdater: WearSyncUpdater,
    // Lazy breaks a Dagger cycle: CloudBackupManager -> JsonExporter -> YataRepository ->
    // WidgetUpdater -> (back to) CloudBackupManager. Deferring construction until the first
    // scheduleDebouncedBackup() call — well after the whole graph is up — avoids it.
    private val cloudBackupManager: Lazy<CloudBackupManager>
) : WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun notifyTasksChanged() {
        scope.launch { WidgetRefresher.refreshAll(context) }
        // Piggybacks on the same "something changed" signal — the paired watch's complication
        // needs the same refresh the home-screen widgets do, and a cloud backup should reflect
        // the same edit (debounced so a burst of edits produces one upload, not one per edit).
        wearSyncUpdater.notifyTasksChanged()
        cloudBackupManager.get().scheduleDebouncedBackup()
    }
}
