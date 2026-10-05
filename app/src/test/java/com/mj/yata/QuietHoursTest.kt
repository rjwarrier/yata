package com.mj.yata

import com.mj.yata.util.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class QuietHoursTest {

    private fun millisAt(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun disabledLeavesTriggerUnchanged() {
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(23, 0))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = false, 22, 0, 7, 0)
        assertEquals(trigger, result)
    }

    @Test
    fun outsideOvernightWindowIsUnchanged() {
        // 22:00-07:00 window; a 2pm trigger is well outside it.
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(14, 0))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = true, 22, 0, 7, 0)
        assertEquals(trigger, result)
    }

    @Test
    fun eveningSideOfOvernightWindowDefersToNextMorning() {
        // 22:00-07:00 window; 23:30 is on the evening side, so it defers to 07:00 the next day.
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(23, 30))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = true, 22, 0, 7, 0)
        val expected = millisAt(LocalDate.of(2026, 7, 3), LocalTime.of(7, 0))
        assertEquals(expected, result)
    }

    @Test
    fun earlyMorningSideOfOvernightWindowDefersToSameDayEnd() {
        // 22:00-07:00 window; 02:00 is on the early-morning side, so it defers to 07:00 that same day.
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(2, 0))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = true, 22, 0, 7, 0)
        val expected = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(7, 0))
        assertEquals(expected, result)
    }

    @Test
    fun sameDayWindowDefersToItsEnd() {
        // A same-day (non-wrapping) 13:00-15:00 window; 14:00 is inside it.
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(14, 0))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = true, 13, 0, 15, 0)
        val expected = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(15, 0))
        assertEquals(expected, result)
    }

    @Test
    fun degenerateWindowIsTreatedAsNoWindow() {
        val trigger = millisAt(LocalDate.of(2026, 7, 2), LocalTime.of(22, 0))
        val result = QuietHours.deferIfWithinQuietHours(trigger, enabled = true, 22, 0, 22, 0)
        assertEquals(trigger, result)
    }
}
