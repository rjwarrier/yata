package com.mj.yata.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedQuickAdd(
    val title: String,
    val due: String?, // "YYYY-MM-DD", null if nothing matched
    val time: String? // "h:mm a", null if nothing matched
)

/**
 * Rule-based date/time extraction for the quick-add title field. Deliberately doesn't touch
 * #tag/@person tokens — NewTaskSheet's own mention autocomplete already owns that convention
 * (see detectMentionToken in NewTaskSheet.kt), so re-parsing them here would double-handle the
 * same syntax two different ways.
 */
object NaturalLanguageParser {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    private val weekdayNames = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
    )

    private val timeRegex = Regex(
        "\\b(at\\s+)?(\\d{1,2})(:(\\d{2}))?\\s*(am|pm|AM|PM)\\b"
    )
    private val inDaysRegex = Regex("\\bin\\s+(\\d+)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val inWeeksRegex = Regex("\\bin\\s+(\\d+)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("\\bnext\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: String, referenceDate: LocalDate = LocalDate.now()): ParsedQuickAdd {
        var remaining = raw
        var due: LocalDate? = null
        var time: String? = null

        // Time — checked first so "tomorrow 3pm" doesn't have "3" mistaken for a day count.
        timeRegex.find(remaining)?.let { match ->
            val hour = match.groupValues[2].toIntOrNull()
            val minute = match.groupValues[4].toIntOrNull() ?: 0
            val meridiem = match.groupValues[5]
            if (hour != null && hour in 1..12 && minute in 0..59) {
                val hour24 = when {
                    meridiem.equals("am", ignoreCase = true) && hour == 12 -> 0
                    meridiem.equals("pm", ignoreCase = true) && hour != 12 -> hour + 12
                    else -> hour
                }
                time = java.time.LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                remaining = remaining.removeRange(match.range).trim()
            }
        }

        due = when {
            remaining.containsWord("today") -> referenceDate
            remaining.containsWord("tomorrow") || remaining.containsWord("tmrw") -> referenceDate.plusDays(1)
            remaining.contains("next week", ignoreCase = true) -> referenceDate.plusWeeks(1)
            else -> null
        }
        if (due != null) {
            remaining = remaining.removeWordIgnoreCase(
                when {
                    remaining.containsWord("today") -> "today"
                    remaining.containsWord("tmrw") -> "tmrw"
                    remaining.containsWord("tomorrow") -> "tomorrow"
                    else -> "next week"
                }
            )
        }

        if (due == null) {
            inDaysRegex.find(remaining)?.let { match ->
                val n = match.groupValues[1].toIntOrNull()
                if (n != null) {
                    due = referenceDate.plusDays(n.toLong())
                    remaining = remaining.removeRange(match.range).trim()
                }
            }
        }

        if (due == null) {
            inWeeksRegex.find(remaining)?.let { match ->
                val n = match.groupValues[1].toIntOrNull()
                if (n != null) {
                    due = referenceDate.plusWeeks(n.toLong())
                    remaining = remaining.removeRange(match.range).trim()
                }
            }
        }

        if (due == null) {
            nextWeekdayRegex.find(remaining)?.let { match ->
                val day = weekdayNames[match.groupValues[1].lowercase()]
                if (day != null) {
                    var candidate = referenceDate.plusDays(1)
                    while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
                    due = candidate
                    remaining = remaining.removeRange(match.range).trim()
                }
            }
        }

        if (due == null) {
            for ((name, day) in weekdayNames) {
                if (remaining.containsWord(name)) {
                    var candidate = referenceDate.plusDays(1)
                    while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
                    due = candidate
                    remaining = remaining.removeWordIgnoreCase(name)
                    break
                }
            }
        }

        return ParsedQuickAdd(
            title = remaining.replace(Regex("\\s{2,}"), " ").trim(),
            due = due?.toString(),
            time = time
        )
    }

    private fun String.containsWord(word: String): Boolean =
        Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(this)

    private fun String.removeWordIgnoreCase(word: String): String =
        Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).replace(this, "").trim()
}
