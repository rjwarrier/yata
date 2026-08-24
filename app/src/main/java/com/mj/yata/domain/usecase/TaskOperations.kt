package com.mj.yata.domain.usecase

import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.nextBusinessDay
import com.mj.yata.domain.model.nextPostponementCount
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.TaskScheduleUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-task orchestration extracted from MainViewModel: bulk actions, duplication/rollover,
 * snooze presets, and drag-reorder commits. Bulk paths build one updated list and use repository
 * batch writes where task semantics allow it.
 */
@Singleton
class TaskOperations @Inject constructor(
    private val repository: YataRepository,
    private val userPreferences: UserPreferences
) {

    private suspend fun currentTasks(): List<Task> = repository.getTasks().first()

    suspend fun bulkComplete(ids: List<String>) {
        val byId = currentTasks().associateBy { it.id }
        val now = System.currentTimeMillis()
        val standardUpdates = mutableListOf<Task>()
        var changed = false

        ids.forEach { id ->
            val task = byId[id]
            if (task != null && !task.done) {
                if (task.recurrence != null && task.due != null) {
                    repository.toggleTaskDone(id, notify = false)
                } else {
                    standardUpdates += task.copy(done = true, completedAt = now)
                }
                changed = true
            }
        }

        if (standardUpdates.isNotEmpty()) {
            repository.upsertTasks(standardUpdates, notify = false, resyncReminder = true)
        }
        if (changed) repository.notifyTasksChanged()
    }

    suspend fun bulkDelete(ids: List<String>) {
        val byId = currentTasks().associateBy { it.id }
        val now = System.currentTimeMillis()
        val updated = ids.mapNotNull { id -> byId[id]?.copy(deletedAt = now) }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = true)
        }
    }

    suspend fun bulkAddTag(ids: List<String>, tagId: String) {
        val byId = currentTasks().associateBy { it.id }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (tagId in task.tagIds) null else task.copy(tagIds = task.tagIds + tagId)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkAssignPerson(ids: List<String>, personId: String) {
        val byId = currentTasks().associateBy { it.id }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (personId in task.assigneeIds) null else task.copy(assigneeIds = task.assigneeIds + personId)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetPriority(ids: List<String>, priority: String) {
        val byId = currentTasks().associateBy { it.id }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (task.priority == priority) null else task.copy(priority = priority)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetFlag(ids: List<String>, flag: Boolean) {
        val byId = currentTasks().associateBy { it.id }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (task.flag == flag) null else task.copy(flag = flag)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetProject(ids: List<String>, projectId: String?) {
        val tasks = currentTasks()
        val byId = tasks.associateBy { it.id }
        var nextSortOrder = tasks.count { it.projectId == projectId }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            task.copy(
                listId = task.listId,
                projectId = projectId,
                sortOrder = nextSortOrder
            ).also {
                nextSortOrder++
            }
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetList(ids: List<String>, listId: String?) {
        val tasks = currentTasks()
        val byId = tasks.associateBy { it.id }
        var nextSortOrder = tasks.count { it.listId == listId }
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            task.copy(
                listId = listId,
                projectId = task.projectId,
                sortOrder = nextSortOrder
            ).also {
                nextSortOrder++
            }
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun duplicate(
        taskId: String,
        dueAdjustment: (LocalDate) -> LocalDate = { it },
        notify: Boolean = true
    ): Task? {
        val task = currentTasks().find { it.id == taskId } ?: return null
        val duplicated = duplicateTask(task, dueAdjustment, appendDuplicateTitle = true)
        repository.upsertTasks(
            listOf(duplicated),
            notify = notify,
            resyncReminder = true
        )
        return duplicated
    }

    suspend fun bulkDuplicate(ids: List<String>): List<Task> {
        val byId = currentTasks().associateBy { it.id }
        val duplicated = ids.mapNotNull { id -> byId[id]?.let { duplicateTask(it, appendDuplicateTitle = true) } }
        if (duplicated.isNotEmpty()) {
            repository.upsertTasks(
                duplicated,
                notify = true,
                resyncReminder = true
            )
        }
        return duplicated
    }

    suspend fun rolloverProjectTasks(projectId: String) {
        val duplicated = currentTasks()
            .filter { it.projectId == projectId && !it.done && it.recurrence == null }
            .map { duplicateTask(it, dueAdjustment = { due -> due.plusMonths(1) }) }
        if (duplicated.isNotEmpty()) {
            repository.upsertTasks(
                duplicated,
                notify = true,
                resyncReminder = true
            )
        }
    }

    suspend fun rolloverOverdueProjectTasks(projectId: String) {
        val today = LocalDate.now()
        val duplicated = currentTasks()
            .filter { task ->
                task.projectId == projectId &&
                    !task.done &&
                    task.recurrence == null &&
                    task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
            }
            .map { task ->
                duplicateTask(task, dueAdjustment = { due ->
                    var next = due.plusMonths(1)
                    while (next.isBefore(today)) next = next.plusMonths(1)
                    next
                })
            }
        if (duplicated.isNotEmpty()) {
            repository.upsertTasks(
                duplicated,
                notify = true,
                resyncReminder = true
            )
        }
    }

    private fun duplicateTask(
        task: Task,
        dueAdjustment: (LocalDate) -> LocalDate = { it },
        appendDuplicateTitle: Boolean = false
    ): Task {
        val newDue = task.due?.let { due ->
            try {
                dueAdjustment(LocalDate.parse(due)).toString()
            } catch (e: Exception) {
                due
            }
        }
        val newTitle = if (appendDuplicateTitle) {
            duplicateTaskTitle(task.title)
        } else {
            task.title
        }
        return task.copy(
            id = "t_" + UUID.randomUUID().toString(),
            title = newTitle,
            due = newDue,
            done = false,
            completedAt = null,
            createdAt = null
        )
    }

    /** Returns the updated task (or null only if [id] no longer exists), regardless of whether
     * the reschedule counts as a postponement — callers that only care about the postponement
     * warning are responsible for that comparison themselves (see
     * `MainViewModel.quickSnoozeTask`), since a second, independent warning (rescheduled onto a
     * configured weekend day) also needs the full result of every reschedule, not just the ones
     * that happened to move the due date later. */
    suspend fun quickSnooze(id: String, preset: QuickSnoozePreset): Task? {
        val task = currentTasks().find { it.id == id } ?: return null
        val (dueDate, dueTime) = presetSchedule(preset)
        val due = dueDate.toString()
        val postponementCount = nextPostponementCount(task.due, due, task.postponementCount)
        val updated = task.copy(
            due = due,
            time = dueTime,
            done = false,
            completedAt = null,
            postponementCount = postponementCount
        )
        repository.upsertTask(
            updated
        )
        return updated
    }

    /** See [quickSnooze] — returns every rescheduled task unfiltered, for the same reason. */
    suspend fun bulkReschedule(ids: List<String>, preset: QuickSnoozePreset): List<Task> {
        val byId = currentTasks().associateBy { it.id }
        val (dueDate, dueTime) = presetSchedule(preset)
        val due = dueDate.toString()
        val updated = ids.mapNotNull { byId[it] }.map { task ->
            task.copy(
                due = due,
                time = dueTime,
                done = false,
                completedAt = null,
                postponementCount = nextPostponementCount(task.due, due, task.postponementCount)
            )
        }
        repository.upsertTasks(updated, notify = true, resyncReminder = true)
        return updated
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
            QuickSnoozePreset.NEXT_WEEKDAY -> {
                val weekendDays = userPreferences.weekendDaysFlow.first()
                val holidays = userPreferences.holidaysFlow.first().mapNotNull(Holiday::decode)
                nextBusinessDay(today.plusDays(1), weekendDays, holidays) to TaskScheduleUtils.formatTime(
                    userPreferences.snoozeTomorrowHourFlow.first(),
                    userPreferences.snoozeTomorrowMinuteFlow.first()
                )
            }
        }
    }

    suspend fun commitTaskOrder(orderedTasks: List<Task>) {
        val updated = orderedTasks.mapIndexedNotNull { index, task ->
            if (task.sortOrder == index) null else task.copy(sortOrder = index)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun moveTaskToList(taskId: String, targetListId: String?, targetProjectId: String? = null) {
        val tasks = currentTasks()
        val task = tasks.find { it.id == taskId } ?: return
        val targetSortOrder = tasks.count { it.listId == targetListId && it.projectId == targetProjectId }
        repository.upsertTask(
            task.copy(
                listId = targetListId,
                projectId = targetProjectId,
                sortOrder = targetSortOrder
            ),
            resyncReminder = false
        )
    }
}

fun duplicateTaskTitle(title: String): String {
    val base = title.trim()
    return if (base.isEmpty()) "Duplicate" else "$base Duplicate"
}
