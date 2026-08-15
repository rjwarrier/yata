package com.mj.yata.util

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.DateAliasDefinition
import com.mj.yata.domain.model.DateAliasTarget
import com.mj.yata.util.nl.DateRuleConfig
import com.mj.yata.util.nl.DateRules
import com.mj.yata.util.nl.EntityRules
import com.mj.yata.util.nl.NATURAL_LANGUAGE_NUMBER_COUNT
import com.mj.yata.util.nl.NaturalLanguageLexicon
import com.mj.yata.util.nl.ParseState
import com.mj.yata.util.nl.ParserContext
import com.mj.yata.util.nl.PriorityFlagRules
import com.mj.yata.util.nl.RecurrenceRuleConfig
import com.mj.yata.util.nl.RecurrenceRules
import com.mj.yata.util.nl.ReminderRules
import com.mj.yata.util.nl.StartDateRules
import com.mj.yata.util.nl.TimeRules
import com.mj.yata.util.nl.cleanNaturalLanguageTitle
import com.mj.yata.util.nl.naturalLanguageCountOrOne
import com.mj.yata.util.nl.normalizeNaturalLanguageInputWithRanges
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class QuickAddHighlightType {
    DueDate,
    StartDate,
    Time,
    Recurrence,
    Reminder,
    Priority,
    Flag,
    Project,
    List,
    Tag,
    Assignee,
    Other
}

data class QuickAddHighlightSpan(
    val range: IntRange,
    val type: QuickAddHighlightType
)

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
    val highlightRanges: List<IntRange>, // recognized spans in the *original* raw string, for underlining
    val highlightSpans: List<QuickAddHighlightSpan> = highlightRanges.map { QuickAddHighlightSpan(it, QuickAddHighlightType.Other) }
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
object NaturalLanguageParser {

    // Deliberately fixed at 12-hour, and not routed through the user's clock preference: what this
    // produces is written to `Task.time`, which is the storage format (see
    // TaskScheduleUtils.storageTimeFormatter). The preference is applied when the time is shown.
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    @Volatile private var customDateAliases: Map<String, DateAliasTarget> = emptyMap()

