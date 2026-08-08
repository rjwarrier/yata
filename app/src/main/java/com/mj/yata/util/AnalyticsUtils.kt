package com.mj.yata.util

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.effectiveTagIds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AnalyticsPeriod { WEEK, MONTH, ALL }

fun AnalyticsPeriod.label(): String = when (this) {
    AnalyticsPeriod.WEEK -> "7 Days"
    AnalyticsPeriod.MONTH -> "30 Days"
    AnalyticsPeriod.ALL -> "All Time"
}

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

/**
 * One day's real completion count (from `Task.completedAt`) alongside how many tasks were
 * *created* that day (from `Task.createdAt`, DB 27).
 *
 * Completions alone say how much you finished; they can't say whether the backlog is growing.
 * Two days that both show "5 done" mean opposite things if one took in 1 new task and the other
 * took in 12. [createdCount] is what makes the chart answer that, and is 0 for days whose tasks
 * predate the `createdAt` column rather than being guessed at.
 */
data class DayActivity(
    val date: LocalDate,
    val completedCount: Int,
    val createdCount: Int = 0
)

/** One priority bucket's counts. */
data class PriorityStat(val priority: String, val total: Int, val done: Int) {
    val pct: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** One overdue-age bucket, e.g. "0-3 days" -> 5 tasks currently sitting overdue that long. */
data class AgingBucket(val label: String, val count: Int)

/**
 * How a metric moved against the equal-length window immediately before this one.
 *
 * [delta] is in the metric's own unit — tasks for counts, percentage points for rates — and
 * [improved] says whether that direction is the good one, which differs per metric: fewer overdue
 * is an improvement, a *higher* on-time rate is. Keeping that judgement here rather than in the
 * composable means the arrow and its colour can't disagree with each other on one screen and
 * agree on another.
 */
data class MetricTrend(val delta: Int, val improved: Boolean) {
    val isFlat: Boolean get() = delta == 0
}

/**
 * Planned effort still on the books, from `Task.estimateMinutes` (DB 30).
 *
 * Every other figure on the screen counts tasks, which treats a two-minute reply and a two-day
 * write-up as the same unit of work. This is the one view in terms of the thing that actually
 * runs out. [unestimatedOpenCount] is carried alongside deliberately: a total is only as
 * trustworthy as its coverage, and "14h planned" means something different with three
 * unestimated tasks behind it than with sixty.
 */
data class CapacitySnapshot(
    val openMinutes: Int,
    val dueNext7Minutes: Int,
    val overdueMinutes: Int,
    val estimatedOpenCount: Int,
    val unestimatedOpenCount: Int
)

/**
 * The weekday work actually gets finished on, when one stands out.
 *
 * Only reported when the pattern is strong enough to be a pattern rather than a coincidence —
 * see [WEEKDAY_PATTERN_MIN_COMPLETIONS] and [WEEKDAY_PATTERN_MIN_RATIO] — because "you finish
 * most on Thursdays" derived from nine completions is a statement about noise.
 */
data class WeekdayPattern(
    val day: java.time.DayOfWeek,
    val completions: Int,
    val shareOfTotal: Float
)

/** One person's share of all currently-open (not done) assigned work — a workload-equity view,
 * independent of the period filter (it's a live snapshot, like [AnalyticsUtils.overdueCount]). */
data class WorkloadShare(val person: Person, val openCount: Int, val share: Float)

/**
 * Everything worth knowing about one assignee, in the one place a delegator looks.
 *
 * The existing [EntityStat] row answers "how much of their work is done", which is a progress
 * question. Handing work to people raises different ones — is it moving, is it aging, do they
 * finish by the date agreed — so this carries throughput, on-time rate and age alongside the raw
 * counts rather than making the reader infer them from a percentage.
 *
 * [avgOpenAgeDays] and [medianTurnaroundDays] are null until there are tasks with a `createdAt`
 * (DB 27) to measure; rows predating it are skipped rather than counted as age zero.
 */
data class DelegationStat(
    val person: Person,
    val openCount: Int,
    val overdueCount: Int,
    val completedInPeriod: Int,
    val onTimeRate: Float?,
    val avgOpenAgeDays: Int?,
    val oldestOpenAgeDays: Int?,
    val medianTurnaroundDays: Int?
) {
    /** Share of this person's open work that is past its due date — the "is this going wrong"
     * signal, distinct from how much they're carrying. */
    val overdueRatio: Float get() = if (openCount > 0) overdueCount.toFloat() / openCount else 0f
}

/** Where open work sits relative to *you*, the person doing the delegating. Counts tasks, not
 * assignments, so a task with two assignees is one task. */
data class DelegationSummary(
    val delegatedOpen: Int,
    val selfOpen: Int,
    val unassignedOpen: Int,
    val peopleWithOpenWork: Int,
    val peopleWithOverdue: Int
) {
    val totalOpen: Int get() = delegatedOpen + selfOpen + unassignedOpen
    /** Fraction of open work that is somebody else's — how much you've actually handed off. */
    val delegationRate: Float? get() = if (totalOpen > 0) delegatedOpen.toFloat() / totalOpen else null
}

/**
 * A ranked callout the screen prints verbatim, e.g. "Website is the biggest overdue cluster".
 * Computed here so the phrasing and the ranking stay together rather than the composable
 * re-deriving which entity "wins".
 *
 * [searchFilter] is the encoded `SmartFilter` set that shows the exact tasks this callout is
 * about, so the reader can act on it rather than being told a number and left to find them.
 * Deliberately a plain string rather than the enum: `SmartFilter` is internal to the search
 * screen, and the nav route takes this same comma-joined form anyway. Null when no existing
 * filter matches the claim exactly — sending someone to an *almost* right list is worse than
 * leaving the row inert, since they'd act on the wrong tasks.
 */
data class AnalyticsInsight(
    val headline: String,
    val detail: String,
    val severity: InsightSeverity,
    val searchFilter: String? = null
)

enum class InsightSeverity { GOOD, NEUTRAL, WARN }

/** Every metric the Analytics screen renders, computed in one pass by [AnalyticsUtils.computeUiState]
 * off the raw domain flows — the screen composable only ever reads this, it never derives a metric
 * itself. [zeroOverdueStreakDays], [workloadShares] and [overdueCount] are live snapshots independent
 * of [period]; everything else is scoped to it. */
data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.WEEK,
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    val completionPct: Float = 0f,
    val previousPeriodCompletionPct: Float? = null,
    /** Tasks actually finished inside the window (by `completedAt`) — throughput, as opposed to
     * [doneCount], which counts done tasks in the period set however they got there. */
    val completedInPeriod: Int = 0,
    val previousPeriodCompleted: Int? = null,
    val currentStreak: Int = 0,
    val overdueCount: Int = 0,
    val zeroOverdueStreakDays: Int = 0,
    val overallOnTimeRate: Float? = null,
    /** Movement against one window ago, so the headline figures read as direction rather than
     * level. Null for ALL, which has no previous window to compare with. */
    val overdueTrend: MetricTrend? = null,
    val onTimeRateTrend: MetricTrend? = null,
    /** Tasks created in the window — paired with [completedInPeriod], this is whether the backlog
     * grew or shrank. */
    val createdInPeriod: Int = 0,
    val dueNext7: Int = 0,
    val dueNext30: Int = 0,
    val agingBuckets: List<AgingBucket> = emptyList(),
    val workloadShares: List<WorkloadShare> = emptyList(),
    val dailyActivity: List<DayActivity> = emptyList(),
    val priorityStats: List<PriorityStat> = emptyList(),
    val projectStats: List<EntityStat> = emptyList(),
    val personStats: List<EntityStat> = emptyList(),
    val tagStats: List<EntityStat> = emptyList(),
    val listStats: List<EntityStat> = emptyList(),
    val delegationStats: List<DelegationStat> = emptyList(),
    val delegationSummary: DelegationSummary = DelegationSummary(0, 0, 0, 0, 0),
    val insights: List<AnalyticsInsight> = emptyList(),
    /** Median days from creation to completion across everything finished in the period. Null
     * until enough tasks carry a `createdAt` to measure. */
    val medianTurnaroundDays: Int? = null,
    /** Oldest currently-open task's age in days, and how many open tasks have no due date at all —
     * the two things that quietly rot a delegated backlog. */
    val oldestOpenAgeDays: Int? = null,
    val openWithoutDueDate: Int = 0,
    /** Planned effort still outstanding. Null when nothing open is estimated. */
    val capacity: CapacitySnapshot? = null,
    /** Completions this figure rests on, so a rate over a handful of tasks can be told apart from
     * a rate over hundreds. */
    val onTimeRateSampleSize: Int = 0
)

