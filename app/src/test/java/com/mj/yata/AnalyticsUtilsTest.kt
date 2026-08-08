package com.mj.yata

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the two things the Analytics overhaul changed that are easy to get subtly wrong: what
 * "this period" includes, and the delegation metrics that depend on `createdAt` being nullable.
 */
class AnalyticsUtilsTest {

    private val today: LocalDate = LocalDate.of(2026, 6, 15)

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Anchors weekday-pattern fixtures to a known day, so they don't depend on what weekday
     * `today` happens to be. */
    private fun mondayBefore(date: LocalDate): LocalDate =
        date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    private fun task(
        id: String,
        done: Boolean = false,
        due: String? = null,
        completedOn: LocalDate? = null,
        createdOn: LocalDate? = null,
        assignees: List<String> = emptyList(),
        listId: String? = null,
        priority: String = "none",
        estimate: Int? = null
    ) = Task(
        id = id,
        title = "Task $id",
        listId = listId,
        projectId = null,
        section = "Morning",
        due = due,
        time = null,
        reminder = null,
        priority = priority,
        flag = false,
        done = done,
        completedAt = completedOn?.let { millis(it) },
        createdAt = createdOn?.let { millis(it) },
        assigneeIds = assignees,
        tagIds = emptyList(),
        recurrence = null,
        subtasks = emptyList(),
        notes = null,
        estimateMinutes = estimate
    )

    private val me = Person("me", "Me", "M", "accentA", isMe = true)
    private val bob = Person("bob", "Bob", "B", "accentB")
    private val ann = Person("ann", "Ann", "A", "accentC")

    // ── Period semantics ────────────────────────────────────────────────────────────────────

    @Test
    fun undatedOpenTask_isIncludedInWeek() {
        // The old due-date-based filter dropped this entirely outside All Time, which made
        // delegated work with no agreed date invisible.
        val tasks = listOf(task("t1"))
        val result = AnalyticsUtils.filterTasksByPeriod(tasks, AnalyticsPeriod.WEEK, today)
        assertEquals(1, result.size)
    }

    @Test
    fun taskCompletedInWindow_countsEvenWhenDueOutsideIt() {
        val tasks = listOf(
            task("t1", done = true, due = "2026-01-01", completedOn = today.minusDays(2))
        )
        val result = AnalyticsUtils.filterTasksByPeriod(tasks, AnalyticsPeriod.WEEK, today)
        assertEquals(1, result.size)
    }

    @Test
    fun taskCompletedLongAgo_isExcludedFromWeek() {
        val tasks = listOf(
            task("t1", done = true, due = "2026-01-01", completedOn = today.minusDays(90))
        )
        val result = AnalyticsUtils.filterTasksByPeriod(tasks, AnalyticsPeriod.WEEK, today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun openTaskIsAlwaysInPeriod_regardlessOfHowOld() {
        val tasks = listOf(task("t1", due = "2020-01-01", createdOn = today.minusDays(900)))
        assertEquals(1, AnalyticsUtils.filterTasksByPeriod(tasks, AnalyticsPeriod.WEEK, today).size)
        assertEquals(1, AnalyticsUtils.filterTasksByPeriod(tasks, AnalyticsPeriod.MONTH, today).size)
    }

    // ── Throughput ──────────────────────────────────────────────────────────────────────────

    @Test
    fun completedInPeriod_countsOnlyCompletionsInsideWindow() {
        val tasks = listOf(
            task("a", done = true, completedOn = today),
            task("b", done = true, completedOn = today.minusDays(6)),
            task("c", done = true, completedOn = today.minusDays(8)),
            task("d")
        )
        assertEquals(2, AnalyticsUtils.completedInPeriod(tasks, AnalyticsPeriod.WEEK, today))
    }

    @Test
    fun previousPeriodCompleted_isNullForAllTime() {
        assertNull(AnalyticsUtils.previousPeriodCompleted(emptyList(), AnalyticsPeriod.ALL, today))
    }

    // ── Delegation ──────────────────────────────────────────────────────────────────────────

    @Test
    fun delegationSummary_countsTasksNotAssignments() {
        // One task with two assignees is one delegated task, not two.
        val tasks = listOf(task("t1", assignees = listOf("bob", "ann")))
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob, ann))
        assertEquals(1, summary.delegatedOpen)
        assertEquals(1, summary.totalOpen)
    }