    private val weekdayNames = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "mo" to DayOfWeek.MONDAY, "mondy" to DayOfWeek.MONDAY, "mnday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tus" to DayOfWeek.TUESDAY, "tueday" to DayOfWeek.TUESDAY, "tuesdy" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wensday" to DayOfWeek.WEDNESDAY, "wendsday" to DayOfWeek.WEDNESDAY, "wednsday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thrs" to DayOfWeek.THURSDAY, "thurday" to DayOfWeek.THURSDAY, "thrusday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "fr" to DayOfWeek.FRIDAY, "firday" to DayOfWeek.FRIDAY, "fridy" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "saterday" to DayOfWeek.SATURDAY, "satrday" to DayOfWeek.SATURDAY, "satrdy" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY, "sundy" to DayOfWeek.SUNDAY,
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
        "dimanche" to DayOfWeek.SUNDAY,
        // Additional localized app languages: German, Italian, Dutch, Swedish, Polish, Romanian,
        // Turkish, Indonesian, and Vietnamese. ASCII fallbacks cover speech engines that strip accents.
        "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY,
        "lunedi" to DayOfWeek.MONDAY, "lunedì" to DayOfWeek.MONDAY, "martedi" to DayOfWeek.TUESDAY, "martedì" to DayOfWeek.TUESDAY,
        "mercoledi" to DayOfWeek.WEDNESDAY, "mercoledì" to DayOfWeek.WEDNESDAY, "giovedi" to DayOfWeek.THURSDAY, "giovedì" to DayOfWeek.THURSDAY,
        "venerdi" to DayOfWeek.FRIDAY, "venerdì" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY, "domenica" to DayOfWeek.SUNDAY,
        "maandag" to DayOfWeek.MONDAY, "dinsdag" to DayOfWeek.TUESDAY, "woensdag" to DayOfWeek.WEDNESDAY,
        "donderdag" to DayOfWeek.THURSDAY, "vrijdag" to DayOfWeek.FRIDAY, "zaterdag" to DayOfWeek.SATURDAY, "zondag" to DayOfWeek.SUNDAY,
        "måndag" to DayOfWeek.MONDAY, "mandag" to DayOfWeek.MONDAY, "tisdag" to DayOfWeek.TUESDAY, "onsdag" to DayOfWeek.WEDNESDAY,
        "torsdag" to DayOfWeek.THURSDAY, "fredag" to DayOfWeek.FRIDAY, "lördag" to DayOfWeek.SATURDAY, "lordag" to DayOfWeek.SATURDAY, "söndag" to DayOfWeek.SUNDAY, "sondag" to DayOfWeek.SUNDAY,
        "poniedziałek" to DayOfWeek.MONDAY, "poniedzialek" to DayOfWeek.MONDAY, "wtorek" to DayOfWeek.TUESDAY, "środa" to DayOfWeek.WEDNESDAY, "sroda" to DayOfWeek.WEDNESDAY,
        "czwartek" to DayOfWeek.THURSDAY, "piątek" to DayOfWeek.FRIDAY, "piatek" to DayOfWeek.FRIDAY, "sobota" to DayOfWeek.SATURDAY, "niedziela" to DayOfWeek.SUNDAY,
        "luni" to DayOfWeek.MONDAY, "marți" to DayOfWeek.TUESDAY, "marti" to DayOfWeek.TUESDAY, "miercuri" to DayOfWeek.WEDNESDAY,
        "joi" to DayOfWeek.THURSDAY, "vineri" to DayOfWeek.FRIDAY, "sâmbătă" to DayOfWeek.SATURDAY, "sambata" to DayOfWeek.SATURDAY, "duminică" to DayOfWeek.SUNDAY, "duminica" to DayOfWeek.SUNDAY,
        "pazartesi" to DayOfWeek.MONDAY, "salı" to DayOfWeek.TUESDAY, "sali" to DayOfWeek.TUESDAY, "çarşamba" to DayOfWeek.WEDNESDAY, "carsamba" to DayOfWeek.WEDNESDAY,
        "perşembe" to DayOfWeek.THURSDAY, "persembe" to DayOfWeek.THURSDAY, "cuma" to DayOfWeek.FRIDAY, "cumartesi" to DayOfWeek.SATURDAY, "pazar" to DayOfWeek.SUNDAY,
        "senin" to DayOfWeek.MONDAY, "selasa" to DayOfWeek.TUESDAY, "rabu" to DayOfWeek.WEDNESDAY, "kamis" to DayOfWeek.THURSDAY, "jumat" to DayOfWeek.FRIDAY, "sabtu" to DayOfWeek.SATURDAY, "minggu" to DayOfWeek.SUNDAY,
        "thứ hai" to DayOfWeek.MONDAY, "thu hai" to DayOfWeek.MONDAY, "thứ ba" to DayOfWeek.TUESDAY, "thu ba" to DayOfWeek.TUESDAY,
        "thứ tư" to DayOfWeek.WEDNESDAY, "thu tu" to DayOfWeek.WEDNESDAY, "thứ năm" to DayOfWeek.THURSDAY, "thu nam" to DayOfWeek.THURSDAY,
        "thứ sáu" to DayOfWeek.FRIDAY, "thu sau" to DayOfWeek.FRIDAY, "thứ bảy" to DayOfWeek.SATURDAY, "thu bay" to DayOfWeek.SATURDAY, "chủ nhật" to DayOfWeek.SUNDAY, "chu nhat" to DayOfWeek.SUNDAY
    ) + NaturalLanguageLexicon.weekdayNames
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
    private val timeOfDayWords = mapOf(
        "night" to LocalTime.of(21, 0),
        "nite" to LocalTime.of(21, 0),
        "midnight" to LocalTime.of(0, 0),
        "morning" to LocalTime.of(9, 0),
        "morn" to LocalTime.of(9, 0),
        "mrng" to LocalTime.of(9, 0),
        "noon" to LocalTime.of(12, 0),
        "midday" to LocalTime.of(12, 0),
        "afternoon" to LocalTime.of(15, 0),
        "aft" to LocalTime.of(15, 0),
        "evening" to LocalTime.of(18, 0),
        "eve" to LocalTime.of(18, 0),
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
    private const val EVERY = "(?:every|evry|evr|each|ea\\.?|cada|todo|toda|todos\\s+os|todas\\s+as|chaque|tous\\s+les|toutes\\s+les|jeden|jede|jeder|jedes|ogni|elke|iedere|varje|co|kazdy|fiecare|her|setiap|kila|bawat|moi)"
    private const val DAY_UNIT = "(?:days?|dys?|dy|d|día|dÃ­a|dia|jour|tag|tage|giorno|giorni|dag|dagen|dagar|dzien|dni|zi|zie|gun|hari|siku|araw|ngay)s?"
    private const val WEEK_UNIT = "(?:weeks?|wks?|wk|w|semana|semaine|woche|wochen|settimana|settimane|week|weken|vecka|veckor|tydzien|tygodnie|saptamana|hafta|minggu|wiki|linggo|tuan)s?"
    private const val MONTH_UNIT = "(?:months?|mos?|mths?|mth|mes(?:es)?|mês|mÃªs|mêses|mÃªses|mois|monat|monate|mese|mesi|maand|maanden|manad|manader|miesiac|miesiace|luna|ay|bulan|mwezi|buwan|thang)"
    private const val YEAR_UNIT = "(?:years?|yrs?|yr|y|año|aÃ±o|ano|an|année|annÃ©e|annee|jahr|jahre|anni|jaar|ar|rok|lata|yil|tahun|mwaka|taon|nam)s?"
    private const val QUARTER_UNIT = "(?:quarters?|qtrs?|qtr)"
    private const val NUMBER_COUNT = NATURAL_LANGUAGE_NUMBER_COUNT
    private val everyAlternateDayRegex = Regex("\\b$EVERY\\s+(?:other|othr|alternate|alternating|alt|otro|alterno|outro|alternado|autre)\\s+$DAY_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateWeekRegex = Regex("\\b$EVERY\\s+(?:other|othr|alternate|alternating|alt|otra|alterna|outra|alternada|autre)\\s+$WEEK_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateMonthRegex = Regex("\\b$EVERY\\s+(?:other|othr|alternate|alternating|alt|otro|alterno|outro|alternado|autre)\\s+$MONTH_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyAlternateYearRegex = Regex("\\b$EVERY\\s+(?:other|othr|alternate|alternating|alt|otro|alterno|outro|alternado|autre)\\s+$YEAR_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyNDaysRegex = Regex("\\b$EVERY\\s+($NUMBER_COUNT)\\s+$DAY_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyNWeeksRegex = Regex("\\b$EVERY\\s+($NUMBER_COUNT)\\s+$WEEK_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyNMonthsRegex = Regex("\\b$EVERY\\s+($NUMBER_COUNT)\\s+$MONTH_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyNQuartersRegex = Regex("\\b$EVERY\\s+($NUMBER_COUNT)\\s+$QUARTER_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyNYearsRegex = Regex("\\b$EVERY\\s+($NUMBER_COUNT)\\s+$YEAR_UNIT\\b", RegexOption.IGNORE_CASE)
    private val everyWeekdayRegex = Regex("\\b$EVERY\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)

    // Longest names first so "sun" can't win against "sunday" and leave "day" stranded.
    private val weekdayAlt by lazy { literalAlternation(weekdayNames.keys) }
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
        "\\b(?:(?:for|por|pour)\\s+)?($NUMBER_COUNT)\\s*(?:times|occurrences|occurrence|veces|ocurrencias|vezes|ocorrências|ocorrencias|fois|x)\\b",
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
        "bi-wkly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "bi wkly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "bi-weekly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "biweekly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "fortnightly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) },
        "fortnighly" to { Recurrence("weekly", 2, null, null, RecurrenceEnds.Never) }, // common typo
        "quaterly" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "quartly" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
        "qtrly" to { Recurrence("monthly", 3, null, null, RecurrenceEnds.Never) },
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
        "annully" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "yrly" to { Recurrence("yearly", 1, null, null, RecurrenceEnds.Never) },
        "daily" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "dly" to { Recurrence("daily", 1, null, null, RecurrenceEnds.Never) },
        "weekly" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "wkly" to { Recurrence("weekly", 1, null, null, RecurrenceEnds.Never) },
        "monthly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "montly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "mnthly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
        "mthly" to { Recurrence("monthly", 1, null, null, RecurrenceEnds.Never) },
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
    private val COUNT = "(?:a\\s+couple\\s+of|a\\s+couple|a\\s+few|several|un|una|um|uma|unos|unas|uns|umas|varios|varias|vários|várias|quelques|plusieurs|an|a|$NUMBER_COUNT)"
    private val inDaysRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+$DAY_UNIT\\b", RegexOption.IGNORE_CASE)
    private val inWeeksRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+$WEEK_UNIT\\b", RegexOption.IGNORE_CASE)
    private val inMonthsRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+$MONTH_UNIT\\b", RegexOption.IGNORE_CASE)
    private val inQuartersRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+$QUARTER_UNIT\\b", RegexOption.IGNORE_CASE)
    private val inYearsRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+$YEAR_UNIT\\b", RegexOption.IGNORE_CASE)
    // "in 3 business days" â€” counts weekdays only, which is the whole point of saying it.
    private val inBusinessDaysRegex = Regex("\\b(?:in|en|em|dans)\\s+($COUNT)\\s+(?:business|buisness|working|work|biz|bus|bd|bday|hábiles|habiles|laborables|úteis|uteis|ouvrables)\\s+$DAY_UNIT\\b", RegexOption.IGNORE_CASE)
    private val nextWeekdayRegex = Regex("\\b(?:next|nxt|nx|nex|próximo|proximo|próxima|proxima|prochain|prochaine)\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)
    private val thisWeekdayRegex = Regex("\\b(?:this|ths|tis|este|esta|ce|cet|cette)\\s+([\\p{L}-]+)\\b", RegexOption.IGNORE_CASE)
    // "wednesday next week" â€” the same day "next wednesday" means, said back to front. Without
    // this the "next week" phrase claims its half and the weekday is left behind in the title.
    private val weekdayNextWeekRegex by lazy {
        Regex("\\b($weekdayAlt)\\s+(?:next\\s+week|la\\s+próxima\\s+semana|la\\s+proxima\\s+semana|semana\\s+que\\s+vem|semaine\\s+prochaine)\\b", RegexOption.IGNORE_CASE)
    }

    private val countWordValues = mapOf(
        "one" to 1L,
        "two" to 2L,
        "three" to 3L, "thre" to 3L, "tree" to 3L,
        "four" to 4L,
        "five" to 5L, "fiv" to 5L,
        "six" to 6L,
        "seven" to 7L,
        "eight" to 8L, "eigth" to 8L,
        "nine" to 9L,
        "ten" to 10L,
        "eleven" to 11L, "elevenn" to 11L,
        "twelve" to 12L, "twelv" to 12L, "tweleve" to 12L,
        "thirteen" to 13L,
        "fourteen" to 14L,
        "fifteen" to 15L,
        "sixteen" to 16L,
        "seventeen" to 17L,
        "eighteen" to 18L,
        "nineteen" to 19L
    )
    private val countTensValues = mapOf(
        "twenty" to 20L, "twnty" to 20L,
        "thirty" to 30L,
        "forty" to 40L, "fourty" to 40L,
        "fifty" to 50L,
        "sixty" to 60L,
        "seventy" to 70L,
        "eighty" to 80L,
        "ninety" to 90L
    )

    /** Digits, "a"/"an", or one of the vague words in [COUNT]. Anything unrecognized reads as 1. */
    private fun countOrOne(token: String): Long {
        val t = token.trim().lowercase().replace('-', ' ').replace(Regex("\\s+"), " ")
        t.toLongOrNull()?.let { return it }
        countWordValues[t]?.let { return it }
        countTensValues[t]?.let { return it }
        val parts = t.split(" ")
        if (parts.size == 2) {
            val tens = countTensValues[parts[0]]
            val unit = countWordValues[parts[1]]
            if (tens != null && unit != null && unit in 1L..9L) return tens + unit
        }
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

    private fun relativeUnitKind(token: String): String? {
        val t = token.trim().lowercase().removeSuffix("s")
        return when (t) {
            "day", "dy", "d", "dÃ­a", "dia", "jour" -> "day"
            "week", "wk", "w", "semana", "semaine" -> "week"
            "month", "mo", "mth", "mes", "mÃª", "mÃªs", "moi" -> "month"
            "quarter", "qtr" -> "quarter"
            "year", "yr", "y", "aÃ±o", "ano", "an", "annÃ©e", "annee" -> "year"
            else -> null
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
    private val fromNowRegex = Regex("\\b(a|an|um|uma|un|une|$NUMBER_COUNT)\\s+($DAY_UNIT|$WEEK_UNIT|$MONTH_UNIT|$QUARTER_UNIT|$YEAR_UNIT)\\s+(?:from\\s+(?:now|today)|a\\s+partir\\s+de\\s+(?:agora|hoje)|à\\s+partir\\s+d['’]?aujourd['’]?hui)\\b", RegexOption.IGNORE_CASE)
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
    private val ordinalWordAlt by lazy { literalAlternation(ordinalWords.keys) }
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
        "beginning nxt month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "beginning next mth" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start of next month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start nxt month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "start next mth" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atDay(1) },
        "end of next month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "end nxt month" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "end next mth" to { ref: LocalDate -> YearMonth.from(ref).plusMonths(1).atEndOfMonth() },
        "next weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "nxt weekend" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "next wknd" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "nxt wknd" to { ref: LocalDate -> nextOrSame(ref, DayOfWeek.SATURDAY).plusDays(7) },
        "next quarter" to { ref: LocalDate -> nextQuarterStart(ref) },
        "nxt quarter" to { ref: LocalDate -> nextQuarterStart(ref) },
        "next qtr" to { ref: LocalDate -> nextQuarterStart(ref) },
        "nxt qtr" to { ref: LocalDate -> nextQuarterStart(ref) },
        "end of quarter" to { ref: LocalDate -> nextQuarterStart(ref).minusDays(1) },
        "end of the quarter" to { ref: LocalDate -> nextQuarterStart(ref).minusDays(1) },
        "next week" to { ref: LocalDate -> ref.plusWeeks(1) },
        "nxt week" to { ref: LocalDate -> ref.plusWeeks(1) },
        "next wk" to { ref: LocalDate -> ref.plusWeeks(1) },
        "nxt wk" to { ref: LocalDate -> ref.plusWeeks(1) },
        "next month" to { ref: LocalDate -> ref.plusMonths(1) },
        "nxt month" to { ref: LocalDate -> ref.plusMonths(1) },
        "next mth" to { ref: LocalDate -> ref.plusMonths(1) },
        "nxt mth" to { ref: LocalDate -> ref.plusMonths(1) },
        "next year" to { ref: LocalDate -> ref.plusYears(1) },
        "nxt year" to { ref: LocalDate -> ref.plusYears(1) },
        "next yr" to { ref: LocalDate -> ref.plusYears(1) },
        "nxt yr" to { ref: LocalDate -> ref.plusYears(1) },
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
        Triple("tmr morning", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("tmrw morning", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(9, 0)),
        Triple("tmr afternoon", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(15, 0)),
        Triple("tmrw afternoon", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(15, 0)),
        Triple("tmr evening", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("tmrw evening", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(18, 0)),
        Triple("tmr night", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("tmrw night", { ref: LocalDate -> ref.plusDays(1) }, LocalTime.of(21, 0)),
        Triple("this morning", { ref: LocalDate -> ref }, LocalTime.of(9, 0)),
        Triple("this afternoon", { ref: LocalDate -> ref }, LocalTime.of(15, 0)),
        Triple("this evening", { ref: LocalDate -> ref }, LocalTime.of(18, 0)),
        Triple("later tonight", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
        Triple("later tonite", { ref: LocalDate -> ref }, LocalTime.of(21, 0)),
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
        "tdy" to { ref: LocalDate -> ref },
        "tday" to { ref: LocalDate -> ref },
        "tody" to { ref: LocalDate -> ref },
        "tomorrow" to { ref: LocalDate -> ref.plusDays(1) },
        "tomorow" to { ref: LocalDate -> ref.plusDays(1) },
        "tommorow" to { ref: LocalDate -> ref.plusDays(1) },
        "tommorrow" to { ref: LocalDate -> ref.plusDays(1) },
        "tmr" to { ref: LocalDate -> ref.plusDays(1) },
        "tmrw" to { ref: LocalDate -> ref.plusDays(1) },
        "tomrw" to { ref: LocalDate -> ref.plusDays(1) },
        "tmw" to { ref: LocalDate -> ref.plusDays(1) },
        "tomo" to { ref: LocalDate -> ref.plusDays(1) },
        "yesterday" to { ref: LocalDate -> ref.minusDays(1) },
        "yday" to { ref: LocalDate -> ref.minusDays(1) },
        "yest" to { ref: LocalDate -> ref.minusDays(1) },
        "ystrday" to { ref: LocalDate -> ref.minusDays(1) },
        "yestrday" to { ref: LocalDate -> ref.minusDays(1) },
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
        "hier" to { ref: LocalDate -> ref.minusDays(1) },
        "heute" to { ref: LocalDate -> ref },
        "morgen" to { ref: LocalDate -> ref.plusDays(1) },
        "gestern" to { ref: LocalDate -> ref.minusDays(1) },
        "oggi" to { ref: LocalDate -> ref },
        "domani" to { ref: LocalDate -> ref.plusDays(1) },
        "ieri" to { ref: LocalDate -> ref.minusDays(1) },
        "vandaag" to { ref: LocalDate -> ref },
        "gisteren" to { ref: LocalDate -> ref.minusDays(1) },
        "idag" to { ref: LocalDate -> ref },
        "imorgon" to { ref: LocalDate -> ref.plusDays(1) },
        "i morgon" to { ref: LocalDate -> ref.plusDays(1) },
        "igar" to { ref: LocalDate -> ref.minusDays(1) },
        "dzisiaj" to { ref: LocalDate -> ref },
        "dzis" to { ref: LocalDate -> ref },
        "jutro" to { ref: LocalDate -> ref.plusDays(1) },
        "wczoraj" to { ref: LocalDate -> ref.minusDays(1) },
        "azi" to { ref: LocalDate -> ref },
        "maine" to { ref: LocalDate -> ref.plusDays(1) },
        "bugun" to { ref: LocalDate -> ref },
        "yarin" to { ref: LocalDate -> ref.plusDays(1) },
        "dun" to { ref: LocalDate -> ref.minusDays(1) },
        "hari ini" to { ref: LocalDate -> ref },
        "besok" to { ref: LocalDate -> ref.plusDays(1) },
        "kemarin" to { ref: LocalDate -> ref.minusDays(1) },
        "leo" to { ref: LocalDate -> ref },
        "kesho" to { ref: LocalDate -> ref.plusDays(1) },
        "ngayon" to { ref: LocalDate -> ref },
        "bukas" to { ref: LocalDate -> ref.plusDays(1) },
        "kahapon" to { ref: LocalDate -> ref.minusDays(1) },
        "hom nay" to { ref: LocalDate -> ref },
        "ngay mai" to { ref: LocalDate -> ref.plusDays(1) },
        "hom qua" to { ref: LocalDate -> ref.minusDays(1) }
    ) + NaturalLanguageLexicon.relativeDateWords.toList()

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
        "jan" to 1, "january" to 1, "janury" to 1, "januarry" to 1,
        "feb" to 2, "february" to 2, "febuary" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "apirl" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "augst" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "septmber" to 9, "sepetember" to 9,
        "oct" to 10, "october" to 10, "octber" to 10,
        "nov" to 11, "november" to 11, "novemeber" to 11,
        "dec" to 12, "december" to 12, "decemeber" to 12,
        "januar" to 1, "februar" to 2, "maerz" to 3, "märz" to 3, "april" to 4, "mai" to 5,
        "juni" to 6, "juli" to 7, "oktober" to 10, "dezember" to 12,
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4, "maggio" to 5,
        "giugno" to 6, "luglio" to 7, "settembre" to 9, "ottobre" to 10, "dicembre" to 12,
        "januari" to 1, "februari" to 2, "maart" to 3, "mei" to 5, "augustus" to 8,
        "december" to 12,
        "maj" to 5, "augusti" to 8,
        "styczen" to 1, "styczeń" to 1, "luty" to 2, "lutego" to 2, "marzec" to 3, "marca" to 3,
        "kwiecien" to 4, "kwiecień" to 4, "kwietnia" to 4, "maja" to 5,
        "czerwiec" to 6, "czerwca" to 6, "lipiec" to 7, "lipca" to 7,
        "sierpien" to 8, "sierpień" to 8, "sierpnia" to 8,
        "wrzesien" to 9, "wrzesień" to 9, "wrzesnia" to 9, "września" to 9,
        "pazdziernik" to 10, "październik" to 10, "pazdziernika" to 10, "października" to 10,
        "listopad" to 11, "listopada" to 11, "grudzien" to 12, "grudzień" to 12, "grudnia" to 12,
        "ianuarie" to 1, "februarie" to 2, "martie" to 3, "iunie" to 6, "iulie" to 7,
        "septembrie" to 9, "octombrie" to 10, "noiembrie" to 11, "decembrie" to 12,
        "ocak" to 1, "subat" to 2, "şubat" to 2, "mart" to 3, "nisan" to 4,
        "mayis" to 5, "mayıs" to 5, "haziran" to 6, "temmuz" to 7,
        "agustos" to 8, "ağustos" to 8, "eylul" to 9, "eylül" to 9, "ekim" to 10,
        "kasim" to 11, "kasım" to 11, "aralik" to 12, "aralık" to 12,
        "maret" to 3, "desember" to 12,
        "enero" to 1, "pebrero" to 2, "marso" to 3, "mayo" to 5, "hunyo" to 6,
        "hulyo" to 7, "setyembre" to 9, "oktubre" to 10, "nobyembre" to 11, "disyembre" to 12,
        "thang mot" to 1, "thang hai" to 2, "thang ba" to 3, "thang tu" to 4,
        "thang nam" to 5, "thang sau" to 6, "thang bay" to 7, "thang tam" to 8,
        "thang chin" to 9, "thang muoi" to 10, "thang muoi mot" to 11, "thang muoi hai" to 12,
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
    ) + NaturalLanguageLexicon.monthNames
    private val monthAlt = literalAlternation(monthNames.keys)
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
    private const val ENTITY_NAME_CHARS = "\\p{L}\\p{N}_\\-\\s"
    private const val ENTITY_BOUNDARY_KEYWORDS =
            "project\\b|proyecto\\b|projeto\\b|projet\\b|list\\b|lista\\b|liste\\b|tag\\b|etiqueta\\b|Ã©tiquette\\b|\\u00e9tiquette\\b|etiquette\\b|" +
            "tagged?\\b|label\\b|labeled?\\b|assign(?:ed)?\\s+to\\b|asignad[ao]\\s+a\\b|asignar\\s+a\\b|" +
            "atribu[iÃ­]d[ao]\\s+a\\b|atribuir\\s+a\\b|assignÃ©\\s+Ã \\b|assign\\u00e9\\s+\\u00e0\\b|assigne\\s+a\\b|assigner\\s+Ã \\b|assigner\\s+\\u00e0\\b|" +
            "give(?:n)?\\s+to\\b|delegate\\b|delegar\\b|send\\s+to\\b|assign\\b|due\\b|vence\\b|Ã©chÃ©ance\\b|echeance\\b|" +
            "at\\b|a\\s+las?\\b|Ã s?\\b|Ã \\b|every\\b|cada\\b|todo\\b|toda\\b|chaque\\b|on\\b|el\\b|le\\b|" +
            "today\\b|tdy\\b|tomorrow\\b|tmr\\b|tmrw\\b|tomrw\\b|tonight\\b|tonite\\b|" +
            "morning\\b|morn\\b|afternoon\\b|evening\\b|night\\b|noon\\b|midnight\\b|" +
            "monday\\b|mon\\b|tuesday\\b|tue\\b|wednesday\\b|wed\\b|thursday\\b|thu\\b|friday\\b|fri\\b|saturday\\b|sat\\b|sunday\\b|sun\\b|" +
            "lunes\\b|martes\\b|miÃ©rcoles\\b|miercoles\\b|jueves\\b|viernes\\b|sÃ¡bado\\b|sabado\\b|domingo\\b|" +
            "segunda(?:-feira)?\\b|terÃ§a(?:-feira)?\\b|terca(?:-feira)?\\b|quarta(?:-feira)?\\b|quinta(?:-feira)?\\b|sexta(?:-feira)?\\b|" +
            "lundi\\b|mardi\\b|mercredi\\b|jeudi\\b|vendredi\\b|samedi\\b|dimanche\\b|" +
            "next\\b|nxt\\b|this\\b|in\\b|by\\b|before\\b|after\\b|starts?\\b|start(?:ing)?\\b|not\\s+before\\b|p[1-3]\\b|" +
            "hash\\s*tag\\b|hashtag\\b|pound\\s*tag\\b|at\\s+sign\\b|plus\\s+project\\b|equals\\s+list\\b"
    private const val ENTITY_WORD_BOUNDARY = "!|#|@|\\+|=|$ENTITY_BOUNDARY_KEYWORDS"
    private val quotedEntityValueRegex = Regex("^\\s*(?:\"([^\"]+)\"|'([^']+)'|([$ENTITY_NAME_CHARS]+?))\\s*$")
    private val projectEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:\\+|plus\\s+project\\b|in\\s+project\\b|for\\s+project\\b|under\\s+project\\b|project\\b|en\\s+proyecto\\b|para\\s+proyecto\\b|bajo\\s+proyecto\\b|proyecto\\b|em\\s+projeto\\b|para\\s+projeto\\b|projeto\\b|dans\\s+projet\\b|pour\\s+projet\\b|projet\\b)\\s*(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )
    private val listEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:=|equals\\s+list\\b|in\\s+list\\b|for\\s+list\\b|under\\s+list\\b|list\\b|en\\s+lista\\b|para\\s+lista\\b|bajo\\s+lista\\b|lista\\b|em\\s+lista\\b|dans\\s+liste\\b|pour\\s+liste\\b|liste\\b)\\s*(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )
    private val tagEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:#|hash\\s*tag\\s+|hashtag\\s+|pound\\s*tag\\s+|tagged?\\s+as\\s+|tagged?\\s+|tag\\s+as\\s+|tag\\s+|labeled?\\s+as\\s+|labeled?\\s+|label\\s+as\\s+|label\\s+|with\\s+tag\\s+|etiquetad[ao]\\s+como\\s+|etiquetad[ao]\\s+|etiqueta\\s+como\\s+|etiqueta\\s+|con\\s+etiqueta\\s+|marcad[ao]\\s+como\\s+|rÃ³tulo\\s+|rotulo\\s+|Ã©tiquette\\s+|\\u00e9tiquette\\s+|etiquette\\s+|avec\\s+Ã©tiquette\\s+|avec\\s+\\u00e9tiquette\\s+|avec\\s+etiquette\\s+)([\\p{L}\\p{N}_\\-]+)(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
    )
    private val assigneeEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:assign(?:ed)?\\s+to\\s+|give(?:n)?\\s+to\\s+|delegate(?:d)?\\s+to\\s+|send\\s+to\\s+|assign\\s+|asignad[ao]\\s+a\\s+|asignar\\s+a\\s+|delegad[ao]\\s+a\\s+|delegar\\s+a\\s+|enviar\\s+a\\s+|atribu[iÃ­]d[ao]\\s+a\\s+|atribuir\\s+a\\s+|delegar\\s+para\\s+|enviar\\s+para\\s+|assignÃ©\\s+Ã \\s+|assign\\u00e9\\s+\\u00e0\\s+|assigne\\s+a\\s+|assigner\\s+Ã \\s+|assigner\\s+\\u00e0\\s+|assigner\\s+a\\s+|dÃ©lÃ©guÃ©\\s+Ã \\s+|d\\u00e9l\\u00e9gu\\u00e9\\s+\\u00e0\\s+|delegue\\s+a\\s+|dÃ©lÃ©guer\\s+Ã \\s+|d\\u00e9l\\u00e9guer\\s+\\u00e0\\s+|deleguer\\s+a\\s+|envoyer\\s+Ã \\s+|envoyer\\s+\\u00e0\\s+|envoyer\\s+a\\s+|at\\s+sign\\s+|@)(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )

    private fun entityValue(rawValue: String): String =
        quotedEntityValueRegex.matchEntire(rawValue)?.let { m ->
            m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
        } ?: rawValue.trim().trim('"', '\'')

    // â”€â”€ Reminder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // "remind"/"remind me" phrases set the *reminder*, distinct from the due time â€” checked
    // before due-time parsing so "remind at 5pm" doesn't leave a stray "5pm" behind for the
    // due-time rule to also claim as the task's own due time.
    private val remindShortAtTimeKeywordRegex = Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:(?:at|on)\\s+time)\\b", RegexOption.IGNORE_CASE)
    private val remindShortMinutesBeforeRegex = Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+($NUMBER_COUNT)\\s*(?:m|min|mins|minute|minutes)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortHourBeforeRegex = Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:1\\s*(?:h|hr|hrs|hour)|one\\s*(?:h|hr|hrs|hour)|an?\\s+hour)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortDayBeforeRegex = Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:1\\s*(?:d|dy|day)|one\\s*(?:d|dy|day)|a\\s+day)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortAtClockTimeRegex = Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:(?:at)\\s+)?(\\d{1,2})([:.](\\d{2}))?\\s*(am|pm|AM|PM)\\b", RegexOption.IGNORE_CASE)
    private val remindAtTimeKeywordRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:(?:at|on)\\s+time|a\\s+la\\s+hora|na\\s+hora|à\\s+l['’]?heure)\\b", RegexOption.IGNORE_CASE)
    private val remindMinutesBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+($NUMBER_COUNT)\\s*(?:min|mins|minute|minutes|minuto|minutos)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindHourBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:hour|hora|heure)|one\\s+hour|an?\\s+hour|una\\s+hora|uma\\s+hora|une\\s+heure)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindDayBeforeRegex = Regex("\\b(?:remind(?:\\s+me)?|recu[eé]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:day|día|dia|jour)|one\\s+day|a\\s+day|un\\s+día|un\\s+dia|um\\s+dia|une\\s+jour|un\\s+jour)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
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
        "hi priority" to "high",
        "high pri" to "high",
        "hi pri" to "high",
        "super urgent" to "high",
        "must do" to "high",
        "vital" to "high",
        "essential" to "high",
        "urgent" to "high",
        "urgnt" to "high",
        "urgentt" to "high",
        "critical" to "high",
        "critcal" to "high",
        "crit" to "high",
        "asap" to "high",
        "as soon as possible" to "high",
        "drop everything" to "high",
        "high prio" to "high",
        "hi prio" to "high",
        "h prio" to "high",
        "top prio" to "high",
        "medium priority" to "med",
        "meduim priority" to "med",
        "med priority" to "med",
        "normal priority" to "med",
        "medium prio" to "med",
        "medium pri" to "med",
        "med prio" to "med",
        "med pri" to "med",
        "m prio" to "med",
        "normal prio" to "med",
        "lowest priority" to "low",
        "low priority" to "low",
        "lo priority" to "low",
        "low pri" to "low",
        "lo pri" to "low",
        "minor priority" to "low",
        "low prio" to "low",
        "lo prio" to "low",
        "l prio" to "low",
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
        "important", "importnt", "importnat", "impt", "mark as important", "bookmark", "bookmarked",
        "marcar", "marcar esto", "marcada", "destacar", "destacado", "importante", "marcar como importante",
        "sinalizar", "sinalizado", "destacar isto", "marcar como importante",
        "marquer", "marqué", "marquee", "signaler", "favori", "mettre en favori", "marquer comme important"
    )

    // â”€â”€ Additional relative dates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val inHoursRegex = Regex("\\b(?:in|en|em|dans)\\s+(a|an|un|una|um|uma|une|$NUMBER_COUNT)\\s+(?:h|hr|hrs|hour|hora|heure)(s)?\\b", RegexOption.IGNORE_CASE)
    private val inMinutesRegex = Regex("\\b(?:in|en|em|dans)\\s+(a|an|un|una|um|uma|une|$NUMBER_COUNT)\\s+(?:m|min|mins|min(?:ute)?|minuto|minute)s?\\b", RegexOption.IGNORE_CASE)
    private val halfAnHourRegex = Regex("\\b(?:in|en|em|dans)\\s+(?:half\\s+(?:an?\\s+)?hour|media\\s+hora|meia\\s+hora|une\\s+demi[-\\s]heure|demi[-\\s]heure)\\b", RegexOption.IGNORE_CASE)
    /**
     * Word/phrase rules are stored as plain strings and only become regexes here. Compiling them
     * inline meant rebuilding ~90 `Regex` objects on every call, and [parse] runs on every
     * keystroke in the quick-add field â€” the phrase lists have grown enough for that to be worth
     * paying once instead of per character typed.
     */
    private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun cachedWordRegex(word: String): Regex =
        wordRegexCache.getOrPut(word) { literalWordRegex(word) }

    /** As [cachedWordRegex], but also absorbing a trailing "ish"/"-ish". */
    private fun cachedIshWordRegex(word: String): Regex =
        wordRegexCache.getOrPut("ish:$word") {
            literalIshWordRegex(word)
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

        val normalizedInput = normalizeNaturalLanguageInputWithRanges(rawInput)
        val raw = normalizedInput.normalized

        val claimTracker = ClaimTracker()
        val parserContext = ParserContext(
            raw = raw,
            referenceDate = referenceDate,
            referenceTime = referenceTime,
            dayFirst = dayFirst,
            claims = claimTracker
        )
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
        escapeRegex.findAll(raw).forEach { m ->
            val backslashIndex = m.range.first
            val escapedWordRange = m.groups[1]!!.range
            claimTracker.addEscape(backslashIndex..backslashIndex, escapedWordRange)
            val escapedWord = m.groupValues[1].lowercase()
            if (escapedWord in setOf("every", "each", "cada", "todo", "toda", "chaque")) {
                Regex("\\G\\s+([\\p{L}-]+)", RegexOption.IGNORE_CASE)
                    .find(raw, escapedWordRange.last + 1)
                    ?.takeIf { weekdayNames.containsKey(it.groupValues[1].lowercase()) }
                    ?.let { claimTracker.addProtectedRange(escapedWordRange.first..it.range.last) }
            }
        }

        fun isFree(range: IntRange) = parserContext.isFree(range)
        fun claimReminder(range: IntRange) = parserContext.claimReminder(range)

        // 1. Recurrence. Checked first so "every sunday" is claimed whole before due-date rules.
        val recurrenceResult = RecurrenceRules.apply(
            context = parserContext,
            config = RecurrenceRuleConfig(
                everyAlternateDayRegex = everyAlternateDayRegex,
                everyAlternateWeekRegex = everyAlternateWeekRegex,
                everyAlternateMonthRegex = everyAlternateMonthRegex,
                everyAlternateYearRegex = everyAlternateYearRegex,
                everyNDaysRegex = everyNDaysRegex,
                everyNWeeksRegex = everyNWeeksRegex,
                everyNMonthsRegex = everyNMonthsRegex,
                everyNQuartersRegex = everyNQuartersRegex,
                everyNYearsRegex = everyNYearsRegex,
                everyLastDayOfMonthRegex = everyLastDayOfMonthRegex,
                everyMonthOnDayRegex = everyMonthOnDayRegex,
                everyOrdinalOfMonthRegex = everyOrdinalOfMonthRegex,
                everyMultiWeekdayRegex = everyMultiWeekdayRegex,
                multiWeekdaySplitRegex = multiWeekdaySplitRegex,
                everyWeekdayRegex = everyWeekdayRegex,
                bareRecurrenceWords = bareRecurrenceWords,
                recurrenceUntilRegex = recurrenceUntilRegex,
                recurrenceTimesRegex = recurrenceTimesRegex,
                weekdayNames = weekdayNames,
                rruleDay = rruleDay,
                resolveOrdinalDayOfMonth = ::resolveOrdinalDayOfMonth,
                parseNested = { phrase -> parse(phrase, referenceDate, referenceTime, dayFirst) },
                claimEndFor = ::claimEndFor,
                countOrOne = ::countOrOne,
                wordRegex = ::cachedWordRegex
            ),
            existingDue = due
        )
        recurrence = recurrenceResult.recurrence
        due = recurrenceResult.due
        dueRange = recurrenceResult.dueRange

        // 1.5 Reminder. Runs before due-time parsing so "remind at 5pm" does not claim task time.
        var reminder: String? = ReminderRules.apply(parserContext, timeFormatter)

        time = TimeRules.applyExplicit(parserContext, timeFormatter)

        // 2. Explicit time is handled by TimeRules above.

        // 2.5 Start date â€” "starts monday", "from next week", "defer to the 15th". Must run
        // before section 3, or the bare date inside the phrase gets claimed as the *due* date and
        // "starts monday" silently means the opposite of what it says.
        //
        // The date phrase itself is resolved by recursing into parse() on just the captured text,
        // rather than duplicating the ~160 lines of date rules below. The recursion terminates at
        // depth 1: the captured group can't contain another start keyword, since the keyword is
        // what delimits it. Only the resolved date is taken from the nested result â€” its title,
        // priority and everything else are discarded.
        val startDate: LocalDate? = StartDateRules.apply(
            context = parserContext,
            startDateRegex = startDateRegex,
            parseNested = { phrase -> parse(phrase, referenceDate, referenceTime, dayFirst) },
            claimEndFor = ::claimEndFor
        )

        val dateResult = DateRules.apply(
            context = parserContext,
            config = DateRuleConfig(
                timeFormatter = timeFormatter,
                timeOfDayWords = timeOfDayWords,
                phraseDateTimes = phraseDateTimes,
                customDateAliases = customDateAliases,
                resolveDateAlias = ::resolveDateAlias,
                halfAnHourRegex = halfAnHourRegex,
                inHoursRegex = inHoursRegex,
                inMinutesRegex = inMinutesRegex,
                isoDateRegex = isoDateRegex,
                slashDateRegex = slashDateRegex,
                dottedDateRegex = dottedDateRegex,
                monthDayRegex = monthDayRegex,
                dayMonthRegex = dayMonthRegex,
                midMonthNameRegex = midMonthNameRegex,
                monthNames = monthNames,
                dayAfterTomorrowRegex = dayAfterTomorrowRegex,
                weekdayNextWeekRegex = weekdayNextWeekRegex,
                bareDateWords = bareDateWords,
                phraseDates = phraseDates,
                eodTime = eodTime,
                eobTime = eobTime,
                fortnightRegex = fortnightRegex,
                fromNowRegex = fromNowRegex,
                ordinalDayOfMonthRegex = ordinalDayOfMonthRegex,
                ordinalWordDayRegex = ordinalWordDayRegex,
                ordinalWords = ordinalWords,
                inBusinessDaysRegex = inBusinessDaysRegex,
                inDaysRegex = inDaysRegex,
                inWeeksRegex = inWeeksRegex,
                inMonthsRegex = inMonthsRegex,
                inQuartersRegex = inQuartersRegex,
                inYearsRegex = inYearsRegex,
                nextWeekdayRegex = nextWeekdayRegex,
                thisWeekdayRegex = thisWeekdayRegex,
                weekdayNames = weekdayNames,
                resolveIsoDate = ::resolveIsoDate,
                resolveSlashDate = ::resolveSlashDate,
                resolveMonthDay = ::resolveMonthDay,
                resolveOrdinalDayOfMonth = ::resolveOrdinalDayOfMonth,
                relativeUnitKind = ::relativeUnitKind,
                plusBusinessDays = ::plusBusinessDays,
                nextAfter = ::nextAfter,
                nextOrSame = ::nextOrSame,
                countOrOne = ::countOrOne,
                wordRegex = ::cachedWordRegex
            ),
            existingDue = due,
            existingTime = time
        )
        due = dateResult.due
        time = dateResult.time
        dateResult.dueRange?.let { dueRange = it }

        // 3.5 "remind <date>" â€” a bare "remind"/"remind me" immediately before a date phrase
        // (no offset/clock-time suffix, since those are already claimed in section 1.5) implies
        // the reminder should fire at the task's due time.
        if (reminder == null && dueRange != null) {
            val range = dueRange!!
            val prefix = raw.substring(0, range.first)
            Regex("\\b(?:remind(?:\\s+me)?|rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider|recu[eé]rdame|recordatorio)\\s*$", RegexOption.IGNORE_CASE).find(prefix)?.let { m ->
                if (isFree(m.range)) {
                    reminder = "At time"
                    claimReminder(m.range)
                }
            }
        }

        if (time == null) {
            time = TimeRules.applyFallback(parserContext, timeFormatter)
        }

        // 5-6. Priority and flag
        val priorityFlag = PriorityFlagRules.apply(parserContext)

        // 7. Project, List, Tag, Assignee keywords
        val entities = EntityRules.apply(parserContext)
        val prepositionRegex = Regex("(?:^|\\s)(for|on|at|by|scheduled\\s+for|remind\\s+me\\s+for|remind\\s+me\\s+on|para|el|a\\s+las?|às?|à|programad[ao]\\s+para|recu[eé]rdame\\s+para|recu[eé]rdame\\s+el)\\s*$", RegexOption.IGNORE_CASE)

        val sortedNormalizedHighlightSpans = parserContext.expandedSpans(prepositionRegex)
            .sortedBy { it.range.first }
        val sortedHighlightSpans = sortedNormalizedHighlightSpans
            .map { span -> span.copy(range = normalizedInput.toRawRange(span.range)) }
            .sortedBy { it.range.first }
        val sortedClaims = sortedHighlightSpans.map { it.range }
        val sortedStrip = (sortedNormalizedHighlightSpans.map { it.range } + parserContext.stripOnlyRanges())
            .map { normalizedInput.toRawRange(it) }
            .sortedBy { it.first }
        val title = cleanNaturalLanguageTitle(normalizedInput.raw, sortedStrip)

        val result = ParseState(
            due = due,
            startDate = startDate,
            time = time,
            recurrence = recurrence,
            reminder = reminder,
            priority = priorityFlag.priority,
            flag = priorityFlag.flag,
            projectName = entities.projectName,
            listName = entities.listName,
            tagNames = entities.tagNames,
            assigneeNames = entities.assigneeNames
        ).toParsedQuickAdd(
            title = title,
            highlightRanges = sortedClaims,
            highlightSpans = sortedHighlightSpans
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