/**
 * Encoded `SmartFilter` names the Analytics screen can hand to the search route, so a stat can
 * drill through to the tasks behind it. These must stay spelled exactly as the enum constants in
 * `SmartFilter` (the route decodes by name); a typo silently yields an empty filter set rather
 * than an error, so treat them as the API they are.
 */
const val SEARCH_FILTER_OVERDUE = "OVERDUE"
const val SEARCH_FILTER_NO_DUE_DATE = "NO_DUE_DATE"
const val SEARCH_FILTER_HIGH_PRIORITY = "HIGH_PRIORITY"

/**
 * How much a metric has to move before it's worth a callout.
 *
 * Set by what a reader would act on rather than by statistics: two tasks' drift in a week is
 * noise, and a section that announces noise every week is one nobody reads by the third week.
 * Percentage-point thresholds are deliberately coarser than the count ones, since a rate over a
 * handful of tasks swings hard on one late finish.
 */
private const val MATERIAL_OVERDUE_CHANGE = 3
private const val MATERIAL_ON_TIME_CHANGE_POINTS = 10
private const val MATERIAL_BACKLOG_CHANGE = 3

/** Roughly two completions per weekday — below this, one busy afternoon decides the "pattern". */
private const val WEEKDAY_PATTERN_MIN_COMPLETIONS = 14

