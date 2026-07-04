package com.mj.yata

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.util.RecurrenceEvaluator
import org.junit.Assert.*
import org.junit.Test

class RecurrenceEvaluatorTest {

    @Test
    fun testDailyRecurrence() {
        val r = Recurrence("daily", 2, null, null, RecurrenceEnds.Never)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-02")
        assertEquals("2026-07-04", next)
    }

    @Test
    fun testWeeklyRecurrence() {
        // Mondays and Wednesdays
        val r = Recurrence("weekly", 1, listOf("MO", "WE"), null, RecurrenceEnds.Never)
        
        // 2026-07-02 is a Thursday. Next should be Monday (2026-07-06)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-02")
        assertEquals("2026-07-06", next)
    }

    @Test
    fun testMonthlyRecurrence() {
        val r = Recurrence("monthly", 1, null, 15, RecurrenceEnds.Never)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-02")
        assertEquals("2026-07-15", next)
    }

    @Test
    fun testRecurrenceSummary() {
        val r = Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
        val summary = RecurrenceEvaluator.recurrenceSummary(r)
        assertEquals("Daily", summary)

        val r2 = Recurrence("weekly", 2, listOf("MO", "WE"), null, RecurrenceEnds.Never)
        val summary2 = RecurrenceEvaluator.recurrenceSummary(r2)
        assertEquals("Every 2 weeks on Mon, Wed", summary2)
    }
}
