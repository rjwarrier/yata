package com.mj.yata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** RFC5545 two-letter day codes, matching the vocabulary already used by [Recurrence.byday] and
 * `RecurrenceEvaluator` — reusing it here keeps "weekend days" and "recurs on these days"
 * expressed the same way instead of inventing a second convention. */
val DEFAULT_WEEKEND_DAYS = setOf("SA", "SU")

private val DAY_CODE_BY_DAY_OF_WEEK = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU"
)

/** True when [date]'s day of week is one of [weekendDays] (RFC5545 codes). An empty [weekendDays]
 * — every day unchecked in Settings — means the feature is off; nothing is ever a weekend rather
 * than needing a separate enabled flag. */
fun isWeekendDate(date: LocalDate, weekendDays: Set<String>): Boolean {
    if (weekendDays.isEmpty()) return false
    return DAY_CODE_BY_DAY_OF_WEEK[date.dayOfWeek] in weekendDays
}

/** True when [nextDue] falls on a configured weekend day and actually differs from [previousDue]
 * — re-saving a task that was already due on a weekend, untouched, must not re-warn on every
 * unrelated edit. Unlike [isPostponedLater], this fires for a move in either direction: the
 * concern is where the task landed, not whether it moved later. */
fun isRescheduledToWeekend(previousDue: String?, nextDue: String?, weekendDays: Set<String>): Boolean {
    if (nextDue == null || nextDue == previousDue) return false
    val date = runCatching { LocalDate.parse(nextDue) }.getOrNull() ?: return false
    return isWeekendDate(date, weekendDays)
}

/** [holidays] entry whose [Holiday.date] matches [nextDue], if [nextDue] actually differs from
 * [previousDue] — same "must have moved" guard as [isRescheduledToWeekend]. Returns the label
 * (not just a boolean) since the holiday warning names the holiday, not just the day of week. */
fun rescheduledHolidayLabel(previousDue: String?, nextDue: String?, holidays: List<Holiday>): String? {
    if (nextDue == null || nextDue == previousDue) return null
    return holidays.firstOrNull { it.matches(nextDue) }?.label
}
