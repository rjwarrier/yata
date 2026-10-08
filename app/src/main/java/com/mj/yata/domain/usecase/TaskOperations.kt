package com.mj.yata.domain.usecase

import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.nextPostponementCount
import com.mj.yata.domain.model.resolve
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

    /** Just the live tasks among [ids] — most actions here touch a known selection, and loading
     * every task (relations, subtasks, recurrence parsing) to pick a handful out was the bulk of
     * their cost on a long-lived database. */
    private suspend fun tasksById(ids: Collection<String>): Map<String, Task> =
        repository.getTasksByIds(ids).associateBy { it.id }

    suspend fun bulkComplete(ids: List<String>) {
        val byId = tasksById(ids)
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
        val byId = tasksById(ids)
        val now = System.currentTimeMillis()
        val updated = ids.mapNotNull { id -> byId[id]?.copy(deletedAt = now) }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = true)
        }
    }

    suspend fun bulkAddTag(ids: List<String>, tagId: String) {
        val byId = tasksById(ids)
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (tagId in task.tagIds) null else task.copy(tagIds = task.tagIds + tagId)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkAssignPerson(ids: List<String>, personId: String) {
        val byId = tasksById(ids)
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (personId in task.assigneeIds) null else task.copy(assigneeIds = task.assigneeIds + personId)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetPriority(ids: List<String>, priority: String) {
        val byId = tasksById(ids)
        val updated = ids.mapNotNull { id ->
            val task = byId[id] ?: return@mapNotNull null
            if (task.priority == priority) null else task.copy(priority = priority)
        }
        if (updated.isNotEmpty()) {
            repository.upsertTasks(updated, notify = true, resyncReminder = false)
        }
    }

    suspend fun bulkSetFlag(ids: List<String>, flag: Boolean) {
        val byId = tasksById(ids)
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
        val task = tasksById(listOf(taskId))[taskId] ?: return null
        val duplicated = duplicateTask(task, dueAdjustment, appendDuplicateTitle = true)
        repository.upsertTasks(
            listOf(duplicated),
            notify = notify,
            resyncReminder = true
        )
        return duplicated
    }

    suspend fun bulkDuplicate(ids: List<String>): List<Task> {
        val byId = tasksById(ids)
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
        val duplicated = repository.getTasksForProject(projectId).first()
            .filter { !it.done && it.recurrence == null }
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
        val duplicated = repository.getTasksForProject(projectId).first()
            .filter { task ->
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
            createdAt = null,
            trackedMinutes = 0
        )
    }

    /** Returns the change (or null only if [id] no longer exists), regardless of whether the
     * reschedule counts as a postponement — callers that only care about the postponement
     * warning are responsible for that comparison themselves (see
     * `MainViewModel.quickSnoozeTask`), since a second, independent warning (rescheduled onto a
     * configured weekend day) also needs the full result of every reschedule, not just the ones
     * that happened to move the due date later. [Rescheduled.previous] is the stored task as it
     * was, which is also what [undoReschedule] puts back. */
    suspend fun quickSnooze(id: String, preset: QuickSnoozePreset): Rescheduled? {
        val task = tasksById(listOf(id))[id] ?: return null
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
        return Rescheduled(previous = task, updated = updated)
    }

    /** See [quickSnooze] — returns every rescheduled task unfiltered, for the same reason.
     *
     * [keepExistingTime] moves only the date: each task keeps whatever time it already had (or
     * stays untimed), instead of every selected task being stamped with the preset's one time. */
    suspend fun bulkReschedule(
        ids: List<String>,
        preset: QuickSnoozePreset,
        keepExistingTime: Boolean = false
    ): List<Rescheduled> {
        val byId = tasksById(ids)
        val (dueDate, dueTime) = presetSchedule(preset)
        val due = dueDate.toString()
        val changes = ids.mapNotNull { byId[it] }.map { task ->
            Rescheduled(
                previous = task,
                updated = task.copy(
                    due = due,
                    time = if (keepExistingTime) task.time else dueTime,
                    done = false,
                    completedAt = null,
                    postponementCount = nextPostponementCount(task.due, due, task.postponementCount)
                )
            )
        }
        repository.upsertTasks(changes.map { it.updated }, notify = true, resyncReminder = true)
        return changes
    }

    /**
     * Reverses [changes] made by [quickSnooze]/[bulkReschedule]: due date, time, done state and
     * postponement count go back to what they were. Everything else is taken from the task as it
     * is *now*, so an edit made in the meantime (a renamed title, a new note) survives the undo.
     *
     * A task whose date or time no longer matches what the reschedule wrote is skipped entirely —
     * it has been rescheduled again since, and that later choice wins. A task that has been
     * deleted or archived in between isn't in [currentTasks] and is skipped too.
     *
     * Written with postponement tracking off: the repository otherwise keeps the larger of the
     * incoming and stored counts, which would leave the reschedule's increment in place.
     */
    suspend fun undoReschedule(changes: List<Rescheduled>) {
        val restored = scheduleRestorations(changes, tasksById(changes.map { it.updated.id }))
        if (restored.isNotEmpty()) {
            repository.upsertTasks(restored, notify = true, resyncReminder = true, trackPostponements = false)
        }
    }

    private suspend fun presetSchedule(preset: QuickSnoozePreset): Pair<LocalDate, String> {
        val target = preset.resolve(userPreferences.quickSnoozeSettingsFlow.first(), LocalDate.now())
        return target.toLocalDate() to TaskScheduleUtils.formatTime(target.hour, target.minute)
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

/** One task's reschedule: the stored task before it, and what was written. */
data class Rescheduled(val previous: Task, val updated: Task) {
    /** False when the reschedule left the schedule exactly as it was, so there is nothing to undo. */
    val changedSchedule: Boolean
        get() = previous.due != updated.due || previous.time != updated.time || previous.done != updated.done
}

/**
 * The writes that undo [changes], given the tasks as they are now ([currentById]). See
 * [TaskOperations.undoReschedule] for the rules; kept separate from it so they can be tested
 * without a repository.
 */
internal fun scheduleRestorations(changes: List<Rescheduled>, currentById: Map<String, Task>): List<Task> =
    changes.mapNotNull { change ->
        val task = currentById[change.updated.id] ?: return@mapNotNull null
        if (task.due != change.updated.due || task.time != change.updated.time) return@mapNotNull null
        task.copy(
            due = change.previous.due,
            time = change.previous.time,
            done = change.previous.done,
            completedAt = change.previous.completedAt,
            postponementCount = change.previous.postponementCount
        )
    }

fun duplicateTaskTitle(title: String): String {
    val base = title.trim()
    return if (base.isEmpty()) "Duplicate" else "$base Duplicate"
}
