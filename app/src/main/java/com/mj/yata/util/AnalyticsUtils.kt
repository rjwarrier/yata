package com.mj.yata.util

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.effectiveTagIds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AnalyticsPeriod { WEEK, MONTH, ALL }

/** One breakdown row — a project, person, or tag with its task counts for the current period. */
data class EntityStat(
    val id: String,
    val name: String,
    val colorKey: String,
    val total: Int,
    val done: Int
) {
    val pct: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** One day's real completion count (from `Task.completedAt`), for the daily activity chart. */
data class DayActivity(val date: LocalDate, val completedCount: Int)

/** One priority bucket's counts. */
data class PriorityStat(val priority: String, val total: Int, val done: Int) {
    val pct: Float get() = if (total > 0) done.toFloat() / total else 0f
}

object AnalyticsUtils {
    private fun periodStart(period: AnalyticsPeriod, today: LocalDate): LocalDate = when (period) {
        AnalyticsPeriod.WEEK -> today.minusDays(6)
        AnalyticsPeriod.MONTH -> today.minusDays(29)
        AnalyticsPeriod.ALL -> LocalDate.MIN
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Tasks whose due date falls in the period. Undated tasks only count under ALL. */
    fun filterTasksByPeriod(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): List<Task> {
        if (period == AnalyticsPeriod.ALL) return tasks
        val start = periodStart(period, today)
        return tasks.filter { task ->
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
            !due.isBefore(start) && !due.isAfter(today)
        }
    }

    /** Completion % (0f-1f) for the equal-length window immediately before the current period —
     * null for ALL, since there's no meaningful "previous all-time" to compare against. */
    fun previousPeriodCompletionPct(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): Float? {
        if (period == AnalyticsPeriod.ALL) return null
        val lengthDays = when (period) {
            AnalyticsPeriod.WEEK -> 7L
            AnalyticsPeriod.MONTH -> 30L
            AnalyticsPeriod.ALL -> return null
        }
        val prevEnd = today.minusDays(lengthDays)
        val prevStart = prevEnd.minusDays(lengthDays - 1)
        val prevTasks = tasks.filter { task ->
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
            !due.isBefore(prevStart) && !due.isAfter(prevEnd)
        }
        if (prevTasks.isEmpty()) return null
        return prevTasks.count { it.done }.toFloat() / prevTasks.size
    }

    /** Real completions per day (from `completedAt`) across the period, oldest first. Skipped
     * for ALL (unbounded range). */
    fun dailyActivity(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): List<DayActivity> {
        if (period == AnalyticsPeriod.ALL) return emptyList()
        val start = periodStart(period, today)
        val completedByDate = tasks.mapNotNull { task -> task.completedAt?.toLocalDate() }
            .groupingBy { it }
            .eachCount()

        val days = mutableListOf<DayActivity>()
        var day = start
        while (!day.isAfter(today)) {
            days.add(DayActivity(day, completedByDate[day] ?: 0))
            day = day.plusDays(1)
        }
        return days
    }

    /** Tasks overdue right now — due before today and not done. Always "all time", ignores the period filter. */
    fun overdueCount(tasks: List<Task>, today: LocalDate = LocalDate.now()): Int =
        tasks.count { task ->
            if (task.done) return@count false
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
            due.isBefore(today)
        }

    /** Consecutive days (ending today or yesterday) with at least one real completion that day
     * (via `completedAt`). Today doesn't break the streak if nothing's done yet — you still have
     * the rest of it. Tasks completed before the completedAt column existed (backfilled as null)
     * don't count toward any day, so a long-time user's streak may look shorter than it "really" is. */
    fun currentStreak(tasks: List<Task>, today: LocalDate = LocalDate.now()): Int {
        val completedDates = tasks.mapNotNull { it.completedAt?.toLocalDate() }.toHashSet()
        var day = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        while (day in completedDates) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun byPriority(tasks: List<Task>): List<PriorityStat> {
        val order = listOf("high", "med", "low", "none")
        val grouped = tasks.groupBy { it.priority }
        return order.mapNotNull { priority ->
            val priorityTasks = grouped[priority] ?: return@mapNotNull null
            if (priorityTasks.isEmpty()) return@mapNotNull null
            PriorityStat(priority, priorityTasks.size, priorityTasks.count { it.done })
        }
    }

    fun byProject(tasks: List<Task>, projects: List<Project>): List<EntityStat> {
        val grouped = tasks.filter { it.projectId != null }.groupBy { it.projectId!! }
        return projects.mapNotNull { project ->
            val projectTasks = grouped[project.id] ?: return@mapNotNull null
            if (projectTasks.isEmpty()) return@mapNotNull null
            EntityStat(project.id, project.name, project.color, projectTasks.size, projectTasks.count { it.done })
        }.sortedByDescending { it.total }
    }

    fun byPerson(tasks: List<Task>, people: List<Person>): List<EntityStat> {
        return people.mapNotNull { person ->
            val personTasks = tasks.filter { person.id in it.assigneeIds }
            if (personTasks.isEmpty()) return@mapNotNull null
            EntityStat(person.id, person.name, person.color, personTasks.size, personTasks.count { it.done })
        }.sortedByDescending { it.total }
    }

    fun byTag(tasks: List<Task>, projects: List<Project>, tags: List<Tag>): List<EntityStat> {
        return tags.mapNotNull { tag ->
            val tagTasks = tasks.filter { tag.id in it.effectiveTagIds(projects) }
            if (tagTasks.isEmpty()) return@mapNotNull null
            EntityStat(tag.id, tag.name, tag.color, tagTasks.size, tagTasks.count { it.done })
        }.sortedByDescending { it.total }
    }
}
