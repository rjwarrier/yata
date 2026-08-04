package com.mj.yata.data.demo

import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.repository.YataRepositoryImpl
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.PersonGroup
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.TagGroup
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskComment
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [YataRepository] every screen actually injects. Transparently routes every read/write to
 * either [real] (the real Room-backed repository) or [demo] (the in-memory demo dataset) based on
 * [UserPreferences.demoModeEnabledFlow], so screens/ViewModels stay unaware demo mode exists at
 * all. [seedInitialDataIfNeeded]/[purgeOldTrash]/[deleteAllData] are app-lifecycle/maintenance
 * operations, not user-facing display state — they always target [real], regardless of demo mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RoutingYataRepository @Inject constructor(
    private val real: YataRepositoryImpl,
    private val demo: DemoRepository,
    private val userPreferences: UserPreferences
) : YataRepository {

    private fun <T> routed(realFlow: Flow<T>, demoFlow: Flow<T>): Flow<T> =
        userPreferences.demoModeEnabledFlow.flatMapLatest { if (it) demoFlow else realFlow }

    private suspend fun isDemo(): Boolean = userPreferences.demoModeEnabledFlow.first()

    private suspend fun write(block: suspend () -> Unit) {
        if (!isDemo()) block()
    }

    override fun getTasks(): Flow<List<Task>> = routed(real.getTasks(), demo.getTasks())
    override fun getTaskById(id: String): Flow<Task?> = routed(real.getTaskById(id), demo.getTaskById(id))
    override fun getTasksForList(listId: String): Flow<List<Task>> = routed(real.getTasksForList(listId), demo.getTasksForList(listId))
    override fun getTasksForProject(projectId: String): Flow<List<Task>> = routed(real.getTasksForProject(projectId), demo.getTasksForProject(projectId))
    override fun getTasksForPerson(personId: String): Flow<List<Task>> = routed(real.getTasksForPerson(personId), demo.getTasksForPerson(personId))
    override suspend fun getTaskStreak(taskId: String): Int = if (isDemo()) demo.getTaskStreak(taskId) else real.getTaskStreak(taskId)

    override suspend fun upsertTask(task: Task, notify: Boolean, resyncReminder: Boolean) = write { real.upsertTask(task, notify, resyncReminder) }
    override suspend fun upsertTasks(
        tasks: List<Task>,
        notify: Boolean,
        resyncReminder: Boolean,
        preserveExistingCreatedAt: Boolean
    ) = write {
        real.upsertTasks(tasks, notify, resyncReminder, preserveExistingCreatedAt)
    }
    override suspend fun toggleTaskDone(id: String, notify: Boolean) = write { real.toggleTaskDone(id, notify) }
    override suspend fun skipTaskOccurrence(id: String) = write { real.skipTaskOccurrence(id) }
    override fun searchTasks(query: String): Flow<List<Task>> = routed(real.searchTasks(query), demo.searchTasks(query))
    override suspend fun setTaskFlag(id: String, flag: Boolean, notify: Boolean) = write { real.setTaskFlag(id, flag, notify) }
    override suspend fun setTaskPriority(id: String, priority: String, notify: Boolean) = write { real.setTaskPriority(id, priority, notify) }
    override suspend fun setTaskContainer(id: String, listId: String?, projectId: String?, sortOrder: Int, notify: Boolean) = write { real.setTaskContainer(id, listId, projectId, sortOrder, notify) }
    override suspend fun setTaskSortOrder(id: String, sortOrder: Int, notify: Boolean) = write { real.setTaskSortOrder(id, sortOrder, notify) }
    override fun notifyTasksChanged() { real.notifyTasksChanged() }

    override suspend fun deleteTask(task: Task, notify: Boolean) = write { real.deleteTask(task, notify) }
    override fun getDeletedTasks(): Flow<List<Task>> = routed(real.getDeletedTasks(), demo.getDeletedTasks())
    override suspend fun restoreTask(id: String) = write { real.restoreTask(id) }
    override fun getArchivedTasks(): Flow<List<Task>> = routed(real.getArchivedTasks(), demo.getArchivedTasks())
    override suspend fun setTaskArchived(id: String, archived: Boolean) = write { real.setTaskArchived(id, archived) }
    override suspend fun permanentlyDeleteTask(task: Task) = write { real.permanentlyDeleteTask(task) }
    override suspend fun emptyTrash() = write { real.emptyTrash() }
    override suspend fun purgeOldTrash() = real.purgeOldTrash()
    override suspend fun autoArchiveOldCompleted() = real.autoArchiveOldCompleted()

    override fun getCommentsForTask(taskId: String): Flow<List<TaskComment>> = routed(real.getCommentsForTask(taskId), demo.getCommentsForTask(taskId))
    override fun getAllComments(): Flow<List<TaskComment>> = routed(real.getAllComments(), demo.getAllComments())
    override suspend fun addComment(taskId: String, body: String, authorId: String?) = write { real.addComment(taskId, body, authorId) }
    override suspend fun upsertComment(comment: TaskComment) = write { real.upsertComment(comment) }
    override suspend fun deleteComment(comment: TaskComment) = write { real.deleteComment(comment) }

    override fun getProjects(): Flow<List<Project>> = routed(real.getProjects(), demo.getProjects())
    override fun getActiveProjects(): Flow<List<Project>> = routed(real.getActiveProjects(), demo.getActiveProjects())
    override fun getArchivedProjects(): Flow<List<Project>> = routed(real.getArchivedProjects(), demo.getArchivedProjects())
    override fun getProjectById(id: String): Flow<Project?> = routed(real.getProjectById(id), demo.getProjectById(id))
    override suspend fun upsertProject(project: Project) = write { real.upsertProject(project) }
    override suspend fun deleteProject(project: Project) = write { real.deleteProject(project) }
    override suspend fun deleteProjectOnly(project: Project) = write { real.deleteProjectOnly(project) }
    override suspend fun setProjectsArchived(ids: List<String>, archived: Boolean) = write { real.setProjectsArchived(ids, archived) }

    override fun getLists(): Flow<List<YataList>> = routed(real.getLists(), demo.getLists())
    override fun getActiveLists(): Flow<List<YataList>> = routed(real.getActiveLists(), demo.getActiveLists())
    override fun getArchivedLists(): Flow<List<YataList>> = routed(real.getArchivedLists(), demo.getArchivedLists())
    override fun getListById(id: String): Flow<YataList?> = routed(real.getListById(id), demo.getListById(id))
    override suspend fun upsertList(list: YataList) = write { real.upsertList(list) }
    override suspend fun deleteList(list: YataList) = write { real.deleteList(list) }
    override suspend fun setListsArchived(ids: List<String>, archived: Boolean) = write { real.setListsArchived(ids, archived) }

    override fun getPeople(): Flow<List<Person>> = routed(real.getPeople(), demo.getPeople())
    override fun getActivePeople(): Flow<List<Person>> = routed(real.getActivePeople(), demo.getActivePeople())
    override fun getArchivedPeople(): Flow<List<Person>> = routed(real.getArchivedPeople(), demo.getArchivedPeople())
    override fun getPersonById(id: String): Flow<Person?> = routed(real.getPersonById(id), demo.getPersonById(id))
    override suspend fun upsertPerson(person: Person) = write { real.upsertPerson(person) }
    override suspend fun deletePerson(person: Person) = write { real.deletePerson(person) }

    override fun getPersonGroups(): Flow<List<PersonGroup>> = routed(real.getPersonGroups(), demo.getPersonGroups())
    override suspend fun upsertPersonGroup(group: PersonGroup) = write { real.upsertPersonGroup(group) }
    override suspend fun deletePersonGroup(group: PersonGroup) = write { real.deletePersonGroup(group) }

    override fun getTags(): Flow<List<Tag>> = routed(real.getTags(), demo.getTags())
    override fun getTagById(id: String): Flow<Tag?> = routed(real.getTagById(id), demo.getTagById(id))
    override suspend fun upsertTag(tag: Tag) = write { real.upsertTag(tag) }
    override suspend fun deleteTag(tag: Tag) = write { real.deleteTag(tag) }

    override fun getTagGroups(): Flow<List<TagGroup>> = routed(real.getTagGroups(), demo.getTagGroups())
    override suspend fun upsertTagGroup(group: TagGroup) = write { real.upsertTagGroup(group) }
    override suspend fun deleteTagGroup(group: TagGroup) = write { real.deleteTagGroup(group) }

    override suspend fun seedInitialDataIfNeeded() = real.seedInitialDataIfNeeded()
    override suspend fun deleteAllData() = real.deleteAllData()
}
