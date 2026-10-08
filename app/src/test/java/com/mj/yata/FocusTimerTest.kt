package com.mj.yata

import com.mj.yata.domain.model.MAX_FOCUS_SESSION_MINUTES
import com.mj.yata.domain.model.Task
import com.mj.yata.util.TrackedVsEstimate
import com.mj.yata.domain.model.focusSessionMinutes
import com.mj.yata.domain.model.isDelegated
import com.mj.yata.util.AnalyticsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusTimerTest {

    private val start = 1_000_000L

    @Test
    fun roundsToNearestMinute() {
        assertEquals(0, focusSessionMinutes(start, start + 29_000))
        assertEquals(1, focusSessionMinutes(start, start + 30_000))
        assertEquals(25, focusSessionMinutes(start, start + 25 * 60_000 + 10_000))
    }

    @Test
    fun clockGoingBackwardsLogsNothing() {
        assertEquals(0, focusSessionMinutes(start, start - 60_000))
    }

    @Test
    fun capsAForgottenTimer() {
        assertEquals(MAX_FOCUS_SESSION_MINUTES, focusSessionMinutes(start, start + 3L * 24 * 60 * 60_000))
    }

    @Test
    fun trackedVsEstimateCountsOnlyTasksWithBoth() {
        fun task(tracked: Int, estimate: Int?) = Task(
            id = "t$tracked$estimate", title = "t", listId = null, projectId = null, section = "", due = null,
            time = null, reminder = null, priority = "none", flag = false, done = true,
            assigneeIds = emptyList(), tagIds = emptyList(), recurrence = null, subtasks = emptyList(), notes = null,
            estimateMinutes = estimate, trackedMinutes = tracked
        )
        assertNull(AnalyticsUtils.trackedVsEstimate(listOf(task(30, null), task(0, 60))))
        assertEquals(
            TrackedVsEstimate(trackedMinutes = 90, estimatedMinutes = 60),
            AnalyticsUtils.trackedVsEstimate(listOf(task(40, 30), task(50, 30), task(20, null)))
        )
    }

    @Test
    fun timerIsOnlyForTasksThatAreMine() {
        fun task(vararg assignees: String) = Task(
            id = "t", title = "t", listId = null, projectId = null, section = "", due = null,
            time = null, reminder = null, priority = "none", flag = false, done = false,
            assigneeIds = assignees.toList(), tagIds = emptyList(), recurrence = null, subtasks = emptyList(), notes = null
        )
        assertEquals(false, task().isDelegated("me"))
        assertEquals(false, task("me", "p2").isDelegated("me"))
        assertEquals(true, task("p2").isDelegated("me"))
        assertEquals(true, task("p2").isDelegated(null))
    }
}