    @Test
    fun delegationSummary_separatesSelfUnassignedAndDelegated() {
        val tasks = listOf(
            task("t1", assignees = listOf("bob")),
            task("t2", assignees = listOf("me")),
            task("t3"),
            task("t4", done = true, assignees = listOf("bob"))  // done — excluded from open counts
        )
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob))
        assertEquals(1, summary.delegatedOpen)
        assertEquals(1, summary.selfOpen)
        assertEquals(1, summary.unassignedOpen)
        assertEquals(3, summary.totalOpen)
    }

    @Test
    fun byDelegation_includesPersonHoldingOnlyUndatedWork() {
        // The pre-existing byPerson dropped anyone with nothing in the period. Someone sitting on
        // undated tasks is precisely who a delegator needs to see.
        val tasks = listOf(task("t1", assignees = listOf("bob")))
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        assertEquals(1, stats.size)
        assertEquals(1, stats.first().openCount)
    }

    @Test
    fun byDelegation_omitsPersonWithNothingOpenAndNoRecentCompletions() {
        val tasks = listOf(task("t1", done = true, assignees = listOf("bob"), completedOn = today.minusDays(60)))
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        assertTrue(stats.isEmpty())
    }

    @Test
    fun byDelegation_countsOverdueAndRanksWorstFirst() {
        val tasks = listOf(
            task("t1", due = "2026-06-01", assignees = listOf("bob")),
            task("t2", due = "2026-06-02", assignees = listOf("bob")),
            task("t3", due = "2026-06-30", assignees = listOf("ann"))
        )
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(ann, bob), AnalyticsPeriod.WEEK, today)
        assertEquals("bob", stats.first().person.id)
        assertEquals(2, stats.first().overdueCount)
        assertEquals(0, stats.last().overdueCount)
    }

    @Test
    fun ageMetrics_areNullWhenCreatedAtIsMissing() {
        // Rows predating DB 27 carry no createdAt; they must be skipped rather than treated as
        // age zero, which would make an old backlog look brand new.
        val tasks = listOf(task("t1", assignees = listOf("bob")))
        val stat = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today).first()
        assertNull(stat.avgOpenAgeDays)
        assertNull(stat.oldestOpenAgeDays)
        assertNull(stat.medianTurnaroundDays)
    }

    @Test
    fun ageMetrics_computeFromCreatedAtWhenPresent() {
        val tasks = listOf(
            task("t1", assignees = listOf("bob"), createdOn = today.minusDays(10)),
            task("t2", assignees = listOf("bob"), createdOn = today.minusDays(30))
        )
        val stat = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today).first()
        assertEquals(20, stat.avgOpenAgeDays)
        assertEquals(30, stat.oldestOpenAgeDays)
    }

    @Test
    fun turnaround_measuresCreationToCompletion() {
        val tasks = listOf(
            task("t1", done = true, assignees = listOf("bob"), createdOn = today.minusDays(10), completedOn = today.minusDays(6))
        )
        val stat = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today).first()
        assertEquals(4, stat.medianTurnaroundDays)
    }

    // ── Lists ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun byList_groupsTasksAndCountsDone() {
        val lists = listOf(YataList("l1", "Work", "accentA", "work"), YataList("l2", "Home", "accentB", "home"))
        val tasks = listOf(
            task("t1", listId = "l1", done = true),
            task("t2", listId = "l1"),
            task("t3", listId = "l2")
        )
        val stats = AnalyticsUtils.byList(tasks, lists)
        assertEquals(2, stats.size)
        val work = stats.first { it.id == "l1" }
        assertEquals(2, work.total)
        assertEquals(1, work.done)
    }

    @Test
    fun byList_omitsEmptyLists() {
        val lists = listOf(YataList("l1", "Work", "accentA", "work"))
        assertTrue(AnalyticsUtils.byList(emptyList(), lists).isEmpty())
    }

    // ── Insights ────────────────────────────────────────────────────────────────────────────

    @Test
    fun insights_flagOverdueAssigneeFirst() {
        val tasks = listOf(task("t1", due = "2026-06-01", assignees = listOf("bob")))
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob))
        val insights = AnalyticsUtils.buildInsights(
            tasks, stats, summary, emptyList(), emptyList(), emptyList(), today
        )
        assertTrue(insights.isNotEmpty())
        assertTrue(insights.first().headline.contains("Bob"))
    }

    @Test
    fun insights_reportCleanStateWhenNothingIsWrong() {
        val tasks = listOf(task("t1", due = "2026-12-01", assignees = listOf("bob")))
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob))
        val insights = AnalyticsUtils.buildInsights(
            tasks, stats, summary, emptyList(), emptyList(), emptyList(), today
        )
        assertEquals(1, insights.size)
        assertEquals("Nothing overdue", insights.first().headline)
    }

    @Test
    fun insights_areCapped() {
        val tasks = (1..40).map {
            task("t$it", due = "2026-01-01", assignees = listOf("bob"), createdOn = today.minusDays(200))
        } + task("u1")
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob))
        val insights = AnalyticsUtils.buildInsights(
            tasks, stats, summary, emptyList(), emptyList(), emptyList(), today
        )
        assertTrue(insights.size <= 4)
    }

    // ── Full pass ───────────────────────────────────────────────────────────────────────────

    @Test
    fun computeUiState_populatesListAndDelegationSections() {
        val lists = listOf(YataList("l1", "Work", "accentA", "work"))
        val tasks = listOf(
            task("t1", listId = "l1", assignees = listOf("bob"), createdOn = today.minusDays(5)),
            task("t2", listId = "l1", done = true, completedOn = today.minusDays(1), createdOn = today.minusDays(4))
        )
        val state = AnalyticsUtils.computeUiState(
            tasks = tasks,
            projects = emptyList(),
            people = listOf(me, bob),
            tags = emptyList(),
            lists = lists,
            period = AnalyticsPeriod.WEEK,
            today = today
        )
        assertEquals(1, state.listStats.size)
        assertEquals(1, state.delegationStats.size)
        assertEquals(1, state.completedInPeriod)
        assertEquals(1, state.delegationSummary.delegatedOpen)
        assertNotNull(state.medianTurnaroundDays)
    }

    // ── Drill-through ───────────────────────────────────────────────────────────────────────

    /**
     * The drill-through constants are matched against `SmartFilter` *by name* when the search
     * route decodes them, and an unrecognized name is silently dropped rather than failing — so
     * a rename on either side would quietly turn "show me those tasks" into "show me everything"
     * with nothing to notice at build time. This is that noticing.
     */
    @Test
    fun searchFilterConstants_matchRealSmartFilterNames() {
        val known = com.mj.yata.ui.screen.search.SmartFilter.entries.map { it.name }
        listOf(
            com.mj.yata.util.SEARCH_FILTER_OVERDUE,
            com.mj.yata.util.SEARCH_FILTER_NO_DUE_DATE,
            com.mj.yata.util.SEARCH_FILTER_HIGH_PRIORITY
        ).forEach { encoded ->
            assertTrue("$encoded is not a SmartFilter name", encoded in known)
        }
    }

    @Test
    fun overdueInsight_carriesTheFilterThatShowsThoseTasks() {
        val tasks = listOf(task("t1", due = "2026-06-01", assignees = listOf("bob")))
        val stats = AnalyticsUtils.byDelegation(tasks, listOf(bob), AnalyticsPeriod.WEEK, today)
        val summary = AnalyticsUtils.delegationSummary(tasks, listOf(me, bob))
        val insights = AnalyticsUtils.buildInsights(
            tasks, stats, summary, emptyList(), emptyList(), emptyList(), today
        )
        assertEquals(com.mj.yata.util.SEARCH_FILTER_OVERDUE, insights.first().searchFilter)
    }

    // ── Trends ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun overdueCountOn_countsWhatWasOpenAndLateThen() {
        val tasks = listOf(
            // Was overdue a week ago, finished since — not overdue now, but was then.
            task("t1", done = true, due = "2026-06-01", completedOn = today.minusDays(2)),
            // Overdue then and still overdue now.
            task("t2", due = "2026-06-01"),
            // Not yet due a week ago.
            task("t3", due = "2026-06-14")
        )
        assertEquals(2, AnalyticsUtils.overdueCountOn(tasks, today.minusDays(6)))
        assertEquals(2, AnalyticsUtils.overdueCountOn(tasks, today))
    }

    /**
     * A task completed before `completedAt` existed has no timestamp to place it in time. Treating
     * that as "never finished" would mark it overdue on every historical day, which used to pin
     * the zero-overdue streak at 0 permanently for anyone with pre-DB-24 history.
     */
    @Test
    fun overdueHistory_ignoresCompletedTasksWithNoTimestamp() {
        val tasks = listOf(task("t1", done = true, due = "2020-01-01"))
        assertEquals(0, AnalyticsUtils.overdueCountOn(tasks, today))
        assertTrue(AnalyticsUtils.zeroOverdueStreak(tasks, today) > 0)
    }

    @Test
    fun overdueTrend_reportsRiseAsNotImproved() {
        val tasks = listOf(
            // Already overdue at the window start, and still is.
            task("t1", due = "2026-06-01"),
            // Only became overdue inside the window.
            task("t2", due = today.minusDays(2).toString()),
            task("t3", due = today.minusDays(1).toString())
        )
        val trend = AnalyticsUtils.overdueTrend(tasks, AnalyticsPeriod.WEEK, today)
        assertNotNull(trend)
        assertEquals(2, trend!!.delta)
        assertTrue(!trend.improved)
    }

    @Test
    fun overdueTrend_isNullForAllTimeWhichHasNoPreviousWindow() {
        val tasks = listOf(task("t1", due = "2026-06-01"))
        assertNull(AnalyticsUtils.overdueTrend(tasks, AnalyticsPeriod.ALL, today))
        assertNull(AnalyticsUtils.onTimeRateTrend(tasks, AnalyticsPeriod.ALL, today))
    }

    @Test
    fun onTimeRateTrend_comparesWindowsAndNeedsBothToBeJudgeable() {
        // Previous window: one late finish (0%). Current window: one on-time finish (100%).
        val tasks = listOf(
            task("old", done = true, due = today.minusDays(12).toString(), completedOn = today.minusDays(9)),
            task("new", done = true, due = today.minusDays(1).toString(), completedOn = today.minusDays(1))
        )
        val trend = AnalyticsUtils.onTimeRateTrend(tasks, AnalyticsPeriod.WEEK, today)
        assertNotNull(trend)
        assertEquals(100, trend!!.delta)
        assertTrue(trend.improved)

        // Drop the previous window's data and there's nothing to compare against.
        val currentOnly = listOf(tasks[1])
        assertNull(AnalyticsUtils.onTimeRateTrend(currentOnly, AnalyticsPeriod.WEEK, today))
    }

    // ── Change-detection insights ───────────────────────────────────────────────────────────

    @Test
    fun insights_callOutAMaterialOverdueRiseButIgnoreDrift() {
        val material = AnalyticsUtils.buildInsights(
            emptyList(), emptyList(), AnalyticsUtils.delegationSummary(emptyList(), emptyList()),
            emptyList(), emptyList(), emptyList(), today,
            overdueTrend = com.mj.yata.util.MetricTrend(delta = 5, improved = false)
        )
        assertTrue(material.any { it.headline.contains("Overdue up 5") })

        val drift = AnalyticsUtils.buildInsights(
            emptyList(), emptyList(), AnalyticsUtils.delegationSummary(emptyList(), emptyList()),
            emptyList(), emptyList(), emptyList(), today,
            overdueTrend = com.mj.yata.util.MetricTrend(delta = 1, improved = false)
        )
        assertTrue(drift.none { it.headline.contains("Overdue up") })
    }

    @Test
    fun insights_warnWhenWorkArrivesFasterThanItLeaves() {
        val insights = AnalyticsUtils.buildInsights(
            emptyList(), emptyList(), AnalyticsUtils.delegationSummary(emptyList(), emptyList()),
            emptyList(), emptyList(), emptyList(), today,
            createdInPeriod = 12,
            completedInPeriod = 4
        )
        assertTrue(insights.any { it.headline.contains("faster than finishing") })
    }

    /** With no creation dates recorded (pre-DB-27 rows) the in/out comparison has no input, and
     * must not read as "nothing came in". */
    @Test
    fun insights_skipBacklogCallOutWhenNothingHasACreationDate() {
        val insights = AnalyticsUtils.buildInsights(
            emptyList(), emptyList(), AnalyticsUtils.delegationSummary(emptyList(), emptyList()),
            emptyList(), emptyList(), emptyList(), today,
            createdInPeriod = 0,
            completedInPeriod = 9
        )
        assertTrue(insights.none { it.headline.contains("Backlog") })
    }

    // ── Capacity ────────────────────────────────────────────────────────────────────────────

    @Test
    fun capacity_sumsOpenEffortAndSplitsOutLateAndImminent() {
        val tasks = listOf(
            task("late", due = today.minusDays(2).toString(), estimate = 30),
            task("soon", due = today.plusDays(3).toString(), estimate = 60),
            task("later", due = today.plusDays(40).toString(), estimate = 90),
            task("undated", estimate = 15),
            // Done work is not effort you still owe.
            task("done", done = true, completedOn = today, estimate = 240)
        )
        val capacity = AnalyticsUtils.capacitySnapshot(tasks, today)
        assertNotNull(capacity)
        assertEquals(30 + 60 + 90 + 15, capacity!!.openMinutes)
        assertEquals(60, capacity.dueNext7Minutes)
        assertEquals(30, capacity.overdueMinutes)
        assertEquals(4, capacity.estimatedOpenCount)
        assertEquals(0, capacity.unestimatedOpenCount)
    }

    /** A confident "0h planned" for a backlog nobody has estimated says the opposite of the
     * truth, so the whole readout has to be absent rather than zero. */
    @Test
    fun capacity_isNullWhenNothingOpenIsEstimated() {
        val tasks = listOf(task("t1"), task("t2", done = true, completedOn = today, estimate = 60))
        assertNull(AnalyticsUtils.capacitySnapshot(tasks, today))
    }

    @Test
    fun capacity_reportsHowMuchOfTheBacklogItCouldNotSee() {
        val tasks = listOf(task("a", estimate = 60), task("b"), task("c"))
        val capacity = AnalyticsUtils.capacitySnapshot(tasks, today)
        assertNotNull(capacity)
        assertEquals(1, capacity!!.estimatedOpenCount)
        assertEquals(2, capacity.unestimatedOpenCount)
    }

    // ── Weekday pattern ─────────────────────────────────────────────────────────────────────

    @Test
    fun weekdayPattern_namesTheDayWorkClustersOn() {
        // 12 completions on Mondays against 4 spread elsewhere — a real lean.
        val mondays = (1..12).map { i ->
            task("m$i", done = true, completedOn = mondayBefore(today).minusWeeks(i.toLong()))
        }
        val others = (1..4).map { i ->
            task("o$i", done = true, completedOn = mondayBefore(today).minusWeeks(i.toLong()).plusDays(2))
        }
        val pattern = AnalyticsUtils.weekdayPattern(mondays + others)
        assertNotNull(pattern)
        assertEquals(java.time.DayOfWeek.MONDAY, pattern!!.day)
        assertEquals(12, pattern.completions)
    }

    /** Nine completions can't establish a weekly habit; naming one would be a statement about
     * noise. */
    @Test
    fun weekdayPattern_staysQuietWithoutEnoughHistory() {
        val tasks = (1..9).map { i ->
            task("t$i", done = true, completedOn = mondayBefore(today).minusWeeks(i.toLong()))
        }
        assertNull(AnalyticsUtils.weekdayPattern(tasks))
    }

    @Test
    fun weekdayPattern_staysQuietWhenCompletionsAreEvenlySpread() {
        // Three completions on each weekday — 21 in total, no day leads.
        val tasks = (0..6).flatMap { dayOffset ->
            (1..3).map { week ->
                task(
                    "d$dayOffset-$week",
                    done = true,
                    completedOn = mondayBefore(today).minusWeeks(week.toLong()).plusDays(dayOffset.toLong())
                )
            }
        }
        assertNull(AnalyticsUtils.weekdayPattern(tasks))
    }

    // ── Sample size ─────────────────────────────────────────────────────────────────────────

    @Test
    fun onTimeRateSampleSize_countsOnlyWhatTheRateCouldJudge() {
        val tasks = listOf(
            task("judgeable", done = true, due = "2026-06-01", completedOn = today),
            // Done but no due date — nothing to be on time against.
            task("noDue", done = true, completedOn = today),
            // Done before completion timestamps existed — no way to place it.
            task("noTimestamp", done = true, due = "2026-06-01"),
            task("open", due = "2026-06-01")
        )
        assertEquals(1, AnalyticsUtils.onTimeRateSampleSize(tasks))
    }

    // ── Daily activity ──────────────────────────────────────────────────────────────────────

    @Test
    fun dailyActivity_countsCreationsAlongsideCompletions() {
        val tasks = listOf(
            task("t1", done = true, completedOn = today.minusDays(1), createdOn = today.minusDays(3)),
            task("t2", createdOn = today.minusDays(1)),
            task("t3", createdOn = today.minusDays(1))
        )
        val days = AnalyticsUtils.dailyActivity(tasks, AnalyticsPeriod.WEEK, today)
        val yesterday = days.single { it.date == today.minusDays(1) }
        assertEquals(1, yesterday.completedCount)
        assertEquals(2, yesterday.createdCount)
        // A task created outside the window still counts on its own day, not the window edge.
        assertEquals(1, days.single { it.date == today.minusDays(3) }.createdCount)
    }

    /** Rows predating the `createdAt` column must read as "not recorded" (zero), not as a
     * creation on some arbitrary day — the chart hides the series entirely on that basis. */
    @Test
    fun dailyActivity_reportsZeroCreatedForTasksWithoutCreatedAt() {
        val tasks = listOf(task("t1", done = true, completedOn = today.minusDays(1)))
        val days = AnalyticsUtils.dailyActivity(tasks, AnalyticsPeriod.WEEK, today)
        assertEquals(0, days.sumOf { it.createdCount })
        assertEquals(1, days.sumOf { it.completedCount })
    }
}
