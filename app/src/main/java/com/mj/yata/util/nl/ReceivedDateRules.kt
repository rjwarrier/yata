package com.mj.yata.util.nl

import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.QuickAddHighlightType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Receipt is historical metadata, so claim it before the normal scheduling rules. */
internal object ReceivedDateRules {
    // Curated aliases only: do not fuzzy-match ordinary words such as "revised" or "reserved",
    // and do not treat "rec" as receipt (it is also used for recurrence).
    private const val RECEIVED_WORD = "received|recieved|recived|recievd|receivd|receved|recved|recevied|receieved|recieived|receivedd|recievedd|rcvdd|rcvd|recvd|recd|recv|rcv|rec['’]d|recv['’]d|rcv['’]d"
    private const val RECEIPT_NOUN = "receipt|reciept|recipt|recepit|receit|rcpt|receiving|arrival|delivery|intake|assignment|handover"
    private const val DATE_WORD = "(?:date|dte|dt)\\b\\.?"
    private const val RECEIPT_VERB = "$RECEIVED_WORD|got|obtained|arrived|delivered|assigned|handed\\s+(?:over|in)|passed\\s+(?:over|on)|came\\s+(?:in|through|over)|landed|picked\\s+up|taken\\s+in|took\\s+(?:delivery|receipt)|accepted"
    private const val RECEIPT_LABEL = "(?:$DATE_WORD\\s+(?:of\\s+)?(?:$RECEIPT_NOUN|$RECEIVED_WORD)(?![\\p{L}\\p{N}_’'])\\.?)|(?:(?:$RECEIPT_NOUN)\\s+$DATE_WORD)|(?:d\\.?o\\.?r\\.?)(?=\\s*[:=])|in\\s+(?:my\\s+|our\\s+)?inbox|in\\s+hand"
    // Also used by the start-date rule to stop at the next receipt field.
    const val KEYWORD_ALT = "(?:$RECEIPT_LABEL)|(?:(?:(?:i|we)\\s+(?:have\\s+|had\\s+)?)?(?:(?:was|were|is|has\\s+been|had\\s+been|just)\\s+)?(?:$RECEIPT_VERB)(?![\\p{L}’'])\\.?(?:\\s+$DATE_WORD)?)"
    private val prefix = Regex(
        "\\b(?:$KEYWORD_ALT)(?:\\s+(?:back\\s+on|as\\s+(?:of|at)|on\\s+$DATE_WORD|on|dated|dtd\\.?|since|at))?\\s*[:=–-]?\\s*",
        RegexOption.IGNORE_CASE
    )
    private val boundary = Regex(
        ";|,(?!\\s*\\d)|(?:[,;]\\s*|\\s+)(?:(?:due|deadline|starts?|starting|begins?|remind|every|repeat)\\b|(?:$KEYWORD_ALT)\\b|[@#!+=])|[\\n\\r]",
        RegexOption.IGNORE_CASE
    )
    private const val COUNT = "a\\s+couple(?:\\s+of)?|a|an|$NATURAL_LANGUAGE_NUMBER_COUNT"
    private const val LAST = "(?:last|lst|previous|prev\\.?|prv\\.?)"
    private const val DAY = "(?:days?|dys?|d)"
    private const val BUSINESS_DAY = "(?:(?:business|buisness|biz|bus|working|work|wrk|wkng)\\s+$DAY|workdays?|bdays?|bds?)"
    private val ago = Regex("^($COUNT)\\s+($BUSINESS_DAY|$DAY|nights?|weeks?|wks?|fortnights?|months?|mos?|mths?|mnths?|quarters?|qtrs?|years?|yrs?)\\s+(?:ago|back|earlier)\\b", RegexOption.IGNORE_CASE)
    private val last = Regex("^$LAST\\s+([\\p{L}]+)\\b", RegexOption.IGNORE_CASE)
    private val dayBeforeYesterday = Regex("^(?:the\\s+)?day\\s+(?:before|b4)\\s+yesterday\\b", RegexOption.IGNORE_CASE)
    private val yesterdayAlias = Regex("^(?:yesterday|yest|ystrdy|ystrday|ystdy|yday|y['’]day|ydy)\\b", RegexOption.IGNORE_CASE)
    private val todayAlias = Regex("^(?:today|tdy|tday|2dy)\\b", RegexOption.IGNORE_CASE)
    private val earlierToday = Regex("^(?:earlier\\s+today|just\\s+now)\\b", RegexOption.IGNORE_CASE)
    private val pastWeekday = Regex("^(?:this\\s+past|past|this\\s+last)\\s+([\\p{L}]+)\\b", RegexOption.IGNORE_CASE)
    private val weekdayLastWeek = Regex("^(?:([\\p{L}]+)\\s+(?:of\\s+)?$LAST\\s+(?:week|wk)|$LAST\\s+(?:week|wk)\\s+(?:on\\s+)?([\\p{L}]+))\\b", RegexOption.IGNORE_CASE)
    private val periodEdge = Regex("^(?:(?:the\\s+)?(start|beginning|end)\\s+of|(?:the\\s+)?(first|last)\\s+day\\s+of)\\s+(?:the\\s+)?$LAST\\s+(month|year)\\b", RegexOption.IGNORE_CASE)
    // "Previous" names one working day; an unspecified plural is not a concrete date.
    private val previousBusinessDay = Regex("^$LAST\\s+$BUSINESS_DAY(?<!s)\\b", RegexOption.IGNORE_CASE)
    private val businessDayUnit = Regex("^$BUSINESS_DAY$", RegexOption.IGNORE_CASE)

