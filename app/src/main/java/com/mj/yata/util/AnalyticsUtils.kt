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

/** One breakdown row — a project, person, or tag with its task counts for the current period.
 * [overdue] and [onTimeRate] are only populated for people/tags (see [AnalyticsUtils.byPerson],
 * [AnalyticsUtils.byTag]) — projects don't compute them and leave the defaults. */
data class EntityStat(
    val id: String,
    val name: String,
    val colorKey: String,
    val total: Int,
    val done: Int,
    val overdue: Int = 0,
    val onTimeRate: Float? = null
) {
    val pct: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** One day's real completion count (from `Task.completedAt`), for the daily activity chart. */
data class DayActivity(val date: LocalDate, val completedCount: Int)

/** One priority bucket's counts. */
data class PriorityStat(val priority: String, val total: Int, val done: Int) {
    val pct: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** One overdue-age bucket, e.g. "0-3 days" -> 5 tasks currently sitting overdue that long. */
data class AgingBucket(val label: String, val count: Int)

/** One person's share of all currently-open (not done) assigned work — a workload-equity view,
 * independent of the period filter (it's a live snapshot, like [AnalyticsUtils.overdueCount]). */
data class WorkloadShare(val person: Person, val openCount: Int, val share: Float)

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

    /** Consecutive days (ending today) with zero tasks overdue at day's end — a team-management
     * metric (did the owner keep the team's backlog clean) rather than [currentStreak]'s personal
     * completion-activity metric. Looks back at most 60 days to bound the work. */
    fun zeroOverdueStreak(tasks: List<Task>, today: LocalDate = LocalDate.now()): Int {
        val withDue = tasks.filter { it.due != null }
        var day = today
        var streak = 0
        while (streak < 60) {
            val overdueAtDayEnd = withDue.any { task ->
                val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@any false
                val completedDay = task.completedAt?.toLocalDate()
                due.isBefore(day) && (completedDay == null || completedDay.isAfter(day))
            }
            if (overdueAtDayEnd) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Overdue-now tasks bucketed by how many days overdue they are — a classic MIS "aging"
     * view (like a debtor-aging report, but for slipped deadlines) so a manager can tell "a bit
     * late" apart from "seriously stuck." Buckets with zero tasks are omitted. */
    fun agingBuckets(tasks: List<Task>, today: LocalDate = LocalDate.now()): List<AgingBucket> {
        val overdueDays = tasks.mapNotNull { task ->
            if (task.done) return@mapNotNull null
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            if (!due.isBefore(today)) return@mapNotNull null
            java.time.temporal.ChronoUnit.DAYS.between(due, today).toInt()
        }
        val buckets = listOf(
            "0-3 days" to (0..3),
            "4-7 days" to (4..7),
            "8-14 days" to (8..14),
            "15+ days" to (15..Int.MAX_VALUE)
        )
        return buckets.mapNotNull { (label, range) ->
            val count = overdueDays.count { it in range }
            if (count == 0) null else AgingBucket(label, count)
        }
    }

    /** Live snapshot of who's carrying how much open (not done) assigned work right now, as a
     * share of the total — surfaces workload imbalance across the team. People with zero open
     * tasks are omitted. */
    fun workloadShare(tasks: List<Task>, people: List<Person>): List<WorkloadShare> {
        val openTasks = tasks.filter { !it.done }
        val totalAssignedOpen = openTasks.count { it.assigneeIds.isNotEmpty() }
        if (totalAssignedOpen == 0) return emptyList()
        return people.mapNotNull { person ->
            val count = openTasks.count { person.id in it.assigneeIds }
            if (count == 0) return@mapNotNull null
            WorkloadShare(person, count, count.toFloat() / totalAssignedOpen)
        }.sortedByDescending { it.openCount }
    }

    /** Overall on-time completion rate across every task ever completed with a due date and a
     * `completedAt` timestamp — the headline number behind the per-person/per-tag breakdowns.
     * Null if nothing judgeable yet. */
    fun overallOnTimeRate(tasks: List<Task>): Float? {
        val judgeable = tasks.filter { it.done && it.completedAt != null && it.due != null }
        if (judgeable.isEmpty()) return null
        val onTime = judgeable.count { task ->
            val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@count true
            !task.completedAt!!.toLocalDate().isAfter(due)
        }
        return onTime.toFloat() / judgeable.size
    }

    /** Not-yet-done tasks due within the next [days] days (inclusive of today) — a capacity
     * forecast so a manager can see a crunch coming before it arrives, not just today's overdue. */
    fun upcomingDueCount(tasks: List<Task>, days: Int, today: LocalDate = LocalDate.now()): Int {
        val end = today.plusDays((days - 1).toLong())
        return tasks.count { task ->
            if (task.done) return@count false
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
            !due.isBefore(today) && !due.isAfter(end)
        }
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

    /** Tasks overdue right now (ignores the period filter — a person/tag can have overdue work
     * even if it falls outside the selected window), and the on-time rate among tasks in the
     * period that were actually completed (tasks with no due date, or completed on/before it,
     * count as on-time; tasks completed before `completedAt` existed have no timestamp to judge
     * and are excluded from the rate rather than assumed either way). */
    private fun overdueAndOnTimeRate(periodEntityTasks: List<Task>, allEntityTasks: List<Task>, today: LocalDate): Pair<Int, Float?> {
        val overdue = allEntityTasks.count { task ->
            if (task.done) return@count false
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
            due.isBefore(today)
        }
        val judgeable = periodEntityTasks.filter { it.done && it.completedAt != null }
        val onTimeRate = if (judgeable.isEmpty()) null else {
            val onTime = judgeable.count { task ->
                val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val completedDate = task.completedAt!!.toLocalDate()
                due == null || !completedDate.isAfter(due)
            }
            onTime.toFloat() / judgeable.size
        }
        return overdue to onTimeRate
    }

    private fun List<Task>.groupByAssignee(): Map<String, List<Task>> {
        val map = mutableMapOf<String, MutableList<Task>>()
        this.forEach { task ->
            task.assigneeIds.forEach { pid ->
                map.getOrPut(pid) { mutableListOf() }.add(task)
            }
        }
        return map
    }

    private fun List<Task>.groupByTag(projects: List<Project>): Map<String, List<Task>> {
        val map = mutableMapOf<String, MutableList<Task>>()
        this.forEach { task ->
            task.effectiveTagIds(projects).forEach { tid ->
                map.getOrPut(tid) { mutableListOf() }.add(task)
            }
        }
        return map
    }

    fun byPerson(periodTasks: List<Task>, allTasks: List<Task>, people: List<Person>, today: LocalDate = LocalDate.now()): List<EntityStat> {
        val periodTasksByPerson = periodTasks.groupByAssignee()
        val allTasksByPerson = allTasks.groupByAssignee()
        return people.mapNotNull { person ->
            val personPeriodTasks = periodTasksByPerson[person.id] ?: emptyList()
            if (personPeriodTasks.isEmpty()) return@mapNotNull null
            val personAllTasks = allTasksByPerson[person.id] ?: emptyList()
            val (overdue, onTimeRate) = overdueAndOnTimeRate(personPeriodTasks, personAllTasks, today)
            EntityStat(person.id, person.name, person.color, personPeriodTasks.size, personPeriodTasks.count { it.done }, overdue, onTimeRate)
        }.sortedByDescending { it.total }
    }

    fun byTag(periodTasks: List<Task>, allTasks: List<Task>, projects: List<Project>, tags: List<Tag>, today: LocalDate = LocalDate.now()): List<EntityStat> {
        val periodTasksByTag = periodTasks.groupByTag(projects)
        val allTasksByTag = allTasks.groupByTag(projects)
        return tags.mapNotNull { tag ->
            val tagPeriodTasks = periodTasksByTag[tag.id] ?: emptyList()
            if (tagPeriodTasks.isEmpty()) return@mapNotNull null
            val tagAllTasks = allTasksByTag[tag.id] ?: emptyList()
            val (overdue, onTimeRate) = overdueAndOnTimeRate(tagPeriodTasks, tagAllTasks, today)
            EntityStat(tag.id, tag.name, tag.color, tagPeriodTasks.size, tagPeriodTasks.count { it.done }, overdue, onTimeRate)
        }.sortedByDescending { it.total }
    }
}
