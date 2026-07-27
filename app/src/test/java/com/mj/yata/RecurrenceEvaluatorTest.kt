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
    fun testYearlyRecurrence() {
        val r = Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-02")
        assertEquals("2027-07-02", next)
    }

    @Test
    fun testEveryTwoYearsRecurrence() {
        val r = Recurrence("yearly", 2, null, null, RecurrenceEnds.Never)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-02")
        assertEquals("2028-07-02", next)
    }

    @Test
    fun testLeapDayYearlyRecurrenceUsesLocalDateSemantics() {
        val r = Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2024-02-29")
        assertEquals("2025-02-28", next)
    }

    @Test
    fun testRecurrenceSummary() {
        val r = Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
        val summary = RecurrenceEvaluator.recurrenceSummary(r)
        assertEquals("Daily", summary)

        val r2 = Recurrence("weekly", 2, listOf("MO", "WE"), null, RecurrenceEnds.Never)
        val summary2 = RecurrenceEvaluator.recurrenceSummary(r2)
        assertEquals("Every 2 weeks on Mon, Wed", summary2)

        val r3 = Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
        assertEquals("Yearly", RecurrenceEvaluator.recurrenceSummary(r3))

        val r4 = Recurrence("yearly", 2, null, null, RecurrenceEnds.Never)
        assertEquals("Every 2 years", RecurrenceEvaluator.recurrenceSummary(r4))
    }

    @Test
    fun testYearlyRRule() {
        val r = Recurrence("yearly", 2, null, null, RecurrenceEnds.On("2030-07-02"))
        assertEquals("RRULE:FREQ=YEARLY;INTERVAL=2;UNTIL=20300702", RecurrenceEvaluator.toRRULE(r))
    }

    @Test
    fun testCompletionBasedRecurrenceSummary() {
        val r = Recurrence("daily", 3, null, null, RecurrenceEnds.Never, basedOnCompletion = true)
        assertEquals("Every 3 days after completion", RecurrenceEvaluator.recurrenceSummary(r))
    }

    @Test
    fun testCompletionBasedRecurrenceCountsFromLateCompletionDate() {
        // Due 2026-07-02, but finished late on 2026-07-10 — completion-based recurrence
        // counts the interval from the completion date, not the original due date.
        val r = Recurrence("daily", 3, null, null, RecurrenceEnds.Never, basedOnCompletion = true)
        val next = RecurrenceEvaluator.calculateNextOccurrence(r, "2026-07-10")
        assertEquals("2026-07-13", next)
    }
}