    private val literalQuotes = Regex("(?<![\\p{L}\\p{N}])(?:\"[^\"\\n]*\"|'[^'\\n]*')")
    private val nonReceiptContext = Regex(
        "(?:\\b(?:not|never|no|without|if|when|once|until|after|before|awaiting|expecting|expected)|" +
            "\\b(?:hasn['’]t|haven['’]t|wasn['’]t|weren['’]t|isn['’]t|didn['’]t|don['’]t)|" +
            "\\b(?:to\\s+be|will\\s+be|not\\s+yet|still\\s+not)|\\bremind(?:\\s+me)?(?:\\s+to)?)(?:\\s+(?:been|yet|actually|already)){0,2}\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val explicitReceipt = Regex("^(?:$RECEIPT_LABEL)|\\b(?:$RECEIVED_WORD)\\b", RegexOption.IGNORE_CASE)
    private val badDateRemainder = Regex("^(?:[/.\\-]\\d|,?\\s*\\d{3,})")
    private val clockSuffix = Regex("^\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE)

    fun apply(context: ParserContext, parseNested: (String) -> ParsedQuickAdd, weekdays: Map<String, DayOfWeek>, months: Map<String, Int>): LocalDate? {
        // Most task titles contain no receipt phrase; avoid scanning entity/quote patterns then.
        val candidates = prefix.findAll(context.raw).toList()
        if (candidates.isEmpty()) return null
        val reserved = EntityRules.receiptNameRanges(context) + literalQuotes.findAll(context.raw).map { it.range }.toList()
        for (match in candidates) {
            if (!context.isFree(match.range) || reserved.any { it.first <= match.range.last && match.range.first <= it.last }) continue
            val previous = context.raw.getOrNull(match.range.first - 1)
            if (previous != null && (previous.isLetterOrDigit() || previous in "_@#+=/-")) continue
            if (nonReceiptContext.containsMatchIn(context.raw.take(match.range.first).takeLast(64))) continue
            val start = match.range.last + 1
            if (context.raw.getOrNull(start - 1)?.isLetterOrDigit() == true && context.raw.getOrNull(start)?.isLetterOrDigit() == true) continue
            val tail = context.raw.substring(start).take(160)
            val end = boundary.find(tail)?.range?.first ?: tail.length
            val phrase = tail.substring(0, end).trimEnd(',', ';', ' ', '.')
            if (phrase.isBlank()) continue
            val resolved = resolve(phrase, context.referenceDate, context.dayFirst, parseNested, weekdays, months) ?: continue
            // Broad past-tense synonyms must not turn future plans into receipt metadata.
            if (resolved.first > context.referenceDate && !explicitReceipt.containsMatchIn(match.value)) continue
            val remainder = phrase.substring(resolved.second)
            if (remainder.firstOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true ||
                badDateRemainder.containsMatchIn(remainder) || clockSuffix.containsMatchIn(remainder)) continue
            var claimStart = match.range.first
            var before = claimStart - 1
            while (before >= 0 && context.raw[before].isWhitespace()) before--
            if (before >= 0 && context.raw[before] in ",;") claimStart = before
            var claimEnd = start + resolved.second
            if (context.raw.getOrNull(claimEnd) in listOf(',', ';')) claimEnd++
            val range = claimStart until claimEnd
            if (!context.isFree(range)) continue
            context.claim(range, QuickAddHighlightType.ReceivedDate)
            return resolved.first
        }
        return null
    }

    private fun resolve(phrase: String, reference: LocalDate, dayFirst: Boolean, parseNested: (String) -> ParsedQuickAdd, weekdays: Map<String, DayOfWeek>, months: Map<String, Int>): Pair<LocalDate, Int>? {
        earlierToday.find(phrase)?.let { return reference to it.value.length }
        yesterdayAlias.find(phrase)?.let { return reference.minusDays(1) to it.value.length }
        todayAlias.find(phrase)?.let { return reference to it.value.length }
        dayBeforeYesterday.find(phrase)?.let { return reference.minusDays(2) to it.value.length }
        previousBusinessDay.find(phrase)?.let { return minusBusinessDays(reference, 1) to it.value.length }
        periodEdge.find(phrase)?.let {
            val start = if (it.groupValues[3].equals("month", true)) reference.withDayOfMonth(1).minusMonths(1)
                else reference.withDayOfYear(1).minusYears(1)
            val first = it.groupValues[1].lowercase() in setOf("start", "beginning") || it.groupValues[2].equals("first", true)
            val date = if (first) start else if (it.groupValues[3].equals("month", true)) start.withDayOfMonth(start.lengthOfMonth())
                else start.withDayOfYear(start.lengthOfYear())
            return date to it.value.length
        }
        weekdayLastWeek.find(phrase)?.let {
            val word = it.groupValues[1].ifBlank { it.groupValues[2] }.lowercase()
            weekdays[word]?.let { day ->
                return reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                    .plusDays((day.value - 1).toLong()) to it.value.length
            }
        }
        pastWeekday.find(phrase)?.let {
            weekdays[it.groupValues[1].lowercase()]?.let { day ->
                return reference.with(TemporalAdjusters.previousOrSame(day)) to it.value.length
            }
        }
        ago.find(phrase)?.let {
            val token = it.groupValues[1]
            if (token.all(Char::isDigit) && token.toLongOrNull() == null) return null
            val count = naturalLanguageCountOrOne(token)
            val unit = it.groupValues[2].lowercase()
            val date = runCatching {
                when {
                    businessDayUnit.matches(unit) -> minusBusinessDays(reference, count)
                    unit.startsWith("d") || unit.startsWith("n") -> reference.minusDays(count)
                    unit.startsWith("f") -> reference.minusWeeks(Math.multiplyExact(count, 2))
                    unit.startsWith("q") -> reference.minusMonths(Math.multiplyExact(count, 3))
                    unit.startsWith("w") -> reference.minusWeeks(count)
                    unit.startsWith("m") -> reference.minusMonths(count)
                    else -> reference.minusYears(count)
                }
            }.getOrNull() ?: return null
            return date to it.value.length
        }
        last.find(phrase)?.let {
            val word = it.groupValues[1].lowercase()
            val weekday = weekdays[word]
            val date = when {
                weekday != null -> reference.with(TemporalAdjusters.previous(weekday))
                word in setOf("day", "night") -> reference.minusDays(1)
                word in setOf("week", "wk") -> reference.minusWeeks(1)
                word == "fortnight" -> reference.minusWeeks(2)
                word in setOf("quarter", "qtr") -> reference.minusMonths(3)
                word in setOf("month", "mo", "mth", "mnth") -> reference.minusMonths(1)
                word in setOf("year", "yr") -> reference.minusYears(1)
                else -> null
            } ?: return null
            return date to it.value.length
        }
        absoluteReceiptDate(phrase, reference, dayFirst, months)?.let { return it }
        // Parse only complete date prefixes. Parsing the whole tail lets a later deadline
        // outrank the receipt date ("received yesterday Review tomorrow"). Recognized words
        // beyond the date remain available to the scheduling/entity rules in the outer parse.
        val ends = Regex("\\s+").findAll(phrase).map { it.range.first }.take(12).toList() + phrase.length
        var result: Pair<LocalDate, Int>? = null
        for (end in ends.distinct().take(13)) {
            val candidate = phrase.take(end)
            val parsed = parseNested(candidate)
            val spans = parsed.highlightSpans.filter { it.type == QuickAddHighlightType.DueDate }
            if (spans.minOfOrNull { it.range.first } != 0 || spans.maxOfOrNull { it.range.last } != end - 1) continue
            val date = parsed.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val weekday = weekdays[candidate.lowercase()]
            result = (if (weekday != null) reference.with(TemporalAdjusters.previousOrSame(weekday)) else date) to end
        }
        return result
    }

    private val namedDate = Regex(
        "^(?:(\\d{1,2})(?:st|nd|rd|th)?\\s+([\\p{L}]+)\\.?|([\\p{L}]+)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?)(?:,?\\s+(\\d{4}|\\d{2}))?\\b",
        RegexOption.IGNORE_CASE
    )
    private val numericDate = Regex("^(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{4}|\\d{2}))?\\b")

    /** A yearless receipt is the most recent valid calendar date, rather than a future due date.
     * Resolve from month/day directly so Feb 29 stays Feb 29 and explicit short years survive. */
    private fun absoluteReceiptDate(phrase: String, reference: LocalDate, dayFirst: Boolean, months: Map<String, Int>): Pair<LocalDate, Int>? {
        namedDate.find(phrase)?.let { match ->
            val month = months[match.groupValues[2].ifBlank { match.groupValues[3] }.lowercase()]
            val day = match.groupValues[1].ifBlank { match.groupValues[4] }.toIntOrNull()
            if (month != null && day != null) {
                return calendarDate(month, day, match.groupValues[5], reference)?.let { it to match.value.length }
            }
        }
        numericDate.find(phrase)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val (month, day) = when {
                first in 1..12 && second in 1..12 -> if (dayFirst) second to first else first to second
                first in 1..12 && second in 13..31 -> first to second
                first in 13..31 && second in 1..12 -> second to first
                else -> return null
            }
            return calendarDate(month, day, match.groupValues[3], reference)?.let { it to match.value.length }
        }
        return null
    }

    private fun calendarDate(month: Int, day: Int, year: String, reference: LocalDate): LocalDate? {
        if (year.isNotBlank()) {
            val explicitYear = year.toInt() + if (year.length == 2) 2000 else 0
            return runCatching { LocalDate.of(explicitYear, month, day) }.getOrNull()
        }
        for (yearsBack in 0L..8L) {
            val candidate = runCatching { LocalDate.of(Math.toIntExact(reference.year - yearsBack), month, day) }.getOrNull()
            if (candidate != null && candidate <= reference) return candidate
        }
        return null
    }

    /** Matches the parser's existing business-day meaning: Monday through Friday. */
    private fun minusBusinessDays(reference: LocalDate, count: Long): LocalDate {
        if (count == 0L) return reference
        var date = reference
        var remaining = count
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            date = date.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
            remaining--
        }
        date = date.minusWeeks(remaining / 5)
        repeat((remaining % 5).toInt()) {
            do { date = date.minusDays(1) }
            while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
        }
        return date
    }

}
