package com.mj.yata.util

import java.time.LocalTime

/**
 * Whether the current time falls in the dark portion of a fixed daily schedule. Handles the
 * overnight-wrap case (e.g. start=21:00, end=07:00 spans midnight) as well as a same-day span
 * (e.g. start=07:00, end=21:00 would mean dark during the day, which is unusual but valid).
 */
fun isDarkNow(start: LocalTime, end: LocalTime, now: LocalTime = LocalTime.now()): Boolean {
    return if (start <= end) {
        now >= start && now < end
    } else {
        now >= start || now < end
    }
}
