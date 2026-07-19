package com.mj.yata.domain.repository

import com.mj.yata.domain.model.*
import kotlinx.coroutines.flow.Flow

interface YataRepository {
    // Tasks
    fun getTasks(): Flow<List<Task>>
    fun getTaskById(id: String): Flow<Task?>
    fun getTasksForList(listId: String): Flow<List<Task>>
    fun getTasksForProject(projectId: String): Flow<List<Task>>
    fun getTasksForPerson(personId: String): Flow<List<Task>>

    // [notify] defaults to true (fires the widget-refresh/Wear-sync/cloud-backup-debounce
    // signal immediately, as every existing call site expects). Bulk callers that loop this N
    // times pass false and call [notifyTasksChanged] once after the loop instead — a 20-task
    // bulk action used to fire that whole pipeline 20 times.
    //
    // [resyncReminder] independently defaults to true (reschedules the task's AlarmManager
    // reminder — 2 DataStore reads + an AlarmManager call). Callers that only change fields with
    // no bearing on reminders (tags, project/list, sort order, flag) pass false — rescheduling
    // an alarm that can't have changed was pure waste, and multiplied per item in a bulk loop.
    suspend fun upsertTask(task: Task, notify: Boolean = true, resyncReminder: Boolean = true)
    suspend fun upsertTasks(tasks: List<Task>, notify: Boolean = true, resyncReminder: Boolean = true)
    suspend fun toggleTaskDone(id: String, notify: Boolean = true)
    suspend fun skipTaskOccurrence(id: String)
    fun searchTasks(query: String): Flow<List<Task>>
    suspend fun setTaskFlag(id: String, flag: Boolean, notify: Boolean = true)
    suspend fun setTaskPriority(id: String, priority: String, notify: Boolean = true)
    suspend fun setTaskContainer(id: String, listId: String?, projectId: String?, sortOrder: Int, notify: Boolean = true)
    suspend fun setTaskSortOrder(id: String, sortOrder: Int, notify: Boolean = true)

    /** Manually fires the same "something changed" signal [upsertTask]/etc. fire automatically —
     * for bulk callers that suppressed it per-item via `notify = false`. */
    fun notifyTasksChanged()

    // Deleting a task moves it to Trash (soft delete) rather than removing it outright.
    suspend fun deleteTask(task: Task, notify: Boolean = true)
    fun getDeletedTasks(): Flow<List<Task>>
    suspend fun restoreTask(id: String)
    suspend fun permanentlyDeleteTask(task: Task)
    suspend fun emptyTrash()

    // Hard-deletes trashed tasks older than 30 days — called once at startup so Trash doesn't
    // grow forever for someone who never visits it.
    suspend fun purgeOldTrash()

    // Comments
    fun getCommentsForTask(taskId: String): Flow<List<TaskComment>>
    fun getAllComments(): Flow<List<TaskComment>>
    suspend fun addComment(taskId: String, body: String, authorId: String?)
    suspend fun upsertComment(comment: TaskComment)
    suspend fun deleteComment(comment: TaskComment)

    // Projects
    fun getProjects(): Flow<List<Project>>
    fun getActiveProjects(): Flow<List<Project>>
    fun getArchivedProjects(): Flow<List<Project>>
    fun getProjectById(id: String): Flow<Project?>
    suspend fun upsertProject(project: Project)
    suspend fun deleteProject(project: Project)
    suspend fun deleteProjectOnly(project: Project)
    suspend fun setProjectsArchived(ids: List<String>, archived: Boolean)

    // Lists
    fun getLists(): Flow<List<YataList>>
    fun getActiveLists(): Flow<List<YataList>>
    fun getArchivedLists(): Flow<List<YataList>>
    fun getListById(id: String): Flow<YataList?>
    suspend fun upsertList(list: YataList)
    suspend fun deleteList(list: YataList)
    suspend fun setListsArchived(ids: List<String>, archived: Boolean)

    // People
    fun getPeople(): Flow<List<Person>>
    fun getActivePeople(): Flow<List<Person>>
    fun getArchivedPeople(): Flow<List<Person>>
    fun getPersonById(id: String): Flow<Person?>
    suspend fun upsertPerson(person: Person)
    suspend fun deletePerson(person: Person)

    // Person groups
    fun getPersonGroups(): Flow<List<PersonGroup>>
    suspend fun upsertPersonGroup(group: PersonGroup)
    suspend fun deletePersonGroup(group: PersonGroup)

    // Tags
    fun getTags(): Flow<List<Tag>>
    fun getTagById(id: String): Flow<Tag?>
    suspend fun upsertTag(tag: Tag)
    suspend fun deleteTag(tag: Tag)

    // Tag groups
    fun getTagGroups(): Flow<List<TagGroup>>
    suspend fun upsertTagGroup(group: TagGroup)
    suspend fun deleteTagGroup(group: TagGroup)

    // Seed initial data if database is empty
    suspend fun seedInitialDataIfNeeded()

    // Wipes every table — irreversible, callers are responsible for backing up first
    suspend fun deleteAllData()
}
