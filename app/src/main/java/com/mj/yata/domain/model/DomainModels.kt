package com.mj.yata.domain.model

data class Person(
    val id: String,
    val name: String,
    val initials: String,
    val color: String, // accentA - accentP
    val photoUri: String? = null,
    val isMe: Boolean = false,
    val groupId: String? = null,
    val starred: Boolean = false
)

data class PersonGroup(
    val id: String,
    val name: String,
    val color: String // accentA - accentP
)

data class Project(
    val id: String,
    val name: String,
    val color: String, // accentA - accentP
    val icon: String,
    val due: String? = null,
    val starred: Boolean = false,
    val commonTagIds: List<String> = emptyList(), // live-applied to every task in this project
    val defaultReminder: String? = null // pre-fills NewTaskSheet's reminder for tasks in this project
)

data class YataList(
    val id: String,
    val name: String,
    val color: String, // accentA - accentP
    val icon: String,
    val starred: Boolean = false
)

data class Tag(
    val id: String,
    val name: String,
    val color: String, // accentA - accentP or "error"
    val groupId: String? = null,
    val starred: Boolean = false,
    val hideCompletedByDefault: Boolean = false
)

data class TagGroup(
    val id: String,
    val name: String,
    val color: String // accentA - accentP
)

data class TaskComment(
    val id: String,
    val taskId: String,
    val body: String,
    val createdAt: Long,
    val authorId: String?
)

data class Subtask(
    val id: String,
    val title: String,
    val done: Boolean,
    val parentSubtaskId: String? = null,
    val sortOrder: Int = 0
)

data class Recurrence(
    val freq: String, // "daily" | "weekly" | "monthly" | "yearly"
    val interval: Int,
    val byday: List<String>? = null, // e.g. ["MO", "TU", ...]
    val bymonthday: Int? = null, // 1..31, or -1 to mean "last day of month"
    val ends: RecurrenceEnds = RecurrenceEnds.Never
)

sealed interface RecurrenceEnds {
    object Never : RecurrenceEnds
    data class After(val count: Int) : RecurrenceEnds
    data class On(val date: String) : RecurrenceEnds // "YYYY-MM-DD"
}

data class Task(
    val id: String,
    val title: String,
    val listId: String?,
    val projectId: String?,
    val section: String, // "Morning" | "Afternoon"
    val due: String?, // "YYYY-MM-DD"
    val time: String?, // "2:00 PM"
    val reminder: String?, // "15 min before"
    val priority: String, // "none" | "low" | "med" | "high"
    val flag: Boolean,
    val done: Boolean,
    val completedAt: Long? = null, // epoch millis — when `done` last flipped to true
    val assigneeIds: List<String>,
    val tagIds: List<String>,
    val recurrence: Recurrence?,
    val subtasks: List<Subtask>,
    val notes: String?,
    val sortOrder: Int = 0
)

/**
 * Tag IDs this task carries, including tag IDs its project live-syncs to every task
 * (Project.commonTagIds). Derived, never persisted — a project's common tags always
 * reflect the project's current state without touching individual tasks.
 */
fun Task.effectiveTagIds(projects: List<Project>): List<String> {
    val project = projects.find { it.id == projectId }
    val common = project?.commonTagIds ?: emptyList()
    return (tagIds + common).distinct()
}

fun Task.effectiveTags(projects: List<Project>, tags: List<Tag>): List<Tag> {
    val ids = effectiveTagIds(projects)
    return ids.mapNotNull { id -> tags.find { it.id == id } }
}

/** Tag IDs inherited live from the task's project — not removable at the task level. */
fun Task.inheritedTagIds(projects: List<Project>): List<String> {
    val project = projects.find { it.id == projectId }
    return project?.commonTagIds ?: emptyList()
}
