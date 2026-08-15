package com.mj.yata.util.nl

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.util.ParsedQuickAdd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

internal data class RecurrenceRuleConfig(
    val everyAlternateDayRegex: Regex,
    val everyAlternateWeekRegex: Regex,
    val everyAlternateMonthRegex: Regex,
    val everyAlternateYearRegex: Regex,
    val everyNDaysRegex: Regex,
    val everyNWeeksRegex: Regex,
    val everyNMonthsRegex: Regex,
    val everyNQuartersRegex: Regex,
    val everyNYearsRegex: Regex,
    val everyLastDayOfMonthRegex: Regex,
    val everyMonthOnDayRegex: Regex,
    val everyOrdinalOfMonthRegex: Regex,
    val everyMultiWeekdayRegex: Regex,
    val multiWeekdaySplitRegex: Regex,
    val everyWeekdayRegex: Regex,
    val bareRecurrenceWords: Map<String, () -> Recurrence>,
    val recurrenceUntilRegex: Regex,
    val recurrenceTimesRegex: Regex,
    val weekdayNames: Map<String, DayOfWeek>,
    val rruleDay: Map<DayOfWeek, String>,
    val resolveOrdinalDayOfMonth: (Int, LocalDate) -> LocalDate?,
    val parseNested: (String) -> ParsedQuickAdd,
    val claimEndFor: (MatchResult, Int, ParsedQuickAdd) -> Int,
    val countOrOne: (String) -> Long,
    val wordRegex: (String) -> Regex
)

internal data class RecurrenceRuleResult(
    val recurrence: Recurrence?,
    val due: LocalDate?,
    val dueRange: IntRange?
)

internal object RecurrenceRules {
    fun apply(
        context: ParserContext,
        config: RecurrenceRuleConfig,
        existingDue: LocalDate? = null
    ): RecurrenceRuleResult {
        var due = existingDue
        var dueRange: IntRange? = null
        var recurrence: Recurrence? = null

        config.everyAlternateDayRegex.let { context.firstFreeMatch(it) }?.let { match ->
            recurrence = Recurrence("daily", 2, null, null, RecurrenceEnds.Never)
            context.claimRecurrence(match.range)
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyAlternateWeekRegex)?.let { match ->
                recurrence = Recurrence("weekly", 2, null, null, RecurrenceEnds.Never)
                context.claimRecurrence(match.range)
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyAlternateMonthRegex)?.let { match ->
                recurrence = Recurrence("monthly", 2, null, null, RecurrenceEnds.Never)
                context.claimRecurrence(match.range)
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyAlternateYearRegex)?.let { match ->
                recurrence = Recurrence("yearly", 2, null, null, RecurrenceEnds.Never)
                context.claimRecurrence(match.range)
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyNDaysRegex)?.let { match ->
                config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                    recurrence = Recurrence("daily", count, null, null, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                }
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyNWeeksRegex)?.let { match ->
                config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                    recurrence = Recurrence("weekly", count, null, null, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                }
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyNMonthsRegex)?.let { match ->
                config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                    recurrence = Recurrence("monthly", count, null, null, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                }
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyNQuartersRegex)?.let { match ->
                config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                    recurrence = Recurrence("monthly", count * 3, null, null, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                }
            }
        }
        if (recurrence == null) {
            context.firstFreeMatch(config.everyNYearsRegex)?.let { match ->
                config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                    recurrence = Recurrence("yearly", count, null, null, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                }
            }
        }

        if (recurrence == null) {
            context.firstFreeMatch(config.everyLastDayOfMonthRegex)?.let { match ->
                recurrence = Recurrence("monthly", 1, null, -1, RecurrenceEnds.Never)
                context.claimRecurrence(match.range)
                if (due == null) {
                    due = YearMonth.from(context.referenceDate).atEndOfMonth()
                        .let {
                            if (it.isBefore(context.referenceDate)) {
                                YearMonth.from(context.referenceDate).plusMonths(1).atEndOfMonth()
                            } else {
                                it
                            }
                        }
                    dueRange = match.range
                }
            }
        }

