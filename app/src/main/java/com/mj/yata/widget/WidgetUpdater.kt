package com.mj.yata.widget

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.mj.yata.domain.model.effectiveTagIds
import com.mj.yata.domain.model.hiddenFromMainTaskListIds
import com.mj.yata.domain.model.hiddenFromMainTaskProjectIds
import com.mj.yata.domain.model.isActionableToday
import com.mj.yata.domain.model.wasPendingAsOf
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import java.time.LocalDate

interface WidgetUpdater {
    /** Called after any sync-relevant data write to schedule sync and refresh placed home-screen widgets. */
    fun notifyTasksChanged()
}

@Singleton
class WidgetUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy breaks Dagger cycles
    private val backupOperations: Lazy<com.mj.yata.domain.usecase.BackupOperations>,
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
        backupOperations.get().scheduleDebouncedBackup()
        changeEvents.tryEmit(Unit)
    }

    private suspend fun hasPlacedInstances(widgetClass: Class<out GlanceAppWidget>): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(widgetClass).isNotEmpty()

    private suspend fun processUpdate() {
        val repository = yataRepository.get()
        val hasProgress = hasPlacedInstances(ProgressStatsWidget::class.java)
        val hasUpcoming = hasPlacedInstances(UpcomingWidget::class.java)
        val hasTeam = hasPlacedInstances(TeamOverdueWidget::class.java)
        val hasSingleList = hasPlacedInstances(SingleListWidget::class.java)
        val hasAppWidget = hasPlacedInstances(YataAppWidget::class.java)
        if (!hasProgress && !hasUpcoming && !hasTeam && !hasSingleList && !hasAppWidget) return

        val tasks = repository.getTasks().first()
        val needsPeople = hasProgress || hasUpcoming || hasTeam || hasSingleList || hasAppWidget
        val needsProjects = hasProgress || hasUpcoming || hasSingleList || hasAppWidget
        val needsLists = hasProgress || hasUpcoming || hasSingleList || hasAppWidget

        val people = if (needsPeople) repository.getPeople().first() else emptyList()
        val projects = if (needsProjects) repository.getProjects().first() else emptyList()
        val lists = if (needsLists) repository.getLists().first() else emptyList()
        val today = LocalDate.now()
        val todayStr = today.toString()
        val nowMillis = System.currentTimeMillis()
        val myId = people.firstOrNull { it.isMe }?.id
        val excludedProjectIds = projects.hiddenFromMainTaskProjectIds()
        val excludedListIds = lists.hiddenFromMainTaskListIds()

        if (hasProgress) {
            val progressTasks = tasks
                .filter {
                    it.isActionableToday(todayStr, nowMillis, myId) &&
                        it.projectId !in excludedProjectIds &&
                        it.listId !in excludedListIds
                }
                .filter { it.wasPendingAsOf(today) }
            val progressHash = listOf(
                progressTasks.map { task ->
                    listOf(task.id, task.done, task.listId, task.projectId, task.due, task.startDate, task.followUpAt, task.assigneeIds)
                },
                lists.map { list -> listOf(list.id, list.name, list.color, list.excludeFromToday) }
            )
            if (progressHash != lastProgressHash) {
                lastProgressHash = progressHash
                WidgetRefresher.refreshWidget(context, ProgressStatsWidget::class.java)
            }
        }

        if (hasUpcoming) {
            val upcomingHash = listOf(
                tasks.filter { it.due != null }.map { task ->
                    listOf(
                        task.id,
                        task.title,
                        task.done,
                        task.due,
                        task.time,
                        task.listId,
                        task.projectId,
                        task.startDate,
                        task.followUpAt,
                        task.assigneeIds
                    )
                },
                people.map { person -> listOf(person.id, person.name, person.initials, person.color, person.photoUri, person.isMe) },
                lists.map { list -> listOf(list.id, list.name, list.color, list.excludeFromToday) },
                projects.map { project -> listOf(project.id, project.excludeFromToday, project.archived) }
            )
            if (upcomingHash != lastUpcomingHash) {
                lastUpcomingHash = upcomingHash
                WidgetRefresher.refreshWidget(context, UpcomingWidget::class.java)
            }
        }

        if (hasTeam) {
            val activePeople = people.filter { !it.archived }
            val teamHash = listOf(
                tasks.filter { !it.done && it.due != null && isOverdue(it, today) }.map { task ->
                    listOf(task.id, task.title, task.due, task.assigneeIds)
                },
                activePeople.map { person -> listOf(person.id, person.name, person.initials, person.color) }
            )
            if (teamHash != lastTeamHash) {
                lastTeamHash = teamHash
                WidgetRefresher.refreshWidget(context, TeamOverdueWidget::class.java)
            }
        }

        if (hasSingleList) {
            val projectsById = projects.associateBy { it.id }
            val singleListHash = listOf(
                tasks.map { task ->
                    listOf(
                        task.id,
                        task.title,
                        task.done,
                        task.sortOrder,
                        task.listId,
                        task.projectId,
                        task.tagIds,
                        task.effectiveTagIds(projectsById),
                        task.assigneeIds
                    )
                },
                lists.map { list -> listOf(list.id, list.name, list.color, list.icon) },
                projects.map { project -> listOf(project.id, project.name, project.color, project.icon, project.commonTagIds) },
                people.map { person -> listOf(person.id, person.name, person.initials, person.color, person.photoUri) }
            )
            if (singleListHash != lastSingleListHash) {
                lastSingleListHash = singleListHash
                WidgetRefresher.refreshWidget(context, SingleListWidget::class.java)
            }
        }

        if (hasAppWidget) {
            val todayTasks = tasks
                .filter {
                    it.isActionableToday(todayStr, nowMillis, myId) &&
                        it.projectId !in excludedProjectIds &&
                        it.listId !in excludedListIds
                }
                .sortedWith(compareBy({ it.done }, { it.sortOrder }))
            val progressTasks = todayTasks.filter { it.wasPendingAsOf(today) }
            val appWidgetHash = listOf(
                todayTasks.map { task ->
                    listOf(task.id, task.title, task.done, task.due, task.time, task.listId, task.assigneeIds, task.sortOrder)
                },
                progressTasks.map { it.id to it.done },
                lists.map { list -> listOf(list.id, list.name, list.color, list.excludeFromToday) },
                people.map { person -> listOf(person.id, person.name, person.initials, person.color, person.photoUri, person.isMe) },
                projects.map { project -> listOf(project.id, project.excludeFromToday, project.archived) }
            )
            if (appWidgetHash != lastAppWidgetHash) {
                lastAppWidgetHash = appWidgetHash
                WidgetRefresher.refreshWidget(context, YataAppWidget::class.java)
            }
        }

    }

    private fun isOverdue(task: com.mj.yata.domain.model.Task, today: java.time.LocalDate): Boolean {
        if (task.done) return false
        val due = task.due?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
        return due.isBefore(today)
    }
}
