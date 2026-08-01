package com.mj.yata

import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.isActionableToday
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [isActionableToday], the predicate the Today tab, the today/progress/upcoming home-screen
 * widgets all share. They each had their own copy of this filter and drifted: the widgets kept
 * showing tasks the app had already hidden from Today. A test here is cheaper than catching that
 * again on a launcher.
 */
class ActionableTodayTest {

    private val today = "2026-08-01"
    private val now = 1_785_000_000_000L
    private val me = "p_me"

    private fun task(
        due: String? = today,
        startDate: String? = null,
        done: Boolean = false,
        assigneeIds: List<String> = emptyList(),
        followUpAt: Long? = null
    ) = Task(
        id = "t1",
        title = "T",
        listId = null,
        projectId = null,
        section = "",
        due = due,
        startDate = startDate,
        time = null,
        reminder = null,
        priority = "none",
        flag = false,
        done = done,
        assigneeIds = assigneeIds,
        tagIds = emptyList(),
        recurrence = null,
        subtasks = emptyList(),
        notes = null,
        followUpAt = followUpAt
    )

    @Test
    fun dueToday_isActionable() {
        assertTrue(task().isActionableToday(today, now, me))
    }

    @Test
    fun overdue_isActionable() {
        // "Today" means today-or-overdue everywhere in this app, not just an exact date match.
        assertTrue(task(due = "2026-07-20").isActionableToday(today, now, me))
    }

    @Test
    fun futureDue_isNotActionable() {
        assertFalse(task(due = "2026-08-05").isActionableToday(today, now, me))
    }

    @Test
    fun noDueDate_isNotActionable() {
        assertFalse(task(due = null).isActionableToday(today, now, me))
    }

    @Test
    fun deferredByFutureStartDate_isNotActionable() {
        // Due (even overdue) but not yet startable — the case that keeps a backlog item out of
        // the day view until its start date lands.
        assertFalse(task(due = today, startDate = "2026-08-10").isActionableToday(today, now, me))
    }

    @Test
    fun startDateAlreadyPassed_isActionable() {
        assertTrue(task(due = today, startDate = "2026-07-01").isActionableToday(today, now, me))
    }

    @Test
    fun delegatedWithFutureFollowUp_isNotActionable() {
        val t = task(assigneeIds = listOf("p_other"), followUpAt = now + 86_400_000L)
        assertFalse(t.isActionableToday(today, now, me))
    }

    @Test
    fun delegatedWithElapsedFollowUp_isActionableAgain() {
        // Un-snoozes itself once the date passes — no background job re-enables it.
        val t = task(assigneeIds = listOf("p_other"), followUpAt = now - 1)
        assertTrue(t.isActionableToday(today, now, me))
    }

    @Test
    fun ownTaskWithFutureFollowUp_staysActionable() {
        // Waiting-on only applies to work owned by someone else. A follow-up on your own task
        // doesn't excuse you from it today.
        val t = task(assigneeIds = listOf(me), followUpAt = now + 86_400_000L)
        assertTrue(t.isActionableToday(today, now, me))
    }

    @Test
    fun completedTask_stillMatches_soCompletedSectionsKeepWorking() {
        // Neither deferral nor waiting-on applies to a done task; callers that want only open
        // work filter on `done` themselves, and the Today tab needs completed ones to render its
        // Completed section.
        assertTrue(task(done = true).isActionableToday(today, now, me))
    }
}
