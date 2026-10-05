package com.mj.yata

import com.mj.yata.domain.model.Task
import com.mj.yata.domain.usecase.Rescheduled
import com.mj.yata.domain.usecase.scheduleRestorations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RescheduleUndoTest {

    private fun task(
        id: String = "t1",
        title: String = "Pay rent",
        due: String? = "2026-10-02",
        time: String? = "10:00 AM",
        done: Boolean = false,
        completedAt: Long? = null,
        postponementCount: Int = 1
    ) = Task(
        id = id, title = title, listId = null, projectId = null, section = "",
        due = due, time = time, reminder = null, priority = "none", flag = false,
        done = done, completedAt = completedAt, assigneeIds = emptyList(), tagIds = emptyList(),
        recurrence = null, subtasks = emptyList(), notes = null, postponementCount = postponementCount
    )

    private val before = task()
    private val snoozed = before.copy(due = "2026-10-05", time = "9:00 AM", postponementCount = 2)
    private val change = Rescheduled(previous = before, updated = snoozed)

    @Test
    fun restoresTheScheduleAndPostponementCount() {
        val restored = scheduleRestorations(listOf(change), mapOf("t1" to snoozed)).single()
        assertEquals("2026-10-02", restored.due)
        assertEquals("10:00 AM", restored.time)
        assertEquals(1, restored.postponementCount)
    }

    @Test
    fun keepsEditsMadeSinceTheReschedule() {
        val renamed = snoozed.copy(title = "Pay rent (landlord)", notes = "Bank transfer")
        val restored = scheduleRestorations(listOf(change), mapOf("t1" to renamed)).single()
        assertEquals("Pay rent (landlord)", restored.title)
        assertEquals("Bank transfer", restored.notes)
        assertEquals("2026-10-02", restored.due)
    }

    @Test
    fun putsBackACompletedTasksDoneState() {
        val completed = before.copy(done = true, completedAt = 1_000L)
        val reopened = completed.copy(due = "2026-10-05", done = false, completedAt = null)
        val restored = scheduleRestorations(
            listOf(Rescheduled(previous = completed, updated = reopened)),
            mapOf("t1" to reopened)
        ).single()
        assertTrue(restored.done)
        assertEquals(1_000L, restored.completedAt)
    }

    @Test
    fun skipsATaskRescheduledAgainSinceOrGone() {
        val movedAgain = snoozed.copy(due = "2026-10-09")
        assertTrue(scheduleRestorations(listOf(change), mapOf("t1" to movedAgain)).isEmpty())
        assertTrue(scheduleRestorations(listOf(change), emptyMap()).isEmpty())
    }

    @Test
    fun unchangedRescheduleHasNothingToUndo() {
        assertTrue(change.changedSchedule)
        assertTrue(!Rescheduled(previous = before, updated = before).changedSchedule)
    }
}
