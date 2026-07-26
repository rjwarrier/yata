package com.mj.yata.data.demo

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.PersonGroup
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.TagGroup
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskComment
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only, in-memory stand-in for [YataRepository] used while demo mode is active (see
 * [RoutingYataRepository]) — seeded once per process with [DemoData.build] so screenshots always
 * show fresh-looking dates. Every mutating method is a deliberate no-op: demo mode exists purely
 * to show realistic data for screenshots without ever touching the real database.
 */
@Singleton
class DemoRepository @Inject constructor() : YataRepository {

    private val dataset = MutableStateFlow(DemoData.build())

    override fun getTasks(): Flow<List<Task>> = dataset.map { it.tasks }
    override fun getTaskById(id: String): Flow<Task?> = dataset.map { d -> d.tasks.find { it.id == id } }
    override fun getTasksForList(listId: String): Flow<List<Task>> = dataset.map { d -> d.tasks.filter { it.listId == listId } }
    override fun getTasksForProject(projectId: String): Flow<List<Task>> = dataset.map { d -> d.tasks.filter { it.projectId == projectId } }
    override fun getTasksForPerson(personId: String): Flow<List<Task>> = dataset.map { d -> d.tasks.filter { personId in it.assigneeIds } }
    override suspend fun getTaskStreak(taskId: String): Int = 0

    override suspend fun upsertTask(task: Task, notify: Boolean, resyncReminder: Boolean) = Unit
    override suspend fun upsertTasks(tasks: List<Task>, notify: Boolean, resyncReminder: Boolean) = Unit
    override suspend fun toggleTaskDone(id: String, notify: Boolean) = Unit
    override suspend fun skipTaskOccurrence(id: String) = Unit
    override fun searchTasks(query: String): Flow<List<Task>> =
        dataset.map { d -> d.tasks.filter { it.title.contains(query, ignoreCase = true) } }
    override suspend fun setTaskFlag(id: String, flag: Boolean, notify: Boolean) = Unit
    override suspend fun setTaskPriority(id: String, priority: String, notify: Boolean) = Unit
    override suspend fun setTaskContainer(id: String, listId: String?, projectId: String?, sortOrder: Int, notify: Boolean) = Unit
    override suspend fun setTaskSortOrder(id: String, sortOrder: Int, notify: Boolean) = Unit
    override fun notifyTasksChanged() = Unit

    override suspend fun deleteTask(task: Task, notify: Boolean) = Unit
    override fun getDeletedTasks(): Flow<List<Task>> = dataset.map { emptyList() }
    override suspend fun restoreTask(id: String) = Unit
    override fun getArchivedTasks(): Flow<List<Task>> = dataset.map { emptyList() }
    override suspend fun setTaskArchived(id: String, archived: Boolean) = Unit
    override suspend fun permanentlyDeleteTask(task: Task) = Unit
    override suspend fun emptyTrash() = Unit
    override suspend fun purgeOldTrash() = Unit

    override fun getCommentsForTask(taskId: String): Flow<List<TaskComment>> =
        dataset.map { d -> d.comments.filter { it.taskId == taskId } }
    override fun getAllComments(): Flow<List<TaskComment>> = dataset.map { it.comments }
    override suspend fun addComment(taskId: String, body: String, authorId: String?) = Unit
    override suspend fun upsertComment(comment: TaskComment) = Unit
    override suspend fun deleteComment(comment: TaskComment) = Unit

    override fun getProjects(): Flow<List<Project>> = dataset.map { it.projects }
    override fun getActiveProjects(): Flow<List<Project>> = dataset.map { d -> d.projects.filter { !it.archived } }
    override fun getArchivedProjects(): Flow<List<Project>> = dataset.map { d -> d.projects.filter { it.archived } }
    override fun getProjectById(id: String): Flow<Project?> = dataset.map { d -> d.projects.find { it.id == id } }
    override suspend fun upsertProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
    override suspend fun deleteProjectOnly(project: Project) = Unit
    override suspend fun setProjectsArchived(ids: List<String>, archived: Boolean) = Unit

    override fun getLists(): Flow<List<YataList>> = dataset.map { it.lists }
    override fun getActiveLists(): Flow<List<YataList>> = dataset.map { d -> d.lists.filter { !it.archived } }
    override fun getArchivedLists(): Flow<List<YataList>> = dataset.map { d -> d.lists.filter { it.archived } }
    override fun getListById(id: String): Flow<YataList?> = dataset.map { d -> d.lists.find { it.id == id } }
    override suspend fun upsertList(list: YataList) = Unit
    override suspend fun deleteList(list: YataList) = Unit
    override suspend fun setListsArchived(ids: List<String>, archived: Boolean) = Unit

    override fun getPeople(): Flow<List<Person>> = dataset.map { it.people }
    override fun getActivePeople(): Flow<List<Person>> = dataset.map { d -> d.people.filter { !it.archived } }
    override fun getArchivedPeople(): Flow<List<Person>> = dataset.map { d -> d.people.filter { it.archived } }
    override fun getPersonById(id: String): Flow<Person?> = dataset.map { d -> d.people.find { it.id == id } }
    override suspend fun upsertPerson(person: Person) = Unit
    override suspend fun deletePerson(person: Person) = Unit

    override fun getPersonGroups(): Flow<List<PersonGroup>> = dataset.map { it.personGroups }
    override suspend fun upsertPersonGroup(group: PersonGroup) = Unit
    override suspend fun deletePersonGroup(group: PersonGroup) = Unit

    override fun getTags(): Flow<List<Tag>> = dataset.map { it.tags }
    override fun getTagById(id: String): Flow<Tag?> = dataset.map { d -> d.tags.find { it.id == id } }
    override suspend fun upsertTag(tag: Tag) = Unit
    override suspend fun deleteTag(tag: Tag) = Unit

    override fun getTagGroups(): Flow<List<TagGroup>> = dataset.map { it.tagGroups }
    override suspend fun upsertTagGroup(group: TagGroup) = Unit
    override suspend fun deleteTagGroup(group: TagGroup) = Unit

    override suspend fun seedInitialDataIfNeeded() = Unit
    override suspend fun deleteAllData() = Unit
}
