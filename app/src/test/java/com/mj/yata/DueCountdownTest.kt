package com.mj.yata

import com.mj.yata.util.TaskScheduleUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class DueCountdownTest {

    private val now = LocalDateTime.of(2026, 7, 4, 10, 0)

    @Test
    fun noDueDateHasNoCountdown() {
        assertNull(TaskScheduleUtils.dueCountdown(null, null, now))
        assertNull(TaskScheduleUtils.dueCountdown(null, "3:00 PM", now))
        assertNull(TaskScheduleUtils.dueCountdown("not-a-date", null, now))
    }

    @Test
    fun timedTaskCountsDownToTheMinute() {
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "2h 15m"),
            TaskScheduleUtils.dueCountdown("2026-07-04", "12:15 PM", now)
        )
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "45m"),
            TaskScheduleUtils.dueCountdown("2026-07-04", "10:45 AM", now)
        )
        // Whole hours drop the redundant "0m".
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "3h"),
            TaskScheduleUtils.dueCountdown("2026-07-04", "1:00 PM", now)
        )
    }

    @Test
    fun timedTaskInThePastIsOverdueNotNegative() {
        val result = TaskScheduleUtils.dueCountdown("2026-07-04", "8:30 AM", now)
        assertEquals(TaskScheduleUtils.DueCountdown(isOverdue = true, span = "1h 30m"), result)
        // The magnitude is always rendered unsigned — the direction lives in isOverdue, so a
        // caller never has to strip a "-" back out of the text to display it.
        assertFalse(result!!.span.startsWith("-"))
    }

    @Test
    fun exactlyDueReadsAsUnderAMinuteRatherThanZero() {
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "<1m"),
            TaskScheduleUtils.dueCountdown("2026-07-04", "10:00 AM", now)
        )
    }

    @Test
    fun untimedTaskUsesWholeDaysNotEndOfDaySentinel() {
        // The end-of-day target an untimed task resolves against is an implementation detail; a
        // task due tomorrow must read "1d" all day, not count 37h down to 14h as today wears on.
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "1d"),
            TaskScheduleUtils.dueCountdown("2026-07-05", null, now)
        )
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "1d"),
            TaskScheduleUtils.dueCountdown("2026-07-05", null, now.withHour(23).withMinute(59))
        )
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = true, span = "3d"),
            TaskScheduleUtils.dueCountdown("2026-07-01", null, now)
        )
    }

    @Test
    fun untimedTaskDueTodayHasNoCountdown() {
        // Nothing sub-day to report for a task with no time on it, and the "Due today" badge
        // already carries the fact — a countdown here would only invent precision.
        assertNull(TaskScheduleUtils.dueCountdown("2026-07-04", null, now))
        assertNull(TaskScheduleUtils.dueCountdown("2026-07-04", null, now.withHour(6)))
        assertNull(TaskScheduleUtils.dueCountdown("2026-07-04", null, now.withHour(23)))
    }

    @Test
    fun multiDayTimedTaskShowsDaysAndHours() {
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "2d 5h"),
            TaskScheduleUtils.dueCountdown("2026-07-06", "3:00 PM", now)
        )
        // Exact day multiples drop the redundant "0h".
        assertEquals(
            TaskScheduleUtils.DueCountdown(isOverdue = false, span = "2d"),
            TaskScheduleUtils.dueCountdown("2026-07-06", "10:00 AM", now)
        )
    }

    @Test
    fun countdownCrossesFromPendingToOverdueAsTimePasses() {
        // The bug this whole reworking exists for: the value must be a function of the passed-in
        // clock, so a ticking caller sees it flip rather than freezing at first composition.
        val due = "2026-07-04"
        val time = "10:30 AM"
        assertEquals(false, TaskScheduleUtils.dueCountdown(due, time, now)?.isOverdue)
        assertEquals(false, TaskScheduleUtils.dueCountdown(due, time, now.withMinute(29))?.isOverdue)
        assertEquals(true, TaskScheduleUtils.dueCountdown(due, time, now.withMinute(31))?.isOverdue)
        assertEquals(true, TaskScheduleUtils.dueCountdown(due, time, now.withHour(11))?.isOverdue)
    }
}
