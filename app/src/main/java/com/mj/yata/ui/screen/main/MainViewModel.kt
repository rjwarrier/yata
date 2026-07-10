package com.mj.yata.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.yata.data.cloud.CloudBackupEntry
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: YataRepository,
    private val userPreferences: UserPreferences,
    private val jsonExporter: JsonExporter,
    private val cloudBackupManager: CloudBackupManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
            repository.purgeOldTrash()
        }
    }

    // Data streams
    val tasks: StateFlow<List<Task>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = repository.getProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjects: StateFlow<List<Project>> = projects.map { it.activeProjects() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedProjects: StateFlow<List<Project>> = projects.map { it.archivedProjects() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Today's remaining (due, incomplete) task count — the badge shown on every bottom nav bar. */
    val todayRemainingCount: StateFlow<Int> = combine(tasks, projects) { list, projectList ->
        val todayStr = LocalDate.now().toString()
        val hiddenProjectIds = projectList.hiddenFromMainTaskProjectIds()
        list.count { it.due != null && it.due <= todayStr && !it.done && it.projectId !in hiddenProjectIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lists: StateFlow<List<YataList>> = repository.getLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<Person>> = repository.getPeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePeople: StateFlow<List<Person>> = people.map { it.activePeople() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedPeople: StateFlow<List<Person>> = people.map { it.archivedPeople() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = repository.getTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tagGroups: StateFlow<List<TagGroup>> = repository.getTagGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personGroups: StateFlow<List<PersonGroup>> = repository.getPersonGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Preferences
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val appFont: StateFlow<AppFont> = userPreferences.appFontFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.INTER)

    val userName: StateFlow<String> = userPreferences.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userEmail: StateFlow<String> = userPreferences.userEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userPhotoUri: StateFlow<String?> = userPreferences.userPhotoUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val defaultListId: StateFlow<String> = userPreferences.defaultListIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val startOfWeekSunday: StateFlow<Boolean> = userPreferences.startOfWeekSundayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultReminderHour: StateFlow<Int> = userPreferences.defaultReminderHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)

    val defaultReminderMinute: StateFlow<Int> = userPreferences.defaultReminderMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeScheduleStartHour: StateFlow<Int> = userPreferences.themeScheduleStartHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21)

    val themeScheduleStartMinute: StateFlow<Int> = userPreferences.themeScheduleStartMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeScheduleEndHour: StateFlow<Int> = userPreferences.themeScheduleEndHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val themeScheduleEndMinute: StateFlow<Int> = userPreferences.themeScheduleEndMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiScale: StateFlow<Float> = userPreferences.uiScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val dynamicColorEnabled: StateFlow<Boolean> = userPreferences.dynamicColorEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val peopleFeatureEnabled: StateFlow<Boolean> = userPreferences.peopleFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tagsFeatureEnabled: StateFlow<Boolean> = userPreferences.tagsFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val projectsFeatureEnabled: StateFlow<Boolean> = userPreferences.projectsFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reduceMotionEnabled: StateFlow<Boolean> = userPreferences.reduceMotionEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedToday: StateFlow<Boolean> = userPreferences.hideCompletedTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedProject: StateFlow<Boolean> = userPreferences.hideCompletedProjectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedList: StateFlow<Boolean> = userPreferences.hideCompletedListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedPerson: StateFlow<Boolean> = userPreferences.hideCompletedPersonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val textScale: StateFlow<Float> = userPreferences.textScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val taskRowDensity: StateFlow<com.mj.yata.domain.model.TaskRowDensity> = userPreferences.taskRowDensityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.TaskRowDensity.COMFORTABLE)

    val hapticsEnabled: StateFlow<Boolean> = userPreferences.hapticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val todayTabEnabled: StateFlow<Boolean> = userPreferences.todayTabEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val upcomingTabEnabled: StateFlow<Boolean> = userPreferences.upcomingTabEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fabPosition: StateFlow<com.mj.yata.domain.model.FabPosition> = userPreferences.fabPositionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.FabPosition.RIGHT)

    val cloudBackupEnabled: StateFlow<Boolean> = userPreferences.cloudBackupEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cloudBackupAccountEmail: StateFlow<String?> = userPreferences.cloudBackupAccountEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cloudBackupLastAt: StateFlow<Long?> = userPreferences.cloudBackupLastAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cloudBackupWifiOnly: StateFlow<Boolean> = userPreferences.cloudBackupWifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val cloudBackupIntervalMinutes: StateFlow<Long> = userPreferences.cloudBackupIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24 * 60L)

    // Actions
    fun toggleTaskDone(id: String, onDoneCallback: () -> Unit) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id }
            val wasDone = task?.done ?: false
            repository.toggleTaskDone(id)
            if (!wasDone) {
                onDoneCallback() // Trigger confetti
            }
        }
    }

    fun skipTaskOccurrence(id: String) {
        viewModelScope.launch {
            repository.skipTaskOccurrence(id)
        }
    }

    fun bulkCompleteTasks(ids: List<String>) {
        viewModelScope.launch {
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id }
                if (task != null && !task.done) {
                    repository.toggleTaskDone(id, notify = false)
                    changed = true
                }
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    fun bulkDeleteTasks(ids: List<String>) {
        viewModelScope.launch {
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id } ?: return@forEach
                repository.deleteTask(task, notify = false)
                changed = true
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    fun bulkAddTag(ids: List<String>, tagId: String) {
        viewModelScope.launch {
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id } ?: return@forEach
                if (!task.tagIds.contains(tagId)) {
                    repository.upsertTask(task.copy(tagIds = task.tagIds + tagId), notify = false, resyncReminder = false)
                    changed = true
                }
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    fun bulkSetProject(ids: List<String>, projectId: String?) {
        viewModelScope.launch {
            // Append after the destination's existing tasks, like moveTaskToList does for a
            // single move — otherwise every bulk-moved task keeps its old sortOrder, which can
            // collide with whatever's already in the destination project.
            var nextSortOrder = tasks.value.count { it.projectId == projectId }
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id } ?: return@forEach
                repository.upsertTask(task.copy(projectId = projectId, sortOrder = nextSortOrder), notify = false, resyncReminder = false)
                nextSortOrder++
                changed = true
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    fun bulkSetList(ids: List<String>, listId: String?) {
        viewModelScope.launch {
            var nextSortOrder = tasks.value.count { it.listId == listId }
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id } ?: return@forEach
                repository.upsertTask(task.copy(listId = listId, sortOrder = nextSortOrder), notify = false, resyncReminder = false)
                nextSortOrder++
                changed = true
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    /**
     * Duplicates a single task, keeping every field (priority, flag, assignees, tags,
     * subtasks) except id (fresh UUID) and done (reset to false). due is left unchanged
     * unless the caller supplies a dueAdjustment (e.g. rollover shifts +1 month).
     */
    /** Suspend core shared by [duplicateTask], [bulkDuplicateTasks], and [rolloverProjectTasks] —
     * callers that duplicate many tasks at once await these sequentially in one coroutine
     * instead of each fanning out its own untracked `launch`, so there's a real completion
     * point and no unordered race between the writes. */
    private suspend fun duplicateTaskSuspend(taskId: String, dueAdjustment: (LocalDate) -> LocalDate = { it }, notify: Boolean = true) {
        val task = tasks.value.find { it.id == taskId } ?: return
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

    fun duplicateTask(taskId: String, dueAdjustment: (LocalDate) -> LocalDate = { it }) {
        viewModelScope.launch { duplicateTaskSuspend(taskId, dueAdjustment) }
    }

    /**
     * Duplicates every open, non-recurring task in a project's lists into next month
     * (due date shifted +1 month, or no due date if the task had none). Recurring tasks
     * already advance themselves on completion, so they're excluded here.
     */
    fun rolloverProjectTasks(projectId: String) {
        viewModelScope.launch {
            val openTasks = tasks.value.filter {
                it.projectId == projectId && !it.done && it.recurrence == null
            }
            openTasks.forEach { task ->
                duplicateTaskSuspend(task.id, dueAdjustment = { it.plusMonths(1) }, notify = false)
            }
            if (openTasks.isNotEmpty()) repository.notifyTasksChanged()
        }
    }

    fun bulkDuplicateTasks(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { duplicateTaskSuspend(it, notify = false) }
            if (ids.isNotEmpty()) repository.notifyTasksChanged()
        }
    }

    /**
     * Persists a drag-and-drop reorder: [orderedTasks] is the final order of one container
     * (a single list's or project's tasks), sortOrder is reassigned 0..n within it only —
     * other tasks are never touched.
     */
    fun commitTaskOrder(orderedTasks: List<Task>) {
        viewModelScope.launch {
            var changed = false
            orderedTasks.forEachIndexed { index, task ->
                if (task.sortOrder != index) {
                    repository.upsertTask(task.copy(sortOrder = index), notify = false, resyncReminder = false)
                    changed = true
                }
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    /** Persists a drag-and-drop reorder of the whole Projects tab (a single flat list). */
    fun commitProjectOrder(orderedProjects: List<Project>) {
        viewModelScope.launch {
            orderedProjects.forEachIndexed { index, project ->
                if (project.sortOrder != index) {
                    repository.upsertProject(project.copy(sortOrder = index))
                }
            }
        }
    }

    /** Persists a drag-and-drop reorder of the nav drawer's Lists section (a single flat list). */
    fun commitListOrder(orderedLists: List<YataList>) {
        viewModelScope.launch {
            orderedLists.forEachIndexed { index, list ->
                if (list.sortOrder != index) {
                    repository.upsertList(list.copy(sortOrder = index))
                }
            }
        }
    }

    /** Persists a drag-and-drop reorder of one group's (or "Ungrouped") people in the People tab —
     * sortOrder is reassigned 0..n within that group only, other groups are never touched. */
    fun commitPersonOrder(orderedPeople: List<Person>) {
        viewModelScope.launch {
            orderedPeople.forEachIndexed { index, person ->
                if (person.sortOrder != index) {
                    repository.upsertPerson(person.copy(sortOrder = index))
                }
            }
        }
    }

    /** Moves a task to a different list/project (drag-to-edge cross-container move), appended to the end. */
    fun moveTaskToList(taskId: String, targetListId: String?, targetProjectId: String? = null) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == taskId } ?: return@launch
            val targetSiblings = tasks.value.filter { it.listId == targetListId && it.projectId == targetProjectId }
            repository.upsertTask(
                task.copy(listId = targetListId, projectId = targetProjectId, sortOrder = targetSiblings.size),
                resyncReminder = false
            )
        }
    }

    fun bulkAssignPerson(ids: List<String>, personId: String) {
        viewModelScope.launch {
            var changed = false
            ids.forEach { id ->
                val task = tasks.value.find { it.id == id } ?: return@forEach
                if (!task.assigneeIds.contains(personId)) {
                    repository.upsertTask(task.copy(assigneeIds = task.assigneeIds + personId), notify = false, resyncReminder = false)
                    changed = true
                }
            }
            if (changed) repository.notifyTasksChanged()
        }
    }

    fun toggleTaskFlag(id: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
            repository.upsertTask(task.copy(flag = !task.flag), resyncReminder = false)
        }
    }

    fun cycleTaskPriority(id: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
            val nextPriority = when (task.priority) {
                "none" -> "low"
                "low" -> "med"
                "med" -> "high"
                "high" -> "none"
                else -> "none"
            }
            repository.upsertTask(task.copy(priority = nextPriority), resyncReminder = false)
        }
    }

    fun addTask(
        title: String,
        listId: String?,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        notes: String? = null,
        due: String? = LocalDate.now().toString(),
        time: String? = null,
        reminder: String? = null,
        section: String = "Afternoon",
        projectId: String? = null,
        subtasks: List<Subtask> = emptyList()
    ) {
        viewModelScope.launch {
            val newTask = Task(
                id = "t_" + UUID.randomUUID().toString(),
                title = title,
                listId = listId,
                projectId = projectId,
                section = section,
                due = due,
                time = time,
                reminder = reminder,
                priority = priority,
                flag = false,
                done = false,
                assigneeIds = assigneeIds,
                tagIds = tagIds,
                recurrence = recurrence,
                subtasks = subtasks,
                notes = notes
            )
            repository.upsertTask(newTask)
        }
    }

    fun upsertTask(task: Task) {
        viewModelScope.launch {
            repository.upsertTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Trash
    val deletedTasks: StateFlow<List<Task>> = repository.getDeletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreTask(id: String) {
        viewModelScope.launch {
            repository.restoreTask(id)
        }
    }

    fun permanentlyDeleteTask(task: Task) {
        viewModelScope.launch {
            repository.permanentlyDeleteTask(task)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    // Cached by taskId — without this, every call created its own independent stateIn()
    // subscriber tied to viewModelScope, so a caller that invoked this per-recomposition
    // instead of hoisting the result (e.g. via remember) would leak one hot flow per call.
    private val commentsFlowCache = mutableMapOf<String, StateFlow<List<TaskComment>>>()

    fun getCommentsForTask(taskId: String): StateFlow<List<TaskComment>> =
        commentsFlowCache.getOrPut(taskId) {
            repository.getCommentsForTask(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun addComment(taskId: String, body: String) {
        viewModelScope.launch {
            val authorId = people.value.find { it.isMe }?.id
            repository.addComment(taskId, body, authorId)
        }
    }

    fun deleteComment(comment: TaskComment) {
        viewModelScope.launch {
            repository.deleteComment(comment)
        }
    }

    fun addProject(name: String, color: String, icon: String = "layers", due: String? = null, commonTagIds: List<String> = emptyList(), defaultReminder: String? = null, description: String? = null, excludeFromToday: Boolean = false) {
        viewModelScope.launch {
            val pid = "pr_" + UUID.randomUUID().toString()
            val project = Project(
                id = pid,
                name = name,
                color = color,
                icon = icon,
                due = due,
                commonTagIds = commonTagIds,
                defaultReminder = defaultReminder,
                description = description,
                excludeFromToday = excludeFromToday
            )
            repository.upsertProject(project)
        }
    }

    fun upsertProject(project: Project) {
        viewModelScope.launch {
            repository.upsertProject(project)
        }
    }

    fun toggleProjectStarred(id: String) {
        viewModelScope.launch {
            val project = projects.value.find { it.id == id } ?: return@launch
            repository.upsertProject(project.copy(starred = !project.starred))
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun deleteProjectOnly(project: Project) {
        viewModelScope.launch {
            repository.deleteProjectOnly(project)
        }
    }

    fun bulkDeleteProjects(ids: List<String>) {
        viewModelScope.launch {
            val byId = projects.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteProject(it) } }
        }
    }

    /** Archiving hides a project from active project surfaces while keeping its tasks linked. */
    fun setProjectArchived(project: Project, archived: Boolean) {
        viewModelScope.launch {
            repository.upsertProject(project.copy(archived = archived))
        }
    }

    fun bulkArchiveProjects(ids: List<String>) {
        viewModelScope.launch {
            val byId = projects.value.associateBy { it.id }
            ids.forEach { id ->
                byId[id]?.let { repository.upsertProject(it.copy(archived = true)) }
            }
        }
    }

    fun addPerson(name: String, color: String, groupId: String? = null, photoUri: String? = null) {
        viewModelScope.launch {
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()

            val person = Person(
                id = "p_" + UUID.randomUUID().toString(),
                name = name,
                initials = if (initials.isEmpty()) "P" else initials,
                color = color,
                isMe = false,
                groupId = groupId,
                photoUri = photoUri
            )
            repository.upsertPerson(person)
        }
    }

    fun upsertPerson(person: Person) {
        viewModelScope.launch {
            repository.upsertPerson(person)
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            repository.deletePerson(person)
        }
    }

    /** Archiving (rather than deleting) a person keeps their historical assigned-task stats
     * intact in Analytics/PersonDetail — used when a team member leaves. */
    fun setPersonArchived(person: Person, archived: Boolean) {
        viewModelScope.launch {
            repository.upsertPerson(person.copy(archived = archived))
        }
    }

    fun togglePersonStarred(id: String) {
        viewModelScope.launch {
            val person = people.value.find { it.id == id } ?: return@launch
            repository.upsertPerson(person.copy(starred = !person.starred))
        }
    }

    fun setPeopleGroup(personIds: List<String>, groupId: String?) {
        viewModelScope.launch {
            val byId = people.value.associateBy { it.id }
            personIds.forEach { id ->
                byId[id]?.let { repository.upsertPerson(it.copy(groupId = groupId)) }
            }
        }
    }

    fun addPersonGroup(name: String, color: String) {
        viewModelScope.launch {
            repository.upsertPersonGroup(PersonGroup(id = "pg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertPersonGroup(group: PersonGroup) {
        viewModelScope.launch {
            repository.upsertPersonGroup(group)
        }
    }

    fun deletePersonGroup(group: PersonGroup) {
        viewModelScope.launch {
            repository.deletePersonGroup(group)
        }
    }

    fun addTag(name: String, color: String, groupId: String? = null, hideCompletedByDefault: Boolean = false) {
        viewModelScope.launch {
            val tag = Tag(
                id = "tag_" + UUID.randomUUID().toString(),
                name = name.lowercase().trim(),
                color = color,
                groupId = groupId,
                hideCompletedByDefault = hideCompletedByDefault
            )
            repository.upsertTag(tag)
        }
    }

    fun upsertTag(tag: Tag) {
        viewModelScope.launch {
            repository.upsertTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    fun bulkDeleteTags(ids: List<String>) {
        viewModelScope.launch {
            val byId = tags.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteTag(it) } }
        }
    }

    fun toggleTagStarred(id: String) {
        viewModelScope.launch {
            val tag = tags.value.find { it.id == id } ?: return@launch
            repository.upsertTag(tag.copy(starred = !tag.starred))
        }
    }

    fun addTagGroup(name: String, color: String) {
        viewModelScope.launch {
            repository.upsertTagGroup(TagGroup(id = "tg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertTagGroup(group: TagGroup) {
        viewModelScope.launch {
            repository.upsertTagGroup(group)
        }
    }

    fun deleteTagGroup(group: TagGroup) {
        viewModelScope.launch {
            repository.deleteTagGroup(group)
        }
    }

    fun addList(name: String, color: String, icon: String, excludeFromToday: Boolean = false) {
        viewModelScope.launch {
            val yataList = YataList(
                id = "l_" + UUID.randomUUID().toString(),
                name = name,
                color = color,
                icon = icon,
                excludeFromToday = excludeFromToday
            )
            repository.upsertList(yataList)
        }
    }

    fun upsertList(list: YataList) {
        viewModelScope.launch {
            repository.upsertList(list)
        }
    }

    fun toggleListStarred(id: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == id } ?: return@launch
            repository.upsertList(list.copy(starred = !list.starred))
        }
    }

    fun deleteList(list: YataList) {
        viewModelScope.launch {
            repository.deleteList(list)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setThemeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            userPreferences.setThemeSchedule(startHour, startMinute, endHour, endMinute)
        }
    }

    fun setAppFont(font: AppFont) {
        viewModelScope.launch {
            userPreferences.setAppFont(font)
        }
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setReduceMotionEnabled(enabled)
        }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch {
            userPreferences.setTextScale(scale)
        }
    }

    fun setTaskRowDensity(density: com.mj.yata.domain.model.TaskRowDensity) {
        viewModelScope.launch {
            userPreferences.setTaskRowDensity(density)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setHapticsEnabled(enabled)
        }
    }

    fun setTodayTabEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTodayTabEnabled(enabled)
        }
    }

    fun setUpcomingTabEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setUpcomingTabEnabled(enabled)
        }
    }

    fun setFabPosition(position: com.mj.yata.domain.model.FabPosition) {
        viewModelScope.launch {
            userPreferences.setFabPosition(position)
        }
    }

    /**
     * Backs up everything to Downloads first, and only wipes the database if that backup
     * actually succeeded — never delete without a safety copy landing on disk.
     */
    fun backupThenDeleteAllData(onResult: (backupFilename: String?) -> Unit) {
        viewModelScope.launch {
            val filename = jsonExporter.exportToDownloads()
            if (filename != null) {
                repository.deleteAllData()
            }
            onResult(filename)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferences.setUserName(name)
        }
    }

    fun setUserEmail(email: String) {
        viewModelScope.launch {
            userPreferences.setUserEmail(email)
        }
    }

    fun setUserPhotoUri(uri: String?) {
        viewModelScope.launch {
            userPreferences.setUserPhotoUri(uri)
        }
    }

    fun setDefaultListId(id: String) {
        viewModelScope.launch {
            userPreferences.setDefaultListId(id)
        }
    }

    fun setStartOfWeekSunday(sunday: Boolean) {
        viewModelScope.launch {
            userPreferences.setStartOfWeekSunday(sunday)
        }
    }

    fun setDefaultReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.setDefaultReminderTime(hour, minute)
        }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch {
            userPreferences.setUiScale(scale)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDynamicColorEnabled(enabled)
        }
    }

    fun setHideCompletedToday(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedToday(hide)
        }
    }

    fun setHideCompletedProject(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedProject(hide)
        }
    }

    fun setHideCompletedList(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedList(hide)
        }
    }

    fun setHideCompletedPerson(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedPerson(hide)
        }
    }

    fun setHasSeenWelcome() {
        viewModelScope.launch {
            userPreferences.setHasSeenWelcome(true)
        }
    }

    fun setPeopleFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setPeopleFeatureEnabled(enabled)
        }
    }

    fun setTagsFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTagsFeatureEnabled(enabled)
        }
    }

    fun setProjectsFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setProjectsFeatureEnabled(enabled)
        }
    }

    fun cloudSignOut() {
        viewModelScope.launch {
            cloudBackupManager.signOut()
        }
    }

    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setCloudBackupEnabled(enabled)
        }
    }

    fun setCloudBackupWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            userPreferences.setCloudBackupWifiOnly(wifiOnly)
        }
    }

    fun setCloudBackupIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            userPreferences.setCloudBackupIntervalMinutes(minutes)
        }
        cloudBackupManager.updateBackupInterval(minutes)
    }

    fun cloudBackupNow(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(cloudBackupManager.backupNow())
        }
    }

    fun listCloudBackups(onResult: (Result<List<CloudBackupEntry>>) -> Unit) {
        viewModelScope.launch {
            onResult(cloudBackupManager.listBackups())
        }
    }

    fun restoreCloudBackup(fileId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(cloudBackupManager.restoreBackup(fileId))
        }
    }

    fun compareWithLastBackup(onResult: (Result<com.mj.yata.data.cloud.CloudBackupDiff>) -> Unit) {
        viewModelScope.launch {
            onResult(cloudBackupManager.compareWithLatestBackup(tasks.value))
        }
    }
}
