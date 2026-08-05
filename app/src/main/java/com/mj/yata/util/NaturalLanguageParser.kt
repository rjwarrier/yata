package com.mj.yata.util

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.DateAliasDefinition
import com.mj.yata.domain.model.DateAliasTarget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedQuickAdd(
    val title: String,
    val due: String?, // "YYYY-MM-DD", null if nothing matched
    val startDate: String? = null, // "YYYY-MM-DD" â€” "starts monday"/"not before the 3rd"
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
 * doesn't touch #tag/@person tokens â€” NewTaskSheet's own mention autocomplete already owns
 * that convention (see detectMentionToken in NewTaskSheet.kt), so re-parsing them here would
 * double-handle the same syntax two different ways.
 *
 * Every rule searches the *original* string and records the matched range instead of
 * destructively consuming a shrinking "remaining" copy â€” that's what lets the caller
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

    // Deliberately fixed at 12-hour, and not routed through the user's clock preference: what this
    // produces is written to `Task.time`, which is the storage format (see
    // TaskScheduleUtils.storageTimeFormatter). The preference is applied when the time is shown.
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    @Volatile private var customDateAliases: Map<String, DateAliasTarget> = emptyMap()

    private val weekdayNames = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
        // Spanish aliases. Add new languages here and the date/recurrence rules pick them up.
        "lunes" to DayOfWeek.MONDAY, "lun" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY, "mar" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY, "mié" to DayOfWeek.WEDNESDAY, "mie" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "jue" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "vie" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "sáb" to DayOfWeek.SATURDAY, "sab" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY, "dom" to DayOfWeek.SUNDAY,
        // Portuguese aliases.
        "segunda" to DayOfWeek.MONDAY, "segunda-feira" to DayOfWeek.MONDAY, "seg" to DayOfWeek.MONDAY,
        "terça" to DayOfWeek.TUESDAY, "terca" to DayOfWeek.TUESDAY, "terça-feira" to DayOfWeek.TUESDAY, "terca-feira" to DayOfWeek.TUESDAY, "ter" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY, "quarta-feira" to DayOfWeek.WEDNESDAY, "qua" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY, "quinta-feira" to DayOfWeek.THURSDAY, "qui" to DayOfWeek.THURSDAY,
        "sexta" to DayOfWeek.FRIDAY, "sexta-feira" to DayOfWeek.FRIDAY, "sex" to DayOfWeek.FRIDAY,
        // French aliases.
        "lundi" to DayOfWeek.MONDAY,
        "mardi" to DayOfWeek.TUESDAY,
        "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY,
        "vendredi" to DayOfWeek.FRIDAY,
        "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY
    )
    private val rruleDay = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU"
    )

    fun configureDateAliases(encodedDefinitions: Set<String>) {
        customDateAliases = encodedDefinitions
            .mapNotNull(DateAliasDefinition::decode)
            .associate { it.alias to it.target }
        synchronized(cacheLock) { parseCache.clear() }
    }

    private fun resolveDateAlias(target: DateAliasTarget, referenceDate: LocalDate): LocalDate =
        when (target) {
            DateAliasTarget.TODAY -> referenceDate
            DateAliasTarget.TOMORROW -> referenceDate.plusDays(1)
            DateAliasTarget.NEXT_WEEK -> referenceDate.plusWeeks(1)
            DateAliasTarget.NEXT_MONTH -> referenceDate.plusMonths(1)
            DateAliasTarget.WEEKEND -> nextOrSame(referenceDate, DayOfWeek.SATURDAY)
            DateAliasTarget.MONDAY -> nextOrSame(referenceDate, DayOfWeek.MONDAY)
            DateAliasTarget.TUESDAY -> nextOrSame(referenceDate, DayOfWeek.TUESDAY)
            DateAliasTarget.WEDNESDAY -> nextOrSame(referenceDate, DayOfWeek.WEDNESDAY)
            DateAliasTarget.THURSDAY -> nextOrSame(referenceDate, DayOfWeek.THURSDAY)
            DateAliasTarget.FRIDAY -> nextOrSame(referenceDate, DayOfWeek.FRIDAY)
            DateAliasTarget.SATURDAY -> nextOrSame(referenceDate, DayOfWeek.SATURDAY)
            DateAliasTarget.SUNDAY -> nextOrSame(referenceDate, DayOfWeek.SUNDAY)
        }

    // â”€â”€ Time â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Accepts "5:30pm", "5.30pm", "5 a.m.", "5:30 a. m.", "at 5 pm", "10 A.M.", "5p.m."
    private val time12Regex = Regex("\\b(?:(?:at|a\\s+las?|às?|as|à|a)\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|AM|PM|A\\.?\\s*M\\.?|P\\.?\\s*M\\.?)\\b", RegexOption.IGNORE_CASE)
    private val timeOClockRegex = Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*o'?clock(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
    private val atTimeRegex = Regex("\\b(?:at|a\\s+las?|às?|as|à|a)\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s+(?:in\\s+the\\s+|de\\s+la\\s+|da\\s+|du\\s+)?(morning|afternoon|evening|night|mañana|manana|tarde|noche|manhã|manha|matin|après-midi|apres-midi|soir|nuit|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
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

    // "8ish" / "8-ish" / "half 8ish" â€” an hour with the edges filed off. Same 1..7-reads-as-PM
    // heuristic the other bare-hour rules use.
    private val ishTimeRegex = Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*-?\\s*ish\\b", RegexOption.IGNORE_CASE)
    private val firstThingRegex = Regex("\\bfirst\\s+thing(?:\\s+in\\s+the\\s+morning)?\\b", RegexOption.IGNORE_CASE)
    /**
     * Mealtimes, but only behind a preposition. Bare "lunch"/"dinner" are usually the task itself
     * ("lunch with sam", "cook dinner") and claiming those would set a time nobody asked for and
     * strip the word out of the title; "at lunch" is unambiguously about when.
     */
    private val mealTimeRegex = Regex(
        "\\b(?:at|by|before|after|around|a|al|às?|as|à|antes\\s+de|después\\s+de|despues\\s+de|cerca\\s+de|depois\\s+de|après|apres|avant|vers)\\s+(breakfast|brunch|lunch(?:time)?|dinner(?:time)?|supper|bedtime|desayuno|almuerzo|comida|cena|hora\\s+de\\s+dormir|café\\s+da\\s+manhã|cafe\\s+da\\s+manha|almoço|almoco|jantar|hora\\s+de\\s+dormir|petit[-\\s]déjeuner|petit[-\\s]dejeuner|déjeuner|dejeuner|dîner|diner|souper|coucher)\\b",
        RegexOption.IGNORE_CASE
    )
    private val mealTimes = mapOf(
        "breakfast" to LocalTime.of(8, 0),
        "brunch" to LocalTime.of(11, 0),
        "lunch" to LocalTime.of(12, 30),
        "lunchtime" to LocalTime.of(12, 30),
        "dinner" to LocalTime.of(19, 0),
        "dinnertime" to LocalTime.of(19, 0),
        "supper" to LocalTime.of(19, 0),
        "bedtime" to LocalTime.of(22, 0),
        "desayuno" to LocalTime.of(8, 0),
        "almuerzo" to LocalTime.of(12, 30),
        "comida" to LocalTime.of(14, 0),
        "cena" to LocalTime.of(19, 0),
        "hora de dormir" to LocalTime.of(22, 0),
        "café da manhã" to LocalTime.of(8, 0),
        "cafe da manha" to LocalTime.of(8, 0),
        "almoço" to LocalTime.of(12, 30),
        "almoco" to LocalTime.of(12, 30),
        "jantar" to LocalTime.of(19, 0),
        "petit-déjeuner" to LocalTime.of(8, 0),
        "petit dejeuner" to LocalTime.of(8, 0),
        "déjeuner" to LocalTime.of(12, 30),
        "dejeuner" to LocalTime.of(12, 30),
        "dîner" to LocalTime.of(19, 0),
        "diner" to LocalTime.of(19, 0),
        "souper" to LocalTime.of(19, 0),
        "coucher" to LocalTime.of(22, 0)
    )

    private val timeOfDayWords = mapOf(
        "night" to LocalTime.of(21, 0),
        "midnight" to LocalTime.of(0, 0),
        "morning" to LocalTime.of(9, 0),
        "noon" to LocalTime.of(12, 0),
        "midday" to LocalTime.of(12, 0),
        "afternoon" to LocalTime.of(15, 0),
        "evening" to LocalTime.of(18, 0),
        "noche" to LocalTime.of(21, 0),
        "medianoche" to LocalTime.of(0, 0),
        "mañana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0),
        "mediodía" to LocalTime.of(12, 0),
        "mediodia" to LocalTime.of(12, 0),
        "tarde" to LocalTime.of(18, 0),
        "noite" to LocalTime.of(21, 0),
        "meia-noite" to LocalTime.of(0, 0),
        "manhã" to LocalTime.of(9, 0),
        "manha" to LocalTime.of(9, 0),
        "meio-dia" to LocalTime.of(12, 0),
        "matin" to LocalTime.of(9, 0),
        "midi" to LocalTime.of(12, 0),
        "après-midi" to LocalTime.of(15, 0),
        "apres-midi" to LocalTime.of(15, 0),
        "soir" to LocalTime.of(18, 0),
        "nuit" to LocalTime.of(21, 0),
        "minuit" to LocalTime.of(0, 0)
    )

    // â”€â”€ Recurrence â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "each" is accepted everywhere "every" is â€” it's the same instruction, and people write both.
    private const val EVERY = "(?:every|each|cada|todo|toda|todos\\s+os|todas\\s+as|chaque|tous\\s+les|toutes\\s+les)"
    private val everyAlternateDayRegex = Regex("\\b$EVERY\\s+(?:other|alternate|otro|alterno|outro|alternado|autre)\\s+(?:day|día|dia|jour)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateWeekRegex = Regex("\\b$EVERY\\s+(?:other|alternate|otra|alterna|outra|alternada|autre)\\s+(?:week|semana|semaine)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateMonthRegex = Regex("\\b$EVERY\\s+(?:other|alternate|otro|alterno|outro|alternado|autre)\\s+(?:months?|mes(?:es)?|mês|mêses|mois)\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateYearRegex = Regex("\\b$EVERY\\s+(?:other|alternate|otro|alterno|outro|alternado|autre)\\s+(?:year|año|ano|an|année|annee)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNDaysRegex = Regex("\\b$EVERY\\s+(\\d+)\\s+(?:day|día|dia|jour)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNWeeksRegex = Regex("\\b$EVERY\\s+(\\d+)\\s+(?:week|semana|semaine)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyNMonthsRegex = Regex("\\b$EVERY\\s+(\\d+)\\s+(?:months?|mes(?:es)?|mês|mêses|mois)\\b", RegexOption.IGNORE_CASE)
    private val everyNYearsRegex = Regex("\\b$EVERY\\s+(\\d+)\\s+(?:year|año|ano|an|année|annee)(s)?\\b", RegexOption.IGNORE_CASE)
    private val everyWeekdayRegex = Regex("\\b$EVERY\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)

    // Longest names first so "sun" can't win against "sunday" and leave "day" stranded.
    private val weekdayAlt by lazy { weekdayNames.keys.sortedByDescending { it.length }.joinToString("|") }
    /**
     * "every mon, wed and fri" â€” a weekly recurrence on several days at once. Has to be tried
     * before the single-weekday rule, which would otherwise claim just the first day and leave
     * the rest of the list sitting in the title (and the second weekday free for the bare-weekday
     * due-date rule to misread as a one-off due date).
     */
    private val everyMultiWeekdayRegex by lazy {
        Regex(
            "\\b$EVERY\\s+((?:$weekdayAlt)(?:\\s*(?:,|and|\\by\\b|\\be\\b|\\bet\\b|&|\\+|/)\\s*(?:$weekdayAlt))+)\\b",
            RegexOption.IGNORE_CASE
        )
    }
    private val multiWeekdaySplitRegex = Regex("\\s*(?:,|and|\\by\\b|\\be\\b|\\bet\\b|&|\\+|/)\\s*", RegexOption.IGNORE_CASE)
    // "every month on the 15th" / "monthly on the 1st" â€” a monthly recurrence pinned to a date.
    private val everyMonthOnDayRegex = Regex(
        "\\b(?:$EVERY\\s+(?:month|mes|mês|mois)|monthly|mensual(?:mente)?|mensal(?:mente)?|mensuel(?:le)?(?:ment)?)\\s+(?:on\\s+|el\\s+|no\\s+|le\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b",
        RegexOption.IGNORE_CASE
    )
    // The same thing said the other way round: "every 1st of the month".
    private val everyOrdinalOfMonthRegex = Regex(
        "\\b$EVERY\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+(?:the\\s+|$EVERY\\s+)?month|de\\s+(?:el\\s+)?mes|do\\s+mês|do\\s+mes|du\\s+mois)\\b",
        RegexOption.IGNORE_CASE
    )
    // bymonthday = -1 is the model's "last day of the month", whatever length that month is.
    private val everyLastDayOfMonthRegex = Regex(
        "\\b(?:$EVERY\\s+(?:month|mes|mês|mois)|monthly|mensual(?:mente)?|mensal(?:mente)?|mensuel(?:le)?(?:ment)?|$EVERY)\\s+(?:on\\s+|el\\s+|no\\s+|le\\s+)?(?:the\\s+)?(?:last\\s+day|último\\s+día|ultimo\\s+dia|último\\s+dia|ultimo\\s+dia|dernier\\s+jour)(?:\\s+(?:of\\s+(?:the\\s+)?month|del\\s+mes|do\\s+mês|do\\s+mes|du\\s+mois))?\\b",
        RegexOption.IGNORE_CASE
    )
    /**
     * How a recurrence stops. Both forms have to be claimed while the recurrence rules run, before
     * the due-date section: "every week until dec 20" otherwise hands "dec 20" to the due-date
     * rule, which reads the end of the series as the date of the first occurrence.
     */
    private val recurrenceUntilRegex = Regex(
        "\\b(?:until|untill|till|til|thru|through|up\\s+to|ending|hasta|terminando|termina)\\s+" +
            "((?:\\d|next\\b|this\\b|tomorrow\\b|today\\b|the\\b|in\\b|end\\b|próximo\\b|proximo\\b|próxima\\b|proxima\\b|este\\b|esta\\b|mañana\\b|manana\\b|hoy\\b|el\\b|en\\b|fin\\b|mon|tue|wed|thu|fri|sat|sun|lun|mar|mié|mie|jue|vie|sáb|sab|dom|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|ene|abr|ago|dic)[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9,/\\-\\s]*?)" +
            "(?=\\s+(?:at|a\\s+las?|às?|as|à|every|each|cada|todo|toda|chaque|assign|asign|atrib|rappel|lemb|@|#|!|p[1-3]|for\\b|por\\b|pour\\b|in\\s+(?:list|project)|en\\s+(?:lista|proyecto|liste|projet)|em\\s+(?:lista|projeto))|$)",
        RegexOption.IGNORE_CASE
    )
    private val recurrenceTimesRegex = Regex(
        "\\b(?:(?:for|por|pour)\\s+)?(\\d+)\\s*(?:times|occurrences|occurrence|veces|ocurrencias|vezes|ocorrências|ocorrencias|fois|x)\\b",
        RegexOption.IGNORE_CASE
    )
    // Word aliases for existing frequencies. Spacing/hyphenation variants ("semi-annually",
    // "semi annually") are included since those aren't really typos so much as equally common
    // ways to write the same word â€” genuine arbitrary-typo tolerance (e.g. "quaterly",
    // "biweekyl") would need fuzzy/edit-distance matching, a different technique from the
    // exact-phrase rules this whole file is built on, so it's out of scope here.
    //
    // Order matters: this map is scanned top-to-bottom and stops at the first hit, and a
    // hyphen or space still counts as a "word boundary" character for \b â€” so "weekly" would
    // otherwise match as a bare substring right inside "bi-weekly" (the hyphen creates a
    // boundary right before it), same for "annually" inside "semi-annually"/"bi-annually".
    // Every hyphenated/spaced compound below is listed before the shorter plain word it
    // contains, specifically to win that race. (Un-hyphenated forms like "biweekly" or
    // "semiannually" don't have this problem â€” no boundary character means no accidental
    // match â€” but are kept alongside their hyphenated siblings for readability.)
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
        // as "every two years") â€” mapped to every-2-years here for consistency with this
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
        "weekends" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) },
        "diario" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "diaria" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "diariamente" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "semanal" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "semanalmente" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "quincenal" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "mensual" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "mensualmente" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "trimestral" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "anual" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "anualmente" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "entre semana" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "fines de semana" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) },
        "diário" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "diaria" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "diariamente" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "semanal" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "semanalmente" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "quinzenal" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "mensal" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "mensalmente" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "trimestral" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "anualmente" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "dias úteis" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "dias uteis" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "fins de semana" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) },
        "quotidien" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "quotidienne" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "chaque jour" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "hebdomadaire" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "mensuel" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "mensuelle" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "trimestriel" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "annuel" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "annuelle" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "jours ouvrables" to { Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never) },
        "week-ends" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) },
        "weekends" to { Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never) }
    )

    // â”€â”€ Relative dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "a"/"an" alongside digits so "in a week" reads the same as "in 1 week", plus the vague
    // counts people actually type. Longest alternatives first, or "a" matches inside "a few"
    // and the count silently collapses to 1.
    private const val COUNT = "(?:a\\s+couple\\s+of|a\\s+couple|a\\s+few|several|un|una|um|uma|unos|unas|uns|umas|varios|varias|vários|várias|quelques|plusieurs|an|a|\\d+)"
    private val inDaysRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:day|día|dia|jour)(s)?\\b", RegexOption.IGNORE_CASE)
    private val inWeeksRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:week|semana|semaine)(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMonthsRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:months?|mes(?:es)?|mês|meses|mois)\\b", RegexOption.IGNORE_CASE)
    private val inYearsRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:year|año|ano|an|année|annee)(s)?\\b", RegexOption.IGNORE_CASE)
    // "in 3 business days" â€” counts weekdays only, which is the whole point of saying it.
    private val inBusinessDaysRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:business|working|work|hábiles|habiles|laborables|úteis|uteis|ouvrables)\\s+(?:day|día|dia|jour)(s)?\\b", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("\\b(?:next|próximo|proximo|próxima|proxima|prochain|prochaine)\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)
    private val thisWeekdayRegex = Regex("\\b(?:this|este|esta|ce|cet|cette)\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)
    // "wednesday next week" â€” the same day "next wednesday" means, said back to front. Without
    // this the "next week" phrase claims its half and the weekday is left behind in the title.
    private val weekdayNextWeekRegex by lazy {
        Regex("\\b($weekdayAlt)\\s+(?:next\\s+week|la\\s+próxima\\s+semana|la\\s+proxima\\s+semana|semana\\s+que\\s+vem|semaine\\s+prochaine)\\b", RegexOption.IGNORE_CASE)
    }

    /** Digits, "a"/"an", or one of the vague words in [COUNT]. Anything unrecognized reads as 1. */
    private fun countOrOne(token: String): Long {
        val t = token.trim().lowercase()
        t.toLongOrNull()?.let { return it }
        return when {
            t.contains("couple") -> 2L
            t.contains("few") -> 3L
            t.contains("several") -> 4L
            t == "un" || t == "una" || t == "um" || t == "uma" || t == "une" -> 1L
            t == "unos" || t == "unas" || t == "uns" || t == "umas" -> 2L
            t == "varios" || t == "varias" || t == "vários" || t == "várias" || t == "plusieurs" -> 4L
            t == "quelques" -> 3L
            else -> 1L
        }
    }

    private fun plusBusinessDays(from: LocalDate, days: Long): LocalDate {
        var candidate = from
        var remaining = days
        while (remaining > 0) {
            candidate = candidate.plusDays(1)
            if (candidate.dayOfWeek != DayOfWeek.SATURDAY && candidate.dayOfWeek != DayOfWeek.SUNDAY) remaining--
        }
        return candidate
    }
    private val dayAfterTomorrowRegex = Regex("\\b(?:day\\s+after\\s+tomorrow|pasado\\s+mañana|pasado\\s+manana|depois\\s+de\\s+amanhã|depois\\s+de\\s+amanha|après[-\\s]demain|apres[-\\s]demain)\\b", RegexOption.IGNORE_CASE)
    private val fortnightRegex = Regex("\\b(?:(?:in|en|em|dans)\\s+)?(?:a\\s+|una\\s+|uma\\s+|une\\s+)?(?:fortnight|quincena|quinzena|quinzaine)\\b", RegexOption.IGNORE_CASE)
    /**
     * Start-date phrases: a "not before" keyword plus the date phrase it governs, which group 2
     * captures for [NaturalLanguageParser.parse] to resolve on its own.
     *
     * "start"/"starts"/"starting" needs the trailing anchor to be careful â€” "start the report"
     * is a title, not a start date. Group 2 therefore only accepts a date-ish lead-in
     * (a digit, or one of the words that can begin a date phrase), and the whole rule no-ops when
     * the resolver can't make a date out of what follows. "not before" and "defer (to|until)"
     * are unambiguous enough to take anything.
     */
    private val startDateRegex = Regex(
        "\\b(starts?|starting|begins?|beginning|not\\s+before|defer(?:red)?(?:\\s+(?:to|until|till))?|available|from|empieza|empezar|comienza|comenzar|começa|comecar|começar|inicia|iniciar|desde|no\\s+antes\\s+de|não\\s+antes\\s+de|nao\\s+antes\\s+de|commence|commencer|débute|debute|début|debut|à\\s+partir\\s+de|pas\\s+avant)\\s+" +
            "((?:\\d|next\\b|this\\b|tomorrow\\b|today\\b|the\\b|in\\b|próximo\\b|proximo\\b|próxima\\b|proxima\\b|prochain\\b|prochaine\\b|este\\b|esta\\b|ce\\b|cet\\b|cette\\b|mañana\\b|manana\\b|amanhã\\b|amanha\\b|demain\\b|hoy\\b|hoje\\b|aujourd|el\\b|em\\b|en\\b|dans\\b|le\\b|lun|mar|mié|mie|jue|vie|sáb|sab|dom|seg|ter|qua|qui|sex|lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|mon|tue|wed|thu|fri|sat|sun|ene|feb|mar|abr|apr|mai|may|jun|jul|ago|aug|sep|oct|nov|dic|dec|jan)[A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõçÀÂÊÎÔÛÄËÏÖÜàâêîôûäëïöü0-9,/\\-\\s]*?)" +
            "(?=\\s+(?:due|vence|échéance|at|a\\s+las?|às?|à|every|cada|todo|toda|chaque|assign|asign|atrib|@|#|!|p[1-3]|for\\b|por\\b|pour\\b|in\\s+(?:list|project)|em\\s+(?:lista|projeto)|en\\s+(?:liste|projet|lista|proyecto))|$)",
        RegexOption.IGNORE_CASE
    )
    private val fromNowRegex = Regex("\\b(a|an|um|uma|un|une|\\d+)\\s+(day|week|month|dia|semana|mês|mes|jour|semaine|mois)s?\\s+(?:from\\s+(?:now|today)|a\\s+partir\\s+de\\s+(?:agora|hoje)|à\\s+partir\\s+d['’]?aujourd['’]?hui)\\b", RegexOption.IGNORE_CASE)
    // "the 20th" / "on the 20th" with no month named â€” nearest upcoming month that has that day.
    private val ordinalDayOfMonthRegex = Regex("\\b(?:on\\s+)?the\\s+(\\d{1,2})(?:st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)
    /**
     * The same thing spelled out: "on the first", "the twenty-first". Only ever reached via an
     * explicit "the", because bare "first"/"second" are ordinary words ("first draft", "second
     * opinion") and claiming those would eat real titles.
     */
    private val ordinalWords: Map<String, Int> = buildMap {
        val units = listOf(
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
            "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
            "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18,
            "nineteenth" to 19, "twentieth" to 20, "thirtieth" to 30
        )
        units.forEach { (word, n) -> put(word, n) }
        val cardinals = listOf(
            "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9
        )
        // "twenty first" / "twenty-first" / "twentyfirst", and the thirties.
        cardinals.forEach { (word, n) ->
            listOf("twenty $word" to 20 + n, "twenty-$word" to 20 + n, "twenty$word" to 20 + n).forEach { (k, v) -> put(k, v) }
        }
        put("thirty first", 31); put("thirty-first", 31); put("thirtyfirst", 31)
    }
    private val ordinalWordAlt by lazy { ordinalWords.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } }
    private val ordinalWordDayRegex by lazy {
        Regex("\\b(?:on\\s+)?the\\s+($ordinalWordAlt)\\b", RegexOption.IGNORE_CASE)
    }
    private fun nextQuarterStart(ref: LocalDate): LocalDate =
        ref.withDayOfMonth(1).plusMonths((3 - (ref.monthValue - 1) % 3).toLong())

    /**
     * Order is priority: the first phrase that appears anywhere in the input wins, so every
     * phrase must be listed before any shorter phrase it contains. "beginning of next month"
     * ahead of "next month" is the reason that one resolves to the 1st rather than to today's
     * date a month out.
     */
    private val phraseDates = listOf(
        // Longer/more specific phrases before their shorter substrings â€” "next weekend" must
        // be checked as its own phrase since "weekend" isn't a weekday the generic "next
        // <weekday>" rule below understands, and it'd otherwise silently fail to match at all.
        "beginning of next month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start of next month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "end of next month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "next weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "next quarter" to { ref: LocalDate -> nextQuarterStart(ref) },
        "end of quarter" to { ref: LocalDate -> nextQuarterStart(ref).minusDays(1) },
        "end of the quarter" to { ref: LocalDate -> nextQuarterStart(ref).minusDays(1) },
        "next week" to { ref: LocalDate -> ref.plusWeeks(1) },
        "next month" to { ref: LocalDate -> ref.plusMonths(1) },
        "next year" to { ref: LocalDate -> ref.plusYears(1) },
        "beginning of month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "beginning of the month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start of month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start of the month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "middle of the month" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "mid month" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "end of the month" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "end of month" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "end of the week" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "end of week" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "end of the year" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "end of year" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "later this week" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.FRIDAY) },
        "this weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "over the weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "on the weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "principios del mes que viene" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "inicio del mes que viene" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "fin del mes que viene" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "final del mes que viene" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "próximo fin de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "proximo fin de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "próxima semana" to { ref: LocalDate -> ref.plusWeeks(1) },
        "proxima semana" to { ref: LocalDate -> ref.plusWeeks(1) },
        "semana que viene" to { ref: LocalDate -> ref.plusWeeks(1) },
        "próximo mes" to { ref: LocalDate -> ref.plusMonths(1) },
        "proximo mes" to { ref: LocalDate -> ref.plusMonths(1) },
        "mes que viene" to { ref: LocalDate -> ref.plusMonths(1) },
        "próximo año" to { ref: LocalDate -> ref.plusYears(1) },
        "proximo ano" to { ref: LocalDate -> ref.plusYears(1) },
        "año que viene" to { ref: LocalDate -> ref.plusYears(1) },
        "ano que viene" to { ref: LocalDate -> ref.plusYears(1) },
        "principios de mes" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "inicio de mes" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "mitad de mes" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "fin de mes" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "final de mes" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "fin de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "final de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "fin de año" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "fin de ano" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "início do próximo mês" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "inicio do proximo mes" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "fim do próximo mês" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "fim do proximo mes" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "próximo fim de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "proximo fim de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "próxima semana" to { ref: LocalDate -> ref.plusWeeks(1) },
        "proxima semana" to { ref: LocalDate -> ref.plusWeeks(1) },
        "semana que vem" to { ref: LocalDate -> ref.plusWeeks(1) },
        "próximo mês" to { ref: LocalDate -> ref.plusMonths(1) },
        "proximo mes" to { ref: LocalDate -> ref.plusMonths(1) },
        "mês que vem" to { ref: LocalDate -> ref.plusMonths(1) },
        "mes que vem" to { ref: LocalDate -> ref.plusMonths(1) },
        "próximo ano" to { ref: LocalDate -> ref.plusYears(1) },
        "ano que vem" to { ref: LocalDate -> ref.plusYears(1) },
        "início do mês" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "inicio do mes" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "meio do mês" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "meio do mes" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "fim do mês" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "fim do mes" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "fim de semana" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "fim do ano" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "début du mois prochain" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "debut du mois prochain" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "fin du mois prochain" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "week-end prochain" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "weekend prochain" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "semaine prochaine" to { ref: LocalDate -> ref.plusWeeks(1) },
        "mois prochain" to { ref: LocalDate -> ref.plusMonths(1) },
        "année prochaine" to { ref: LocalDate -> ref.plusYears(1) },
        "annee prochaine" to { ref: LocalDate -> ref.plusYears(1) },
        "début du mois" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "debut du mois" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "milieu du mois" to { ref: LocalDate -> resolveOrdinalDayOfMonth(15, ref) ?: ref },
        "fin du mois" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "week-end" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY) },
        "fin de semaine" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "fin d'année" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "fin d'annee" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) },
        "bom" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "eom" to { ref: LocalDate -> YearMonth.from(ref).atEndOfMonth() },
        "eow" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SUNDAY) },
        "eoy" to { ref: LocalDate -> LocalDate.of(ref.year, 12, 31) }
        // "eod"/"eob"/"cob" are handled separately below (section 3) since â€” unlike every other
        // entry here â€” they also imply a clock time, not just a date.
    )

    /**
     * Date phrases that carry a clock time too, checked ahead of the bare "today"/"tomorrow"
     * words. They have to run first because each one *contains* one of those words: "a week
     * today" would otherwise match "today" and resolve to today, the opposite of what it means.
     */
    private val phraseDateTimes: List<Triple<String, (LocalDate) -> LocalDate, LocalTime?>> = listOf(
        Triple("a week today", { ref: LocalDate -> ref.plusWeeks(1) }, null),
        Triple("a week tomorrow", { ref: LocalDate -> ref.plusWeeks(1).plusDays(1) }, null),
        Triple("this morning", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("this afternoon", { ref: LocalDate -> ref }, LocalTime.of(15, 0)),
        Triple("this evening", { ref: LocalDate -> ref }, LocalTime.of(18, 0)),
        Triple("later tonight", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
        Triple("later today", { ref: LocalDate -> ref }, null),
        Triple("esta mañana", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("esta manana", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("esta tarde", { ref: LocalDate -> ref }, LocalTime.of(18, 0)),
        Triple("esta noche", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
        Triple("mañana por la mañana", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("manana por la manana", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("mañana por la tarde", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("manana por la tarde", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("mañana por la noche", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("manana por la noche", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("esta manhã", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("esta manha", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("esta tarde", { ref: LocalDate -> ref }, LocalTime.of(18, 0)),
        Triple("esta noite", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
        Triple("amanhã de manhã", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("amanha de manha", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("amanhã à tarde", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("amanha a tarde", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("amanhã à noite", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("amanha a noite", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("ce matin", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("cet après-midi", { ref: LocalDate -> ref }, LocalTime.of(15, 0)),
        Triple("cet apres-midi", { ref: LocalDate -> ref }, LocalTime.of(15, 0)),
        Triple("ce soir", { ref: LocalDate -> ref }, LocalTime.of(18, 0)),
        Triple("cette nuit", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
        Triple("demain matin", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("demain après-midi", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(15, 0)),
        Triple("demain apres-midi", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(15, 0)),
        Triple("demain soir", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0))
    )
    private val eodTime: LocalTime = LocalTime.of(18, 0)
    private val eobTime: LocalTime = LocalTime.of(17, 0)
    private val bareDateWords = listOf(
        "today" to { ref: LocalDate -> ref },
        "tomorrow" to { ref: LocalDate -> ref.plusDays(1) },
        "tmrw" to { ref: LocalDate -> ref.plusDays(1) },
        "yesterday" to { ref: LocalDate -> ref.minusDays(1) },
        "hoy" to { ref: LocalDate -> ref },
        "mañana" to { ref: LocalDate -> ref.plusDays(1) },
        "manana" to { ref: LocalDate -> ref.plusDays(1) },
        "ayer" to { ref: LocalDate -> ref.minusDays(1) },
        "hoje" to { ref: LocalDate -> ref },
        "amanhã" to { ref: LocalDate -> ref.plusDays(1) },
        "amanha" to { ref: LocalDate -> ref.plusDays(1) },
        "ontem" to { ref: LocalDate -> ref.minusDays(1) },
        "aujourd'hui" to { ref: LocalDate -> ref },
        "aujourd’hui" to { ref: LocalDate -> ref },
        "demain" to { ref: LocalDate -> ref.plusDays(1) },
        "hier" to { ref: LocalDate -> ref.minusDays(1) }
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

    // â”€â”€ Absolute month/day dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "Jul 20", "July 20", "20 July", "20th July", optionally with a trailing year
    // ("July 20 2026" / "July 20, 2026"). No year given â†’ nearest occurrence on/after
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
        "dec" to 12, "december" to 12,
        "ene" to 1, "enero" to 1,
        "febrero" to 2,
        "marzo" to 3,
        "abr" to 4, "abril" to 4,
        "mayo" to 5,
        "junio" to 6,
        "julio" to 7,
        "ago" to 8, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9,
        "octubre" to 10,
        "noviembre" to 11,
        "dic" to 12, "diciembre" to 12,
        "janeiro" to 1,
        "fevereiro" to 2,
        "março" to 3, "marco" to 3,
        "maio" to 5,
        "junho" to 6,
        "julho" to 7,
        "set" to 9, "setembro" to 9,
        "out" to 10, "outubro" to 10,
        "dez" to 12, "dezembro" to 12,
        "janvier" to 1,
        "février" to 2, "fevrier" to 2, "fév" to 2, "fev" to 2,
        "mars" to 3,
        "avr" to 4, "avril" to 4,
        "juin" to 6,
        "juillet" to 7,
        "août" to 8, "aout" to 8,
        "septembre" to 9,
        "octobre" to 10,
        "novembre" to 11,
        "déc" to 12, "decembre" to 12, "décembre" to 12
    )
    private val monthAlt = monthNames.keys.joinToString("|") { Regex.escape(it) }
    private val monthDayRegex = Regex("\\b($monthAlt)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    // Optional leading "the" / mid "of" so "the 20th of july" also resolves as a full date
    // instead of falling through to the bare ordinalDayOfMonthRegex below and losing the month.
    private val dayMonthRegex = Regex("\\b(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($monthAlt)\\.?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)

    // â”€â”€ Numeric dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // ISO "2026-07-20" is unambiguous, checked first. "7/20" / "7/20/2026" / "7/20/26" default
    // to US month/day order (matching the month-name rules above), but swap automatically when
    // the first number can't be a month (e.g. "20/7" -> day/month) so both conventions parse.
    private val isoDateRegex = Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b")
    private val slashDateRegex = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    /**
     * "20.07.2026" and "20-07-2026". Both demand all three components including the year â€”
     * a two-part "1.5" is a version number far more often than it is the 5th of January, and a
     * two-part "3-4" is a range. The year makes the intent unambiguous, so only that form parses.
     */
    private val dottedDateRegex = Regex("\\b(\\d{1,2})[.\\-](\\d{1,2})[.\\-](\\d{2,4})\\b")
    // "mid july" / "mid-july" â€” the 15th, which is what people mean by the middle of a month.
    private val midMonthNameRegex by lazy {
        Regex("\\bmid[-\\s]?($monthAlt)\\b", RegexOption.IGNORE_CASE)
    }

    private fun resolveIsoDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: java.time.DateTimeException) {
        null
    }

    private fun resolveSlashDate(n1: Int, n2: Int, yearRaw: String?, ref: LocalDate, dayFirst: Boolean): LocalDate? {
        val (month, day) = when {
            // Only this first case is genuinely ambiguous — "3/4" is either. Which way it reads
            // follows the date-order preference, so it agrees with how the app writes dates back
            // out. The other two are decided by arithmetic: 20 can't be a month.
            n1 in 1..12 && n2 in 1..12 -> if (dayFirst) n2 to n1 else n1 to n2
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

    // â”€â”€ Reminder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "remind"/"remind me" phrases set the *reminder*, distinct from the due time â€” checked
    // before due-time parsing so "remind at 5pm" doesn't leave a stray "5pm" behind for the
    // due-time rule to also claim as the task's own due time.
    private val remindAtTimeKeywordRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:(?:at|on)\\s+time|a\\s+la\\s+hora|na\\s+hora|à\\s+l['’]?heure)\\b", RegexOption.IGNORE_CASE)
    private val remindMinutesBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(\\d+)\\s*(?:min|mins|minute|minutes|minuto|minutos)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindHourBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:hour|hora|heure)|an?\\s+hour|una\\s+hora|uma\\s+hora|une\\s+heure)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindDayBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:day|día|dia|jour)|a\\s+day|un\\s+día|un\\s+dia|um\\s+dia|une\\s+jour|un\\s+jour)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindAtClockTimeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:(?:at|a\\s+las?|às?|as|à|a)\\s+)?(\\d{1,2})([:.](\\d{2}))?\\s*(am|pm|AM|PM)\\b", RegexOption.IGNORE_CASE)

    // â”€â”€ Priority â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "!1"/"!!1" (etc.) â€” 1 is the most urgent, matching the common "p1 is highest" convention.
    private val priorityShorthandRegex = Regex("!{1,2}([1-3])\\b")
    // Bare "p1"/"p2"/"p3" (no "!") â€” same convention, checked alongside the "!N" shorthand
    // since it's just as explicit, before falling through to the word-phrase list below.
    private val priorityBareRegex = Regex("\\bp([1-3])\\b", RegexOption.IGNORE_CASE)
    // Multi-word phrases first so "high priority" claims itself whole rather than leaving a
    // dangling "priority" behind for a later rule to trip over.
    private val priorityWordPhrases = listOf(
        // Negations first: "not urgent" contains "urgent", and whichever is listed first wins,
        // so putting these anywhere below would read the phrase as the exact opposite of itself.
        "not urgent" to "low",
        "non urgent" to "low",
        "not important" to "low",
        "not critical" to "low",
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
        "drop everything" to "high",
        "high prio" to "high",
        "top prio" to "high",
        "medium priority" to "med",
        "med priority" to "med",
        "normal priority" to "med",
        "medium prio" to "med",
        "med prio" to "med",
        "normal prio" to "med",
        "lowest priority" to "low",
        "low priority" to "low",
        "minor priority" to "low",
        "low prio" to "low",
        "back burner" to "low",
        "backburner" to "low",
        "nice to have" to "low",
        "if i have time" to "low",
        "when i can" to "low",
        "when i get a chance" to "low",
        "not urgent" to "low",
        "eventually" to "low",
        "someday" to "low",
        "whenever" to "low",
        "no rush" to "low",
        "máxima prioridad" to "high",
        "maxima prioridad" to "high",
        "alta prioridad" to "high",
        "prioridad alta" to "high",
        "muy urgente" to "high",
        "urgente" to "high",
        "crítico" to "high",
        "critico" to "high",
        "importante" to "high",
        "cuanto antes" to "high",
        "lo antes posible" to "high",
        "prioridad media" to "med",
        "media prioridad" to "med",
        "prioridad normal" to "med",
        "baja prioridad" to "low",
        "prioridad baja" to "low",
        "sin prisa" to "low",
        "cuando pueda" to "low",
        "algún día" to "low",
        "algun dia" to "low",
        "prioridade máxima" to "high",
        "prioridade maxima" to "high",
        "alta prioridade" to "high",
        "prioridade alta" to "high",
        "muito urgente" to "high",
        "urgente" to "high",
        "crítico" to "high",
        "critico" to "high",
        "importante" to "high",
        "o quanto antes" to "high",
        "quanto antes" to "high",
        "prioridade média" to "med",
        "prioridade media" to "med",
        "prioridade normal" to "med",
        "baixa prioridade" to "low",
        "prioridade baixa" to "low",
        "sem pressa" to "low",
        "quando puder" to "low",
        "priorité maximale" to "high",
        "priorite maximale" to "high",
        "haute priorité" to "high",
        "haute priorite" to "high",
        "priorité haute" to "high",
        "priorite haute" to "high",
        "très urgent" to "high",
        "tres urgent" to "high",
        "urgent" to "high",
        "critique" to "high",
        "dès que possible" to "high",
        "des que possible" to "high",
        "priorité moyenne" to "med",
        "priorite moyenne" to "med",
        "priorité normale" to "med",
        "priorite normale" to "med",
        "basse priorité" to "low",
        "basse priorite" to "low",
        "priorité basse" to "low",
        "priorite basse" to "low",
        "pas urgent" to "low",
        "quand je peux" to "low"
    )

    // â”€â”€ Flag â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val flagPhrases = listOf(
        "flag this", "flag it", "flagged", "star this", "star it", "starred",
        "important", "mark as important", "bookmark", "bookmarked",
        "marcar", "marcar esto", "marcada", "destacar", "destacado", "importante", "marcar como importante",
        "sinalizar", "sinalizado", "destacar isto", "marcar como importante",
        "marquer", "marqué", "marquee", "signaler", "favori", "mettre en favori", "marquer comme important"
    )

    // â”€â”€ Additional relative dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val inHoursRegex = Regex("\\b(?:in|en|em|dans)\\s+(a|an|un|una|um|uma|une|\\d+)\\s+(?:hour|hora|heure)(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMinutesRegex = Regex("\\b(?:in|en|em|dans)\\s+(a|an|un|una|um|uma|une|\\d+)\\s+(?:min(?:ute)?|minuto|minute)s?\\b", RegexOption.IGNORE_CASE)
    private val halfAnHourRegex = Regex("\\b(?:in|en|em|dans)\\s+(?:half\\s+(?:an?\\s+)?hour|media\\s+hora|meia\\s+hora|une\\s+demi[-\\s]heure|demi[-\\s]heure)\\b", RegexOption.IGNORE_CASE)
    /**
     * Word/phrase rules are stored as plain strings and only become regexes here. Compiling them
     * inline meant rebuilding ~90 `Regex` objects on every call, and [parse] runs on every
     * keystroke in the quick-add field â€” the phrase lists have grown enough for that to be worth
     * paying once instead of per character typed.
     */
    private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun cachedWordRegex(word: String): Regex =
        wordRegexCache.getOrPut(word) { Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE) }

    /** As [cachedWordRegex], but also absorbing a trailing "ish"/"-ish". */
    private fun cachedIshWordRegex(word: String): Regex =
        wordRegexCache.getOrPut("ish:$word") {
            Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?:\\s*-?\\s*ish)?(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        }

    /**
     * How much of a "<keyword> <date phrase>" match to actually claim.
     *
     * The captured phrase is lazy and bounded by a lookahead listing the words that can follow a
     * date. When none of them appears the capture is only stopped by end-of-input, so it swallows
     * the rest of the sentence — "every week until dec 20 sync" captures "dec 20 sync". Claiming
     * the whole match then strips the title down to nothing.
     *
     * The nested parse already knows exactly which spans it recognized, so the claim ends at the
     * last of those rather than at the end of the greedy capture. Everything after it is title.
     */
    private fun claimEndFor(match: MatchResult, groupIndex: Int, nested: ParsedQuickAdd): Int {
        val group = match.groups[groupIndex] ?: return match.range.last
        val lastRecognized = nested.highlightRanges.maxOfOrNull { it.last } ?: return match.range.last
        return (group.range.first + lastRecognized)
            .coerceIn(group.range.first, match.range.last)
    }

    private val cacheLock = Any()
    private val parseCache = object : java.util.LinkedHashMap<String, ParsedQuickAdd>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParsedQuickAdd>?): Boolean {
            return size > 64
        }
    }

    /**
     * @param dayFirst how to read an ambiguous numeric date like "3/4". Defaults to whatever the
     *   user's date-order setting resolves to, so typing a date and reading one back agree.
     */
    fun parse(
        rawInput: String,
        referenceDate: LocalDate = LocalDate.now(),
        referenceTime: LocalTime = LocalTime.now(),
        dayFirst: Boolean = AppFormats.dayFirstDates()
    ): ParsedQuickAdd {
        // The reference *time* belongs in the key as well as the date: "in 30 minutes" resolves
        // against it, so keying on the date alone served a stale clock time to every later call
        // with the same text. Truncated to the minute, which is the resolution the result has.
        val cacheKey = "$rawInput|$referenceDate|${referenceTime.hour}:${referenceTime.minute}|$dayFirst"
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
        // Declared up here rather than in section 3 because the monthly-on-a-date recurrence rules
        // resolve a due date of their own, well before the due-date section runs.
        var dueRange: IntRange? = null
        var time: String? = null
        var recurrence: Recurrence? = null

        // Escape: a backslash directly before a word protects that word from being read as a
        // date/time/recurrence keyword â€” e.g. "call mom \today" keeps "today" as literal text
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
        fun firstFreeWord(word: String) = firstFreeMatch(cachedWordRegex(word))

        // 1. Recurrence â€” checked first so "every sunday"/"every monday" is claimed whole
        // before the later bare-weekday due-date rule can also match "sunday"/"monday".
        everyAlternateDayRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("daily", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateWeekRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("weekly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateMonthRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("monthly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) everyAlternateYearRegex.let { firstFreeMatch(it) }?.let { m -> recurrence = Recurrence("yearly", 2, null, null, RecurrenceEnds.Never); claim(m.range) }
        if (recurrence == null) firstFreeMatch(everyNDaysRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("daily", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNWeeksRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("weekly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNMonthsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("monthly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        if (recurrence == null) firstFreeMatch(everyNYearsRegex)?.let { m -> m.groupValues[1].toIntOrNull()?.let { n -> recurrence = Recurrence("yearly", n, null, null, RecurrenceEnds.Never); claim(m.range) } }
        // "every last day of the month" before "every month on the Nth" â€” the former's "last day"
        // would otherwise fall through and be read as a bare monthly with no day pinned at all.
        if (recurrence == null) {
            firstFreeMatch(everyLastDayOfMonthRegex)?.let { m ->
                recurrence = Recurrence("monthly", 1, null, -1, RecurrenceEnds.Never)
                claim(m.range)
                if (due == null) {
                    due = YearMonth.from(referenceDate).atEndOfMonth()
                        .let { if (it.isBefore(referenceDate)) YearMonth.from(referenceDate).plusMonths(1).atEndOfMonth() else it }
                    dueRange = m.range
                }
            }
        }
        // Monthly pinned to a date, said either way round. Both also set the due date to the next
        // occurrence of that day â€” the series says *which* day, and the first one is still coming.
        if (recurrence == null) {
            (firstFreeMatch(everyMonthOnDayRegex) ?: firstFreeMatch(everyOrdinalOfMonthRegex))?.let { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                    recurrence = Recurrence("monthly", 1, null, day, RecurrenceEnds.Never)
                    claim(m.range)
                    if (due == null) {
                        resolveOrdinalDayOfMonth(day, referenceDate)?.let { d -> due = d; dueRange = m.range }
                    }
                }
            }
        }
        // Several weekdays at once â€” before the single-weekday rule below, see its own comment.
        if (recurrence == null) {
            firstFreeMatch(everyMultiWeekdayRegex)?.let { m ->
                val days = multiWeekdaySplitRegex.split(m.groupValues[1])
                    .mapNotNull { weekdayNames[it.trim().lowercase()] }
                    .distinct()
                    .sortedBy { it.value }
                if (days.isNotEmpty()) {
                    recurrence = Recurrence("weekly", 1, days.map { rruleDay.getValue(it) }, null, RecurrenceEnds.Never)
                    claim(m.range)
                }
            }
        }
        if (recurrence == null) {
            firstFreeMatch(everyWeekdayRegex)?.let { m ->
                val token = m.groupValues[1].lowercase()
                val rec = when {
                    token == "day" || token == "día" || token == "dia" || token == "jour" -> Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
                    token == "week" || token == "semana" || token == "semaine" -> Recurrence("weekly", 1, null, null, RecurrenceEnds.Never)
                    token == "month" || token == "mes" || token == "mês" || token == "mois" -> Recurrence("monthly", 1, null, null, RecurrenceEnds.Never)
                    token == "year" || token == "año" || token == "ano" || token == "an" || token == "année" || token == "annee" -> Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
                    token == "quarter" || token == "qtr" || token == "trimestre" -> Recurrence("monthly", 3, null, null, RecurrenceEnds.Never)
                    // Singular "every weekday"/"every weekend" â€” the bare-word list below only has
                    // the plurals, so without these the whole phrase silently matched nothing.
                    token == "weekday" || token == "weekdays" || token == "laborable" || token == "laborables" || token == "útil" || token == "util" || token == "úteis" || token == "uteis" || token == "ouvrable" || token == "ouvrables" -> Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never)
                    token == "weekend" || token == "weekends" || token == "week-end" -> Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never)
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

        // 1.2 When the series stops. Only meaningful with a recurrence, and claimed here rather
        // than later so the date inside "until dec 20" can't be mistaken for the due date.
        // The end date is resolved by recursing on the captured phrase, the same trick the start
        // date uses; the capture can't contain another "until", so it bottoms out at depth 1.
        if (recurrence != null) {
            firstFreeMatch(recurrenceUntilRegex)?.let { m ->
                val phrase = m.groupValues[1]
                if (phrase.isNotBlank()) {
                    val nested = parse(phrase, referenceDate, referenceTime, dayFirst)
                    nested.due?.let { endDate ->
                        recurrence = recurrence!!.copy(ends = RecurrenceEnds.On(endDate))
                        claim(m.range.first..claimEndFor(m, 1, nested))
                    }
                }
            }
            if (recurrence!!.ends == RecurrenceEnds.Never) {
                firstFreeMatch(recurrenceTimesRegex)?.let { m ->
                    m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { n ->
                        recurrence = recurrence!!.copy(ends = RecurrenceEnds.After(n))
                        claim(m.range)
                    }
                }
            }
        }

        // 1.5 Reminder â€” see the regexes' own comment for why this runs before due-time parsing.
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

        // 2. Explicit time â€” checked before day-count phrases so "tomorrow 3pm" doesn't have
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
            firstFreeMatch(mealTimeRegex)?.let { m ->
                mealTimes[m.groupValues[1].lowercase()]?.let { clock ->
                    time = clock.format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            firstFreeMatch(ishTimeRegex)?.let { m ->
                m.groupValues[1].toIntOrNull()?.takeIf { it in 1..23 }?.let { hour ->
                    val hour24 = if (hour in 1..7) hour + 12 else hour
                    time = LocalTime.of(hour24 % 24, 0).format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                }
            }
        }

        // "first thing" sets only the time, never the date â€” that way "first thing monday" lets
        // the date rules have "monday" instead of being pinned to today by the phrase itself.
        if (time == null) {
            firstFreeMatch(firstThingRegex)?.let { m ->
                time = LocalTime.of(9, 0).format(timeFormatter).uppercase(Locale.getDefault())
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

        // 2.5 Start date â€” "starts monday", "from next week", "defer to the 15th". Must run
        // before section 3, or the bare date inside the phrase gets claimed as the *due* date and
        // "starts monday" silently means the opposite of what it says.
        //
        // The date phrase itself is resolved by recursing into parse() on just the captured text,
        // rather than duplicating the ~160 lines of date rules below. The recursion terminates at
        // depth 1: the captured group can't contain another start keyword, since the keyword is
        // what delimits it. Only the resolved date is taken from the nested result â€” its title,
        // priority and everything else are discarded.
        var startDate: LocalDate? = null
        firstFreeMatch(startDateRegex)?.let { m ->
            val phrase = m.groupValues[2]
            if (phrase.isNotBlank()) {
                val nested = NaturalLanguageParser.parse(phrase, referenceDate, referenceTime, dayFirst)
                nested.due?.let { resolved ->
                    startDate = runCatching { LocalDate.parse(resolved) }.getOrNull()
                    // Claim the keyword *and* the date words it governs, so they're off-limits to
                    // every rule below and get stripped from the saved title together — but only
                    // as far as the date actually reaches, see [claimEndFor].
                    if (startDate != null) claim(m.range.first..claimEndFor(m, 2, nested))
                }
            }
        }

        // 3. Relative dates
        firstFreeWord("tonight")?.let { m ->
            due = referenceDate
            if (time == null) time = timeOfDayWords.getValue("night").format(timeFormatter).uppercase(Locale.getDefault())
            claim(m.range)
            dueRange = m.range
        }
        // Date phrases that also imply a clock time. Before the bare "today"/"tomorrow" words,
        // since each one contains one of them â€” see [phraseDateTimes].
        if (due == null) {
            for ((phrase, resolve, clock) in phraseDateTimes) {
                firstFreeMatch(cachedWordRegex(phrase))?.let { m ->
                    due = resolve(referenceDate)
                    if (clock != null && time == null) time = clock.format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                    dueRange = m.range
                }
                if (due != null) break
            }
        }
        if (due == null) {
            for ((alias, target) in customDateAliases.entries.sortedByDescending { it.key.length }) {
                firstFreeWord(alias)?.let { m ->
                    due = resolveDateAlias(target, referenceDate)
                    claim(m.range)
                    dueRange = m.range
                }
                if (due != null) break
            }
        }
        // "in N hour(s)"/"in N minute(s)"/"in half an hour" are the only relative-date phrases
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
        // Numeric dates â€” most explicit, checked before everything else in this section.
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
                    resolveSlashDate(n1, n2, yearRaw, referenceDate, dayFirst)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // "20.07.2026" / "20-07-2026" â€” same day/month disambiguation as the slash form.
        if (due == null) {
            firstFreeMatch(dottedDateRegex)?.let { m ->
                val n1 = m.groupValues[1].toIntOrNull()
                val n2 = m.groupValues[2].toIntOrNull()
                val yearRaw = m.groupValues[3].ifEmpty { null }
                if (n1 != null && n2 != null) {
                    resolveSlashDate(n1, n2, yearRaw, referenceDate, dayFirst)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // Absolute "Jul 20" / "20 July" style dates â€” checked early since they're explicit
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
        // "mid july" â€” the 15th of that month.
        if (due == null) {
            firstFreeMatch(midMonthNameRegex)?.let { m ->
                monthNames[m.groupValues[1].lowercase()]?.let { month ->
                    resolveMonthDay(month, 15, null, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // "day after tomorrow" â€” checked before the bare "tomorrow" word below so the whole
        // phrase is claimed at once instead of "tomorrow" alone matching first.
        if (due == null) {
            firstFreeMatch(dayAfterTomorrowRegex)?.let { m -> due = referenceDate.plusDays(2); claim(m.range); dueRange = m.range }
        }
        // "wednesday next week" â€” before the phrase list below, which owns the "next week" half
        // of it and would otherwise strand the weekday in the title.
        if (due == null) {
            firstFreeMatch(weekdayNextWeekRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextAfter(referenceDate, day); claim(m.range); dueRange = m.range }
            }
        }
        if (due == null) {
            for ((word, resolve) in bareDateWords) {
                firstFreeWord(word)?.let { m -> due = resolve(referenceDate); claim(m.range); dueRange = m.range }
                if (due != null) break
            }
        }
        if (due == null) {
            for ((phrase, resolve) in phraseDates) {
                firstFreeMatch(cachedWordRegex(phrase))?.let { m ->
                    due = resolve(referenceDate)
                    claim(m.range)
                    dueRange = m.range
                }
                if (due != null) break
            }
        }
        // "eod"/"eob"/"cob" â€” a clock time plus, on their own, today. Unlike every other phrase
        // here they say *when in the day*, not which day, so the date they imply is only a
        // fallback: "eod friday" means Friday at 6pm, not today. The date is therefore applied
        // at the end of this section, once every rule that can name a real day has had its turn.
        var endOfDayFallbackRange: IntRange? = null
        if (due == null && endOfDayFallbackRange == null) {
            firstFreeWord("eod")?.let { m ->
                if (time == null) time = eodTime.format(timeFormatter).uppercase(Locale.getDefault())
                claim(m.range)
                endOfDayFallbackRange = m.range
            }
        }
        if (due == null && endOfDayFallbackRange == null) {
            for (word in listOf("eob", "cob")) {
                firstFreeWord(word)?.let { m ->
                    if (time == null) time = eobTime.format(timeFormatter).uppercase(Locale.getDefault())
                    claim(m.range)
                    endOfDayFallbackRange = m.range
                }
                if (endOfDayFallbackRange != null) break
            }
        }
        if (due == null) {
            firstFreeMatch(fortnightRegex)?.let { m -> due = referenceDate.plusWeeks(2); claim(m.range); dueRange = m.range }
        }
        if (due == null) {
            firstFreeMatch(fromNowRegex)?.let { m ->
                val n = countOrOne(m.groupValues[1])
                due = when (m.groupValues[2].lowercase()) {
                    "day", "dia", "jour" -> referenceDate.plusDays(n)
                    "week", "semana", "semaine" -> referenceDate.plusWeeks(n)
                    "month", "mês", "mes", "mois" -> referenceDate.plusMonths(n)
                    else -> null
                }
                if (due != null) { claim(m.range); dueRange = m.range }
            }
        }
        // "the 20th" (no month named) â€” nearest upcoming month with that day.
        if (due == null) {
            firstFreeMatch(ordinalDayOfMonthRegex)?.let { m ->
                m.groupValues[1].toIntOrNull()?.let { day ->
                    resolveOrdinalDayOfMonth(day, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // The same, spelled out: "on the first", "the twenty-first".
        if (due == null) {
            firstFreeMatch(ordinalWordDayRegex)?.let { m ->
                ordinalWords[m.groupValues[1].lowercase()]?.let { day ->
                    resolveOrdinalDayOfMonth(day, referenceDate)?.let { d -> due = d; claim(m.range); dueRange = m.range }
                }
            }
        }
        // Weekdays-only count, before the plain day count below.
        if (due == null) {
            firstFreeMatch(inBusinessDaysRegex)?.let { m ->
                due = plusBusinessDays(referenceDate, countOrOne(m.groupValues[1])); claim(m.range); dueRange = m.range
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
        if (due == null) {
            firstFreeMatch(inYearsRegex)?.let { m -> due = referenceDate.plusYears(countOrOne(m.groupValues[1])); claim(m.range); dueRange = m.range }
        }
        // "next <weekday>" â€” nearest occurrence strictly after today.
        if (due == null) {
            firstFreeMatch(nextWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextAfter(referenceDate, day); claim(m.range); dueRange = m.range }
            }
        }
        // "this <weekday>" â€” nearest occurrence including today.
        if (due == null) {
            firstFreeMatch(thisWeekdayRegex)?.let { m ->
                weekdayNames[m.groupValues[1].lowercase()]?.let { day -> due = nextOrSame(referenceDate, day); claim(m.range); dueRange = m.range }
            }
        }
        // Bare weekday name (no this/next prefix) â€” nearest occurrence including today.
        if (due == null) {
            for ((name, day) in weekdayNames) {
                firstFreeWord(name)?.let { m -> due = nextOrSame(referenceDate, day); claim(m.range); dueRange = m.range }
                if (due != null) break
            }
        }
        // Nothing named a day, so "eod"/"eob"/"cob" means today after all.
        if (due == null && endOfDayFallbackRange != null) {
            due = referenceDate
            dueRange = endOfDayFallbackRange
        }

        // 3.5 "remind <date>" â€” a bare "remind"/"remind me" immediately before a date phrase
        // (no offset/clock-time suffix, since those are already claimed in section 1.5) implies
        // the reminder should fire at the task's due time.
        if (reminder == null && dueRange != null) {
            val range = dueRange!!
            val prefix = raw.substring(0, range.first)
            Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio)\\s*$", RegexOption.IGNORE_CASE).find(prefix)?.let { m ->
                if (isFree(m.range)) {
                    reminder = "At time"
                    claim(m.range)
                }
            }
        }

        if (time == null) {
            for ((word, clock) in timeOfDayWords) {
                // The optional "-ish" is part of the match so it's stripped with the word it
                // qualifies, rather than being left stranded in the title ("noon-ish" -> "ish").
                firstFreeMatch(cachedIshWordRegex(word))?.let { m ->
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

        // 5. Priority shorthand â€” requires a non-alphanumeric char (or start of string) right
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
        // Bare "p1"/"p2"/"p3" â€” same convention as the "!N" shorthand, checked next since
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
        // Word-based priority â€” only if "!N"/"pN" above didn't already set one.
        if (priority == null) {
            for ((phrase, level) in priorityWordPhrases) {
                firstFreeMatch(cachedWordRegex(phrase))?.let { m ->
                    priority = level
                    claim(m.range)
                }
                if (priority != null) break
            }
        }

        // 6. Flag â€” independent of priority (a task can be both flagged and low-priority).
        var flag = false
        for (phrase in flagPhrases) {
            firstFreeMatch(cachedWordRegex(phrase))?.let { m ->
                flag = true
                claim(m.range)
            }
            if (flag) break
        }

        // 7. Project, List, Tag, Assignee keywords
        var projectName: String? = null
        firstFreeMatch(Regex("\\b(?:in\\s+project|for\\s+project|under\\s+project|project|en\\s+proyecto|para\\s+proyecto|bajo\\s+proyecto|proyecto|em\\s+projeto|para\\s+projeto|projeto|dans\\s+projet|pour\\s+projet|projet)\\s+([A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõçÀÂÊÎÔÛÄËÏÖÜàâêîôûäëïöü0-9_\\-\\s]+?)(?=$|\\s+(?:list|lista|liste|tag|etiqueta|étiquette|etiquette|tagged?|label|labeled?|#|assign(?:ed)?\\s+to|asignad[ao]\\s+a|asignar\\s+a|atribu[ií]d[ao]\\s+a|atribuir\\s+a|assigné\\s+à|assigne\\s+a|assigner\\s+à|give(?:n)?\\s+to|delegate|delegar|send\\s+to|assign|@|due|vence|échéance|echeance|at|a\\s+las?|às?|à|every|cada|todo|toda|chaque|on|el|le|!|p[1-3]))", RegexOption.IGNORE_CASE))?.let { m ->
            projectName = m.groupValues[1].trim()
            claim(m.range)
        }

        var listName: String? = null
        firstFreeMatch(Regex("\\b(?:in\\s+list|for\\s+list|under\\s+list|list|en\\s+lista|para\\s+lista|bajo\\s+lista|lista|em\\s+lista|dans\\s+liste|pour\\s+liste|liste)\\s+([A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõçÀÂÊÎÔÛÄËÏÖÜàâêîôûäëïöü0-9_\\-\\s]+?)(?=$|\\s+(?:project|proyecto|projeto|projet|tag|etiqueta|étiquette|etiquette|tagged?|label|labeled?|#|assign(?:ed)?\\s+to|asignad[ao]\\s+a|asignar\\s+a|atribu[ií]d[ao]\\s+a|atribuir\\s+a|assigné\\s+à|assigne\\s+a|assigner\\s+à|give(?:n)?\\s+to|delegate|delegar|send\\s+to|assign|@|due|vence|échéance|echeance|at|a\\s+las?|às?|à|every|cada|todo|toda|chaque|on|el|le|!|p[1-3]))", RegexOption.IGNORE_CASE))?.let { m ->
            listName = m.groupValues[1].trim()
            claim(m.range)
        }

        val tagNames = mutableListOf<String>()
        val tagMatches = Regex("(?<![\\p{L}\\p{N}_])(?:tagged?\\s+as\\s+|tagged?\\s+|tag\\s+as\\s+|tag\\s+|labeled?\\s+as\\s+|labeled?\\s+|label\\s+as\\s+|label\\s+|with\\s+tag\\s+|etiquetad[ao]\\s+como\\s+|etiquetad[ao]\\s+|etiqueta\\s+como\\s+|etiqueta\\s+|con\\s+etiqueta\\s+|marcad[ao]\\s+como\\s+|rótulo\\s+|rotulo\\s+|étiquette\\s+|etiquette\\s+|avec\\s+étiquette\\s+|avec\\s+etiquette\\s+)([A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõçÀÂÊÎÔÛÄËÏÖÜàâêîôûäëïöü0-9_\\-]+)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).findAll(raw)
        for (m in tagMatches) {
            if (isFree(m.range)) {
                tagNames.add(m.groupValues[1].trim())
                claim(m.range)
            }
        }

        val assigneeNames = mutableListOf<String>()
        val assigneeMatches = Regex("\\b(?:assign(?:ed)?\\s+to\\s+|give(?:n)?\\s+to\\s+|delegate(?:d)?\\s+to\\s+|send\\s+to\\s+|assign\\s+|asignad[ao]\\s+a\\s+|asignar\\s+a\\s+|delegad[ao]\\s+a\\s+|delegar\\s+a\\s+|enviar\\s+a\\s+|atribu[ií]d[ao]\\s+a\\s+|atribuir\\s+a\\s+|delegar\\s+para\\s+|enviar\\s+para\\s+|assigné\\s+à\\s+|assigne\\s+a\\s+|assigner\\s+à\\s+|assigner\\s+a\\s+|délégué\\s+à\\s+|delegue\\s+a\\s+|déléguer\\s+à\\s+|deleguer\\s+a\\s+|envoyer\\s+à\\s+|envoyer\\s+a\\s+|@)([A-Za-zÁÉÍÓÚÜÑáéíóúüñÃÕÇãõçÀÂÊÎÔÛÄËÏÖÜàâêîôûäëïöü0-9_\\-\\s]+?)(?=$|\\s+(?:project|proyecto|projeto|projet|list|lista|liste|tag|etiqueta|étiquette|etiquette|tagged?|label|labeled?|#|due|vence|échéance|echeance|at|a\\s+las?|às?|à|every|cada|todo|toda|chaque|on|el|le|!|p[1-3]))", RegexOption.IGNORE_CASE).findAll(raw)
        for (m in assigneeMatches) {
            if (isFree(m.range)) {
                assigneeNames.add(m.groupValues[1].trim())
                claim(m.range)
            }
        }

        val prepositionRegex = Regex("(?:^|\\s)(for|on|at|by|scheduled\\s+for|remind\\s+me\\s+for|remind\\s+me\\s+on|para|el|a\\s+las?|às?|à|programad[ao]\\s+para|recu[eé]rdame\\s+para|recu[eé]rdame\\s+el)\\s*$", RegexOption.IGNORE_CASE)

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
