package com.mj.yata

import com.mj.yata.domain.model.Holiday
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.QuickSnoozeSettings
import com.mj.yata.domain.model.isAvailableAt
import com.mj.yata.domain.model.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class QuickSnoozeScheduleTest {

    private val settings = QuickSnoozeSettings(
        tonightHour = 19, tonightMinute = 30,
        tomorrowHour = 8, tomorrowMinute = 15,
        weekendDays = setOf("SA", "SU")
    )

    // 2026-10-02 is a Friday.
    private val friday = LocalDate.of(2026, 10, 2)

    @Test
    fun tonightAndTomorrowUseTheConfiguredTimes() {
        assertEquals(LocalDateTime.of(2026, 10, 2, 19, 30), QuickSnoozePreset.TONIGHT.resolve(settings, friday))
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 15), QuickSnoozePreset.TOMORROW_MORNING.resolve(settings, friday))
    }

    @Test
    fun nextBusinessDaySkipsTheWeekendAndHolidays() {
        assertEquals(
            LocalDateTime.of(2026, 10, 5, 8, 15),
            QuickSnoozePreset.NEXT_WEEKDAY.resolve(settings, friday)
        )
        val withMondayHoliday = settings.copy(holidays = listOf(Holiday("2026-10-05", "Office closed")))
        assertEquals(
            LocalDateTime.of(2026, 10, 6, 8, 15),
            QuickSnoozePreset.NEXT_WEEKDAY.resolve(withMondayHoliday, friday)
        )
    }

    @Test
    fun tonightIsOfferedOnlyUntilItsTimeHasPassed() {
        assertTrue(QuickSnoozePreset.TONIGHT.isAvailableAt(settings, friday.atTime(19, 29)))
        assertFalse(QuickSnoozePreset.TONIGHT.isAvailableAt(settings, friday.atTime(19, 30)))
        assertFalse(QuickSnoozePreset.TONIGHT.isAvailableAt(settings, friday.atTime(23, 0)))
        assertTrue(QuickSnoozePreset.TOMORROW_MORNING.isAvailableAt(settings, friday.atTime(23, 0)))
        assertTrue(QuickSnoozePreset.NEXT_WEEKDAY.isAvailableAt(settings, friday.atTime(23, 0)))
    }
}