        if (recurrence == null) {
            (context.firstFreeMatch(config.everyMonthOnDayRegex)
                ?: context.firstFreeMatch(config.everyOrdinalOfMonthRegex))?.let { match ->
                match.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                    recurrence = Recurrence("monthly", 1, null, day, RecurrenceEnds.Never)
                    context.claimRecurrence(match.range)
                    if (due == null) {
                        config.resolveOrdinalDayOfMonth(day, context.referenceDate)?.let { resolved ->
                            due = resolved
                            dueRange = match.range
                        }
                    }
                }
            }
        }

        if (recurrence == null) {
            context.firstFreeMatch(config.everyMultiWeekdayRegex)?.let { match ->
                val days = config.multiWeekdaySplitRegex.split(match.groupValues[1])
                    .mapNotNull { config.weekdayNames[it.trim().lowercase()] }
                    .distinct()
                    .sortedBy { it.value }
                if (days.isNotEmpty()) {
                    recurrence = Recurrence(
                        "weekly",
                        1,
                        days.map { config.rruleDay.getValue(it) },
                        null,
                        RecurrenceEnds.Never
                    )
                    context.claimRecurrence(match.range)
                }
            }
        }

        if (recurrence == null) {
            context.firstFreeMatch(config.everyWeekdayRegex)?.let { match ->
                val token = match.groupValues[1].lowercase()
                val rec = when {
                    token in setOf("days", "dy", "dys", "d") -> Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("weeks", "wk", "wks", "w") -> Recurrence("weekly", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("months", "mo", "mos", "mth", "mths") -> Recurrence("monthly", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("years", "yr", "yrs", "y") -> Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("quarters", "qtrs") -> Recurrence("monthly", 3, null, null, RecurrenceEnds.Never)
                    token in setOf("day", "d\u00eda", "dia", "jour", "tag", "tage", "giorno", "giorni", "dag", "dagen", "dagar", "dzien", "dni", "zi", "zie", "gun", "hari", "siku", "araw", "ngay") -> Recurrence("daily", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("week", "semana", "semaine", "woche", "wochen", "settimana", "settimane", "weken", "vecka", "veckor", "tydzien", "tygodnie", "saptamana", "hafta", "minggu", "wiki", "linggo", "tuan") -> Recurrence("weekly", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("month", "mes", "m\u00eas", "mois", "monat", "monate", "mese", "mesi", "maand", "maanden", "manad", "manader", "miesiac", "miesiace", "luna", "ay", "bulan", "mwezi", "buwan", "thang") -> Recurrence("monthly", 1, null, null, RecurrenceEnds.Never)
                    token in setOf("year", "a\u00f1o", "ano", "an", "ann\u00e9e", "annee", "jahr", "jahre", "anni", "jaar", "ar", "rok", "lata", "yil", "tahun", "mwaka", "taon", "nam") -> Recurrence("yearly", 1, null, null, RecurrenceEnds.Never)
                    token == "quarter" || token == "qtr" || token == "trimestre" -> Recurrence("monthly", 3, null, null, RecurrenceEnds.Never)
                    token == "weekday" || token == "weekdays" || token == "laborable" || token == "laborables" || token == "\u00fatil" || token == "util" || token == "\u00fateis" || token == "uteis" || token == "ouvrable" || token == "ouvrables" -> Recurrence("weekly", 1, listOf("MO", "TU", "WE", "TH", "FR"), null, RecurrenceEnds.Never)
                    token == "weekend" || token == "weekends" || token == "week-end" -> Recurrence("weekly", 1, listOf("SA", "SU"), null, RecurrenceEnds.Never)
                    config.weekdayNames.containsKey(token) -> Recurrence(
                        "weekly",
                        1,
                        listOf(config.rruleDay.getValue(config.weekdayNames.getValue(token))),
                        null,
                        RecurrenceEnds.Never
                    )
                    else -> null
                }
                if (rec != null) {
                    recurrence = rec
                    context.claimRecurrence(match.range)
                }
            }
        }

        if (recurrence == null) {
            for ((word, factory) in config.bareRecurrenceWords) {
                context.firstFreeWord(word, config.wordRegex)?.let { match ->
                    recurrence = factory()
                    context.claimRecurrence(match.range)
                }
                if (recurrence != null) break
            }
        }

        if (recurrence != null) {
            context.firstFreeMatch(config.recurrenceUntilRegex)?.let { match ->
                val phrase = match.groupValues[1]
                if (phrase.isNotBlank()) {
                    val nested = config.parseNested(phrase)
                    nested.due?.let { endDate ->
                        recurrence = recurrence!!.copy(ends = RecurrenceEnds.On(endDate))
                        context.claimRecurrence(match.range.first..config.claimEndFor(match, 1, nested))
                    }
                }
            }
            if (recurrence!!.ends == RecurrenceEnds.Never) {
                context.firstFreeMatch(config.recurrenceTimesRegex)?.let { match ->
                    config.countOrOne(match.groupValues[1]).toInt().takeIf { it > 0 }?.let { count ->
                        recurrence = recurrence!!.copy(ends = RecurrenceEnds.After(count))
                        context.claimRecurrence(match.range)
                    }
                }
            }
        }

        return RecurrenceRuleResult(
            recurrence = recurrence,
            due = due,
            dueRange = dueRange
        )
    }
}
