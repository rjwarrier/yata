package com.mj.yata

import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.MAX_HOLIDAY_LABEL_LENGTH
import com.mj.yata.domain.model.rescheduledHolidayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayRescheduleTest {

    private val holidays = listOf(
        Holiday("2026-01-26", "Republic Day"),
        Holiday("2026-10-20", "Diwali")
    )

    @Test
    fun rescheduleOntoAHolidayReturnsItsLabel() {
        assertEquals("Republic Day", rescheduledHolidayLabel("2026-01-20", "2026-01-26", holidays))
    }

    @Test
    fun rescheduleFiresOnlyWhenDueActuallyChanges() {
        // Re-saving a task already due on the holiday, due date untouched: must not warn.
        assertNull(rescheduledHolidayLabel("2026-01-26", "2026-01-26", holidays))
    }

    @Test
    fun rescheduleToAnOrdinaryDateReturnsNull() {
        assertNull(rescheduledHolidayLabel("2026-01-20", "2026-01-21", holidays))
    }

    @Test
    fun noPreviousDueDateStillWarnsOnFirstScheduleToAHoliday() {
        assertEquals("Diwali", rescheduledHolidayLabel(null, "2026-10-20", holidays))
    }

    @Test
    fun clearingDueDateNeverWarns() {
        assertNull(rescheduledHolidayLabel("2026-01-20", null, holidays))
    }

    @Test
    fun emptyHolidayListNeverWarns() {
        assertNull(rescheduledHolidayLabel("2026-01-20", "2026-01-26", emptyList()))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val holiday = Holiday("2026-10-20", "Diwali")
        assertEquals(holiday, Holiday.decode(holiday.encode()))
    }

    @Test
    fun encodeDecodeRoundTripsForRecurringHoliday() {
        val holiday = Holiday("2026-08-15", "Independence Day", recurring = true)
        assertEquals(holiday, Holiday.decode(holiday.encode()))
    }

    @Test
    fun decodeRejectsMalformedInput() {
        assertNull(Holiday.decode("not-encoded"))
        assertNull(Holiday.decode("2026-10-20|"))
        assertNull(Holiday.decode("|Diwali"))
        assertNull(Holiday.decode("2026-10-20|1|"))
    }

    @Test
    fun decodeAcceptsLegacyTwoFieldEncoding() {
        // Predates the recurring flag -- must still load, defaulting to non-recurring.
        val decoded = Holiday.decode("2026-10-20|Diwali")
        assertEquals(Holiday("2026-10-20", "Diwali", recurring = false), decoded)
    }

    @Test
    fun encodePreservesPipeCharactersInLabelByReplacingThem() {
        val holiday = Holiday("2026-10-20", "A | B")
        val decoded = Holiday.decode(holiday.encode())
        assertEquals("A   B", decoded?.label)
    }

    @Test
    fun recurringHolidayMatchesSameMonthDayInAnyYear() {
        val independenceDay = Holiday("2020-08-15", "Independence Day", recurring = true)
        assertTrue(independenceDay.matches("2026-08-15"))
        assertTrue(independenceDay.matches("2031-08-15"))
        assertFalse(independenceDay.matches("2026-08-16"))
    }

    @Test
    fun nonRecurringHolidayRequiresExactDateMatch() {
        val diwali = Holiday("2026-10-20", "Diwali", recurring = false)
        assertTrue(diwali.matches("2026-10-20"))
        assertFalse(diwali.matches("2027-10-20"))
    }

    @Test
    fun rescheduleFindsRecurringHolidayAcrossYears() {
        val independenceDay = Holiday("2020-08-15", "Independence Day", recurring = true)
        assertEquals(
            "Independence Day",
            rescheduledHolidayLabel("2026-08-10", "2026-08-15", listOf(independenceDay))
        )
        assertEquals(
            "Independence Day",
            rescheduledHolidayLabel("2031-08-10", "2031-08-15", listOf(independenceDay))
        )
    }

    // --- Hardening: decode never trusts a raw stored/imported string blindly ---

    @Test
    fun decodeRejectsUnparseableDate() {
        assertNull(Holiday.decode("not-a-date|0|Diwali"))
        assertNull(Holiday.decode("not-a-date|Diwali")) // legacy 2-field form too
    }

    @Test
    fun decodeRejectsCalendarInvalidDate() {
        // Passes a naive digit-shape check but isn't a real calendar date.
        assertNull(Holiday.decode("2026-13-40|0|Diwali"))
        assertNull(Holiday.decode("2026-02-30|0|Diwali"))
    }

    @Test
    fun decodeCapsAnOverlyLongLabel() {
        val huge = "x".repeat(MAX_HOLIDAY_LABEL_LENGTH * 10)
        val decoded = Holiday.decode("2026-10-20|0|$huge")
        assertEquals(MAX_HOLIDAY_LABEL_LENGTH, decoded?.label?.length)
    }

    @Test
    fun encodeCapsAnOverlyLongLabelBeforePersisting() {
        val huge = "x".repeat(MAX_HOLIDAY_LABEL_LENGTH * 10)
        val holiday = Holiday("2026-10-20", huge)
        assertEquals(MAX_HOLIDAY_LABEL_LENGTH, Holiday.decode(holiday.encode())?.label?.length)
    }

    // --- Hardening: dedupe/conflict detection used when adding a holiday ---

    @Test
    fun recurringHolidaysWithSameMonthDayConflictRegardlessOfAnchorYear() {
        val addedIn2020 = Holiday("2020-08-15", "Independence Day", recurring = true)
        val reAddedIn2027 = Holiday("2027-08-15", "Independence Day", recurring = true)
        assertTrue(addedIn2020.conflictsWith(reAddedIn2027))
        assertTrue(reAddedIn2027.conflictsWith(addedIn2020))
    }

    @Test
    fun oneOffHolidayConflictsWithARecurringHolidayItLandsOn() {
        val recurringIndependenceDay = Holiday("2020-08-15", "Independence Day", recurring = true)
        val oneOffOnSameDay = Holiday("2027-08-15", "Company Founding Day", recurring = false)
        assertTrue(recurringIndependenceDay.conflictsWith(oneOffOnSameDay))
        assertTrue(oneOffOnSameDay.conflictsWith(recurringIndependenceDay))
    }

    @Test
    fun unrelatedHolidaysDoNotConflict() {
        val independenceDay = Holiday("2020-08-15", "Independence Day", recurring = true)
        val diwali = Holiday("2026-10-20", "Diwali", recurring = false)
        assertFalse(independenceDay.conflictsWith(diwali))
        assertFalse(diwali.conflictsWith(independenceDay))
    }

    // --- Optimization: index() must agree with the linear matches() scan it replaces ---

    @Test
    fun indexFindsExactAndRecurringMatchesAndMissesCleanly() {
        val republicDay = Holiday("2026-01-26", "Republic Day", recurring = true)
        val diwali = Holiday("2026-10-20", "Diwali", recurring = false)
        val lookup = Holiday.index(listOf(republicDay, diwali))

        assertEquals("Republic Day", lookup("2031-01-26")?.label)
        assertEquals("Diwali", lookup("2026-10-20")?.label)
        assertNull(lookup("2027-10-20")) // Diwali is non-recurring; a different year must miss.
        assertNull(lookup("2026-03-01"))
    }
}
