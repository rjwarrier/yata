package com.mj.yata.util.nl

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object ReminderRules {
    private val remindShortAtTimeKeywordRegex =
        Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:(?:at|on)\\s+time)\\b", RegexOption.IGNORE_CASE)
    private val remindShortMinutesBeforeRegex =
        Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+($NATURAL_LANGUAGE_NUMBER_COUNT)\\s*(?:m|min|mins|minute|minutes)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortHourBeforeRegex =
        Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:1\\s*(?:h|hr|hrs|hour)|one\\s*(?:h|hr|hrs|hour)|an?\\s+hour)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortDayBeforeRegex =
        Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:1\\s*(?:d|dy|day)|one\\s*(?:d|dy|day)|a\\s+day)\\s+(?:before|bef|b4)\\b", RegexOption.IGNORE_CASE)
    private val remindShortAtClockTimeRegex =
        Regex("\\b(?:rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider)\\s+(?:(?:at)\\s+)?(\\d{1,2})([:.](\\d{2}))?\\s*(am|pm|AM|PM)\\b", RegexOption.IGNORE_CASE)
    private val remindAtTimeKeywordRegex =
        Regex("\\b(?:remind(?:\\s+me)?|recu[e\u00e9]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:(?:at|on)\\s+time|a\\s+la\\s+hora|na\\s+hora|\u00e0\\s+l['\u2019]?heure)\\b", RegexOption.IGNORE_CASE)
    private val remindMinutesBeforeRegex =
        Regex("\\b(?:remind(?:\\s+me)?|recu[e\u00e9]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+($NATURAL_LANGUAGE_NUMBER_COUNT)\\s*(?:min|mins|minute|minutes|minuto|minutos)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindHourBeforeRegex =
        Regex("\\b(?:remind(?:\\s+me)?|recu[e\u00e9]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:hour|hora|heure)|one\\s+hour|an?\\s+hour|una\\s+hora|uma\\s+hora|une\\s+heure)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindDayBeforeRegex =
        Regex("\\b(?:remind(?:\\s+me)?|recu[e\u00e9]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:1\\s+(?:day|d\u00eda|dia|jour)|one\\s+day|a\\s+day|un\\s+d\u00eda|un\\s+dia|um\\s+dia|une\\s+jour|un\\s+jour)\\s+(?:before|antes|avant)\\b", RegexOption.IGNORE_CASE)
    private val remindAtClockTimeRegex =
        Regex("\\b(?:remind(?:\\s+me)?|recu[e\u00e9]rdame|recordatorio|lembra(?:r)?(?:\\s+me)?|lembrete|rappelle(?:[-\\s]moi)?|rappel)\\s+(?:(?:at|a\\s+las?|\u00e0s?|as|\u00e0|a)\\s+)?(\\d{1,2})([:.](\\d{2}))?\\s*(am|pm|AM|PM)\\b", RegexOption.IGNORE_CASE)
    private val remindDateFallbackRegex =
        Regex("\\b(?:remind(?:\\s+me)?|rem(?:\\s+me)?|rmd|rmndr|remndr|reminder|remindr|remider|recu[e\u00e9]rdame|recordatorio)\\s*$", RegexOption.IGNORE_CASE)

    fun apply(context: ParserContext, timeFormatter: DateTimeFormatter): String? {
        (context.firstFreeMatch(remindShortAtTimeKeywordRegex) ?: context.firstFreeMatch(remindAtTimeKeywordRegex))?.let { match ->
            context.claimReminder(match.range)
            return "At time"
        }

        (context.firstFreeMatch(remindShortMinutesBeforeRegex) ?: context.firstFreeMatch(remindMinutesBeforeRegex))?.let { match ->
            val label = when (naturalLanguageCountOrOne(match.groupValues[1]).toInt()) {
                5 -> "5 min before"
                15 -> "15 min before"
                30 -> "30 min before"
                else -> null
            }
            if (label != null) {
                context.claimReminder(match.range)
                return label
            }
        }

        (context.firstFreeMatch(remindShortHourBeforeRegex) ?: context.firstFreeMatch(remindHourBeforeRegex))?.let { match ->
            context.claimReminder(match.range)
            return "1 hour before"
        }

        (context.firstFreeMatch(remindShortDayBeforeRegex) ?: context.firstFreeMatch(remindDayBeforeRegex))?.let { match ->
            context.claimReminder(match.range)
            return "1 day before"
        }

        (context.firstFreeMatch(remindShortAtClockTimeRegex) ?: context.firstFreeMatch(remindAtClockTimeRegex))?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            val meridiem = match.groupValues[4]
            if (hour != null && hour in 1..12 && minute in 0..59) {
                val hour24 = when {
                    meridiem.equals("am", ignoreCase = true) && hour == 12 -> 0
                    meridiem.equals("pm", ignoreCase = true) && hour != 12 -> hour + 12
                    else -> hour
                }
                context.claimReminder(match.range)
                return LocalTime.of(hour24, minute).format(timeFormatter).uppercase(Locale.getDefault())
            }
        }

        return null
    }

    fun applyDateFallback(context: ParserContext, dueRange: IntRange?): String? {
        if (dueRange == null) return null

        val prefix = context.raw.substring(0, dueRange.first)
        remindDateFallbackRegex.find(prefix)?.let { match ->
            if (context.isFree(match.range)) {
                context.claimReminder(match.range)
                return "At time"
            }
        }

        return null
    }
}
