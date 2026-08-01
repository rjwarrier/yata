package com.mj.yata.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Person(
    val id: String,
    val name: String,
    val initials: String,
    val color: String, // accentA - accentP
    val photoUri: String? = null,
    val isMe: Boolean = false,
    val groupId: String? = null,
    val starred: Boolean = false,
    val sortOrder: Int = 0, // manual drag-and-drop order within the person's group in the People tab
    val archived: Boolean = false // true = former team member; kept for historical stats, hidden from active pickers
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
    val defaultReminder: String? = null, // pre-fills NewTaskSheet's reminder for tasks in this project
    val description: String? = null, // max 100 chars, enforced in ProjectEditorSheet
    val excludeFromToday: Boolean = false, // tasks here never show in Today, regardless of due date
    val sortOrder: Int = 0, // manual drag-and-drop order in the Projects tab
    val archived: Boolean = false, // true = kept for history, hidden from active project surfaces
    // User-defined headings tasks can be grouped under within this project (e.g. "Design",
    // "Backend") — order here is display order. A task whose `section` isn't in this list (blank,
    // or left over from before a rename/delete) falls into an implicit "No section" bucket rather
    // than being hidden. Empty means the project isn't using sections — ProjectDetailScreen then
    // renders its old flat Pending/Completed list unchanged.
    val sectionNames: List<String> = emptyList()
)

fun List<Project>.activeProjects(includeId: String? = null): List<Project> =
    filter { !it.archived || it.id == includeId }

fun List<Project>.archivedProjects(): List<Project> =
    filter { it.archived }

fun List<Project>.hiddenFromMainTaskProjectIds(): Set<String> =
    filter { it.archived || it.excludeFromToday }.map { it.id }.toSet()

data class YataList(
    val id: String,
    val name: String,
    val color: String, // accentA - accentP
    val icon: String,
    val starred: Boolean = false,
    val excludeFromToday: Boolean = false, // tasks here never show in Today, regardless of due date
    val sortOrder: Int = 0, // manual drag-and-drop order in the nav drawer's Lists section
    val archived: Boolean = false // true = kept for history, hidden from active list surfaces
)

fun List<YataList>.activeLists(includeId: String? = null): List<YataList> =
    filter { !it.archived || it.id == includeId }

/** Mirrors [List.hiddenFromMainTaskProjectIds], but archived lists are deliberately left out —
 * that's the existing Today tab behaviour this only extracts, not a new rule; unlike projects,
 * an archived list's tasks were never excluded from Today. */
fun List<YataList>.hiddenFromMainTaskListIds(): Set<String> =
    filter { it.excludeFromToday }.map { it.id }.toSet()

fun List<YataList>.archivedLists(): List<YataList> =
    filter { it.archived }

fun List<Person>.activePeople(includeIds: Set<String> = emptySet()): List<Person> =
    filter { !it.archived || it.id in includeIds }

fun List<Person>.archivedPeople(): List<Person> =
    filter { it.archived }

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

enum class QuickSnoozePreset(val label: String) {
    TONIGHT("Tonight"),
    TOMORROW_MORNING("Tomorrow morning"),
    NEXT_WEEKDAY("Next weekday")
}

