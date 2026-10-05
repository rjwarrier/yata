package com.mj.yata.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Everything a [QuickSnoozePreset] needs to resolve to a concrete date and time: the two
 * configurable snooze times (Settings → Notifications → Quick snooze times) plus the working-day
 * calendar "Next business day" skips over.
 *
 * One resolver, [resolve], is shared by the write path (`TaskOperations`) and the menus that
 * preview each choice's date — so what a menu row says and what tapping it does can't drift.
 */
data class QuickSnoozeSettings(
    val tonightHour: Int = DEFAULT_TONIGHT_HOUR,
    val tonightMinute: Int = 0,
    val tomorrowHour: Int = DEFAULT_TOMORROW_HOUR,
    val tomorrowMinute: Int = 0,
    val weekendDays: Set<String> = DEFAULT_WEEKEND_DAYS,
    val holidays: List<Holiday> = emptyList()
) {
    val tonightTime: LocalTime get() = safeTime(tonightHour, tonightMinute)
    val tomorrowTime: LocalTime get() = safeTime(tomorrowHour, tomorrowMinute)

    companion object {
        const val DEFAULT_TONIGHT_HOUR = 18
        const val DEFAULT_TOMORROW_HOUR = 9

        private fun safeTime(hour: Int, minute: Int): LocalTime =
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
}

/** The date and time [this] preset moves a task to, as of [today]. */
fun QuickSnoozePreset.resolve(settings: QuickSnoozeSettings, today: LocalDate): LocalDateTime =
    when (this) {
        QuickSnoozePreset.TONIGHT -> today.atTime(settings.tonightTime)
        QuickSnoozePreset.TOMORROW_MORNING -> today.plusDays(1).atTime(settings.tomorrowTime)
        QuickSnoozePreset.NEXT_WEEKDAY ->
            nextBusinessDay(today.plusDays(1), settings.weekendDays, settings.holidays)
                .atTime(settings.tomorrowTime)
    }

/**
 * False only for "Tonight" once tonight's configured time has already passed — snoozing to it then
 * would land the task in the past, immediately overdue, which is the opposite of what snoozing is
 * for. Every other preset always resolves to a future day.
 */
fun QuickSnoozePreset.isAvailableAt(settings: QuickSnoozeSettings, now: LocalDateTime): Boolean =
    this != QuickSnoozePreset.TONIGHT || now.toLocalTime().isBefore(settings.tonightTime)