/** How far the leading day must sit above an even spread before it's worth naming. 1/7th of
 * completions on each day is no pattern at all; 1.6× that is a habit. */
private const val WEEKDAY_PATTERN_MIN_RATIO = 1.6f

object AnalyticsUtils {
    /** Single entrypoint the ViewModel calls off the UI thread whenever tasks/projects/people/tags
     * or the selected period change — every metric on the Analytics screen is computed here, once,
     * rather than scattered across `remember` blocks in the composable. */
    fun computeUiState(
        tasks: List<Task>,
        projects: List<Project>,
        people: List<Person>,
        tags: List<Tag>,
        lists: List<YataList> = emptyList(),
        period: AnalyticsPeriod,
        today: LocalDate = LocalDate.now()
    ): AnalyticsUiState {
        val periodTasks = filterTasksByPeriod(tasks, period, today)
        val totalCount = periodTasks.size
        val doneCount = periodTasks.count { it.done }
        val delegationStats = byDelegation(tasks, people, period, today)
        val delegationSummary = delegationSummary(tasks, people)
        val projectStats = byProject(periodTasks, projects)
        val personStats = byPerson(periodTasks, tasks, people, today)
        val tagStats = byTag(periodTasks, tasks, projects, tags, today)
        val listStats = byList(periodTasks, lists)
        val overdueTrend = overdueTrend(tasks, period, today)
        val onTimeRateTrend = onTimeRateTrend(tasks, period, today)
        val createdInPeriod = createdInPeriod(tasks, period, today)
        val completedInPeriod = completedInPeriod(tasks, period, today)
        return AnalyticsUiState(
            period = period,
            totalCount = totalCount,
            doneCount = doneCount,
            completionPct = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
            previousPeriodCompletionPct = previousPeriodCompletionPct(tasks, period, today),
            completedInPeriod = completedInPeriod,
            previousPeriodCompleted = previousPeriodCompleted(tasks, period, today),
            currentStreak = currentStreak(tasks, today),
            overdueCount = overdueCount(tasks, today),
            zeroOverdueStreakDays = zeroOverdueStreak(tasks, today),
            overallOnTimeRate = overallOnTimeRate(tasks),
            overdueTrend = overdueTrend,
            onTimeRateTrend = onTimeRateTrend,
            createdInPeriod = createdInPeriod,
            dueNext7 = upcomingDueCount(tasks, 7, today),
            dueNext30 = upcomingDueCount(tasks, 30, today),
            agingBuckets = agingBuckets(tasks, today),
            workloadShares = workloadShare(tasks, people),
            dailyActivity = dailyActivity(tasks, period, today),
            priorityStats = byPriority(periodTasks),
            projectStats = projectStats,
            personStats = personStats,
            tagStats = tagStats,
            listStats = listStats,
            delegationStats = delegationStats,
            delegationSummary = delegationSummary,
            medianTurnaroundDays = medianTurnaround(periodTasks.filter { it.done }),
            oldestOpenAgeDays = oldestOpenAge(tasks, today),
            openWithoutDueDate = tasks.count { !it.done && it.due == null },
            capacity = capacitySnapshot(tasks, today),
            onTimeRateSampleSize = onTimeRateSampleSize(tasks),
            insights = buildInsights(
                tasks = tasks,
                delegationStats = delegationStats,
                summary = delegationSummary,
                projectStats = projectStats,
                tagStats = tagStats,
                listStats = listStats,
                today = today,
                overdueTrend = overdueTrend,
                onTimeRateTrend = onTimeRateTrend,
                createdInPeriod = createdInPeriod,
                completedInPeriod = completedInPeriod
            )
        )
    }

