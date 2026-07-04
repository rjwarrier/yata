package com.mj.yata.data.repository

import com.mj.yata.data.local.db.AppDatabase
import com.mj.yata.data.local.db.entity.*
import com.mj.yata.data.mapper.*
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.notification.TaskReminderScheduler
import com.mj.yata.util.RecurrenceEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YataRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val reminderScheduler: TaskReminderScheduler
) : YataRepository {

    override fun getTasks(): Flow<List<Task>> {
        return db.taskDao().getTasksWithRelations().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTaskById(id: String): Flow<Task?> {
        return db.taskDao().getTaskWithRelationsById(id).map { it?.toDomain() }
    }

    override suspend fun upsertTask(task: Task) {
        val entity = task.toEntity()

        // Insert task entity
        db.taskDao().insert(entity)

        // Sync many-to-many assignees
        db.taskDao().deleteTaskPersonCrossRefs(task.id)
        if (task.assigneeIds.isNotEmpty()) {
            val refs = task.assigneeIds.map { TaskPersonCrossRef(task.id, it) }
            db.taskDao().insertTaskPersonCrossRefs(refs)
        }

        // Sync many-to-many tags
        db.taskDao().deleteTaskTagCrossRefs(task.id)
        if (task.tagIds.isNotEmpty()) {
            val refs = task.tagIds.map { TaskTagCrossRef(task.id, it) }
            db.taskDao().insertTaskTagCrossRefs(refs)
        }

        syncReminder(entity)
    }

    override suspend fun toggleTaskDone(id: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val taskEntity = db.taskDao().getByIdDirect(id) ?: return@withContext
        val wasDone = taskEntity.done
        val isNowDone = !wasDone

        // Fetch current assignees and tags to maintain them
        val assigneeIds = db.taskDao().getPeopleForTaskDirect(id).map { it.id }
        val tagIds = db.taskDao().getTagsForTaskDirect(id).map { it.id }

        // Check if task is recurring and we are marking it as done
        val recurrence = deserializeRecurrence(taskEntity.recurrenceJson)
        
        if (isNowDone && recurrence != null && taskEntity.dueDate != null) {
            // Calculate next due date
            val nextDate = RecurrenceEvaluator.calculateNextOccurrence(recurrence, taskEntity.dueDate)
            
            if (nextDate != null) {
                // 1. Keep the current instance completed but as a one-off in history.
                val historicalTaskId = UUID.randomUUID().toString()
                val completedHistoryTask = taskEntity.copy(
                    id = historicalTaskId,
                    done = true,
                    recurrenceJson = null // one-off completed instance
                )
                db.taskDao().insert(completedHistoryTask)
                
                // Copy assignees and tags to completed history instance
                db.taskDao().insertTaskPersonCrossRefs(assigneeIds.map { TaskPersonCrossRef(historicalTaskId, it) })
                db.taskDao().insertTaskTagCrossRefs(tagIds.map { TaskTagCrossRef(historicalTaskId, it) })

                // 2. Advance the original task to the next occurrence
                // If it is count-based recurrence, decrement the remaining count
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
                
                val updatedTask = taskEntity.copy(
                    done = false, // Reset done for next occurrence
                    dueDate = nextDate,
                    recurrenceJson = serializeRecurrence(updatedRecurrence)
                )
                db.taskDao().insert(updatedTask)
                syncReminder(updatedTask)
                return@withContext
            }
        }

        // Standard behavior
        val updatedTask = taskEntity.copy(done = isNowDone)
        db.taskDao().insert(updatedTask)
        syncReminder(updatedTask)
    }

    override suspend fun deleteTask(task: Task) {
        reminderScheduler.cancelReminder(task.toEntity())
        db.taskDao().delete(task.toEntity())
    }

    override fun getProjects(): Flow<List<Project>> {
        return db.projectDao().getAll().map { list ->
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

    override fun getLists(): Flow<List<YataList>> {
        return db.listDao().getAll().map { list ->
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

    override fun getPeople(): Flow<List<Person>> {
        return db.personDao().getAll().map { list ->
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

    override fun getPersonGroups(): Flow<List<PersonGroup>> {
        return db.personGroupDao().getAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun upsertPersonGroup(group: PersonGroup) {
        db.personGroupDao().insert(group.toEntity())
    }

    override suspend fun deletePersonGroup(group: PersonGroup) {
        db.personDao().clearGroup(group.id)
        db.personGroupDao().delete(group.toEntity())
    }

    override fun getTags(): Flow<List<Tag>> {
        return db.tagDao().getAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTagById(id: String): Flow<Tag?> {
        return db.tagDao().getById(id).map { it?.toDomain() }
    }

    override suspend fun upsertTag(tag: Tag) {
        db.tagDao().insert(tag.toEntity())
    }

    override suspend fun deleteTag(tag: Tag) {
        db.tagDao().delete(tag.toEntity())

        // Note: Task relations are deleted automatically by cascade in task_tag_cross_ref
    }

    override fun getTagGroups(): Flow<List<TagGroup>> {
        return db.tagGroupDao().getAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun upsertTagGroup(group: TagGroup) {
        db.tagGroupDao().insert(group.toEntity())
    }

    override suspend fun deleteTagGroup(group: TagGroup) {
        db.tagDao().clearGroup(group.id)
        db.tagGroupDao().delete(group.toEntity())
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

    private fun syncReminder(task: TaskEntity) {
        if (task.done || task.dueDate == null || task.reminder.isNullOrBlank()) {
            reminderScheduler.cancelReminder(task)
        } else {
            reminderScheduler.scheduleReminder(task)
        }
    }
}


