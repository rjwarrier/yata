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

import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

interface WidgetUpdater {
    /** Refreshes every placed instance of every home-screen widget. Called after any task write. */
    fun notifyTasksChanged()
}

@Singleton
class WidgetUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearSyncUpdater: WearSyncUpdater,
    // Lazy breaks Dagger cycles
    private val cloudBackupManager: Lazy<CloudBackupManager>,
    private val yataRepository: Lazy<YataRepository>
) : WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val changeEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var lastProgressHash: Any? = null
    private var lastUpcomingHash: Any? = null
    private var lastTeamHash: Any? = null
    private var lastSingleListHash: Any? = null
    private var lastAppWidgetHash: Any? = null
    private var lastWearHash: Any? = null

    init {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        scope.launch {
            changeEvents
                .debounce(500)
                .collect {
                    processUpdate()
                }
        }
    }

    override fun notifyTasksChanged() {
        changeEvents.tryEmit(Unit)
    }

    private suspend fun processUpdate() {
        val repository = yataRepository.get()
        val tasks = repository.getTasks().first()

        // 1. ProgressStatsWidget: displays progress percentage of active tasks
        val progressHash = tasks.count { it.done } to tasks.size
        if (progressHash != lastProgressHash) {
            lastProgressHash = progressHash
            WidgetRefresher.refreshWidget(context, ProgressStatsWidget::class.java)
        }

        // 2. UpcomingWidget: displays upcoming tasks
        val upcomingHash = tasks.filter { !it.done && it.due != null }.map { it.id to it.title to it.due to it.time }
        if (upcomingHash != lastUpcomingHash) {
            lastUpcomingHash = upcomingHash
            WidgetRefresher.refreshWidget(context, UpcomingWidget::class.java)
        }

        // 3. TeamOverdueWidget: displays overdue tasks
        val today = java.time.LocalDate.now()
        val teamHash = tasks.filter { !it.done && it.due != null && isOverdue(it, today) }.map { it.id to it.title to it.assigneeIds }
        if (teamHash != lastTeamHash) {
            lastTeamHash = teamHash
            WidgetRefresher.refreshWidget(context, TeamOverdueWidget::class.java)
        }

        // 4. SingleListWidget: displays tasks for specific lists
        val singleListHash = tasks.map { it.id to it.listId to it.title to it.done to it.sortOrder }
        if (singleListHash != lastSingleListHash) {
            lastSingleListHash = singleListHash
            WidgetRefresher.refreshWidget(context, SingleListWidget::class.java)
        }

        // 5. YataAppWidget: main app widget displaying list task summary
        val appWidgetHash = tasks.map { it.id to it.title to it.done to it.due to it.listId to it.assigneeIds to it.tagIds }
        if (appWidgetHash != lastAppWidgetHash) {
            lastAppWidgetHash = appWidgetHash
            WidgetRefresher.refreshWidget(context, YataAppWidget::class.java)
        }

        // 6. Wear Sync Update
        val wearHash = tasks.map { it.id to it.title to it.done to it.due to it.time to it.assigneeIds }
        if (wearHash != lastWearHash) {
            lastWearHash = wearHash
            wearSyncUpdater.notifyTasksChanged()
        }

        // Trigger backup debounce
        cloudBackupManager.get().scheduleDebouncedBackup()
    }

    private fun isOverdue(task: com.mj.yata.domain.model.Task, today: java.time.LocalDate): Boolean {
        if (task.done) return false
        val due = task.due?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
        return due.isBefore(today)
    }
}
