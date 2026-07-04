package com.mj.yata.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: YataRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    // Data streams
    val tasks: StateFlow<List<Task>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = repository.getProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<YataList>> = repository.getLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<Person>> = repository.getPeople()
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

    val userName: StateFlow<String> = userPreferences.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userEmail: StateFlow<String> = userPreferences.userEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val defaultListId: StateFlow<String> = userPreferences.defaultListIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val startOfWeekSunday: StateFlow<Boolean> = userPreferences.startOfWeekSundayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultReminderHour: StateFlow<Int> = userPreferences.defaultReminderHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)

    val defaultReminderMinute: StateFlow<Int> = userPreferences.defaultReminderMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiScale: StateFlow<Float> = userPreferences.uiScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

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

    fun toggleTaskFlag(id: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
            repository.upsertTask(task.copy(flag = !task.flag))
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
            repository.upsertTask(task.copy(priority = nextPriority))
        }
    }

    fun addTask(
        title: String,
        listId: String,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        notes: String? = null,
        due: String? = LocalDate.now().toString(),
        time: String? = null,
        reminder: String? = null
    ) {
        viewModelScope.launch {
            val newTask = Task(
                id = "t_" + UUID.randomUUID().toString(),
                title = title,
                listId = listId,
                section = "Afternoon", // Default bucket
                due = due,
                time = time,
                reminder = reminder,
                priority = priority,
                flag = false,
                done = false,
                assigneeIds = assigneeIds,
                tagIds = tagIds,
                recurrence = recurrence,
                subtasks = emptyList(),
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

    fun addProject(name: String, color: String, due: String? = null, commonTagIds: List<String> = emptyList()) {
        viewModelScope.launch {
            val pid = "pr_" + UUID.randomUUID().toString()
            val lid = "l_" + UUID.randomUUID().toString()
            val project = Project(
                id = pid,
                name = name,
                color = color,
                icon = "layers",
                listIds = listOf(lid),
                due = due,
                commonTagIds = commonTagIds
            )
            val defaultList = YataList(
                id = lid,
                name = "General",
                color = color,
                icon = "folder",
                projectId = pid
            )
            repository.upsertProject(project)
            repository.upsertList(defaultList)
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

    fun addPerson(name: String, color: String, groupId: String? = null) {
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
                groupId = groupId
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

    fun addTag(name: String, color: String, groupId: String? = null) {
        viewModelScope.launch {
            val tag = Tag(
                id = "tag_" + UUID.randomUUID().toString(),
                name = name.lowercase().trim(),
                color = color,
                groupId = groupId
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

    fun addList(name: String, color: String, icon: String, projectId: String? = null) {
        viewModelScope.launch {
            val listId = "l_" + UUID.randomUUID().toString()
            val yataList = YataList(
                id = listId,
                name = name,
                color = color,
                icon = icon,
                projectId = projectId
            )
            repository.upsertList(yataList)

            // Update project list order
            if (projectId != null) {
                val project = projects.value.find { it.id == projectId }
                if (project != null) {
                    val updatedIds = project.listIds + listId
                    repository.upsertProject(project.copy(listIds = updatedIds))
                }
            }
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
            
            // Remove list ID from project listIds
            val project = projects.value.find { it.id == list.projectId }
            if (project != null) {
                val updatedIds = project.listIds.filter { it != list.id }
                repository.upsertProject(project.copy(listIds = updatedIds))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
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
}