data class Recurrence(
    val freq: String, // "daily" | "weekly" | "monthly" | "yearly"
    val interval: Int,
    val byday: List<String>? = null, // e.g. ["MO", "TU", ...]
    val bymonthday: Int? = null, // 1..31, or -1 to mean "last day of month"
    val ends: RecurrenceEnds = RecurrenceEnds.Never,
    val basedOnCompletion: Boolean = false // true: next occurrence counts from completion date, not due date
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
    // "Not before" — the mirror of [due]'s "not after". Null means available now. A task whose
    // startDate is still in the future is deferred: live and untouched everywhere else, but kept
    // out of Today so the day lists only what can actually be started. See [isDeferredOn].
    val startDate: String? = null, // "YYYY-MM-DD"
    val time: String?, // "2:00 PM"
    val reminder: String?, // "15 min before"
    val priority: String, // "none" | "low" | "med" | "high"
    val flag: Boolean,
    val done: Boolean,
    val completedAt: Long? = null, // epoch millis — when `done` last flipped to true
    // epoch millis — when the task was created. Null for rows predating DB 27; analytics excludes
    // those from age/turnaround metrics rather than treating "unknown" as "created at upgrade time".
    val createdAt: Long? = null,
    val deletedAt: Long? = null, // epoch millis — non-null means "in Trash", not hard-deleted
    val assigneeIds: List<String>,
    val tagIds: List<String>,
    val recurrence: Recurrence?,
    val subtasks: List<Subtask>,
    val notes: String?,
    val sortOrder: Int = 0,
    val seriesId: String? = null, // links a recurring task to its historical completed instances
    // Shelved but intact — hidden from normal listings, recoverable from Archive. Independent of
    // [deletedAt]/Trash; mirrors the same flag on Project/Person/YataList.
    val archived: Boolean = false,
    // epoch millis — "come back and check on this" for a task delegated to someone else. Distinct
    // from [due]/[startDate]: it doesn't mean the work itself is scheduled, it means *you* have
    // nothing to do until this date, so a delegated task with a future follow-up drops out of
    // Today (see [isWaitingOn]) the same way a deferred task does, and reappears on its own once
    // the date arrives. Null means no follow-up is set — the normal case for your own tasks.
    val followUpAt: Long? = null,
    // Planned effort in minutes. Null means unestimated — deliberately distinct from 0, since
    // "no idea yet" and "this takes no time" are different answers and only the former should be
    // excluded from a day's planned total. Never inferred or auto-filled: a guessed estimate is
    // worse than none, because it makes the capacity figure look authoritative when it isn't.
    val estimateMinutes: Int? = null
)

/**
 * True when this task's start date hasn't arrived yet, i.e. it is not actionable on [today].
 *
 * Compared as strings on purpose: dates are stored ISO ("YYYY-MM-DD"), which sorts
 * lexicographically the same way it sorts chronologically, so this stays allocation-free in the
 * list filters that call it per row per recomposition. A malformed value can't throw here the way
 * LocalDate.parse would — it just compares as some other string and the task stays visible, which
 * is the safe direction to fail: a task wrongly shown is a nuisance, one wrongly hidden is lost.
 *
 * Completed tasks are never deferred — a done task with a future start date is history, not
 * something waiting to become actionable, and hiding it would break the Completed sections.
 */
fun Task.isDeferredOn(today: String): Boolean =
    !done && startDate != null && startDate > today

/**
 * True when this task is delegated (owned by someone other than [myId] — see [[AssigneeStack]]
 * for the owner-is-assigneeIds[0] convention) and its [followUpAt] date hasn't arrived yet, i.e.
 * you've said "nothing to do until then" and it should stay out of Today until it does. Mirrors
 * [isDeferredOn]'s reasoning: the task itself stays live and visible everywhere else (its
 * project/list, search, the owning person's task list) and un-snoozes itself the moment
 * [nowMillis] passes [followUpAt], no background job required.
 */
fun Task.isWaitingOn(nowMillis: Long, myId: String?): Boolean {
    if (done || followUpAt == null || followUpAt <= nowMillis) return false
    val owner = assigneeIds.firstOrNull() ?: return false
    return myId == null || owner != myId
}

/**
 * True when this task belongs on a "your Today" surface — due or overdue as of [today], and
 * neither deferred ([isDeferredOn]) nor snoozed waiting on someone else ([isWaitingOn]).
 *
 * Exists so the Today tab, the home-screen widgets and the daily agenda digest can't drift apart:
 * they had, which is how a task hidden from Today in the app kept showing on the launcher. Date
 * *browsing* surfaces — the Upcoming/Calendar tab, Next 10 Days, a project or list detail screen —
 * deliberately do **not** use this: a deferred task still legitimately appears on its due date
 * there, since the point of a start date is only to keep it out of the day view.
 *
 * Callers still apply their own scoping on top (excludeFromToday containers, archived projects,
 * `wasPendingAsOf` for progress counts) — this covers the two task-level "not yet mine to do"
 * rules alone.
 */
fun Task.isActionableToday(today: String, nowMillis: Long, myId: String?): Boolean =
    due != null && due <= today && !isDeferredOn(today) && !isWaitingOn(nowMillis, myId)

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

/**
 * True if this task was still open, or was completed on [day], as of the end of [day] — i.e. it
 * belongs in that day's pending/done total. A task done on an *earlier* day no longer counts:
 * without this, a "due <= today" style query keeps matching a task forever after its due date,
 * so an old completion would permanently inflate a "today" progress metric. Shared by the Today
 * tab and the home-screen widgets, which all define "today" the same way.
 */
fun Task.wasPendingAsOf(day: LocalDate): Boolean {
    if (!done) return true
    val completedDay = completedAt?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    return completedDay == day
}
