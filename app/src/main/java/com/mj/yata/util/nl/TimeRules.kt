package com.mj.yata.util.nl

import com.mj.yata.util.literalIshWordRegex
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object TimeRules {
    private val time12Regex =
        Regex("\\b(?:(?:at|a\\s+las?|\u00e0s?|as|\u00e0|a)\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|AM|PM|A\\.?\\s*M\\.?|P\\.?\\s*M\\.?)\\b", RegexOption.IGNORE_CASE)
    private val timeOClockRegex =
        Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*o'?clock(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
    private val atTimeRegex =
        Regex("\\b(?:at|a\\s+las?|\u00e0s?|as|\u00e0|a)\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s+(?:in\\s+the\\s+|de\\s+la\\s+|da\\s+|du\\s+)?(morning|afternoon|evening|night|ma\u00f1ana|manana|tarde|noche|manh\u00e3|manha|matin|apr\u00e8s-midi|apres-midi|soir|nuit|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?))?\\b", RegexOption.IGNORE_CASE)
    private val bareMeridiemRegex =
        Regex("\\b(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val time24Regex =
        Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")

    private val wordToHourMap = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12
    )

    private val writtenHourRegex = Regex(
        "\\b(?:at\\s+)?(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)" +
            "(?:\\s+(thirty|fifteen|forty\\s+five|45|30|15))?" +
            "(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm))?\\b" +
            "(?!\\s+(?:$DAY_UNIT|$WEEK_UNIT|$MONTH_UNIT|$QUARTER_UNIT|$YEAR_UNIT|h|hr|hrs|hour|m|min|mins|minute))",
        RegexOption.IGNORE_CASE
    )

    private val quarterHalfRegex = Regex(
        "\\b(quarter\\s+past|half\\s+past|quarter\\s+to)\\s+(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)" +
            "(?:\\s+(?:in\\s+the\\s+)?(morning|afternoon|evening|night|a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|am|pm))?\\b",
        RegexOption.IGNORE_CASE
    )

    private val ishTimeRegex =
        Regex("\\b(?:at\\s+)?(\\d{1,2})\\s*-?\\s*ish\\b", RegexOption.IGNORE_CASE)
    private val firstThingRegex =
        Regex("\\bfirst\\s+thing(?:\\s+in\\s+the\\s+morning)?\\b", RegexOption.IGNORE_CASE)
    private val mealTimeRegex = Regex(
        "\\b(?:at|by|before|after|around|a|al|\u00e0s?|as|\u00e0|antes\\s+de|despu\u00e9s\\s+de|despues\\s+de|cerca\\s+de|depois\\s+de|apr\u00e8s|apres|avant|vers)\\s+(breakfast|brunch|lunch(?:time)?|dinner(?:time)?|supper|bedtime|desayuno|almuerzo|comida|cena|hora\\s+de\\s+dormir|caf\u00e9\\s+da\\s+manh\u00e3|cafe\\s+da\\s+manha|almo\u00e7o|almoco|jantar|hora\\s+de\\s+dormir|petit[-\\s]d\u00e9jeuner|petit[-\\s]dejeuner|d\u00e9jeuner|dejeuner|d\u00eener|diner|souper|coucher)\\b",
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
        "caf\u00e9 da manh\u00e3" to LocalTime.of(8, 0),
        "cafe da manha" to LocalTime.of(8, 0),
        "almo\u00e7o" to LocalTime.of(12, 30),
        "almoco" to LocalTime.of(12, 30),
        "jantar" to LocalTime.of(19, 0),
        "petit-d\u00e9jeuner" to LocalTime.of(8, 0),
        "petit dejeuner" to LocalTime.of(8, 0),
        "petit d\u00e9jeuner" to LocalTime.of(8, 0),
        "d\u00e9jeuner" to LocalTime.of(12, 30),
        "dejeuner" to LocalTime.of(12, 30),
        "d\u00eener" to LocalTime.of(19, 0),
        "diner" to LocalTime.of(19, 0),
        "souper" to LocalTime.of(19, 0),
        "coucher" to LocalTime.of(22, 0)
    )

    // Order matters: applyFallback below returns the FIRST entry that matches anywhere in the
    // input, so this list is priority order, not just a lookup table. "morning" must precede
    // "noon" or "standup morning before noon" resolves to noon instead of 9am.
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
        "tonight" to LocalTime.of(21, 0),
        "tonite" to LocalTime.of(21, 0),
        "noche" to LocalTime.of(21, 0),
        "medianoche" to LocalTime.of(0, 0),
        "ma\u00f1ana" to LocalTime.of(9, 0),
        "manana" to LocalTime.of(9, 0),
        "mediod\u00eda" to LocalTime.of(12, 0),
        "mediodia" to LocalTime.of(12, 0),
        "tarde" to LocalTime.of(18, 0),
        "noite" to LocalTime.of(21, 0),
        "meia-noite" to LocalTime.of(0, 0),
        "manh\u00e3" to LocalTime.of(9, 0),
        "manha" to LocalTime.of(9, 0),
        "meio-dia" to LocalTime.of(12, 0),
        "matin" to LocalTime.of(9, 0),
        "midi" to LocalTime.of(12, 0),
        "apr\u00e8s-midi" to LocalTime.of(15, 0),
        "apres-midi" to LocalTime.of(15, 0),
        "soir" to LocalTime.of(18, 0),
        "nuit" to LocalTime.of(21, 0),
        "minuit" to LocalTime.of(0, 0)
    )

    fun applyExplicit(context: ParserContext, timeFormatter: DateTimeFormatter): String? {
        context.firstFreeMatch(time12Regex)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3]
            if (hour != null && hour in 1..12 && minute in 0..59) {
                context.claimTime(match.range)
                return format(hour.toHour24(meridiem), minute, timeFormatter)
            }
        }

        context.firstFreeMatch(timeOClockRegex)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val modifier = match.groupValues[2].lowercase()
            if (hour != null && hour in 1..12) {
                context.claimTime(match.range)
                return format(hour.toHour24(modifier, inferBarePm = true), 0, timeFormatter)
            }
        }

        context.firstFreeMatch(atTimeRegex)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val modifier = match.groupValues[3].lowercase()
            if (hour != null && hour in 1..12 && minute in 0..59) {
                context.claimTime(match.range)
                return format(hour.toHour24(modifier, inferBarePm = true), minute, timeFormatter)
            }
        }

        context.firstFreeMatch(quarterHalfRegex)?.let { match ->
            val type = match.groupValues[1].lowercase()
            val hourRaw = match.groupValues[2].lowercase()
            val modifier = match.groupValues[3].lowercase()
            val baseHour = hourRaw.toIntOrNull() ?: wordToHourMap[hourRaw]
            if (baseHour != null && baseHour in 1..12) {
                val (effectiveHour, minute) = when {
                    type.contains("half") -> baseHour to 30
                    type.contains("quarter past") -> baseHour to 15
                    type.contains("quarter to") -> (if (baseHour == 1) 12 else baseHour - 1) to 45
                    else -> baseHour to 0
                }
                context.claimTime(match.range)
                return format(effectiveHour.toHour24(modifier, inferBarePm = true), minute, timeFormatter)
            }
        }

        context.firstFreeMatch(writtenHourRegex)?.let { match ->
            val hour = wordToHourMap[match.groupValues[1].lowercase()]
            val minute = when (match.groupValues[2].lowercase()) {
                "fifteen", "15" -> 15
                "thirty", "30" -> 30
                "forty five", "45" -> 45
                else -> 0
            }
            val modifier = match.groupValues[3].lowercase()
            if (hour != null && hour in 1..12) {
                context.claimTime(match.range)
                return format(hour.toHour24(modifier, inferBarePm = true), minute, timeFormatter)
            }
        }

        context.firstFreeMatch(mealTimeRegex)?.let { match ->
            mealTimes[match.groupValues[1].lowercase()]?.let { clock ->
                context.claimTime(match.range)
                return clock.formatStorage(timeFormatter)
            }
        }

        context.firstFreeMatch(ishTimeRegex)?.let { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..23 }?.let { hour ->
                val hour24 = if (hour in 1..7) hour + 12 else hour
                context.claimTime(match.range)
                return format(hour24 % 24, 0, timeFormatter)
            }
        }

        context.firstFreeMatch(firstThingRegex)?.let { match ->
            context.claimTime(match.range)
            return format(9, 0, timeFormatter)
        }

        context.firstFreeMatch(time24Regex)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23) {
                context.claimTime(match.range)
                return format(hour, minute, timeFormatter)
            }
        }

        return null
    }

    // parse() runs on every keystroke; compiling the ~14-entry ish-regex list from scratch each
    // time was measurable, so cache it the same way NaturalLanguageParser caches its own word
    // regexes.
    private val ishRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun cachedIshWordRegex(word: String): Regex =
        ishRegexCache.getOrPut(word) { literalIshWordRegex(word) }

    fun applyFallback(context: ParserContext, timeFormatter: DateTimeFormatter): String? {
        for ((word, clock) in timeOfDayWords) {
            context.firstFreeMatch(cachedIshWordRegex(word))?.let { match ->
                context.claimTime(match.range)
                return clock.formatStorage(timeFormatter)
            }
        }

        context.firstFreeMatch(bareMeridiemRegex)?.let { match ->
            val isPm = match.groupValues[1].lowercase().contains("p")
            context.claimTime(match.range)
            return if (isPm) "5:00 PM" else "9:00 AM"
        }

        return null
    }

    private fun Int.toHour24(modifier: String, inferBarePm: Boolean = false): Int {
        // `modifier` isn't reliably lowercased by every caller (time12Regex passes its raw
        // capture group straight through), so an uppercase "3PM" — which Android autocapitalize
        // makes common — must not fail this check and silently fall through to AM.
        val m = modifier.lowercase()
        val isPm = m.contains("p") || m == "afternoon" || m == "evening" || m == "night"
        val isAm = m.contains("a") || m == "morning"
        return when {
            isPm && this != 12 -> this + 12
            isAm && this == 12 -> 0
            inferBarePm && !isPm && !isAm && this in 1..7 -> this + 12
            else -> this
        }
    }

    private fun format(hour: Int, minute: Int, timeFormatter: DateTimeFormatter): String =
        LocalTime.of(hour, minute).formatStorage(timeFormatter)

    private fun LocalTime.formatStorage(timeFormatter: DateTimeFormatter): String =
        format(timeFormatter).uppercase(Locale.getDefault())

}
