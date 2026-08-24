package com.mj.yata.util

import com.mj.yata.domain.model.DEFAULT_WEEKEND_DAYS
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object RecurrenceEvaluator {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD

    private val DAY_ORDER = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val DAY_LABEL = mapOf(
        "MO" to "Mon", "TU" to "Tue", "WE" to "Wed", "TH" to "Thu",
        "FR" to "Fri", "SA" to "Sat", "SU" to "Sun"
    )

    private val DAY_MAP = mapOf(
        "MO" to DayOfWeek.MONDAY,
        "TU" to DayOfWeek.TUESDAY,
        "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY
    )

    /** [weekendDays] defaults to Saturday/Sunday, so every existing caller that hasn't been
     * updated to pass the user's configured weekend days (Settings → Task Defaults → Holidays)
     * compiles and reads exactly as before. Only matters when a weekly recurrence's [byday] is
     * exactly the configured weekend/weekday set — otherwise the days are just listed by name. */
    fun recurrenceSummary(r: Recurrence?, weekendDays: Set<String> = DEFAULT_WEEKEND_DAYS): String {
        if (r == null) return "Does not repeat"
        val n = r.interval
        val unit = when (r.freq) {
            "daily" -> "day"
            "weekly" -> "week"
            "monthly" -> "month"
            "yearly" -> "year"
            else -> "day"
        }

        var base = if (n == 1) {
            when (r.freq) {
                "daily" -> "Daily"
                "weekly" -> "Weekly"
                "monthly" -> "Monthly"
                "yearly" -> "Yearly"
                else -> "Daily"
            }
        } else {
            "Every $n ${unit}s"
        }

        if (r.freq == "weekly" && !r.byday.isNullOrEmpty()) {
            val sorted = r.byday.sortedBy { DAY_ORDER.indexOf(it) }
            val isWeekdays = weekendDays.isNotEmpty() && sorted.toSet() == (DAY_ORDER.toSet() - weekendDays)
            val isWeekends = weekendDays.isNotEmpty() && sorted.toSet() == weekendDays

            base += when {
                isWeekdays -> " on weekdays"
                isWeekends -> " on weekends"
                else -> " on " + sorted.joinToString(", ") { DAY_LABEL[it] ?: it }
            }
        }

        if (r.freq == "monthly" && r.byweekday != null && r.bysetpos != null) {
            val weekdayLabel = DAY_LABEL[r.byweekday] ?: r.byweekday
            base += if (r.bysetpos == -1) " on the last $weekdayLabel" else " on the ${getOrdinal(r.bysetpos)} $weekdayLabel"
        } else if (r.freq == "monthly" && r.bymonthday != null) {
            base += if (r.bymonthday == -1) " on the last day" else " on the ${getOrdinal(r.bymonthday)}"
        }

        when (val ends = r.ends) {
            is RecurrenceEnds.After -> base += " · ${ends.count}×"
            is RecurrenceEnds.On -> base += " · until ${ends.date}"
            else -> { /* Never ends, do nothing */ }
        }

        if (r.basedOnCompletion) {
            base += " after completion"
        }

        return base
    }

    fun toRRULE(r: Recurrence?): String {
        if (r == null) return ""
        val parts = mutableListOf("FREQ=${r.freq.uppercase()}")
        if (r.interval > 1) {
            parts.add("INTERVAL=${r.interval}")
        }
        if (r.freq == "weekly" && !r.byday.isNullOrEmpty()) {
            parts.add("BYDAY=${r.byday.joinToString(",")}")
        }
        if (r.freq == "monthly" && r.byweekday != null && r.bysetpos != null) {
            parts.add("BYDAY=${r.bysetpos}${r.byweekday}")
        } else if (r.freq == "monthly" && r.bymonthday != null) {
            parts.add("BYMONTHDAY=${r.bymonthday}")
        }
        when (val ends = r.ends) {
            is RecurrenceEnds.After -> parts.add("COUNT=${ends.count}")
            is RecurrenceEnds.On -> parts.add("UNTIL=${ends.date.replace("-", "")}")
            else -> { }
        }
        return "RRULE:" + parts.joinToString(";")
    }

    fun calculateNextOccurrence(r: Recurrence, baseDateStr: String): String? {
        val baseDate = try {
            LocalDate.parse(baseDateStr, dateFormatter)
        } catch (e: Exception) {
            LocalDate.now()
        }

        val interval = r.interval.coerceAtLeast(1)

        val nextDate: LocalDate = when (r.freq) {
            "daily" -> baseDate.plusDays(interval.toLong())
            "yearly" -> baseDate.plusYears(interval.toLong())
            "monthly" -> {
                if (r.byweekday != null && r.bysetpos != null) {
                    nextMonthlyWeekdayOccurrence(baseDate, r.byweekday, r.bysetpos, interval)
                } else if (r.bymonthday == -1) {
                    // Last day of month.
                    val thisMonthLastDay = baseDate.withDayOfMonth(baseDate.lengthOfMonth())
                    if (baseDate.isBefore(thisMonthLastDay)) {
                        thisMonthLastDay
                    } else {
                        val nextMonth = baseDate.plusMonths(interval.toLong())
                        nextMonth.withDayOfMonth(nextMonth.lengthOfMonth())
                    }
                } else if (r.bymonthday != null) {
                    val day = r.bymonthday.coerceIn(1, 31)
                    if (baseDate.dayOfMonth < day) {
                        val maxDays = baseDate.lengthOfMonth()
                        baseDate.withDayOfMonth(day.coerceAtMost(maxDays))
                    } else {
                        val nextMonth = baseDate.plusMonths(interval.toLong())
                        val maxDays = nextMonth.lengthOfMonth()
                        nextMonth.withDayOfMonth(day.coerceAtMost(maxDays))
                    }
                } else {
                    baseDate.plusMonths(interval.toLong())
                }
            }
            "weekly" -> {
                val byday = r.byday
                if (byday.isNullOrEmpty()) {
                    baseDate.plusWeeks(interval.toLong())
                } else {
                    // Loop forward day by day to find the next matching day
                    var candidate = baseDate.plusDays(1)
                    var found = false
                    val targetDays = byday.mapNotNull { DAY_MAP[it] }.toSet()

                    // Cap loop at 5 years (to prevent infinite loops)
                    for (i in 1..1825) {
                        val candidateDayOfWeek = candidate.dayOfWeek
                        if (targetDays.contains(candidateDayOfWeek)) {
                            // Verify interval matching week
                            val startMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val candidateMonday = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val weeksBetween = ChronoUnit.WEEKS.between(startMonday, candidateMonday)
                            
                            if (weeksBetween % interval == 0L) {
                                found = true
                                break
                            }
                        }
                        candidate = candidate.plusDays(1)
                    }
                    if (found) candidate else baseDate.plusWeeks(interval.toLong())
                }
            }
            else -> baseDate.plusDays(1)
        }

        // Check if nextDate violates end conditions
        when (val ends = r.ends) {
            is RecurrenceEnds.On -> {
                val endDate = try {
                    LocalDate.parse(ends.date, dateFormatter)
                } catch (e: Exception) {
                    null
                }
                if (endDate != null && nextDate.isAfter(endDate)) {
                    return null // recurrence ended
                }
            }
            is RecurrenceEnds.After -> {
                if (ends.count <= 1) {
                    return null // no occurrences left
                }
            }
            else -> { }
        }

        return nextDate.format(dateFormatter)
    }

    /** [completions] must be sorted newest-first (as `getCompletedTasksBySeriesId` already
     * returns them). Counts consecutive on-time completions from the most recent one back —
     * "on-time" meaning completed on or before the due date it was completed against — stopping
     * at the first late (or undated) completion. */
    fun computeStreak(completions: List<Task>): Int {
        var streak = 0
        for (task in completions) {
            val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: break
            val completedDate = task.completedAt
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                ?: break
            if (completedDate.isAfter(due)) break
            streak++
        }
        return streak
    }

    /** The nth occurrence of [weekday] in [month], or the last one when [setPos] is -1. [setPos]
     * outside 1..4 (or -1) is clamped to 4, since a 5th occurrence doesn't exist in every month —
     * a recurrence saved when a month happened to have 5 Fridays would otherwise silently produce
     * no date at all the next time that weekday only occurs 4 times. */
    private fun nthWeekdayOfMonth(month: YearMonth, weekday: DayOfWeek, setPos: Int): LocalDate =
        if (setPos == -1) {
            month.atEndOfMonth().with(TemporalAdjusters.lastInMonth(weekday))
        } else {
            month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(setPos.coerceIn(1, 4), weekday))
        }

    private fun nextMonthlyWeekdayOccurrence(baseDate: LocalDate, weekdayCode: String, setPos: Int, interval: Int): LocalDate {
        val weekday = DAY_MAP[weekdayCode] ?: return baseDate.plusMonths(interval.toLong())
        val currentMonth = YearMonth.from(baseDate)
        val candidateThisMonth = nthWeekdayOfMonth(currentMonth, weekday, setPos)
        return if (candidateThisMonth.isAfter(baseDate)) {
            candidateThisMonth
        } else {
            nthWeekdayOfMonth(currentMonth.plusMonths(interval.toLong()), weekday, setPos)
        }
    }

    private fun getOrdinal(n: Int): String {
        val suffixes = listOf("th", "st", "nd", "rd")
        val v = n % 100
        return "$n" + (suffixes.getOrNull((v - 20) % 10) ?: suffixes.getOrNull(v) ?: suffixes[0])
    }
}
