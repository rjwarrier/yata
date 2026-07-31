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

    private fun task(
        id: String,
        done: Boolean = false,
        due: String? = null,
        completedOn: LocalDate? = null,
        createdOn: LocalDate? = null,
        assignees: List<String> = emptyList(),
        listId: String? = null,
        priority: String = "none"
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
        notes = null
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
}
