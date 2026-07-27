package com.mj.yata.domain.usecase

import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.util.TaskScheduleUtils
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-task orchestration extracted from MainViewModel: bulk actions, duplication/rollover,
 * snooze presets, and drag-reorder commits. Everything here is a suspend function with a real
 * completion point — callers own the coroutine, so many-task writes stay sequential inside one
 * call instead of fanning out untracked launches.
 *
 * Reads current tasks straight from the repository (not a ViewModel StateFlow) so the class has
 * no UI-layer dependency and stays callable from workers/receivers too.
 */
@Singleton
class TaskOperations @Inject constructor(
    private val repository: YataRepository,
    private val userPreferences: UserPreferences
) {

    private suspend fun currentTasks(): List<Task> = repository.getTasks().first()

    suspend fun bulkComplete(ids: List<String>) {
        val byId = currentTasks().associateBy { it.id }
        var changed = false
        ids.forEach { id ->
            val task = byId[id]
            if (task != null && !task.done) {
                repository.toggleTaskDone(id, notify = false)
                changed = true
            }
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkDelete(ids: List<String>) {
        val byId = currentTasks().associateBy { it.id }
        var changed = false
        ids.forEach { id ->
            val task = byId[id] ?: return@forEach
            repository.deleteTask(task, notify = false)
            changed = true
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkAddTag(ids: List<String>, tagId: String) {
        val byId = currentTasks().associateBy { it.id }
        var changed = false
        ids.forEach { id ->
            val task = byId[id] ?: return@forEach
            if (!task.tagIds.contains(tagId)) {
                repository.upsertTask(task.copy(tagIds = task.tagIds + tagId), notify = false, resyncReminder = false)
                changed = true
            }
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkAssignPerson(ids: List<String>, personId: String) {
        val byId = currentTasks().associateBy { it.id }
        var changed = false
        ids.forEach { id ->
            val task = byId[id] ?: return@forEach
            if (!task.assigneeIds.contains(personId)) {
                repository.upsertTask(task.copy(assigneeIds = task.assigneeIds + personId), notify = false, resyncReminder = false)
                changed = true
            }
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkSetProject(ids: List<String>, projectId: String?) {
        // Append after the destination's existing tasks, like moveTaskToList does for a
        // single move — otherwise every bulk-moved task keeps its old sortOrder, which can
        // collide with whatever's already in the destination project.
        val tasks = currentTasks()
        val byId = tasks.associateBy { it.id }
        var nextSortOrder = tasks.count { it.projectId == projectId }
        var changed = false
        ids.forEach { id ->
            val task = byId[id] ?: return@forEach
            repository.setTaskContainer(
                id = task.id,
                listId = task.listId,
                projectId = projectId,
                sortOrder = nextSortOrder,
                notify = false
            )
            nextSortOrder++
            changed = true
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkSetList(ids: List<String>, listId: String?) {
        val tasks = currentTasks()
        val byId = tasks.associateBy { it.id }
        var nextSortOrder = tasks.count { it.listId == listId }
        var changed = false
        ids.forEach { id ->
            val task = byId[id] ?: return@forEach
            repository.setTaskContainer(
                id = task.id,
                listId = listId,
                projectId = task.projectId,
                sortOrder = nextSortOrder,
                notify = false
            )
            nextSortOrder++
            changed = true
        }
        if (changed) repository.notifyTasksChanged()
    }

    /**
     * Duplicates a single task, keeping every field (priority, flag, assignees, tags,
     * subtasks) except id (fresh UUID) and done (reset to false). due is left unchanged
     * unless the caller supplies a dueAdjustment (e.g. rollover shifts +1 month).
     */
    suspend fun duplicate(taskId: String, dueAdjustment: (LocalDate) -> LocalDate = { it }, notify: Boolean = true) {
        val task = currentTasks().find { it.id == taskId } ?: return
        val newDue = task.due?.let { due ->
            try {
                dueAdjustment(LocalDate.parse(due)).toString()
            } catch (e: Exception) {
                due
            }
        }
        repository.upsertTask(
            task.copy(id = "t_" + UUID.randomUUID().toString(), due = newDue, done = false),
            notify = notify
        )
    }

    suspend fun bulkDuplicate(ids: List<String>) {
        ids.forEach { duplicate(it, notify = false) }
        if (ids.isNotEmpty()) repository.notifyTasksChanged()
    }

    /**
     * Duplicates every open, non-recurring task in a project's lists into next month
     * (due date shifted +1 month, or no due date if the task had none). Recurring tasks
     * already advance themselves on completion, so they're excluded here.
     */
    suspend fun rolloverProjectTasks(projectId: String) {
        val openTasks = currentTasks().filter {
            it.projectId == projectId && !it.done && it.recurrence == null
        }
        openTasks.forEach { task ->
            duplicate(task.id, dueAdjustment = { it.plusMonths(1) }, notify = false)
        }
        if (openTasks.isNotEmpty()) repository.notifyTasksChanged()
    }

    suspend fun rolloverOverdueProjectTasks(projectId: String) {
        val today = LocalDate.now()
        val overdueTasks = currentTasks().filter { task ->
            task.projectId == projectId &&
                !task.done &&
                task.recurrence == null &&
                task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
        }
        overdueTasks.forEach { task ->
            duplicate(
                task.id,
                dueAdjustment = { due ->
                    var next = due.plusMonths(1)
                    while (next.isBefore(today)) next = next.plusMonths(1)
                    next
                },
                notify = false
            )
        }
        if (overdueTasks.isNotEmpty()) repository.notifyTasksChanged()
    }

    /** Reschedules one task to a snooze preset, reopening it if it was done. */
    suspend fun quickSnooze(id: String, preset: QuickSnoozePreset) {
        val task = currentTasks().find { it.id == id } ?: return
        val (dueDate, dueTime) = presetSchedule(preset)
        repository.upsertTask(
            task.copy(
                due = dueDate.toString(),
                time = dueTime,
                done = false,
                completedAt = null
            )
        )
    }

    suspend fun bulkReschedule(ids: List<String>, preset: QuickSnoozePreset) {
        val byId = currentTasks().associateBy { it.id }
        val (dueDate, dueTime) = presetSchedule(preset)
        val updated = ids.mapNotNull { byId[it] }.map {
            it.copy(due = dueDate.toString(), time = dueTime, done = false, completedAt = null)
        }
        repository.upsertTasks(updated, notify = true, resyncReminder = true)
    }

    private suspend fun presetSchedule(preset: QuickSnoozePreset): Pair<LocalDate, String> {
        val today = LocalDate.now()
        return when (preset) {
            QuickSnoozePreset.TONIGHT -> today to TaskScheduleUtils.formatTime(
                userPreferences.snoozeTonightHourFlow.first(),
                userPreferences.snoozeTonightMinuteFlow.first()
            )
            QuickSnoozePreset.TOMORROW_MORNING -> today.plusDays(1) to TaskScheduleUtils.formatTime(
                userPreferences.snoozeTomorrowHourFlow.first(),
                userPreferences.snoozeTomorrowMinuteFlow.first()
            )
            QuickSnoozePreset.NEXT_WEEKDAY -> nextWeekday(today.plusDays(1)) to TaskScheduleUtils.formatTime(
                userPreferences.snoozeTomorrowHourFlow.first(),
                userPreferences.snoozeTomorrowMinuteFlow.first()
            )
        }
    }

    private fun nextWeekday(start: LocalDate): LocalDate {
        var date = start
        while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            date = date.plusDays(1)
        }
        return date
    }

    /**
     * Persists a drag-and-drop reorder: [orderedTasks] is the final order of one container
     * (a single list's or project's tasks), sortOrder is reassigned 0..n within it only —
     * other tasks are never touched.
     */
    suspend fun commitTaskOrder(orderedTasks: List<Task>) {
        var changed = false
        orderedTasks.forEachIndexed { index, task ->
            if (task.sortOrder != index) {
                repository.setTaskSortOrder(task.id, index, notify = false)
                changed = true
            }
        }
        if (changed) repository.notifyTasksChanged()
    }

    /** Moves a task to a different list/project (drag-to-edge cross-container move), appended to the end. */
    suspend fun moveTaskToList(taskId: String, targetListId: String?, targetProjectId: String? = null) {
        val tasks = currentTasks()
        val task = tasks.find { it.id == taskId } ?: return
        val targetSiblings = tasks.filter { it.listId == targetListId && it.projectId == targetProjectId }
        repository.setTaskContainer(
            id = task.id,
            listId = targetListId,
            projectId = targetProjectId,
            sortOrder = targetSiblings.size
        )
    }
}
