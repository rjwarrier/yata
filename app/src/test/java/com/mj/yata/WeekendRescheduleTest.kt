package com.mj.yata

import com.mj.yata.domain.model.DEFAULT_WEEKEND_DAYS
import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.isRescheduledToWeekend
import com.mj.yata.domain.model.isWeekendDate
import com.mj.yata.domain.model.nextBusinessDay
import com.mj.yata.domain.model.previousBusinessDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekendRescheduleTest {

    // 2026-08-17 is a Monday; 2026-08-21 Friday; 2026-08-22 Saturday; 2026-08-23 Sunday.

    @Test
    fun defaultWeekendIsSaturdayAndSunday() {
        assertTrue(isWeekendDate(LocalDate.parse("2026-08-22"), DEFAULT_WEEKEND_DAYS))
        assertTrue(isWeekendDate(LocalDate.parse("2026-08-23"), DEFAULT_WEEKEND_DAYS))
        assertFalse(isWeekendDate(LocalDate.parse("2026-08-21"), DEFAULT_WEEKEND_DAYS))
    }

    @Test
    fun emptyWeekendDaysMeansNothingIsAWeekend() {
        assertFalse(isWeekendDate(LocalDate.parse("2026-08-22"), emptySet()))
        assertFalse(isWeekendDate(LocalDate.parse("2026-08-23"), emptySet()))
    }

    @Test
    fun customWeekendDaysAreHonored() {
        // A Friday-Saturday weekend, as used in several countries.
        val friSat = setOf("FR", "SA")
        assertTrue(isWeekendDate(LocalDate.parse("2026-08-21"), friSat))
        assertTrue(isWeekendDate(LocalDate.parse("2026-08-22"), friSat))
        assertFalse(isWeekendDate(LocalDate.parse("2026-08-23"), friSat))
    }

    @Test
    fun rescheduleToWeekendFiresOnlyWhenDueActuallyChanges() {
        // Moved from a weekday onto the weekend: warn.
        assertTrue(isRescheduledToWeekend("2026-08-21", "2026-08-22", DEFAULT_WEEKEND_DAYS))
        // Re-saving a task already due on the weekend, due date untouched: must not warn.
        assertFalse(isRescheduledToWeekend("2026-08-22", "2026-08-22", DEFAULT_WEEKEND_DAYS))
        // Moved between two weekdays: no warning.
        assertFalse(isRescheduledToWeekend("2026-08-17", "2026-08-18", DEFAULT_WEEKEND_DAYS))
    }

    @Test
    fun rescheduleToWeekendFiresInEitherDirection() {
        // Postponed later, onto the weekend.
        assertTrue(isRescheduledToWeekend("2026-08-17", "2026-08-22", DEFAULT_WEEKEND_DAYS))
        // Pulled earlier, onto the weekend -- still a warning, unlike the postponement counter,
        // which only tracks forward moves.
        assertTrue(isRescheduledToWeekend("2026-08-25", "2026-08-23", DEFAULT_WEEKEND_DAYS))
    }

    @Test
    fun noPreviousDueDateStillWarnsOnFirstScheduleToWeekend() {
        assertTrue(isRescheduledToWeekend(null, "2026-08-22", DEFAULT_WEEKEND_DAYS))
    }

    @Test
    fun clearingOrMalformedDueDateNeverWarns() {
        assertFalse(isRescheduledToWeekend("2026-08-21", null, DEFAULT_WEEKEND_DAYS))
        assertFalse(isRescheduledToWeekend("2026-08-21", "not-a-date", DEFAULT_WEEKEND_DAYS))
    }

    // --- nextBusinessDay: backs the "Next business day" quick-snooze preset ---

    @Test
    fun nextBusinessDaySkipsPastAWeekendStartingOnIt() {
        // Starting on the Saturday itself should land on Monday, not stay put.
        assertEquals(LocalDate.parse("2026-08-24"), nextBusinessDay(LocalDate.parse("2026-08-22"), DEFAULT_WEEKEND_DAYS, emptyList()))
    }

    @Test
    fun nextBusinessDayReturnsAnOrdinaryWeekdayUnchanged() {
        assertEquals(LocalDate.parse("2026-08-18"), nextBusinessDay(LocalDate.parse("2026-08-18"), DEFAULT_WEEKEND_DAYS, emptyList()))
    }

    @Test
    fun nextBusinessDaySkipsAHolidayThatIsNotAWeekend() {
        // 2026-08-18 is a Tuesday -- a holiday there, with no weekend involved, still skips.
        val holiday = Holiday("2026-08-18", "Made-up Holiday", recurring = false)
        assertEquals(
            LocalDate.parse("2026-08-19"),
            nextBusinessDay(LocalDate.parse("2026-08-18"), DEFAULT_WEEKEND_DAYS, listOf(holiday))
        )
    }

    @Test
    fun nextBusinessDaySkipsARecurringHolidayAcrossYears() {
        // Recurring holiday anchored in a different year must still be honored via month-day match.
        val recurringHoliday = Holiday("2020-08-18", "Made-up Recurring Holiday", recurring = true)
        assertEquals(
            LocalDate.parse("2026-08-19"),
            nextBusinessDay(LocalDate.parse("2026-08-18"), DEFAULT_WEEKEND_DAYS, listOf(recurringHoliday))
        )
    }

    @Test
    fun nextBusinessDaySkipsConsecutiveHolidayAndWeekend() {
        // Friday 2026-08-21 is a holiday, Saturday/Sunday follow -- should land on Monday 2026-08-24.
        val holiday = Holiday("2026-08-21", "Bridge Day", recurring = false)
        assertEquals(
            LocalDate.parse("2026-08-24"),
            nextBusinessDay(LocalDate.parse("2026-08-21"), DEFAULT_WEEKEND_DAYS, listOf(holiday))
        )
    }

    @Test
    fun nextBusinessDayHonorsCustomWeekendDays() {
        val friSat = setOf("FR", "SA")
        assertEquals(
            LocalDate.parse("2026-08-23"),
            nextBusinessDay(LocalDate.parse("2026-08-21"), friSat, emptyList())
        )
    }

    @Test
    fun nextBusinessDayTerminatesEvenWhenEveryDayIsMarkedWeekend() {
        val everyDay = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        val result = nextBusinessDay(LocalDate.parse("2026-08-18"), everyDay, emptyList())
        // No real answer exists for this pathological config; just must not hang or throw.
        assertEquals(LocalDate.parse("2026-08-18").plusDays(366), result)
    }

    // --- previousBusinessDay: backs Task.effectiveDue's "observe non-working days" setting ---

    @Test
    fun previousBusinessDaySkipsBackPastAWeekendStartingOnIt() {
        // Starting on the Saturday itself should land on the preceding Friday.
        assertEquals(LocalDate.parse("2026-08-21"), previousBusinessDay(LocalDate.parse("2026-08-22"), DEFAULT_WEEKEND_DAYS, emptyList()))
    }

    @Test
    fun previousBusinessDayReturnsAnOrdinaryWeekdayUnchanged() {
        assertEquals(LocalDate.parse("2026-08-18"), previousBusinessDay(LocalDate.parse("2026-08-18"), DEFAULT_WEEKEND_DAYS, emptyList()))
    }

    @Test
    fun previousBusinessDaySkipsAHolidayThatIsNotAWeekend() {
        // 2026-08-18 is a Tuesday.
        val holiday = Holiday("2026-08-18", "Made-up Holiday", recurring = false)
        assertEquals(
            LocalDate.parse("2026-08-17"),
            previousBusinessDay(LocalDate.parse("2026-08-18"), DEFAULT_WEEKEND_DAYS, listOf(holiday))
        )
    }

    @Test
    fun previousBusinessDaySkipsConsecutiveWeekendAndHoliday() {
        // Monday 2026-08-24 is a holiday, preceded by Saturday/Sunday -- should land on Friday 2026-08-21.
        val holiday = Holiday("2026-08-24", "Bridge Day", recurring = false)
        assertEquals(
            LocalDate.parse("2026-08-21"),
            previousBusinessDay(LocalDate.parse("2026-08-24"), DEFAULT_WEEKEND_DAYS, listOf(holiday))
        )
    }

    @Test
    fun previousBusinessDayTerminatesEvenWhenEveryDayIsMarkedWeekend() {
        val everyDay = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        val result = previousBusinessDay(LocalDate.parse("2026-08-18"), everyDay, emptyList())
        assertEquals(LocalDate.parse("2026-08-18").minusDays(366), result)
    }
}
