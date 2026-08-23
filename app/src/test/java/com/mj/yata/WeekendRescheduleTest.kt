package com.mj.yata

import com.mj.yata.domain.model.DEFAULT_WEEKEND_DAYS
import com.mj.yata.domain.model.isRescheduledToWeekend
import com.mj.yata.domain.model.isWeekendDate
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
}
