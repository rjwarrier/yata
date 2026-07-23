package com.mj.yata.util

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

    fun recurrenceSummary(r: Recurrence?): String {
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
            val isWeekdays = sorted.size == 5 && !sorted.contains("SA") && !sorted.contains("SU")
            val isWeekends = sorted.size == 2 && sorted.contains("SA") && sorted.contains("SU")

            base += when {
                isWeekdays -> " on weekdays"
                isWeekends -> " on weekends"
                else -> " on " + sorted.joinToString(", ") { DAY_LABEL[it] ?: it }
            }
        }

        if (r.freq == "monthly" && r.bymonthday != null) {
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
        if (r.freq == "monthly" && r.bymonthday != null) {
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
                if (r.bymonthday == -1) {
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

    private fun getOrdinal(n: Int): String {
        val suffixes = listOf("th", "st", "nd", "rd")
        val v = n % 100
        return "$n" + (suffixes.getOrNull((v - 20) % 10) ?: suffixes.getOrNull(v) ?: suffixes[0])
    }
}
