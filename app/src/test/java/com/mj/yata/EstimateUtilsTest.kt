package com.mj.yata

import com.mj.yata.domain.model.Task
import com.mj.yata.util.EstimateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstimateUtilsTest {

    private fun task(estimateMinutes: Int?) = Task(
        id = "t",
        title = "T",
        listId = null,
        projectId = null,
        section = "",
        due = null,
        time = null,
        reminder = null,
        priority = "none",
        flag = false,
        done = false,
        assigneeIds = emptyList(),
        tagIds = emptyList(),
        recurrence = null,
        subtasks = emptyList(),
        notes = null,
        estimateMinutes = estimateMinutes
    )

    @Test
    fun formatsMinutesOnly() {
        assertEquals("5m", EstimateUtils.format(5))
        assertEquals("45m", EstimateUtils.format(45))
    }

    @Test
    fun formatsWholeHoursWithoutATrailingZeroMinutes() {
        // "2h", not "2h 0m" — the trailing unit reads as precision the estimate doesn't have.
        assertEquals("1h", EstimateUtils.format(60))
        assertEquals("4h", EstimateUtils.format(240))
    }

    @Test
    fun formatsMixedHoursAndMinutes() {
        assertEquals("2h 30m", EstimateUtils.format(150))
        assertEquals("1h 5m", EstimateUtils.format(65))
    }

    @Test
    fun formatsNonPositiveAsZeroMinutes() {
        assertEquals("0m", EstimateUtils.format(0))
        assertEquals("0m", EstimateUtils.format(-30))
    }

    @Test
    fun plannedMinutesSumsOnlyEstimatedTasks() {
        val tasks = listOf(task(30), task(null), task(60))
        assertEquals(90, EstimateUtils.plannedMinutes(tasks))
    }

    @Test
    fun plannedMinutesIsNullWhenNothingIsEstimated() {
        // The distinction the whole capacity line depends on: a day nobody has estimated must
        // hide the readout, not display a confident "0m planned".
        assertNull(EstimateUtils.plannedMinutes(listOf(task(null), task(null))))
        assertNull(EstimateUtils.plannedMinutes(emptyList()))
    }

    @Test
    fun plannedMinutesTreatsAnExplicitZeroAsAnEstimate() {
        // Someone who deliberately marked a task as no-effort has estimated it, so the readout
        // appears — showing "0m planned" rather than hiding as if nothing were estimated.
        assertEquals(0, EstimateUtils.plannedMinutes(listOf(task(0))))
    }

    @Test
    fun unestimatedCountCountsOnlyNulls() {
        val tasks = listOf(task(30), task(null), task(0), task(null))
        assertEquals(2, EstimateUtils.unestimatedCount(tasks))
    }
}
