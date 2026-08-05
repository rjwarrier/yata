package com.mj.yata.tasker.createtask

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.findSimilarTask
import com.mj.yata.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID

class CreateTaskRunner : TaskerPluginRunnerActionNoOutput<CreateTaskInput>() {

    override fun run(context: Context, input: TaskerInput<CreateTaskInput>): TaskerPluginResult<Unit> {
        val fields = input.regular
        val title = fields.title?.trim()
        if (title.isNullOrBlank()) {
            return TaskerPluginResultError(1, "Title is required")
        }

        return try {
            val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
            val userPreferences = UserPreferences(context.applicationContext)
            runBlocking<TaskerPluginResult<Unit>> {
                if (!userPreferences.taskerIntegrationEnabledFlow.first()) {
                    return@runBlocking TaskerPluginResultError(3, "Tasker integration is disabled in Yata Settings.")
                }
                NaturalLanguageParser.configureDateAliases(userPreferences.dateAliasDefinitionsFlow.first())
                // Checked before resolving/creating any project·list·tag·person side effects,
                // so a rejected duplicate never leaves behind newly-created entities for a task
                // that was never actually added.
                val duplicate = findSimilarTask(title, repository.getTasks().first())
                if (duplicate != null) {
                    return@runBlocking TaskerPluginResultError(2, "Similar task already exists: \"${duplicate.title}\". Task not created.")
                }

                val projectId = resolveOrCreateProject(repository, fields.project)
                val listId = resolveOrCreateList(repository, fields.list)
                val tagIds = resolveOrCreateTags(repository, fields.tags)
                val assigneeIds = resolveOrCreatePeople(repository, fields.assignees)

                val due = parseDue(fields.due)
                val time = parseTime(fields.time)
                val recurrence = fields.repeat?.takeIf { it.isNotBlank() }?.let { NaturalLanguageParser.parse(it).recurrence }
                val priority = normalizePriority(fields.priority)
                val section = normalizeSection(fields.section)
                val reminder = normalizeReminder(fields.reminder)

                repository.upsertTask(
                    Task(
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
                        subtasks = emptyList(),
                        notes = fields.notes?.takeIf { it.isNotBlank() }
                    )
                )
                TaskerPluginResultSucess()
            }
        } catch (t: Throwable) {
            TaskerPluginResultError(t)
        }
    }

    private fun parseDue(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        TaskScheduleUtils.parseDate(value)?.let { return value }
        return NaturalLanguageParser.parse(value).due
    }

    private fun parseTime(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        TaskScheduleUtils.parseTime(value)?.let { return TaskScheduleUtils.formatTime(it.hour, it.minute) }
        return NaturalLanguageParser.parse(value).time
    }

    private fun normalizePriority(raw: String?): String {
        val value = raw?.trim()?.lowercase(Locale.getDefault())
        return if (value in setOf("none", "low", "med", "high")) value!! else "none"
    }

    // Section is a free-typed, project-scoped heading (see Project.sectionNames) rather than a
    // fixed Morning/Afternoon choice, and there's no Tasker-facing field to set it — pass through
    // whatever the caller supplied, or leave the task in the project's default "No section" bucket.
    private fun normalizeSection(raw: String?): String = raw?.trim().orEmpty()

    private fun normalizeReminder(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return TaskScheduleUtils.reminderOptions.find { it.equals(value, ignoreCase = true) }
    }

    private suspend fun resolveOrCreateProject(repository: YataRepository, name: String?): String? {
        val trimmed = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val existing = repository.getProjects().first().find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        val id = "pr_" + UUID.randomUUID().toString()
        repository.upsertProject(Project(id = id, name = trimmed, color = accentFor(trimmed), icon = "layers"))
        return id
    }

    private suspend fun resolveOrCreateList(repository: YataRepository, name: String?): String? {
        val trimmed = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val existing = repository.getLists().first().find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        val id = "l_" + UUID.randomUUID().toString()
        repository.upsertList(YataList(id = id, name = trimmed, color = accentFor(trimmed), icon = "folder"))
        return id
    }

    private suspend fun resolveOrCreateTags(repository: YataRepository, raw: String?): List<String> {
        val names = raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: return emptyList()
        val existingTags = repository.getTags().first()
        return names.map { name ->
            val normalized = name.lowercase(Locale.getDefault())
            existingTags.find { it.name.equals(normalized, ignoreCase = true) }?.id ?: run {
                val id = "tag_" + UUID.randomUUID().toString()
                repository.upsertTag(Tag(id = id, name = normalized, color = accentFor(normalized)))
                id
            }
        }
    }

    private suspend fun resolveOrCreatePeople(repository: YataRepository, raw: String?): List<String> {
        val names = raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: return emptyList()
        val existingPeople = repository.getPeople().first()
        return names.map { name ->
            existingPeople.find { it.name.equals(name, ignoreCase = true) }?.id ?: run {
                val id = "p_" + UUID.randomUUID().toString()
                val initials = name.split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(Locale.getDefault()).ifEmpty { "P" }
                repository.upsertPerson(Person(id = id, name = name, initials = initials, color = accentFor(name), isMe = false))
                id
            }
        }
    }

    private fun accentFor(name: String): String {
        val accents = com.mj.yata.ui.theme.ALL_ACCENT_KEYS
        return accents[Math.floorMod(name.hashCode(), accents.size)]
    }
}
