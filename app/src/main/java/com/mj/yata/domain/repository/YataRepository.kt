package com.mj.yata.domain.repository

import com.mj.yata.domain.model.*
import kotlinx.coroutines.flow.Flow

interface YataRepository {
    // Tasks
    fun getTasks(): Flow<List<Task>>
    fun getTaskById(id: String): Flow<Task?>
    suspend fun upsertTask(task: Task)
    suspend fun toggleTaskDone(id: String)
    suspend fun skipTaskOccurrence(id: String)

    // Deleting a task moves it to Trash (soft delete) rather than removing it outright.
    suspend fun deleteTask(task: Task)
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
    fun getProjectById(id: String): Flow<Project?>
    suspend fun upsertProject(project: Project)
    suspend fun deleteProject(project: Project)

    // Lists
    fun getLists(): Flow<List<YataList>>
    fun getListById(id: String): Flow<YataList?>
    suspend fun upsertList(list: YataList)
    suspend fun deleteList(list: YataList)

    // People
    fun getPeople(): Flow<List<Person>>
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
