package com.mj.yata.data.repository

import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.entity.*
import com.mj.yata.data.mapper.*
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.notification.TaskReminderScheduler
import com.mj.yata.util.RecurrenceEvaluator
import com.mj.yata.widget.WidgetUpdater
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YataRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val reminderScheduler: TaskReminderScheduler,
    private val widgetUpdater: WidgetUpdater
) : YataRepository {

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private val tasksStateFlow = db.taskDao().getTasksWithRelations()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val projectsStateFlow = db.projectDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeProjectsStateFlow = db.projectDao().getActive()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val listsStateFlow = db.listDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeListsStateFlow = db.listDao().getActive()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val peopleStateFlow = db.personDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activePeopleStateFlow = db.personDao().getActive()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tagsStateFlow = db.tagDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tagGroupsStateFlow = db.tagGroupDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val personGroupsStateFlow = db.personGroupDao().getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override fun getTasks(): Flow<List<Task>> = tasksStateFlow

    override fun getTaskById(id: String): Flow<Task?> {
        return db.taskDao().getTaskWithRelationsById(id).map { it?.toDomain() }
    }

    override fun getTasksForList(listId: String): Flow<List<Task>> {
        return db.taskDao().getTasksWithRelationsForList(listId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTasksForProject(projectId: String): Flow<List<Task>> {
        return db.taskDao().getTasksWithRelationsForProject(projectId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTasksForPerson(personId: String): Flow<List<Task>> {
        return db.taskDao().getTasksWithRelationsForPerson(personId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTaskStreak(taskId: String): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val seriesId = db.taskDao().getByIdDirect(taskId)?.seriesId ?: return@withContext 0
        val completions = db.taskDao().getCompletedTasksBySeriesId(seriesId)
            .map { it.toDomain(assigneeIds = emptyList(), tagIds = emptyList()) }
        com.mj.yata.util.RecurrenceEvaluator.computeStreak(completions)
    }

    override fun notifyTasksChanged() {
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun upsertTask(task: Task, notify: Boolean, resyncReminder: Boolean) {
        upsertTasks(listOf(task), notify, resyncReminder)
    }

    override suspend fun upsertTasks(tasks: List<Task>, notify: Boolean, resyncReminder: Boolean) {
        if (tasks.isEmpty()) return

        db.withTransaction {
            // Batch-fetch existing cross-refs for every task up front — 3 queries total instead
            // of 3 per task — then diff each task against its slice of these in-memory maps.
            val taskIds = tasks.map { it.id }
            val existingPeopleByTask = db.taskDao().getPersonCrossRefsForTasks(taskIds)
                .groupBy({ it.taskId }, { it.personId })
            val existingTagsByTask = db.taskDao().getTagCrossRefsForTasks(taskIds)
                .groupBy({ it.taskId }, { it.tagId })
            val existingSubtasksByTask = db.subtaskDao().getSubtasksForTasksDirect(taskIds)
                .groupBy { it.taskId }

            tasks.forEach { task ->
                val entity = task.toEntity()
                db.taskDao().insert(entity)

                // Sync many-to-many assignees (only if changed!)
                val existingPeople = existingPeopleByTask[task.id].orEmpty()
                if (existingPeople.toSet() != task.assigneeIds.toSet()) {
                    db.taskDao().deleteTaskPersonCrossRefs(task.id)
                    if (task.assigneeIds.isNotEmpty()) {
                        val refs = task.assigneeIds.map { TaskPersonCrossRef(task.id, it) }
                        db.taskDao().insertTaskPersonCrossRefs(refs)
                    }
                }

                // Sync many-to-many tags (only if changed!)
                val existingTags = existingTagsByTask[task.id].orEmpty()
                if (existingTags.toSet() != task.tagIds.toSet()) {
                    db.taskDao().deleteTaskTagCrossRefs(task.id)
                    if (task.tagIds.isNotEmpty()) {
                        val refs = task.tagIds.map { TaskTagCrossRef(task.id, it) }
                        db.taskDao().insertTaskTagCrossRefs(refs)
                    }
                }

                // Sync subtasks (only if changed!)
                val existingDomainSubtasks = existingSubtasksByTask[task.id].orEmpty().map { it.toDomain() }
                if (existingDomainSubtasks != task.subtasks) {
                    db.subtaskDao().deleteForTask(task.id)
                    if (task.subtasks.isNotEmpty()) {
                        db.subtaskDao().upsertAll(task.subtasks.map { it.toEntity(task.id) })
                    }
                }
            }
        }

        if (resyncReminder) {
            reminderScheduler.syncReminders(tasks.map { it.toEntity() })
        }
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override fun searchTasks(query: String): Flow<List<Task>> {
        if (query.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val ftsQuery = query.split(Regex("[^\\p{L}\\p{N}_]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it.replace("\"", "")}*" }
            .ifBlank { "__yata_no_match__*" }
        return db.taskDao().searchTasksWithRelations(ftsQuery, query).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun setTaskFlag(id: String, flag: Boolean, notify: Boolean) = withContext(Dispatchers.IO) {
        db.taskDao().updateFlag(id, flag)
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override suspend fun setTaskPriority(id: String, priority: String, notify: Boolean) = withContext(Dispatchers.IO) {
        db.taskDao().updatePriority(id, priority)
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override suspend fun setTaskContainer(
        id: String,
        listId: String?,
        projectId: String?,
        sortOrder: Int,
        notify: Boolean
    ) = withContext(Dispatchers.IO) {
        db.taskDao().updateContainer(id, listId, projectId, sortOrder)
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override suspend fun setTaskSortOrder(id: String, sortOrder: Int, notify: Boolean) = withContext(Dispatchers.IO) {
        db.taskDao().updateSortOrder(id, sortOrder)
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override suspend fun toggleTaskDone(id: String, notify: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val taskEntity = db.taskDao().getByIdDirect(id) ?: return@withContext
        val wasDone = taskEntity.done
        val isNowDone = !wasDone

        // Check if task is recurring and we are marking it as done
        val recurrence = deserializeRecurrence(taskEntity.recurrenceJson)

        if (isNowDone && recurrence != null && taskEntity.dueDate != null) {
            val baseDate = if (recurrence.basedOnCompletion) {
                java.time.LocalDate.now().toString()
            } else {
                taskEntity.dueDate
            }
            val nextDate = RecurrenceEvaluator.calculateNextOccurrence(recurrence, baseDate)
            
            if (nextDate != null) {
                // Fetch current assignees, tags and subtasks to maintain them ONLY when we need to advance the recurrence!
                val assigneeIds = db.taskDao().getPeopleForTaskDirect(id).map { it.id }
                val tagIds = db.taskDao().getTagsForTaskDirect(id).map { it.id }
                val subtaskEntities = db.subtaskDao().getSubtasksForTaskDirect(id)

                val updatedRecurrence = when (val ends = recurrence.ends) {
                    is RecurrenceEnds.After -> {
                        val newCount = ends.count - 1
                        if (newCount <= 0) {
                            null // recurrence ended
                        } else {
                            recurrence.copy(ends = RecurrenceEnds.After(newCount))
                        }
                    }
                    else -> recurrence
                }
                // Lazily assigned on the first completion of this series — carried forward
                // unchanged on every completion after that, linking every historical instance
                // back to the live row for streak computation (RecurrenceEvaluator.computeStreak).
                val seriesId = taskEntity.seriesId ?: UUID.randomUUID().toString()
                val updatedTask = taskEntity.copy(
                    done = false, // Reset done for next occurrence
                    completedAt = null,
                    dueDate = nextDate,
                    recurrenceJson = serializeRecurrence(updatedRecurrence),
                    seriesId = seriesId
                )
                val historicalTaskId = UUID.randomUUID().toString()

                db.withTransaction {
                    // 1. Keep the current instance completed but as a one-off in history.
                    val completedHistoryTask = taskEntity.copy(
                        id = historicalTaskId,
                        done = true,
                        completedAt = System.currentTimeMillis(),
                        recurrenceJson = null, // one-off completed instance
                        seriesId = seriesId
                    )
                    db.taskDao().insert(completedHistoryTask)

                    // Copy assignees, tags and subtasks to completed history instance
                    db.taskDao().insertTaskPersonCrossRefs(assigneeIds.map { TaskPersonCrossRef(historicalTaskId, it) })
                    db.taskDao().insertTaskTagCrossRefs(tagIds.map { TaskTagCrossRef(historicalTaskId, it) })
                    db.subtaskDao().upsertAll(
                        subtaskEntities.map { it.copy(id = UUID.randomUUID().toString(), taskId = historicalTaskId, parentSubtaskId = null) }
                    )

                    // 2. Advance the original task to the next occurrence
                    db.taskDao().insert(updatedTask)
                }
                syncReminder(updatedTask)
                if (notify) widgetUpdater.notifyTasksChanged()
                return@withContext
            }
        }

        // Standard behavior
        val updatedTask = taskEntity.copy(
            done = isNowDone,
            completedAt = if (isNowDone) System.currentTimeMillis() else null
        )
        db.taskDao().updateDone(id, isNowDone, updatedTask.completedAt)
        syncReminder(updatedTask)
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    /** Advances a recurring task to its next occurrence without marking the skipped one done. */
    override suspend fun skipTaskOccurrence(id: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val taskEntity = db.taskDao().getByIdDirect(id) ?: return@withContext
        val recurrence = deserializeRecurrence(taskEntity.recurrenceJson)
        if (recurrence == null || taskEntity.dueDate == null) return@withContext

        val nextDate = RecurrenceEvaluator.calculateNextOccurrence(recurrence, taskEntity.dueDate) ?: return@withContext

        val assigneeIds = db.taskDao().getPeopleForTaskDirect(id).map { it.id }
        val tagIds = db.taskDao().getTagsForTaskDirect(id).map { it.id }
        val subtaskEntities = db.subtaskDao().getSubtasksForTaskDirect(id)

        val updatedRecurrence = when (val ends = recurrence.ends) {
            is RecurrenceEnds.After -> {
                val newCount = ends.count - 1
                if (newCount <= 0) null else recurrence.copy(ends = RecurrenceEnds.After(newCount))
            }
            else -> recurrence
        }
        val updatedTask = taskEntity.copy(
            done = false,
            dueDate = nextDate,
            recurrenceJson = serializeRecurrence(updatedRecurrence)
        )
        val historicalTaskId = UUID.randomUUID().toString()

        db.withTransaction {
            // Keep a record of the skipped instance (not done, one-off).
            val skippedHistoryTask = taskEntity.copy(
                id = historicalTaskId,
                done = false,
                recurrenceJson = null
            )
            db.taskDao().insert(skippedHistoryTask)
            db.taskDao().insertTaskPersonCrossRefs(assigneeIds.map { TaskPersonCrossRef(historicalTaskId, it) })
            db.taskDao().insertTaskTagCrossRefs(tagIds.map { TaskTagCrossRef(historicalTaskId, it) })
            db.subtaskDao().upsertAll(
                subtaskEntities.map { it.copy(id = UUID.randomUUID().toString(), taskId = historicalTaskId, parentSubtaskId = null) }
            )
            db.taskDao().insert(updatedTask)
        }
        syncReminder(updatedTask)
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun deleteTask(task: Task, notify: Boolean) {
        reminderScheduler.cancelReminder(task.toEntity())
        db.taskDao().softDelete(task.id, System.currentTimeMillis())
        if (notify) widgetUpdater.notifyTasksChanged()
    }

    override fun getDeletedTasks(): Flow<List<Task>> {
        return db.taskDao().getDeletedTasksWithRelations().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun restoreTask(id: String) {
        db.taskDao().restore(id)
        db.taskDao().getByIdDirect(id)?.let { syncReminder(it) }
        widgetUpdater.notifyTasksChanged()
    }

    override fun getArchivedTasks(): Flow<List<Task>> {
        return db.taskDao().getArchivedTasksWithRelations().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun setTaskArchived(id: String, archived: Boolean) {
        db.taskDao().setArchived(id, archived)
        // An archived task shouldn't keep firing reminders; unarchiving re-arms whatever
        // schedule the row still carries.
        db.taskDao().getByIdDirect(id)?.let { entity ->
            if (archived) reminderScheduler.cancelReminder(entity) else syncReminder(entity)
        }
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun permanentlyDeleteTask(task: Task) {
        reminderScheduler.cancelReminder(task.toEntity())
        db.taskDao().delete(task.toEntity())
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun emptyTrash() {
        db.taskDao().emptyTrash()
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun purgeOldTrash() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        db.taskDao().purgeTrashOlderThan(thirtyDaysAgo)
    }

    override fun getCommentsForTask(taskId: String): Flow<List<TaskComment>> {
        return db.taskCommentDao().getCommentsForTask(taskId).map { list -> list.map { it.toDomain() } }
    }

    override fun getAllComments(): Flow<List<TaskComment>> {
        return db.taskCommentDao().getAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addComment(taskId: String, body: String, authorId: String?) {
        db.taskCommentDao().insert(
            TaskCommentEntity(
                id = "cm_" + UUID.randomUUID().toString(),
                taskId = taskId,
                body = body,
                createdAt = System.currentTimeMillis(),
                authorId = authorId
            )
        )
    }

    override suspend fun upsertComment(comment: TaskComment) {
        db.taskCommentDao().insert(comment.toEntity())
    }

    override suspend fun deleteComment(comment: TaskComment) {
        db.taskCommentDao().delete(comment.toEntity())
    }

    override fun getProjects(): Flow<List<Project>> = projectsStateFlow

    override fun getActiveProjects(): Flow<List<Project>> = activeProjectsStateFlow

    override fun getArchivedProjects(): Flow<List<Project>> {
        return db.projectDao().getArchived().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getProjectById(id: String): Flow<Project?> {
        return db.projectDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun upsertProject(project: Project) {
        db.projectDao().insert(project.toEntity())
    }

    override suspend fun deleteProject(project: Project) {
        db.projectDao().delete(project.toEntity())
    }

    override suspend fun deleteProjectOnly(project: Project) {
        db.withTransaction {
            db.taskDao().clearProject(project.id)
            db.projectDao().delete(project.toEntity())
        }
        widgetUpdater.notifyTasksChanged()
    }

    override suspend fun setProjectsArchived(ids: List<String>, archived: Boolean) {
        if (ids.isEmpty()) return
        db.projectDao().setArchived(ids, archived)
    }

    override fun getLists(): Flow<List<YataList>> = listsStateFlow

    override fun getActiveLists(): Flow<List<YataList>> = activeListsStateFlow

    override fun getArchivedLists(): Flow<List<YataList>> {
        return db.listDao().getArchived().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getListById(id: String): Flow<YataList?> {
        return db.listDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun upsertList(list: YataList) {
        db.listDao().insert(list.toEntity())
    }

    override suspend fun deleteList(list: YataList) {
        db.listDao().delete(list.toEntity())
    }

    override suspend fun setListsArchived(ids: List<String>, archived: Boolean) {
        if (ids.isEmpty()) return
        db.listDao().setArchived(ids, archived)
    }

    override fun getPeople(): Flow<List<Person>> = peopleStateFlow

    override fun getActivePeople(): Flow<List<Person>> = activePeopleStateFlow

    override fun getArchivedPeople(): Flow<List<Person>> {
        return db.personDao().getArchived().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPersonById(id: String): Flow<Person?> {
        return db.personDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun upsertPerson(person: Person) {
        db.personDao().insert(person.toEntity())
    }

    override suspend fun deletePerson(person: Person) {
        // Delete person
        db.personDao().delete(person.toEntity())

        // Note: Task relations are deleted automatically by cascade in task_person_cross_ref
    }

    override fun getPersonGroups(): Flow<List<PersonGroup>> = personGroupsStateFlow

    override suspend fun upsertPersonGroup(group: PersonGroup) {
        db.personGroupDao().insert(group.toEntity())
    }

    override suspend fun deletePersonGroup(group: PersonGroup) {
        db.withTransaction {
            db.personDao().clearGroup(group.id)
            db.personGroupDao().delete(group.toEntity())
        }
    }

    override fun getTags(): Flow<List<Tag>> = tagsStateFlow

    override fun getTagById(id: String): Flow<Tag?> {
        return db.tagDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun upsertTag(tag: Tag) {
        db.tagDao().insert(tag.toEntity())
    }

    override suspend fun deleteTag(tag: Tag) {
        db.tagDao().delete(tag.toEntity())
    }

    override fun getTagGroups(): Flow<List<TagGroup>> = tagGroupsStateFlow

    override suspend fun upsertTagGroup(group: TagGroup) {
        db.tagGroupDao().insert(group.toEntity())
    }

    override suspend fun deleteTagGroup(group: TagGroup) {
        db.withTransaction {
            db.tagDao().clearGroup(group.id)
            db.tagGroupDao().delete(group.toEntity())
        }
    }

    override suspend fun seedInitialDataIfNeeded() {
        // No demo tasks/projects — but a real "me" Person must exist, since new tasks
        // default-assign to the current user (see NewTaskSheet/TodayTab `myId` fallback).
        // Without this row, that default assignment violates the task_person_cross_ref FK.
        val hasMe = db.personDao().getAll().first().any { it.isMe }
        if (!hasMe) {
            db.personDao().insert(
                PersonEntity(
                    id = "me",
                    name = "You",
                    initials = "Y",
                    color = "accentC",
                    photoUri = null,
                    isMe = true
                )
            )
        }
    }

    override suspend fun deleteAllData() {
        // Cancel every scheduled reminder before the rows disappear under it.
        db.taskDao().getTasksWithRelations().first().forEach { reminderScheduler.cancelReminder(it.task) }
        // clearAllTables() + reseeding the required "me" Person happen in one transaction — a
        // crash between the two used to be able to leave a completely empty DB with no "me"
        // row, which violates the task_person_cross_ref FK the moment any task is created.
        withContext(Dispatchers.IO) {
            db.withTransaction {
                db.clearAllTables()
                seedInitialDataIfNeeded()
            }
        }
        widgetUpdater.notifyTasksChanged()
    }

    private suspend fun syncReminder(task: TaskEntity) {
        if (task.done || task.dueDate == null || task.reminder.isNullOrBlank()) {
            reminderScheduler.cancelReminder(task)
        } else {
            reminderScheduler.scheduleReminder(task)
        }
    }
}