    private fun periodStart(period: AnalyticsPeriod, today: LocalDate): LocalDate = when (period) {
        AnalyticsPeriod.WEEK -> today.minusDays(6)
        AnalyticsPeriod.MONTH -> today.minusDays(29)
        AnalyticsPeriod.ALL -> LocalDate.MIN
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Tasks that count as "in" the period.
     *
     * This used to keep only tasks whose *due date* landed in the window, which quietly excluded
     * the two cases that matter most when work is delegated: a task with no due date at all (very
     * common when you hand something over without committing to a date) appeared under ALL and
     * nowhere else, and a task finished inside the window but due outside it didn't register as
     * work done. A teammate holding nothing but undated tasks was therefore invisible on every
     * view except All Time.
     *
     * The window is now activity-based — a task belongs to the period if it was *completed* in it,
     * was *due* in it, or was *created* in it — plus every task still open, which is current state
     * rather than history and is the thing you are actually managing. Undated open work is now
     * always counted; the numbers are consequently higher than the old definition produced.
     */
    fun filterTasksByPeriod(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): List<Task> {
        if (period == AnalyticsPeriod.ALL) return tasks
        val start = periodStart(period, today)
        fun inWindow(date: LocalDate?) = date != null && !date.isBefore(start) && !date.isAfter(today)
        return tasks.filter { task ->
            if (!task.done) return@filter true
            inWindow(task.completedAt?.toLocalDate()) ||
                inWindow(task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) ||
                inWindow(task.createdAt?.toLocalDate())
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

    /** Real completions and creations per day (from `completedAt`/`createdAt`) across the period,
     * oldest first. Skipped for ALL (unbounded range). */
    fun dailyActivity(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): List<DayActivity> {
        if (period == AnalyticsPeriod.ALL) return emptyList()
        val start = periodStart(period, today)
        val completedByDate = tasks.mapNotNull { task -> task.completedAt?.toLocalDate() }
            .groupingBy { it }
            .eachCount()
        val createdByDate = tasks.mapNotNull { task -> task.createdAt?.toLocalDate() }
            .groupingBy { it }
            .eachCount()

        val days = mutableListOf<DayActivity>()
        var day = start
        while (!day.isAfter(today)) {
            days.add(DayActivity(day, completedByDate[day] ?: 0, createdByDate[day] ?: 0))
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

    /**
     * How many tasks were overdue as of [date] — due before it, and not yet finished by then.
     *
     * A task completed before the `completedAt` column existed (DB 24) carries no timestamp to
     * place it in time. It is treated as finished rather than as perpetually overdue: counting
     * an old completed task as overdue on every historical day would drag every backward-looking
     * metric the same way, and would in particular pin [zeroOverdueStreak] at zero forever for
     * anyone whose database predates that column.
     */
    fun overdueCountOn(tasks: List<Task>, date: LocalDate): Int = tasks.count { task ->
        val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
        if (!due.isBefore(date)) return@count false
        val completedDay = task.completedAt?.toLocalDate()
        when {
            completedDay != null -> completedDay.isAfter(date)
            task.done -> false
            else -> true
        }
    }

    /** Consecutive days (ending today) with zero tasks overdue at day's end — a team-management
     * metric (did the owner keep the team's backlog clean) rather than [currentStreak]'s personal
     * completion-activity metric. Looks back at most 60 days to bound the work. */
    fun zeroOverdueStreak(tasks: List<Task>, today: LocalDate = LocalDate.now()): Int {
        var day = today
        var streak = 0
        while (streak < 60) {
            if (overdueCountOn(tasks, day) > 0) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /**
     * Movement in the overdue count against where it stood one window ago — the difference
     * between "9 overdue" as a fact and "9 overdue, up from 2" as a warning.
     *
     * Compared against the *start* of the current window rather than a separate previous-window
     * aggregate, because overdue is a level (a stock), not a flow: the meaningful question is
     * where the number is now versus where it was then, not how much accumulated in between.
     */
    fun overdueTrend(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): MetricTrend? {
        if (period == AnalyticsPeriod.ALL) return null
        val was = overdueCountOn(tasks, periodStart(period, today))
        val delta = overdueCount(tasks, today) - was
        return MetricTrend(delta = delta, improved = delta < 0)
    }

    /** On-time rate among tasks *completed* inside [start]..[end] that had a due date to be judged
     * against. Null when nothing in the window is judgeable, so a quiet week reads as "no data"
     * rather than as a rate of zero. */
    fun onTimeRateInWindow(tasks: List<Task>, start: LocalDate, end: LocalDate): Float? {
        val judgeable = tasks.filter { task ->
            if (!task.done || task.due == null) return@filter false
            val completed = task.completedAt?.toLocalDate() ?: return@filter false
            !completed.isBefore(start) && !completed.isAfter(end)
        }
        if (judgeable.isEmpty()) return null
        val onTime = judgeable.count { task ->
            val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@count true
            !task.completedAt!!.toLocalDate().isAfter(due)
        }
        return onTime.toFloat() / judgeable.size
    }

    /** Change in on-time rate, in percentage points, against the equal-length previous window.
     * Null unless both windows have something judgeable — a delta against nothing is noise. */
    fun onTimeRateTrend(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): MetricTrend? {
        val lengthDays = when (period) {
            AnalyticsPeriod.WEEK -> 7L
            AnalyticsPeriod.MONTH -> 30L
            AnalyticsPeriod.ALL -> return null
        }
        val current = onTimeRateInWindow(tasks, periodStart(period, today), today) ?: return null
        val prevEnd = today.minusDays(lengthDays)
        val previous = onTimeRateInWindow(tasks, prevEnd.minusDays(lengthDays - 1), prevEnd) ?: return null
        val delta = ((current - previous) * 100).roundToInt()
        return MetricTrend(delta = delta, improved = delta > 0)
    }

    /**
     * Planned effort across everything still open, in minutes, plus how much of it is already
     * late or lands in the next week.
     *
     * Null when nothing open carries an estimate — a confident "0h planned" for a backlog nobody
     * has estimated says the opposite of the truth, so the whole readout hides instead. Unlike
     * the task counts elsewhere this is a live snapshot and ignores the period filter: effort
     * you still owe isn't a property of the window you happen to be looking at.
     */
    fun capacitySnapshot(tasks: List<Task>, today: LocalDate = LocalDate.now()): CapacitySnapshot? {
        val open = tasks.filter { !it.done }
        val openMinutes = EstimateUtils.plannedMinutes(open) ?: return null
        val next7End = today.plusDays(6)
        fun dueDate(task: Task) = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return CapacitySnapshot(
            openMinutes = openMinutes,
            dueNext7Minutes = EstimateUtils.plannedMinutes(
                open.filter { task ->
                    val due = dueDate(task) ?: return@filter false
                    !due.isBefore(today) && !due.isAfter(next7End)
                }
            ) ?: 0,
            overdueMinutes = EstimateUtils.plannedMinutes(
                open.filter { task -> dueDate(task)?.isBefore(today) == true }
            ) ?: 0,
            estimatedOpenCount = open.count { it.estimateMinutes != null },
            unestimatedOpenCount = EstimateUtils.unestimatedCount(open)
        )
    }

    /**
     * The weekday completions cluster on, across all recorded history rather than the selected
     * period — a seven-day window holds one of each weekday, which can't show a weekly rhythm.
     *
     * Null unless there's enough history for the leading day to mean something; see
     * [WEEKDAY_PATTERN_MIN_COMPLETIONS] and [WEEKDAY_PATTERN_MIN_RATIO].
     */
    fun weekdayPattern(tasks: List<Task>): WeekdayPattern? {
        val completionDays = tasks.mapNotNull { it.completedAt?.toLocalDate()?.dayOfWeek }
        if (completionDays.size < WEEKDAY_PATTERN_MIN_COMPLETIONS) return null
        val byDay = completionDays.groupingBy { it }.eachCount()
        val (day, count) = byDay.maxByOrNull { it.value } ?: return null
        val evenSpread = completionDays.size / 7f
        if (evenSpread <= 0f || count < evenSpread * WEEKDAY_PATTERN_MIN_RATIO) return null
        return WeekdayPattern(
            day = day,
            completions = count,
            shareOfTotal = count.toFloat() / completionDays.size
        )
    }

    /** How many completed tasks the overall on-time rate is actually derived from — a rate over
     * four tasks and one over four hundred read identically without it. */
    fun onTimeRateSampleSize(tasks: List<Task>): Int =
        tasks.count { it.done && it.completedAt != null && it.due != null }

    /** Tasks created inside the window — the "in" side of the backlog, against
     * [completedInPeriod]'s "out". Zero for rows predating `createdAt` (DB 27). */
    fun createdInPeriod(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): Int {
        val start = periodStart(period, today)
        return tasks.count { task ->
            val created = task.createdAt?.toLocalDate() ?: return@count false
            !created.isBefore(start) && !created.isAfter(today)
        }
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

    /** Lists were the one organising axis with no breakdown at all, despite sitting alongside
     * projects and tags everywhere else in the app. */
    fun byList(tasks: List<Task>, lists: List<YataList>): List<EntityStat> {
        val grouped = tasks.filter { it.listId != null }.groupBy { it.listId!! }
        return lists.mapNotNull { list ->
            val listTasks = grouped[list.id] ?: return@mapNotNull null
            if (listTasks.isEmpty()) return@mapNotNull null
            EntityStat(list.id, list.name, list.color, listTasks.size, listTasks.count { it.done })
        }.sortedByDescending { it.total }
    }

    // ── Delegation ──────────────────────────────────────────────────────────────────────────

    private fun ageInDays(from: Long?, to: LocalDate): Int? =
        from?.let { ChronoUnit.DAYS.between(it.toLocalDate(), to).toInt().coerceAtLeast(0) }

    /** Days from creation to completion, for tasks that carry both timestamps. */
    private fun turnaroundDays(task: Task): Int? {
        val created = task.createdAt?.toLocalDate() ?: return null
        val completed = task.completedAt?.toLocalDate() ?: return null
        return ChronoUnit.DAYS.between(created, completed).toInt().coerceAtLeast(0)
    }

    /** Median rather than mean: one task that sat for a year shouldn't drag the typical case. */
    private fun medianTurnaround(doneTasks: List<Task>): Int? {
        val values = doneTasks.mapNotNull { turnaroundDays(it) }.sorted()
        if (values.isEmpty()) return null
        return values[values.size / 2]
    }

    private fun oldestOpenAge(tasks: List<Task>, today: LocalDate): Int? =
        tasks.filter { !it.done }.mapNotNull { ageInDays(it.createdAt, today) }.maxOrNull()

    fun completedInPeriod(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): Int {
        val start = periodStart(period, today)
        return tasks.count { task ->
            val completed = task.completedAt?.toLocalDate() ?: return@count false
            !completed.isBefore(start) && !completed.isAfter(today)
        }
    }

    /** Completions in the equal-length window immediately before this one, so throughput can be
     * compared like-for-like. Null for ALL, which has no "previous". */
    fun previousPeriodCompleted(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate = LocalDate.now()): Int? {
        val lengthDays = when (period) {
            AnalyticsPeriod.WEEK -> 7L
            AnalyticsPeriod.MONTH -> 30L
            AnalyticsPeriod.ALL -> return null
        }
        val prevEnd = today.minusDays(lengthDays)
        val prevStart = prevEnd.minusDays(lengthDays - 1)
        return tasks.count { task ->
            val completed = task.completedAt?.toLocalDate() ?: return@count false
            !completed.isBefore(prevStart) && !completed.isAfter(prevEnd)
        }
    }

    /**
     * Per-assignee delegation health. Unlike [byPerson] this is not gated on the person having
     * tasks *in the period* — someone holding nothing but old undated work is exactly the case
     * worth surfacing, and the old filter dropped them entirely. Anyone with no open work and no
     * completions in the window is omitted, since there is genuinely nothing to report.
     */
    fun byDelegation(
        tasks: List<Task>,
        people: List<Person>,
        period: AnalyticsPeriod,
        today: LocalDate = LocalDate.now()
    ): List<DelegationStat> {
        val start = periodStart(period, today)
        val byPerson = tasks.groupByAssignee()
        return people.mapNotNull { person ->
            val theirs = byPerson[person.id] ?: return@mapNotNull null
            val open = theirs.filter { !it.done }
            val completedInPeriod = theirs.count { task ->
                val completed = task.completedAt?.toLocalDate() ?: return@count false
                !completed.isBefore(start) && !completed.isAfter(today)
            }
            if (open.isEmpty() && completedInPeriod == 0) return@mapNotNull null

            val overdue = open.count { task ->
                val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
                due.isBefore(today)
            }
            val judgeable = theirs.filter { it.done && it.completedAt != null && it.due != null }
            val onTimeRate = if (judgeable.isEmpty()) null else {
                judgeable.count { task ->
                    val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@count true
                    !task.completedAt!!.toLocalDate().isAfter(due)
                }.toFloat() / judgeable.size
            }
            val openAges = open.mapNotNull { ageInDays(it.createdAt, today) }
            DelegationStat(
                person = person,
                openCount = open.size,
                overdueCount = overdue,
                completedInPeriod = completedInPeriod,
                onTimeRate = onTimeRate,
                avgOpenAgeDays = if (openAges.isEmpty()) null else openAges.average().toInt(),
                oldestOpenAgeDays = openAges.maxOrNull(),
                medianTurnaroundDays = medianTurnaround(theirs.filter { it.done })
            )
        }.sortedWith(compareByDescending<DelegationStat> { it.overdueCount }.thenByDescending { it.openCount })
    }

    /** Counts tasks, not assignments — a task with three assignees is one delegated task, not
     * three. "Delegated" means *owned* by someone who isn't you (assigneeIds[0] by convention —
     * see [[com.mj.yata.ui.widgets.AssigneeStack]]), not merely carrying your name anywhere in
     * assigneeIds — a task you own but hand a collaborator on stays "self", since you're still
     * the one accountable for it. */
    fun delegationSummary(tasks: List<Task>, people: List<Person>): DelegationSummary {
        val myId = people.firstOrNull { it.isMe }?.id
        val open = tasks.filter { !it.done }
        var delegated = 0
        var self = 0
        var unassigned = 0
        open.forEach { task ->
            val owner = task.assigneeIds.firstOrNull()
            when {
                task.assigneeIds.isEmpty() -> unassigned++
                myId != null && owner != myId -> delegated++
                myId != null -> self++
                else -> delegated++
            }
        }
        val othersWithOpen = people.filter { !it.isMe }.count { person -> open.any { person.id in it.assigneeIds } }
        val othersWithOverdue = people.filter { !it.isMe }.count { person ->
            open.any { task ->
                if (person.id !in task.assigneeIds) return@any false
                val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@any false
                due.isBefore(LocalDate.now())
            }
        }
        return DelegationSummary(delegated, self, unassigned, othersWithOpen, othersWithOverdue)
    }

    // ── Insights ────────────────────────────────────────────────────────────────────────────

    /**
     * The ranked callouts printed above the breakdowns. These exist because the breakdown tables
     * answer "what are the numbers" but not "what should I look at" — with a dozen projects and
     * twenty tags, the one cluster that's actually rotting is a scan away rather than stated.
     * Ordered worst-first and capped, so the section stays a summary rather than another table.
     */
    fun buildInsights(
        tasks: List<Task>,
        delegationStats: List<DelegationStat>,
        summary: DelegationSummary,
        projectStats: List<EntityStat>,
        tagStats: List<EntityStat>,
        listStats: List<EntityStat>,
        today: LocalDate = LocalDate.now(),
        overdueTrend: MetricTrend? = null,
        onTimeRateTrend: MetricTrend? = null,
        createdInPeriod: Int = 0,
        completedInPeriod: Int = 0
    ): List<AnalyticsInsight> {
        val insights = mutableListOf<AnalyticsInsight>()

        // ── Change detection ────────────────────────────────────────────────────────────────
        // These sit above the state-of-the-world callouts below because a number that moved is
        // news and a number that is merely large is not: "9 overdue" may be the same 9 as last
        // month, while "up from 2" is the thing that happened. Each is gated on the movement
        // being material — a one-task drift every week would train the reader to ignore the
        // section, which costs more than the insight is worth.

        overdueTrend?.takeIf { abs(it.delta) >= MATERIAL_OVERDUE_CHANGE }?.let { trend ->
            val nowOverdue = overdueCount(tasks, today)
            insights += if (trend.improved) {
                AnalyticsInsight(
                    headline = "Overdue down ${abs(trend.delta)} this period",
                    detail = "$nowOverdue still overdue, from ${nowOverdue - trend.delta} a period ago",
                    severity = InsightSeverity.GOOD,
                    searchFilter = if (nowOverdue > 0) SEARCH_FILTER_OVERDUE else null
                )
            } else {
                AnalyticsInsight(
                    headline = "Overdue up ${trend.delta} this period",
                    detail = "$nowOverdue overdue now, from ${nowOverdue - trend.delta} a period ago",
                    severity = InsightSeverity.WARN,
                    searchFilter = SEARCH_FILTER_OVERDUE
                )
            }
        }

        onTimeRateTrend?.takeIf { abs(it.delta) >= MATERIAL_ON_TIME_CHANGE_POINTS }?.let { trend ->
            insights += AnalyticsInsight(
                headline = if (trend.improved) {
                    "Finishing on time ${trend.delta} points more often"
                } else {
                    "Finishing on time ${abs(trend.delta)} points less often"
                },
                detail = "Compared with the previous period",
                severity = if (trend.improved) InsightSeverity.GOOD else InsightSeverity.WARN
            )
        }

        // Only when creation dates exist to compare (DB 27); on an older database createdInPeriod
        // is 0 and this would read as "everything is shrinking" when nothing was recorded.
        if (createdInPeriod > 0) {
            val net = completedInPeriod - createdInPeriod
            if (abs(net) >= MATERIAL_BACKLOG_CHANGE) {
                insights += if (net < 0) {
                    AnalyticsInsight(
                        headline = "Taking on work faster than finishing it",
                        detail = "$createdInPeriod created vs $completedInPeriod done this period",
                        severity = InsightSeverity.WARN
                    )
                } else {
                    AnalyticsInsight(
                        headline = "Backlog shrank by $net this period",
                        detail = "$completedInPeriod done vs $createdInPeriod created",
                        severity = InsightSeverity.GOOD
                    )
                }
            }
        }

        // Whoever is furthest behind, by count rather than ratio — one overdue out of one task is
        // a worse ratio than eight out of twenty but not the thing to act on first.
        delegationStats.firstOrNull { it.overdueCount > 0 }?.let { worst ->
            insights += AnalyticsInsight(
                headline = "${worst.person.name} has ${worst.overdueCount} overdue",
                detail = "of ${worst.openCount} open " +
                    (worst.oldestOpenAgeDays?.let { "· oldest is $it days old" } ?: "tasks"),
                severity = InsightSeverity.WARN,
                searchFilter = SEARCH_FILTER_OVERDUE
            )
        }

        // Workload imbalance, stated as a multiple of the median so it reads as "is this fair"
        // rather than needing the reader to compare two raw counts.
        val openCounts = delegationStats.map { it.openCount }.filter { it > 0 }.sorted()
        if (openCounts.size >= 3) {
            val median = openCounts[openCounts.size / 2]
            val top = delegationStats.maxByOrNull { it.openCount }
            if (top != null && median > 0 && top.openCount >= median * 2) {
                val multiple = top.openCount.toFloat() / median
                insights += AnalyticsInsight(
                    headline = "${top.person.name} is carrying ${"%.1f".format(multiple)}× the median",
                    detail = "${top.openCount} open vs a team median of $median",
                    severity = InsightSeverity.WARN
                )
            }
        }

        if (summary.unassignedOpen > 0) {
            insights += AnalyticsInsight(
                headline = "${summary.unassignedOpen} open ${if (summary.unassignedOpen == 1) "task has" else "tasks have"} no assignee",
                detail = summary.delegationRate?.let { "${(it * 100).toInt()}% of open work is delegated" }
                    ?: "Nothing is assigned yet",
                severity = InsightSeverity.NEUTRAL
            )
        }

        // The worst-performing grouping across all three organising axes, so the callout points at
        // whichever axis actually explains the problem rather than always naming a project.
        val worstCluster = (projectStats.map { "Project" to it } +
            tagStats.map { "Tag" to it } +
            listStats.map { "List" to it })
            .filter { (_, stat) -> stat.total >= 3 && stat.total - stat.done > 0 }
            .maxByOrNull { (_, stat) -> (stat.total - stat.done).toFloat() / stat.total }
        if (worstCluster != null) {
            val (kind, stat) = worstCluster
            val open = stat.total - stat.done
            insights += AnalyticsInsight(
                headline = "${stat.name} is the least-finished $kind".lowercase()
                    .replaceFirstChar { it.uppercase() },
                detail = "$open of ${stat.total} still open",
                severity = InsightSeverity.NEUTRAL
            )
        }

        val staleOpen = tasks.count { !it.done && (ageInDays(it.createdAt, today) ?: 0) >= 30 }
        if (staleOpen > 0) {
            insights += AnalyticsInsight(
                headline = "$staleOpen open ${if (staleOpen == 1) "task is" else "tasks are"} over 30 days old",
                detail = "Created a month or more ago and still not done",
                severity = InsightSeverity.WARN
            )
        }

        // A habit rather than a problem, so it ranks below everything actionable and only ever
        // appears on a quiet week — which is exactly when there's room to notice it.
        weekdayPattern(tasks)?.let { pattern ->
            val dayName = pattern.day.getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale.getDefault()
            )
            insights += AnalyticsInsight(
                headline = "${dayName}s are when things get finished",
                detail = "${(pattern.shareOfTotal * 100).roundToInt()}% of completions land on that day",
                severity = InsightSeverity.NEUTRAL
            )
        }

        if (insights.isEmpty() && tasks.any { !it.done }) {
            insights += AnalyticsInsight(
                headline = "Nothing overdue",
                detail = "Every open task is still within its due date",
                severity = InsightSeverity.GOOD
            )
        }
        return insights.take(4)
    }
}
