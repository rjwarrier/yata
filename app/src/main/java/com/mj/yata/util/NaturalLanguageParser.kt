package com.mj.yata.util

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedQuickAdd(
    val title: String,
    val due: String?, // "YYYY-MM-DD", null if nothing matched
    val time: String?, // "h:mm a", null if nothing matched
    val recurrence: Recurrence?, // null if nothing matched
    val highlightRanges: List<IntRange> // recognized spans in the *original* raw string, for underlining
)

/**
 * Rule-based date/time/recurrence extraction for the quick-add title field. Deliberately
 * doesn't touch #tag/@person tokens — NewTaskSheet's own mention autocomplete already owns
 * that convention (see detectMentionToken in NewTaskSheet.kt), so re-parsing them here would
 * double-handle the same syntax two different ways.
 *
 * Every rule searches the *original* string and records the matched range instead of
 * destructively consuming a shrinking "remaining" copy — that's what lets the caller
 * underline recognized phrases in place before they're stripped out of the saved title.
 * A `claimed` range list prevents two rules from matching overlapping text (e.g. "every
 * sunday" is claimed whole by the recurrence rule, so the later bare-weekday rule doesn't
 * also treat "sunday" as a one-off due date).
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
    private val rruleDay = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU"
    )

    // ── Time ──────────────────────────────────────────────────────────────
    private val time12Regex = Regex("\\b(at\\s+)?(\\d{1,2})(:(\\d{2}))?\\s*(am|pm|AM|PM)\\b")
    private val time24Regex = Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")
    private val timeOfDayWords = mapOf(
        "night" to LocalTime.of(21, 0),
        "midnight" to LocalTime.of(0, 0),
        "morning" to LocalTime.of(9, 0),
        "noon" to LocalTime.of(12, 0),
        "midday" to LocalTime.of(12, 0),
        "afternoon" to LocalTime.of(15, 0),
        "evening" to LocalTime.of(18, 0)
    )

    // ── Recurrence ────────────────────────────────────────────────────────
    private val everyAlternateDayRegex = Regex("\\bevery\\s+(?:other|alternate)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateWeekRegex = Regex("\\bevery\\s+(?:other|alternate)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNDaysRegex = Regex("\\bevery\\s+(\\d+)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNWeeksRegex = Regex("\\bevery\\s+(\\d+)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNMonthsRegex = Regex("\\bevery\\s+(\\d+)\\s+month(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyWeekdayRegex = Regex("\\bevery\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    private val bareRecurrenceWords = mapOf(
        "daily" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "weekly" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "monthly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "yearly" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "annually" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "weekdays" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "weekends" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) }
    )

    // ── Relative dates ────────────────────────────────────────────────────
    private val inDaysRegex = Regex("\\bin\\s+(\\d+)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val inWeeksRegex = Regex("\\bin\\s+(\\d+)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMonthsRegex = Regex("\\bin\\s+(\\d+)\\s+month(s)?\\b", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("\\bnext\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    private val thisWeekdayRegex = Regex("\\bthis\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    private val phraseDates = listOf(
        "next week" to { ref: LocalDate -> ref.plusWeeks(1) },
        "next month" to { ref: LocalDate -> ref.plusMonths(1) },
        "end of month" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "end of week" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "this weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) }
    )
    private val bareDateWords = listOf(
        "today" to { ref: LocalDate -> ref },
        "tomorrow" to { ref: LocalDate -> ref.plusDays(1) },
        "tmrw" to { ref: LocalDate -> ref.plusDays(1) },
        "yesterday" to { ref: LocalDate -> ref.minusDays(1) }
    )

    fun parse(raw: String, referenceDate: LocalDate = LocalDate.now()): ParsedQuickAdd {
        val claimed = mutableListOf<IntRange>()
        var due: LocalDate? = null
        var time: String? = null
        var recurrence: Recurrence? = null

        fun isFree(range: IntRange) = claimed.none { it.first <= range.last && range.first <= it.last }
        fun claim(range: IntRange) = claimed.add(range)
        fun firstFreeMatch(regex: Regex) = regex.findAll(raw).firstOrNull { isFree(it.range) }
        fun firstFreeWord(word: String) = firstFreeMatch(Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE))

        // 1. Recurrence — checked first so "every sunday"/"every monday" is claimed whole
        // before the later bare-weekday due-date rule can also match "sunday"/"monday".
        everyAlternateDayRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("daily", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateWeekRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("weekly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) firstFreeMatch(everyNDaysRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("daily", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNWeeksRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("weekly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNMonthsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("monthly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) {
            firstFreeMatch(everyWeekdayRegex)?.let { m ->
                val token = m.groupValues[1].lowercase()
                val rec = when {
                    token == "day" -> Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
                    token == "week" -> Recurrence("weekly", 1, null, null, RecurrenceEnds.Never)
                    token == "month" -> Recurrence("monthly", 1, null, null, RecurrenceEnds.Never)
                    token == "year" -> Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
                    weekdayNames.containsKey(token) -> Recurrence("weekly", 1, listOf(rruleDay.getValue(weekdayNames.getValue(token))), null, RecurrenceEnds.Never)
                    else -> null
                }
                if (rec != null) {
                    recurrence = rec
                    claim(m.range)
                }
            }
        }
        if (recurrence == null) {
            for ((word, factory) in bareRecurrenceWords) {
                firstFreeWord(word)?.let { m ->
                    recurrence = factory()
                    claim(m.range)
                }
                if (recurrence != null) break
            }
        }

        // 2. Explicit time — checked before day-count phrases so "tomorrow 3pm" doesn't have
        // "3" mistaken for a bare number, and before time-of-day words so "6pm" wins over "evening".
        firstFreeMatch(time12Regex)?.let { m ->
            val hour = m.groupValues[2].toIntOrNull()
            val minute = m.groupValues[4].toIntOrNull() ?: 0
            val meridiem = m.groupValues[5]
            if (hour != null && hour in 1..12 && minute in 0..59) {
                val hour24 = when {
                    meridiem.equals("am", ignoreCase = true) && hour == 12 -> 0
                    meridiem.equals("pm", ignoreCase = true) && hour != 12 -> hour + 12
                    else -> hour
                }
                time = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                claim(m.range)
            }
        }
        if (time == null) {
            firstFreeMatch(time24Regex)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull()
                val minute = m.groupValues[2].toIntOrNull()
                if (hour != null && minute != null && hour in 0..23) {
                    time = LocalTime.of(hour, minute).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        // 3. Relative dates
        firstFreeWord("tonight")?.let { m ->
            due = referenceDate
            if (time == null) time = timeOfDayWords.getValue("night").format(timeFormatter).uppercase(Locale.getDefault())
            claim(m.range)
        }
        if (due == null) {
            for ((word, resolve) in bareDateWords) {
                firstFreeWord(word)?.let { m -> due = resolve(referenceDate); claim(m.range) }
                if (due != null) break
            }
        }
        if (due == null) {
            for ((phrase, resolve) in phraseDates) {
                firstFreeMatch(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE))?.let { m ->
                    due = resolve(referenceDate)
                    claim(m.range)
                }
                if (due != null) break
            }
        }
        if (due == null) {
            firstFreeMatch(inDaysRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> due = referenceDate.plusDays(n.toLong()); claim(m.range) } }
        }
        if (due == null) {
            firstFreeMatch(inWeeksRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> due = referenceDate.plusWeeks(n.toLong()); claim(m.range) } }
        }
        if (due == null) {
            firstFreeMatch(inMonthsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> due = referenceDate.plusMonths(n.toLong()); claim(m.range) } }
        }
        // "next <weekday>" — nearest occurrence strictly after today.
        if (due == null) {
            firstFreeMatch(nextWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextAfter(referenceDate, day); claim(m.range) }
            }
        }
        // "this <weekday>" — nearest occurrence including today.
        if (due == null) {
            firstFreeMatch(thisWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextOrSame(referenceDate, day); claim(m.range) }
            }
        }
        // Bare weekday name (no this/next prefix) — nearest occurrence including today.
        if (due == null) {
            for ((name, day) in weekdayNames) {
                firstFreeWord(name)?.let { m -> due = nextOrSame(referenceDate, day); claim(m.range) }
                if (due != null) break
            }
        }

        // 4. Time-of-day words — only if no explicit time was already found.
        if (time == null) {
            for ((word, clock) in timeOfDayWords) {
                firstFreeWord(word)?.let { m ->
                    time = clock.format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
                if (time != null) break
            }
        }

        val sortedClaims = claimed.sortedBy { it.first }
        val title = buildString {
            var cursor = 0
            for (range in sortedClaims) {
                if (range.first > cursor) append(raw, cursor, range.first)
                cursor = (range.last + 1).coerceAtLeast(cursor)
            }
            if (cursor < raw.length) append(raw, cursor, raw.length)
        }.replace(Regex("\\s{2,}"), " ").trim()

        return ParsedQuickAdd(title = title, due = due?.toString(), time = time, recurrence = recurrence, highlightRanges = sortedClaims)
    }

    private fun nextAfter(from: LocalDate, day: DayOfWeek): LocalDate {
        var candidate = from.plusDays(1)
        while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
        return candidate
    }

    private fun nextOrSame(from: LocalDate, day: DayOfWeek): LocalDate {
        var candidate = from
        while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
        return candidate
    }
}
