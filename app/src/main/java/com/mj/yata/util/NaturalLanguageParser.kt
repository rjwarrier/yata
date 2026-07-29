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
    val startDate: String? = null, // "YYYY-MM-DD" — "starts monday"/"not before the 3rd"
    val time: String?, // "h:mm a", null if nothing matched
    val recurrence: Recurrence?, // null if nothing matched
    val reminder: String? = null, // one of TaskScheduleUtils.reminderOptions, or a literal "h:mm a" clock time
    val priority: String? = null, // "low" | "med" | "high", null if nothing matched
    val flag: Boolean = false, // true if an "important"/"flag this"-style phrase matched
    val projectName: String? = null,
    val listName: String? = null,
    val tagNames: List<String> = emptyList(),
    val assigneeNames: List<String> = emptyList(),
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
fun String.toProperCase(): String {
    if (this.isBlank()) return this
    return this.split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            if (word.length > 1 && word.all { it.isUpperCase() }) {
                word
            } else {
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
}

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
    // Accepts "5:30pm", "5.30pm", "5 a.m.", "5:30 a. m.", "at 5 pm", "10 A.M.", "5p.m."
    private val time12Regex = Regex("\\b(?:at\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|AM|PM|A\\.?\\s*M\\.?|P\\.?\\s*M\\.?)\\b", RegexOption.IGNORE_CASE)
    private val timeOClockRegex = Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*o'?clock(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
    private val atTimeRegex = Regex("\\bat\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
    private val bareMeridiemRegex = Regex("\\b(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val time24Regex = Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")

    private val wordToHourMap = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12
    )

    private val writtenHourRegex = Regex(
        "\\b(?:at\\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)" +
        "(?:\\s+(thirty|fifteen|forty\\s+five|45|30|15))?" +
        "(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm))?\\b",
        RegexOption.IGNORE_CASE
    )

    private val quarterHalfRegex = Regex(
        "\\b(quarter\\s+past|half\\s+past|quarter\\s+to)\\s+(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)" +
        "(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm))?\\b",
        RegexOption.IGNORE_CASE
    )

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
    private val everyAlternateMonthRegex = Regex("\\bevery\\s+(?:other|alternate)\\s+month(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateYearRegex = Regex("\\bevery\\s+(?:other|alternate)\\s+year(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNDaysRegex = Regex("\\bevery\\s+(\\d+)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNWeeksRegex = Regex("\\bevery\\s+(\\d+)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNMonthsRegex = Regex("\\bevery\\s+(\\d+)\\s+month(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNYearsRegex = Regex("\\bevery\\s+(\\d+)\\s+year(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyWeekdayRegex = Regex("\\bevery\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    // Word aliases for existing frequencies. Spacing/hyphenation variants ("semi-annually",
    // "semi annually") are included since those aren't really typos so much as equally common
    // ways to write the same word — genuine arbitrary-typo tolerance (e.g. "quaterly",
    // "biweekyl") would need fuzzy/edit-distance matching, a different technique from the
    // exact-phrase rules this whole file is built on, so it's out of scope here.
    //
    // Order matters: this map is scanned top-to-bottom and stops at the first hit, and a
    // hyphen or space still counts as a "word boundary" character for \b — so "weekly" would
    // otherwise match as a bare substring right inside "bi-weekly" (the hyphen creates a
    // boundary right before it), same for "annually" inside "semi-annually"/"bi-annually".
    // Every hyphenated/spaced compound below is listed before the shorter plain word it
    // contains, specifically to win that race. (Un-hyphenated forms like "biweekly" or
    // "semiannually" don't have this problem — no boundary character means no accidental
    // match — but are kept alongside their hyphenated siblings for readability.)
    private val bareRecurrenceWords = mapOf(
        "bi-weekly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "biweekly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "fortnightly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "fortnighly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) }, // common typo
        "quarterly" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "semi-annually" to { Recurrence("monthly", 6, null, null, RecurrenceEnds.Never) },
        "semi annually" to { Recurrence("monthly", 6, null, null, RecurrenceEnds.Never) },
        "semiannually" to { Recurrence("monthly", 6, null, null, RecurrenceEnds.Never) },
        "semianually" to { Recurrence("monthly", 6, null, null, RecurrenceEnds.Never) }, // common typo
        "twice a year" to { Recurrence("monthly", 6, null, null, RecurrenceEnds.Never) },
        // "biannual" is genuinely ambiguous in English (some read it as "twice a year", others
        // as "every two years") — mapped to every-2-years here for consistency with this
        // file's own "bi-" = "interval of 2" convention ("biweekly" above), not because one
        // reading is more correct. "Semiannually"/"twice a year" above are unambiguous, so
        // those always mean twice a year regardless.
        "bi-annually" to { Recurrence("yearly", 2, null, null, RecurrenceEnds.Never) },
        "bi annually" to { Recurrence("yearly", 2, null, null, RecurrenceEnds.Never) },
        "biannually" to { Recurrence("yearly", 2, null, null, RecurrenceEnds.Never) },
        "annually" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "daily" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "weekly" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "monthly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "yearly" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "weekdays" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "weekends" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) }
    )

    // ── Relative dates ────────────────────────────────────────────────────
    // "a"/"an" alongside digits so "in a week" reads the same as "in 1 week".
    private val inDaysRegex = Regex("\\bin\\s+(a|an|\\d+)\\s+day(s)?\\b", RegexOption.IGNORE_CASE)
    private val inWeeksRegex = Regex("\\bin\\s+(a|an|\\d+)\\s+week(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMonthsRegex = Regex("\\bin\\s+(a|an|\\d+)\\s+month(s)?\\b", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("\\bnext\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    private val thisWeekdayRegex = Regex("\\bthis\\s+(\\w+)\\b", RegexOption.IGNORE_CASE)
    private fun countOrOne(token: String): Long = token.toLongOrNull() ?: 1L
    private val dayAfterTomorrowRegex = Regex("\\bday\\s+after\\s+tomorrow\\b", RegexOption.IGNORE_CASE)
    private val fortnightRegex = Regex("\\b(?:in\\s+)?(?:a\\s+)?fortnight\\b", RegexOption.IGNORE_CASE)
    /**
     * Start-date phrases: a "not before" keyword plus the date phrase it governs, which group 2
     * captures for [NaturalLanguageParser.parse] to resolve on its own.
     *
     * "start"/"starts"/"starting" needs the trailing anchor to be careful — "start the report"
     * is a title, not a start date. Group 2 therefore only accepts a date-ish lead-in
     * (a digit, or one of the words that can begin a date phrase), and the whole rule no-ops when
     * the resolver can't make a date out of what follows. "not before" and "defer (to|until)"
     * are unambiguous enough to take anything.
     */
    private val startDateRegex = Regex(
        "\\b(starts?|starting|begins?|beginning|not\\s+before|defer(?:red)?(?:\\s+(?:to|until|till))?|available|from)\\s+" +
            "((?:\\d|next\\b|this\\b|tomorrow\\b|today\\b|the\\b|in\\b|mon|tue|wed|thu|fri|sat|sun|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[A-Za-z0-9,/\\-\\s]*?)" +
            "(?=\\s+(?:due|at|every|assign|@|#|!|p[1-3]|for\\b|in\\s+(?:list|project))|$)",
        RegexOption.IGNORE_CASE
    )
    private val fromNowRegex = Regex("\\b(a|an|\\d+)\\s+(day|week|month)s?\\s+from\\s+(?:now|today)\\b", RegexOption.IGNORE_CASE)
    // "the 20th" / "on the 20th" with no month named — nearest upcoming month that has that day.
    private val ordinalDayOfMonthRegex = Regex("\\b(?:on\\s+)?the\\s+(\\d{1,2})(?:st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)
    private val phraseDates = listOf(
        // Longer/more specific phrases before their shorter substrings — "next weekend" must
        // be checked as its own phrase since "weekend" isn't a weekday the generic "next
        // <weekday>" rule below understands, and it'd otherwise silently fail to match at all.
        "next weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "next week" to { ref: LocalDate -> ref.plusWeeks(1) },
        "next month" to { ref: LocalDate -> ref.plusMonths(1) },
        "beginning of month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start of month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "end of month" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "end of week" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "this weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "bom" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "eom" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "eow" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) }
        // "eod"/"eob" are handled separately below (section 3) since — unlike every other
        // entry here — they also imply a clock time, not just a date.
    )
    private val eodTime: LocalTime = LocalTime.of(18, 0)
    private val eobTime: LocalTime = LocalTime.of(17, 0)
    private val bareDateWords = listOf(
        "today" to { ref: LocalDate -> ref },
        "tomorrow" to { ref: LocalDate -> ref.plusDays(1) },
        "tmrw" to { ref: LocalDate -> ref.plusDays(1) },
        "yesterday" to { ref: LocalDate -> ref.minusDays(1) }
    )

    private fun resolveOrdinalDayOfMonth(day: Int, ref: LocalDate): LocalDate? {
        var year = ref.year
        var month = ref.monthValue
        repeat(24) {
            val length = YearMonth.of(year, month).lengthOfMonth()
            if (day in 1..length) {
                val candidate = LocalDate.of(year, month, day)
                if (!candidate.isBefore(ref)) return candidate
            }
            if (month == 12) { month = 1; year++ } else month++
        }
        return null
    }

    // ── Absolute month/day dates ─────────────────────────────────────────
    // "Jul 20", "July 20", "20 July", "20th July", optionally with a trailing year
    // ("July 20 2026" / "July 20, 2026"). No year given → nearest occurrence on/after
    // referenceDate, rolling into next year if the month/day already passed this year.
    private val monthNames = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )
    private val monthAlt = monthNames.keys.joinToString("|") { Regex.escape(it) }
    private val monthDayRegex = Regex("\\b($monthAlt)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    // Optional leading "the" / mid "of" so "the 20th of july" also resolves as a full date
    // instead of falling through to the bare ordinalDayOfMonthRegex below and losing the month.
    private val dayMonthRegex = Regex("\\b(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($monthAlt)\\.?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)

    // ── Numeric dates ─────────────────────────────────────────────────────
    // ISO "2026-07-20" is unambiguous, checked first. "7/20" / "7/20/2026" / "7/20/26" default
    // to US month/day order (matching the month-name rules above), but swap automatically when
    // the first number can't be a month (e.g. "20/7" -> day/month) so both conventions parse.
    private val isoDateRegex = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private val slashDateRegex = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")

    private fun resolveIsoDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: java.time.DateTimeException) {
        null
    }

    private fun resolveSlashDate(n1: Int, n2: Int, yearRaw: String?, ref: LocalDate): LocalDate? {
        val (month, day) = when {
            n1 in 1..12 && n2 in 1..12 -> n1 to n2
            n1 in 1..12 && n2 in 13..31 -> n1 to n2
            n1 in 13..31 && n2 in 1..12 -> n2 to n1
            else -> return null
        }
        return try {
            val y = when {
                yearRaw == null -> {
                    val candidate = LocalDate.of(ref.year, month, day)
                    if (candidate.isBefore(ref)) ref.year + 1 else ref.year
                }
                yearRaw.length <= 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            LocalDate.of(y, month, day)
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    private fun resolveMonthDay(month: Int, day: Int, year: Int?, ref: LocalDate): LocalDate? = try {
        val y = year ?: run {
            val candidate = LocalDate.of(ref.year, month, day)
            if (candidate.isBefore(ref)) ref.year + 1 else ref.year
        }
        LocalDate.of(y, month, day)
    } catch (e: java.time.DateTimeException) {
        null
    }

    private val escapeRegex = Regex("\\\\(\\w+)")

    // ── Reminder ──────────────────────────────────────────────────────────
    // "remind"/"remind me" phrases set the *reminder*, distinct from the due time — checked
    // before due-time parsing so "remind at 5pm" doesn't leave a stray "5pm" behind for the
    // due-time rule to also claim as the task's own due time.
    private val remindAtTimeKeywordRegex = Regex("\\bremind(?:\\s+me)?\\s+(?:at|on)\\s+time\\b", RegexOption.IGNORE_CASE)
    private val remindMinutesBeforeRegex = Regex("\\bremind(?:\\s+me)?\\s+(\\d+)\\s*(?:min|mins|minute|minutes)\\s+before\\b", RegexOption.IGNORE_CASE)
    private val remindHourBeforeRegex = Regex("\\bremind(?:\\s+me)?\\s+(?:1\\s+hour|an?\\s+hour)\\s+before\\b", RegexOption.IGNORE_CASE)
    private val remindDayBeforeRegex = Regex("\\bremind(?:\\s+me)?\\s+(?:1\\s+day|a\\s+day)\\s+before\\b", RegexOption.IGNORE_CASE)
    private val remindAtClockTimeRegex = Regex("\\bremind(?:\\s+me)?\\s+(?:at\\s+)?(\\d{1,2})([:.](\\d{2}))?\\s*(am|pm|AM|PM)\\b", RegexOption.IGNORE_CASE)

    // ── Priority ──────────────────────────────────────────────────────────
    // "!1"/"!!1" (etc.) — 1 is the most urgent, matching the common "p1 is highest" convention.
    private val priorityShorthandRegex = Regex("!{1,2}([1-3])\\b")
    // Bare "p1"/"p2"/"p3" (no "!") — same convention, checked alongside the "!N" shorthand
    // since it's just as explicit, before falling through to the word-phrase list below.
    private val priorityBareRegex = Regex("\\bp([1-3])\\b", RegexOption.IGNORE_CASE)
    // Multi-word phrases first so "high priority" claims itself whole rather than leaving a
    // dangling "priority" behind for a later rule to trip over.
    private val priorityWordPhrases = listOf(
        "highest priority" to "high",
        "top priority" to "high",
        "high priority" to "high",
        "super urgent" to "high",
        "must do" to "high",
        "vital" to "high",
        "essential" to "high",
        "urgent" to "high",
        "critical" to "high",
        "asap" to "high",
        "medium priority" to "med",
        "med priority" to "med",
        "normal priority" to "med",
        "lowest priority" to "low",
        "low priority" to "low",
        "minor priority" to "low",
        "someday" to "low",
        "whenever" to "low",
        "no rush" to "low"
    )

    // ── Flag ──────────────────────────────────────────────────────────────
    private val flagPhrases = listOf(
        "flag this", "flag it", "flagged", "star this", "star it", "starred",
        "important", "mark as important", "bookmark", "bookmarked"
    )

    // ── Additional relative dates ─────────────────────────────────────────
    private val inHoursRegex = Regex("\\bin\\s+(a|an|\\d+)\\s+hour(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMinutesRegex = Regex("\\bin\\s+(a|an|\\d+)\\s+min(?:ute)?s?\\b", RegexOption.IGNORE_CASE)
    private val halfAnHourRegex = Regex("\\bin\\s+half\\s+(?:an?\\s+)?hour\\b", RegexOption.IGNORE_CASE)
    private val cacheLock = Any()
    private val parseCache = object : java.util.LinkedHashMap<String, ParsedQuickAdd>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParsedQuickAdd>?): Boolean {
            return size > 64
        }
    }

    fun parse(rawInput: String, referenceDate: LocalDate = LocalDate.now(), referenceTime: LocalTime = LocalTime.now()): ParsedQuickAdd {
        val cacheKey = "$rawInput|$referenceDate"
        synchronized(cacheLock) {
            parseCache[cacheKey]?.let { return it }
        }

        val raw = rawInput
            .replace(Regex("\\bto\\s+day\\b", RegexOption.IGNORE_CASE), "today")
            .replace(Regex("\\b(to|two|2)\\s*morrow\\b", RegexOption.IGNORE_CASE), "tomorrow")
            .replace(Regex("\\bate\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "8 $1")
            .replace(Regex("\\bwon\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "1 $1")
            .replace(Regex("\\btoo\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "2 $1")
            .replace(Regex("\\bfor\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "4 $1")

        val claimed = mutableListOf<IntRange>()
        var due: LocalDate? = null
        var time: String? = null
        var recurrence: Recurrence? = null

        // Escape: a backslash directly before a word protects that word from being read as a
        // date/time/recurrence keyword — e.g. "call mom \today" keeps "today" as literal text
        // instead of setting the due date, same idea as an escape character in code. The
        // backslash itself is stripped (via `stripOnly`) but never counted as a "recognized"
        // span, so it doesn't get underlined like a real match would.
        val escapedRanges = mutableListOf<IntRange>()
        val stripOnly = mutableListOf<IntRange>()
        escapeRegex.findAll(raw).forEach { m ->
            val backslashIndex = m.range.first
            stripOnly.add(backslashIndex..backslashIndex)
            escapedRanges.add(m.groups[1]!!.range)
        }

        fun isFree(range: IntRange) = claimed.none { it.first <= range.last && range.first <= it.last } &&
            escapedRanges.none { it.first <= range.last && range.first <= it.last }
        fun claim(range: IntRange) = claimed.add(range)
        fun firstFreeMatch(regex: Regex) = regex.findAll(raw).firstOrNull { isFree(it.range) }
        fun firstFreeWord(word: String) = firstFreeMatch(Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE))

        // 1. Recurrence — checked first so "every sunday"/"every monday" is claimed whole
        // before the later bare-weekday due-date rule can also match "sunday"/"monday".
        everyAlternateDayRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("daily", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateWeekRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("weekly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateMonthRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("monthly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateYearRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("yearly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) firstFreeMatch(everyNDaysRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("daily", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNWeeksRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("weekly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNMonthsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("monthly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNYearsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("yearly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) {
            firstFreeMatch(everyWeekdayRegex)?.let { m ->
                val token = m.groupValues[1].lowercase()
                val rec = when {
                    token == "day" -> Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
                    token == "week" -> Recurrence("weekly", 1, null, null, RecurrenceEnds.Never)
                    token == "month" -> Recurrence("monthly", 1, null, null, RecurrenceEnds.Never)
                    token == "year" -> Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
                    token == "quarter" || token == "qtr" -> Recurrence("monthly", 3, null, null, RecurrenceEnds.Never)
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

        // 1.5 Reminder — see the regexes' own comment for why this runs before due-time parsing.
        var reminder: String? = null
        firstFreeMatch(remindAtTimeKeywordRegex)?.let { m -> reminder = "At time"; claim(m.range) }
        if (reminder == null) {
            firstFreeMatch(remindMinutesBeforeRegex)?.let { m ->
                val label = when (m.groupValues[1].toIntOrNull()) {
                    5 -> "5 min before"
                    15 -> "15 min before"
                    30 -> "30 min before"
                    else -> null
                }
                if (label != null) { reminder = label; claim(m.range) }
            }
        }
        if (reminder == null) firstFreeMatch(remindHourBeforeRegex)?.let { m -> reminder = "1 hour before"; claim(m.range) }
        if (reminder == null) firstFreeMatch(remindDayBeforeRegex)?.let { m -> reminder = "1 day before"; claim(m.range) }
        if (reminder == null) {
            firstFreeMatch(remindAtClockTimeRegex)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull()
                val minute = m.groupValues[3].toIntOrNull() ?: 0
                val meridiem = m.groupValues[4]
                if (hour != null && hour in 1..12 && minute in 0..59) {
                    val hour24 = when {
                        meridiem.equals("am", ignoreCase = true) && hour == 12 -> 0
                        meridiem.equals("pm", ignoreCase = true) && hour != 12 -> hour + 12
                        else -> hour
                    }
                    reminder = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        // 2. Explicit time — checked before day-count phrases so "tomorrow 3pm" doesn't have
        // "3" mistaken for a bare number, and before time-of-day words so "6pm" wins over "evening".
        firstFreeMatch(time12Regex)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val meridiem = m.groupValues[3]
            if (hour != null && hour in 1..12 && minute in 0..59) {
                val isPm = meridiem.contains("p", ignoreCase = true)
                val isAm = meridiem.contains("a", ignoreCase = true)
                val hour24 = when {
                    isPm && hour != 12 -> hour + 12
                    isAm && hour == 12 -> 0
                    else -> hour
                }
                time = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                claim(m.range)
            }
        }

        if (time == null) {
            firstFreeMatch(timeOClockRegex)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull()
                val modifier = m.groupValues[2].lowercase()
                if (hour != null && hour in 1..12) {
                    val isPm = modifier.contains("p") || modifier == "afternoon" || modifier == "evening" || modifier == "night"
                    val isAm = modifier.contains("a") || modifier == "morning"
                    val hour24 = when {
                        isPm && hour != 12 -> hour + 12
                        isAm && hour == 12 -> 0
                        !isPm && !isAm && hour in 1..7 -> hour + 12
                        else -> hour
                    }
                    time = LocalTime.of(hour24, 0).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            firstFreeMatch(atTimeRegex)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val modifier = m.groupValues[3].lowercase()
                if (hour != null && hour in 1..12 && minute in 0..59) {
                    val isPm = modifier.contains("p") || modifier == "afternoon" || modifier == "evening" || modifier == "night"
                    val isAm = modifier.contains("a") || modifier == "morning"
                    val hour24 = when {
                        isPm && hour != 12 -> hour + 12
                        isAm && hour == 12 -> 0
                        !isPm && !isAm && hour in 1..7 -> hour + 12
                        else -> hour
                    }
                    time = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            firstFreeMatch(quarterHalfRegex)?.let { m ->
                val type = m.groupValues[1].lowercase()
                val hourRaw = m.groupValues[2].lowercase()
                val modifier = m.groupValues[3].lowercase()
                val baseHour = hourRaw.toIntOrNull() ?: wordToHourMap[hourRaw]
                if (baseHour != null && baseHour in 1..12) {
                    val (effectiveHour, minute) = when {
                        type.contains("half") -> baseHour to 30
                        type.contains("quarter past") -> baseHour to 15
                        type.contains("quarter to") -> {
                            val h = if (baseHour == 1) 12 else baseHour - 1
                            h to 45
                        }
                        else -> baseHour to 0
                    }
                    val isPm = modifier.contains("p") || modifier == "afternoon" || modifier == "evening" || modifier == "night"
                    val isAm = modifier.contains("a") || modifier == "morning"
                    val hour24 = when {
                        isPm && effectiveHour != 12 -> effectiveHour + 12
                        isAm && effectiveHour == 12 -> 0
                        !isPm && !isAm && effectiveHour in 1..7 -> effectiveHour + 12
                        else -> effectiveHour
                    }
                    time = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            firstFreeMatch(writtenHourRegex)?.let { m ->
                val hourWord = m.groupValues[1].lowercase()
                val minuteWord = m.groupValues[2].lowercase()
                val modifier = m.groupValues[3].lowercase()
                val hour = wordToHourMap[hourWord]
                val minute = when (minuteWord) {
                    "fifteen", "15" -> 15
                    "thirty", "30" -> 30
                    "forty five", "45" -> 45
                    else -> 0
                }
                if (hour != null && hour in 1..12) {
                    val isPm = modifier.contains("p") || modifier == "afternoon" || modifier == "evening" || modifier == "night"
                    val isAm = modifier.contains("a") || modifier == "morning"
                    val hour24 = when {
                        isPm && hour != 12 -> hour + 12
                        isAm && hour == 12 -> 0
                        !isPm && !isAm && hour in 1..7 -> hour + 12
                        else -> hour
                    }
                    time = LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
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

        // 2.5 Start date — "starts monday", "from next week", "defer to the 15th". Must run
        // before section 3, or the bare date inside the phrase gets claimed as the *due* date and
        // "starts monday" silently means the opposite of what it says.
        //
        // The date phrase itself is resolved by recursing into parse() on just the captured text,
        // rather than duplicating the ~160 lines of date rules below. The recursion terminates at
        // depth 1: the captured group can't contain another start keyword, since the keyword is
        // what delimits it. Only the resolved date is taken from the nested result — its title,
        // priority and everything else are discarded.
        var startDate: LocalDate? = null
        firstFreeMatch(startDateRegex)?.let { m ->
            val phrase = m.groupValues[2].trim()
            if (phrase.isNotEmpty()) {
                NaturalLanguageParser.parse(phrase, referenceDate, referenceTime).due?.let { resolved ->
                    startDate = runCatching { LocalDate.parse(resolved) }.getOrNull()
                    // Claim the whole "starts <phrase>" span, not just the keyword, so the date
                    // words inside it are off-limits to every rule below and get stripped from
                    // the saved title along with the keyword.
                    if (startDate != null) claim(m.range)
                }
            }
        }

        // 3. Relative dates
        var dueRange: IntRange? = null
        firstFreeWord("tonight")?.let { m ->
            due = referenceDate
            if (time == null) time = timeOfDayWords.getValue("night").format(timeFormatter).uppercase(Locale.getDefault())
            claim(m.range)
            dueRange = m.range
        }
        // "in N hour(s)"/"in N minute(s)"/"in half an hour" — the only relative-date phrases
        // precise enough to need a clock time, not just a calendar date, so they set both
        // together (crossing midnight rolls the due date forward naturally, e.g. "in 3 hours"
        // at 11pm is due tomorrow). A pre-existing explicit time claim wins if there already
        // is one; the due date always gets set.
        fun applyMinutesOffset(minutes: Long, range: IntRange) {
            val target = java.time.LocalDateTime.of(referenceDate, referenceTime).plusMinutes(minutes)
            due = target.toLocalDate()
            if (time == null) time = target.toLocalTime().format(timeFormatter).uppercase(Locale.getDefault())
            claim(range)
            dueRange = range
        }
        if (due == null) {
            firstFreeMatch(halfAnHourRegex)?.let { m -> applyMinutesOffset(30, m.range) }
        }
        if (due == null) {
            firstFreeMatch(inHoursRegex)?.let { m -> applyMinutesOffset(countOrOne(m.groupValues[1]) * 60, m.range) }
        }
        if (due == null) {
            firstFreeMatch(inMinutesRegex)?.let { m -> applyMinutesOffset(countOrOne(m.groupValues[1]), m.range) }
        }
        // Numeric dates — most explicit, checked before everything else in this section.
        if (due == null) {
            firstFreeMatch(isoDateRegex)?.let { m ->
                val year = m.groupValues[1].toIntOrNull()
                val month = m.groupValues[2].toIntOrNull()
                val day = m.groupValues[3].toIntOrNull()
                if (year != null && month != null && day != null) {
                    resolveIsoDate(year, month, day)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        if (due == null) {
            firstFreeMatch(slashDateRegex)?.let { m ->
                val n1 = m.groupValues[1].toIntOrNull()
                val n2 = m.groupValues[2].toIntOrNull()
                val yearRaw = m.groupValues[3].ifEmpty { null }
                if (n1 != null && n2 != null) {
                    resolveSlashDate(n1, n2, yearRaw, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // Absolute "Jul 20" / "20 July" style dates — checked early since they're explicit
        // and shouldn't be shadowed by the vaguer relative-date rules below.
        if (due == null) {
            firstFreeMatch(monthDayRegex)?.let { m ->
                val month = monthNames[m.groupValues[1].lowercase()]
                val day = m.groupValues[2].toIntOrNull()
                val year = m.groupValues[3].toIntOrNull()
                if (month != null && day != null) {
                    resolveMonthDay(month, day, year, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        if (due == null) {
            firstFreeMatch(dayMonthRegex)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                val month = monthNames[m.groupValues[2].lowercase()]
                val year = m.groupValues[3].toIntOrNull()
                if (month != null && day != null) {
                    resolveMonthDay(month, day, year, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // "day after tomorrow" — checked before the bare "tomorrow" word below so the whole
        // phrase is claimed at once instead of "tomorrow" alone matching first.
        if (due == null) {
            firstFreeMatch(dayAfterTomorrowRegex)?.let { m -> due = referenceDate.plusDays(2); claim(m.range); dueRange = m.range }
        }
        if (due == null) {
            for ((word, resolve) in bareDateWords) {
                firstFreeWord(word)?.let { m -> due = resolve(referenceDate); claim(m.range); dueRange = m.range }
                if (due != null) break
            }
        }
        if (due == null) {
            for ((phrase, resolve) in phraseDates) {
                firstFreeMatch(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE))?.let { m ->
                    due = resolve(referenceDate)
                    claim(m.range)
                    dueRange = m.range
                }
                if (due != null) break
            }
        }
        // "eod"/"eob" — today's date plus an implied clock time (unlike every other phrase
        // above, which is date-only), since that's what people actually mean by them.
        if (due == null) {
            firstFreeWord("eod")?.let { m ->
                due = referenceDate
                if (time == null) time = eodTime.format(timeFormatter).uppercase(Locale.getDefault())
                claim(m.range)
                dueRange = m.range
            }
        }
        if (due == null) {
            firstFreeWord("eob")?.let { m ->
                due = referenceDate
                if (time == null) time = eobTime.format(timeFormatter).uppercase(Locale.getDefault())
                claim(m.range)
                dueRange = m.range
            }
        }
        if (due == null) {
            firstFreeMatch(fortnightRegex)?.let { m -> due = referenceDate.plusWeeks(2); claim(m.range); dueRange = m.range }
        }
        if (due == null) {
            firstFreeMatch(fromNowRegex)?.let { m ->
                val n = countOrOne(m.groupValues[1])
                due = when (m.groupValues[2].lowercase()) {
                    "day" -> referenceDate.plusDays(n)
                    "week" -> referenceDate.plusWeeks(n)
                    "month" -> referenceDate.plusMonths(n)
                    else -> null
                }
                if (due != null) { claim(m.range); dueRange = m.range }
            }
        }
        // "the 20th" (no month named) — nearest upcoming month with that day.
        if (due == null) {
            firstFreeMatch(ordinalDayOfMonthRegex)?.let { m ->
                m.groupValues[1].toIntOrNull()?.let { day ->
                    resolveOrdinalDayOfMonth(day, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        if (due == null) {
            firstFreeMatch(inDaysRegex)?.let { m -> due = referenceDate.plusDays(countOrOne(m.groupValues[1])); claim(m.range); dueRange = m.range }
        }
        if (due == null) {
            firstFreeMatch(inWeeksRegex)?.let { m -> due = referenceDate.plusWeeks(countOrOne(m.groupValues[1])); claim(m.range); dueRange = m.range }
        }
        if (due == null) {
            firstFreeMatch(inMonthsRegex)?.let { m -> due = referenceDate.plusMonths(countOrOne(m.groupValues[1])); claim(m.range); dueRange = m.range }
        }
        // "next <weekday>" — nearest occurrence strictly after today.
        if (due == null) {
            firstFreeMatch(nextWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextAfter(referenceDate, day); claim(m.range); dueRange = m.range }
            }
        }
        // "this <weekday>" — nearest occurrence including today.
        if (due == null) {
            firstFreeMatch(thisWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextOrSame(referenceDate, day); claim(m.range); dueRange = m.range }
            }
        }
        // Bare weekday name (no this/next prefix) — nearest occurrence including today.
        if (due == null) {
            for ((name, day) in weekdayNames) {
                firstFreeWord(name)?.let { m -> due = nextOrSame(referenceDate, day); claim(m.range); dueRange = m.range }
                if (due != null) break
            }
        }

        // 3.5 "remind <date>" — a bare "remind"/"remind me" immediately before a date phrase
        // (no offset/clock-time suffix, since those are already claimed in section 1.5) implies
        // the reminder should fire at the task's due time.
        if (reminder == null && dueRange != null) {
            val range = dueRange!!
            val prefix = raw.substring(0, range.first)
            Regex("\\bremind(?:\\s+me)?\\s*$", RegexOption.IGNORE_CASE).find(prefix)?.let { m ->
                if (isFree(m.range)) {
                    reminder = "At time"
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            for ((word, clock) in timeOfDayWords) {
                firstFreeWord(word)?.let { m ->
                    time = clock.format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
                if (time != null) break
            }
        }
        if (time == null) {
            firstFreeMatch(bareMeridiemRegex)?.let { m ->
                val meridiem = m.groupValues[1].lowercase()
                val isPm = meridiem.contains("p")
                time = if (isPm) "5:00 PM" else "9:00 AM"
                claim(m.range)
            }
        }

        // 5. Priority shorthand — requires a non-alphanumeric char (or start of string) right
        // before the "!" run so a mid-word "!" (unlikely, but e.g. "wow!1") doesn't spuriously match.
        var priority: String? = null
        priorityShorthandRegex.findAll(raw)
            .firstOrNull { m -> isFree(m.range) && (m.range.first == 0 || !raw[m.range.first - 1].isLetterOrDigit()) }
            ?.let { m ->
                priority = when (m.groupValues[1]) {
                    "1" -> "high"
                    "2" -> "med"
                    "3" -> "low"
                    else -> null
                }
                claim(m.range)
            }
        // Bare "p1"/"p2"/"p3" — same convention as the "!N" shorthand, checked next since
        // it's just as explicit as that (only if "!N" didn't already match).
        if (priority == null) {
            firstFreeMatch(priorityBareRegex)?.let { m ->
                priority = when (m.groupValues[1]) {
                    "1" -> "high"
                    "2" -> "med"
                    "3" -> "low"
                    else -> null
                }
                if (priority != null) claim(m.range)
            }
        }
        // Word-based priority — only if "!N"/"pN" above didn't already set one.
        if (priority == null) {
            for ((phrase, level) in priorityWordPhrases) {
                firstFreeMatch(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE))?.let { m ->
                    priority = level
                    claim(m.range)
                }
                if (priority != null) break
            }
        }

        // 6. Flag — independent of priority (a task can be both flagged and low-priority).
        var flag = false
        for (phrase in flagPhrases) {
            firstFreeMatch(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE))?.let { m ->
                flag = true
                claim(m.range)
            }
            if (flag) break
        }

        // 7. Project, List, Tag, Assignee keywords
        var projectName: String? = null
        firstFreeMatch(Regex("\\b(?:in\\s+project|for\\s+project|under\\s+project|project)\\s+([A-Za-z0-9_\\-\\s]+?)(?=\\s+(?:list|tag|tagged?|label|labeled?|#|assign(?:ed)?\\s+to|give(?:n)?\\s+to|delegate|send\\s+to|assign|@|due|at|every|on|!|p[1-3]|$))\\b", RegexOption.IGNORE_CASE))?.let { m ->
            projectName = m.groupValues[1].trim()
            claim(m.range)
        }

        var listName: String? = null
        firstFreeMatch(Regex("\\b(?:in\\s+list|for\\s+list|under\\s+list|list)\\s+([A-Za-z0-9_\\-\\s]+?)(?=\\s+(?:project|tag|tagged?|label|labeled?|#|assign(?:ed)?\\s+to|give(?:n)?\\s+to|delegate|send\\s+to|assign|@|due|at|every|on|!|p[1-3]|$))\\b", RegexOption.IGNORE_CASE))?.let { m ->
            listName = m.groupValues[1].trim()
            claim(m.range)
        }

        val tagNames = mutableListOf<String>()
        val tagMatches = Regex("\\b(?:tagged?\\s+as\\s+|tagged?\\s+|tag\\s+as\\s+|tag\\s+|labeled?\\s+as\\s+|labeled?\\s+|label\\s+as\\s+|label\\s+|with\\s+tag\\s+|#)([A-Za-z0-9_\\-]+)\\b", RegexOption.IGNORE_CASE).findAll(raw)
        for (m in tagMatches) {
            if (isFree(m.range)) {
                tagNames.add(m.groupValues[1].trim())
                claim(m.range)
            }
        }

        val assigneeNames = mutableListOf<String>()
        val assigneeMatches = Regex("\\b(?:assign(?:ed)?\\s+to\\s+|give(?:n)?\\s+to\\s+|delegate(?:d)?\\s+to\\s+|send\\s+to\\s+|assign\\s+|@)([A-Za-z0-9_\\-\\s]+?)(?=\\s+(?:project|list|tag|tagged?|label|labeled?|#|due|at|every|on|!|p[1-3]|$))\\b", RegexOption.IGNORE_CASE).findAll(raw)
        for (m in assigneeMatches) {
            if (isFree(m.range)) {
                assigneeNames.add(m.groupValues[1].trim())
                claim(m.range)
            }
        }

        val prepositionRegex = Regex("\\b(for|on|at|by|scheduled\\s+for|remind\\s+me\\s+for|remind\\s+me\\s+on)\\s*$", RegexOption.IGNORE_CASE)

        val expandedClaims = claimed.map { range ->
            var start = range.first
            val prefix = raw.substring(0, start)
            prepositionRegex.find(prefix)?.let { m ->
                if (isFree(m.range)) {
                    start = m.range.first
                }
            }
            start..range.last
        }

        val sortedClaims = expandedClaims.sortedBy { it.first }
        val sortedStrip = (expandedClaims + stripOnly).sortedBy { it.first }
        val titleRaw = buildString {
            var cursor = 0
            for (range in sortedStrip) {
                if (range.first > cursor) append(raw, cursor, range.first)
                cursor = (range.last + 1).coerceAtLeast(cursor)
            }
            if (cursor < raw.length) append(raw, cursor, raw.length)
        }.replace(Regex("\\s{2,}"), " ").trim()

        var titleClean = titleRaw
        repeat(3) {
            titleClean = titleClean
                .replace(Regex("\\b(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[.,;:_\\-/\\\\]+$"), "")
                .replace(Regex("^\\s*[.,;:_\\-/\\\\]+"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }

        val title = if (titleClean.isNotBlank()) titleClean else raw.replace(Regex("[.,;:_\\-/\\\\]+$"), "").trim()

        val result = ParsedQuickAdd(
            title = title,
            due = due?.toString(),
            startDate = startDate?.toString(),
            time = time,
            recurrence = recurrence,
            reminder = reminder,
            priority = priority,
            flag = flag,
            projectName = projectName,
            listName = listName,
            tagNames = tagNames,
            assigneeNames = assigneeNames,
            highlightRanges = sortedClaims
        )
        synchronized(cacheLock) {
            parseCache[cacheKey] = result
        }
        return result
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
