package com.mj.yata.util.nl

import com.mj.yata.domain.model.DateAliasTarget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DateRuleConfig(
    val timeFormatter: DateTimeFormatter,
    val timeOfDayWords: Map<String, LocalTime>,
    val phraseDateTimes: List<Triple<String, (LocalDate) -> LocalDate, LocalTime?>>,
    val customDateAliases: Map<String, DateAliasTarget>,
    val resolveDateAlias: (DateAliasTarget, LocalDate) -> LocalDate,
    val halfAnHourRegex: Regex,
    val inHoursRegex: Regex,
    val inMinutesRegex: Regex,
    val isoDateRegex: Regex,
    val slashDateRegex: Regex,
    val dottedDateRegex: Regex,
    val monthDayRegex: Regex,
    val dayMonthRegex: Regex,
    val midMonthNameRegex: Regex,
    val monthNames: Map<String, Int>,
    val dayAfterTomorrowRegex: Regex,
    val weekdayNextWeekRegex: Regex,
    val bareDateWords: List<Pair<String, (LocalDate) -> LocalDate>>,
    val phraseDates: List<Pair<String, (LocalDate) -> LocalDate>>,
    val eodTime: LocalTime,
    val eobTime: LocalTime,
    val fortnightRegex: Regex,
    val fromNowRegex: Regex,
    val ordinalDayOfMonthRegex: Regex,
    val ordinalWordDayRegex: Regex,
    val ordinalWords: Map<String, Int>,
    val inBusinessDaysRegex: Regex,
    val inDaysRegex: Regex,
    val inWeeksRegex: Regex,
    val inMonthsRegex: Regex,
    val inQuartersRegex: Regex,
    val inYearsRegex: Regex,
    val nextWeekdayRegex: Regex,
    val thisWeekdayRegex: Regex,
    val weekdayNames: Map<String, DayOfWeek>,
    val resolveIsoDate: (Int, Int, Int) -> LocalDate?,
    val resolveSlashDate: (Int, Int, String?, LocalDate, Boolean) -> LocalDate?,
    val resolveMonthDay: (Int, Int, Int?, LocalDate) -> LocalDate?,
    val resolveOrdinalDayOfMonth: (Int, LocalDate) -> LocalDate?,
    val relativeUnitKind: (String) -> String?,
    val plusBusinessDays: (LocalDate, Long) -> LocalDate,
    val nextAfter: (LocalDate, DayOfWeek) -> LocalDate,
    val nextOrSame: (LocalDate, DayOfWeek) -> LocalDate,
    val countOrOne: (String) -> Long,
    val wordRegex: (String) -> Regex
)

internal data class DateRuleResult(
    val due: LocalDate?,
    val time: String?,
    val dueRange: IntRange?
)

