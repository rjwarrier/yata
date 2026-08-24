package com.mj.yata.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object QuietHours {
    /**
     * If quiet hours are enabled and [triggerAtMillis] falls inside the configured
     * [startHour]:[startMinute]-[endHour]:[endMinute] window (local time — the end may be earlier
     * than the start to mean the window spans midnight, e.g. 22:00-07:00), returns the next
     * quiet-hours end time instead so a reminder that would otherwise fire overnight lands the
     * moment quiet hours are over. Otherwise returns [triggerAtMillis] unchanged.
     *
     * A degenerate window (start == end) is treated as no window at all — collapsing start and
     * end would otherwise mean "always" or "never" depending on the wrap direction, and either
     * reading could silently block every reminder from firing.
     */
    fun deferIfWithinQuietHours(
        triggerAtMillis: Long,
        enabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): Long {
        val startOfDay = startHour * 60 + startMinute
        val endOfDay = endHour * 60 + endMinute
        if (!enabled || startOfDay == endOfDay) return triggerAtMillis

        val zone = ZoneId.systemDefault()
        val dateTime = Instant.ofEpochMilli(triggerAtMillis).atZone(zone)
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        val endTime = LocalTime.of(endHour, endMinute)

        val withinWindow: Boolean
        val deferToNextDay: Boolean
        if (startOfDay < endOfDay) {
            withinWindow = minuteOfDay in startOfDay until endOfDay
            deferToNextDay = false
        } else {
            // Overnight wrap, e.g. 22:00-07:00: quiet from start through midnight, then from
            // midnight through end.
            withinWindow = minuteOfDay >= startOfDay || minuteOfDay < endOfDay
            deferToNextDay = minuteOfDay >= startOfDay
        }
        if (!withinWindow) return triggerAtMillis

        val deferredDate = dateTime.toLocalDate().let { if (deferToNextDay) it.plusDays(1) else it }
        return deferredDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
    }
}