internal object DateRules {
    /**
     * "2 weeks from now/today" — resolved ahead of everything else in [apply], and specifically
     * ahead of start-date parsing in [com.mj.yata.util.NaturalLanguageParser.parse]. `startDateRegex`
     * treats a bare "from" as a start-date keyword, so without this running first, "2 weeks from
     * today" got read as start-date phrase "from today" (claiming that span) and [apply]'s own
     * fromNowRegex check — which needs the whole "2 weeks from today" — found its match already
     * partially claimed and silently gave up.
     */
    fun applyFromNow(
        context: ParserContext,
        config: DateRuleConfig,
        existingDue: LocalDate?
    ): DateRuleResult {
        var due = existingDue
        var dueRange: IntRange? = null

        if (due == null) {
            context.firstFreeMatch(config.fromNowRegex)?.let { match ->
                val count = config.countOrOne(match.groupValues[1])
                due = when (config.relativeUnitKind(match.groupValues[2])) {
                    "day" -> context.referenceDate.plusDays(count)
                    "week" -> context.referenceDate.plusWeeks(count)
                    "month" -> context.referenceDate.plusMonths(count)
                    "quarter" -> context.referenceDate.plusMonths(count * 3)
                    "year" -> context.referenceDate.plusYears(count)
                    else -> null
                }
                if (due != null) {
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
            }
        }

        return DateRuleResult(due = due, time = null, dueRange = dueRange)
    }

    fun apply(
        context: ParserContext,
        config: DateRuleConfig,
        existingDue: LocalDate?,
        existingTime: String?
    ): DateRuleResult {
        var due = existingDue
        var time = existingTime
        var dueRange: IntRange? = null

        for (word in listOf("tonight", "tonite", "tnite")) {
            context.firstFreeWord(word, config.wordRegex)?.let { match ->
                due = context.referenceDate
                if (time == null) {
                    time = config.timeOfDayWords.getValue("night").formatStorage(config.timeFormatter)
                }
                context.claimDueDate(match.range)
                dueRange = match.range
            }
            if (due != null) break
        }

        if (due == null) {
            for ((phrase, resolve, clock) in config.phraseDateTimes) {
                context.firstFreeMatch(config.wordRegex(phrase))?.let { match ->
                    due = resolve(context.referenceDate)
                    if (clock != null && time == null) time = clock.formatStorage(config.timeFormatter)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
                if (due != null) break
            }
        }

        if (due == null) {
            for ((alias, target) in config.customDateAliases.entries.sortedByDescending { it.key.length }) {
                context.firstFreeWord(alias, config.wordRegex)?.let { match ->
                    due = config.resolveDateAlias(target, context.referenceDate)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
                if (due != null) break
            }
        }

        fun applyMinutesOffset(minutes: Long, range: IntRange) {
            val target = LocalDateTime.of(context.referenceDate, context.referenceTime).plusMinutes(minutes)
            due = target.toLocalDate()
            if (time == null) time = target.toLocalTime().formatStorage(config.timeFormatter)
            context.claimDueDate(range)
            dueRange = range
        }

        if (due == null) {
            context.firstFreeMatch(config.halfAnHourRegex)?.let { match -> applyMinutesOffset(30, match.range) }
        }
        if (due == null) {
            context.firstFreeMatch(config.inHoursRegex)?.let { match ->
                applyMinutesOffset(config.countOrOne(match.groupValues[1]) * 60, match.range)
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inMinutesRegex)?.let { match ->
                applyMinutesOffset(config.countOrOne(match.groupValues[1]), match.range)
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.isoDateRegex)?.let { match ->
                val year = match.groupValues[1].toIntOrNull()
                val month = match.groupValues[2].toIntOrNull()
                val day = match.groupValues[3].toIntOrNull()
                if (year != null && month != null && day != null) {
                    config.resolveIsoDate(year, month, day)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.slashDateRegex)?.let { match ->
                val first = match.groupValues[1].toIntOrNull()
                val second = match.groupValues[2].toIntOrNull()
                val yearRaw = match.groupValues[3].ifEmpty { null }
                if (first != null && second != null) {
                    config.resolveSlashDate(first, second, yearRaw, context.referenceDate, context.dayFirst)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.dottedDateRegex)?.let { match ->
                val first = match.groupValues[1].toIntOrNull()
                val second = match.groupValues[2].toIntOrNull()
                val yearRaw = match.groupValues[3].ifEmpty { null }
                if (first != null && second != null) {
                    config.resolveSlashDate(first, second, yearRaw, context.referenceDate, context.dayFirst)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.monthDayRegex)?.let { match ->
                val month = config.monthNames[match.groupValues[1].lowercase()]
                val day = match.groupValues[2].toIntOrNull()
                val year = match.groupValues[3].toIntOrNull()
                if (month != null && day != null) {
                    config.resolveMonthDay(month, day, year, context.referenceDate)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.dayMonthRegex)?.let { match ->
                val day = match.groupValues[1].toIntOrNull()
                val month = config.monthNames[match.groupValues[2].lowercase()]
                val year = match.groupValues[3].toIntOrNull()
                if (month != null && day != null) {
                    config.resolveMonthDay(month, day, year, context.referenceDate)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.midMonthNameRegex)?.let { match ->
                config.monthNames[match.groupValues[1].lowercase()]?.let { month ->
                    config.resolveMonthDay(month, 15, null, context.referenceDate)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.dayAfterTomorrowRegex)?.let { match ->
                due = context.referenceDate.plusDays(2)
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.weekdayNextWeekRegex)?.let { match ->
                config.weekdayNames[match.groupValues[1].lowercase()]?.let { day ->
                    due = config.nextAfter(context.referenceDate, day)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
            }
        }

        if (due == null) {
            for ((word, resolve) in config.bareDateWords) {
                context.firstFreeWord(word, config.wordRegex)?.let { match ->
                    due = resolve(context.referenceDate)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
                if (due != null) break
            }
        }

        if (due == null) {
            for ((phrase, resolve) in config.phraseDates) {
                context.firstFreeMatch(config.wordRegex(phrase))?.let { match ->
                    due = resolve(context.referenceDate)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
                if (due != null) break
            }
        }

        var endOfDayFallbackRange: IntRange? = null
        if (due == null && endOfDayFallbackRange == null) {
            context.firstFreeWord("eod", config.wordRegex)?.let { match ->
                if (time == null) time = config.eodTime.formatStorage(config.timeFormatter)
                context.claimTime(match.range)
                endOfDayFallbackRange = match.range
            }
        }
        if (due == null && endOfDayFallbackRange == null) {
            for (word in listOf("eob", "cob")) {
                context.firstFreeWord(word, config.wordRegex)?.let { match ->
                    if (time == null) time = config.eobTime.formatStorage(config.timeFormatter)
                    context.claimTime(match.range)
                    endOfDayFallbackRange = match.range
                }
                if (endOfDayFallbackRange != null) break
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.fortnightRegex)?.let { match ->
                due = context.referenceDate.plusWeeks(2)
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.fromNowRegex)?.let { match ->
                val count = config.countOrOne(match.groupValues[1])
                due = when (config.relativeUnitKind(match.groupValues[2])) {
                    "day" -> context.referenceDate.plusDays(count)
                    "week" -> context.referenceDate.plusWeeks(count)
                    "month" -> context.referenceDate.plusMonths(count)
                    "quarter" -> context.referenceDate.plusMonths(count * 3)
                    "year" -> context.referenceDate.plusYears(count)
                    else -> null
                }
                if (due != null) {
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.ordinalDayOfMonthRegex)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let { day ->
                    config.resolveOrdinalDayOfMonth(day, context.referenceDate)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.ordinalWordDayRegex)?.let { match ->
                config.ordinalWords[match.groupValues[1].lowercase()]?.let { day ->
                    config.resolveOrdinalDayOfMonth(day, context.referenceDate)?.let { resolved ->
                        due = resolved
                        context.claimDueDate(match.range)
                        dueRange = match.range
                    }
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.inBusinessDaysRegex)?.let { match ->
                due = config.plusBusinessDays(context.referenceDate, config.countOrOne(match.groupValues[1]))
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inDaysRegex)?.let { match ->
                due = context.referenceDate.plusDays(config.countOrOne(match.groupValues[1]))
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inWeeksRegex)?.let { match ->
                due = context.referenceDate.plusWeeks(config.countOrOne(match.groupValues[1]))
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inMonthsRegex)?.let { match ->
                due = context.referenceDate.plusMonths(config.countOrOne(match.groupValues[1]))
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inQuartersRegex)?.let { match ->
                due = context.referenceDate.plusMonths(config.countOrOne(match.groupValues[1]) * 3)
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }
        if (due == null) {
            context.firstFreeMatch(config.inYearsRegex)?.let { match ->
                due = context.referenceDate.plusYears(config.countOrOne(match.groupValues[1]))
                context.claimDueDate(match.range)
                dueRange = match.range
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.nextWeekdayRegex)?.let { match ->
                config.weekdayNames[match.groupValues[1].lowercase()]?.let { day ->
                    due = config.nextAfter(context.referenceDate, day)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
            }
        }

        if (due == null) {
            context.firstFreeMatch(config.thisWeekdayRegex)?.let { match ->
                config.weekdayNames[match.groupValues[1].lowercase()]?.let { day ->
                    due = config.nextOrSame(context.referenceDate, day)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
            }
        }

        if (due == null) {
            for ((name, day) in config.weekdayNames) {
                context.firstFreeWord(name, config.wordRegex)?.let { match ->
                    due = config.nextOrSame(context.referenceDate, day)
                    context.claimDueDate(match.range)
                    dueRange = match.range
                }
                if (due != null) break
            }
        }

        if (due == null && endOfDayFallbackRange != null) {
            due = context.referenceDate
            dueRange = endOfDayFallbackRange
        }

        return DateRuleResult(
            due = due,
            time = time,
            dueRange = dueRange
        )
    }

    private fun LocalTime.formatStorage(timeFormatter: DateTimeFormatter): String =
        format(timeFormatter).uppercase(Locale.getDefault())
}
