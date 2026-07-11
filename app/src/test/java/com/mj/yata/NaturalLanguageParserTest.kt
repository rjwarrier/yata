package com.mj.yata

import com.mj.yata.util.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NaturalLanguageParserTest {

    private val ref = LocalDate.of(2026, 7, 4) // a Saturday

    @Test
    fun parsesTomorrowAndTime() {
        val result = NaturalLanguageParser.parse("tomorrow 3pm buy milk", ref)
        assertEquals("2026-07-05", result.due)
        assertEquals("3:00 PM", result.time)
        assertEquals("buy milk", result.title)
    }

    @Test
    fun parsesToday() {
        val result = NaturalLanguageParser.parse("today call mom", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("call mom", result.title)
    }

    @Test
    fun parsesInNDays() {
        val result = NaturalLanguageParser.parse("in 3 days renew license", ref)
        assertEquals("2026-07-07", result.due)
        assertEquals("renew license", result.title)
    }

    @Test
    fun parsesNextWeekday() {
        val result = NaturalLanguageParser.parse("next monday team sync", ref)
        assertEquals("2026-07-06", result.due)
        assertEquals("team sync", result.title)
    }

    @Test
    fun parsesBareWeekdayName() {
        val result = NaturalLanguageParser.parse("friday grocery run", ref)
        assertEquals("2026-07-10", result.due)
        assertEquals("grocery run", result.title)
    }

    @Test
    fun leavesMentionTokensUntouched() {
        val result = NaturalLanguageParser.parse("tomorrow buy milk @home #errand", ref)
        assertEquals("2026-07-05", result.due)
        assertEquals("buy milk @home #errand", result.title)
    }

    @Test
    fun noMatchReturnsOriginalTitle() {
        val result = NaturalLanguageParser.parse("just a plain task", ref)
        assertNull(result.due)
        assertNull(result.time)
        assertEquals("just a plain task", result.title)
    }

    @Test
    fun parsesMonthDayFuture() {
        val result = NaturalLanguageParser.parse("book flight jul 20", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("book flight", result.title)
    }

    @Test
    fun parsesFullMonthNameDayWithOrdinal() {
        val result = NaturalLanguageParser.parse("pay rent July 20th", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("pay rent", result.title)
    }

    @Test
    fun parsesDayThenMonth() {
        val result = NaturalLanguageParser.parse("submit report 20th july", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("submit report", result.title)
    }

    @Test
    fun parsesMonthDayRollsToNextYearWhenPast() {
        // ref is Jul 4 2026; "jan 5" has already passed this year, so it should roll to 2027.
        val result = NaturalLanguageParser.parse("renew passport jan 5", ref)
        assertEquals("2027-01-05", result.due)
        assertEquals("renew passport", result.title)
    }

    @Test
    fun parsesMonthDayWithExplicitYear() {
        val result = NaturalLanguageParser.parse("anniversary jan 5 2026", ref)
        assertEquals("2026-01-05", result.due)
        assertEquals("anniversary", result.title)
    }

    @Test
    fun remindPrefixedDateSetsAtTimeReminder() {
        val result = NaturalLanguageParser.parse("call dentist remind jul 20", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("At time", result.reminder)
        assertEquals("call dentist", result.title)
    }

    @Test
    fun remindPrefixedWeekdaySetsAtTimeReminder() {
        val result = NaturalLanguageParser.parse("remind me next wednesday team sync", ref)
        assertEquals("2026-07-08", result.due)
        assertEquals("At time", result.reminder)
        assertEquals("team sync", result.title)
    }

    @Test
    fun parsesIsoDate() {
        val result = NaturalLanguageParser.parse("flight booking 2026-07-20", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("flight booking", result.title)
    }

    @Test
    fun parsesSlashDateUsOrder() {
        val result = NaturalLanguageParser.parse("pay taxes 7/20", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("pay taxes", result.title)
    }

    @Test
    fun parsesSlashDateSwapsWhenFirstNumberInvalidMonth() {
        // 20/7 can't be month 20, so day/month is inferred instead of US month/day.
        val result = NaturalLanguageParser.parse("pay taxes 20/7", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("pay taxes", result.title)
    }

    @Test
    fun parsesSlashDateWithFourDigitYear() {
        val result = NaturalLanguageParser.parse("renewal 1/5/2027", ref)
        assertEquals("2027-01-05", result.due)
        assertEquals("renewal", result.title)
    }

    @Test
    fun parsesTheNthOfMonth() {
        val result = NaturalLanguageParser.parse("submit report the 20th of july", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("submit report", result.title)
    }

    @Test
    fun parsesBareOrdinalDayOfMonth() {
        // ref is Jul 4; "the 20th" with no month named should resolve within the current month.
        val result = NaturalLanguageParser.parse("rent due the 20th", ref)
        assertEquals("2026-07-20", result.due)
        assertEquals("rent due", result.title)
    }

    @Test
    fun parsesBareOrdinalDayRollsToNextMonthWhenPast() {
        val result = NaturalLanguageParser.parse("rent due the 1st", ref)
        assertEquals("2026-08-01", result.due)
        assertEquals("rent due", result.title)
    }

    @Test
    fun parsesDayAfterTomorrow() {
        val result = NaturalLanguageParser.parse("day after tomorrow dentist", ref)
        assertEquals("2026-07-06", result.due)
        assertEquals("dentist", result.title)
    }

    @Test
    fun parsesFortnight() {
        val result = NaturalLanguageParser.parse("in a fortnight review contract", ref)
        assertEquals("2026-07-18", result.due)
        assertEquals("review contract", result.title)
    }

    @Test
    fun parsesAWeekFromNow() {
        val result = NaturalLanguageParser.parse("a week from now follow up", ref)
        assertEquals("2026-07-11", result.due)
        assertEquals("follow up", result.title)
    }

    @Test
    fun parsesInAWeekWithoutDigit() {
        val result = NaturalLanguageParser.parse("in a week check status", ref)
        assertEquals("2026-07-11", result.due)
        assertEquals("check status", result.title)
    }

    @Test
    fun parsesEodAbbreviation() {
        val result = NaturalLanguageParser.parse("finish slides eod", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("finish slides", result.title)
    }

    @Test
    fun parsesYesterday() {
        val result = NaturalLanguageParser.parse("yesterday standup notes", ref)
        assertEquals("2026-07-03", result.due)
        assertEquals("standup notes", result.title)
    }

    @Test
    fun parsesThisWeekend() {
        // ref is Saturday itself, so "this weekend" should resolve to today.
        val result = NaturalLanguageParser.parse("this weekend clean garage", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("clean garage", result.title)
    }

    @Test
    fun parsesNextMonth() {
        val result = NaturalLanguageParser.parse("next month renew passport", ref)
        assertEquals("2026-08-04", result.due)
        assertEquals("renew passport", result.title)
    }

    @Test
    fun parsesInNMonths() {
        val result = NaturalLanguageParser.parse("in 2 months dentist checkup", ref)
        assertEquals("2026-09-04", result.due)
        assertEquals("dentist checkup", result.title)
    }

    @Test
    fun parsesEndOfMonth() {
        val result = NaturalLanguageParser.parse("end of month rent due", ref)
        assertEquals("2026-07-31", result.due)
        assertEquals("rent due", result.title)
    }

    @Test
    fun parsesEndOfWeek() {
        val result = NaturalLanguageParser.parse("end of week status report", ref)
        assertEquals("2026-07-05", result.due) // next Sunday after Saturday 7/4
        assertEquals("status report", result.title)
    }

    @Test
    fun parsesThisWeekdayIncludingToday() {
        // ref itself is a Saturday, so "this saturday" should mean today, not next week.
        val result = NaturalLanguageParser.parse("this saturday farmers market", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("farmers market", result.title)
    }

    @Test
    fun parsesTonightAsTodayWithEveningTime() {
        val result = NaturalLanguageParser.parse("tonight watch movie", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("9:00 PM", result.time)
        assertEquals("watch movie", result.title)
    }

    @Test
    fun parsesTimeOfDayWords() {
        assertEquals("9:00 AM", NaturalLanguageParser.parse("morning workout", ref).time)
        assertEquals("12:00 PM", NaturalLanguageParser.parse("noon lunch", ref).time)
        assertEquals("3:00 PM", NaturalLanguageParser.parse("afternoon nap", ref).time)
        assertEquals("6:00 PM", NaturalLanguageParser.parse("evening walk", ref).time)
        assertEquals("12:00 AM", NaturalLanguageParser.parse("midnight snack", ref).time)
    }

    @Test
    fun explicitTimeWinsOverTimeOfDayWord() {
        val result = NaturalLanguageParser.parse("evening 8pm dinner", ref)
        assertEquals("8:00 PM", result.time)
    }

    @Test
    fun parses24HourTime() {
        val result = NaturalLanguageParser.parse("15:30 team call", ref)
        assertEquals("3:30 PM", result.time)
        assertEquals("team call", result.title)
    }

    @Test
    fun parsesDailyRecurrence() {
        val result = NaturalLanguageParser.parse("daily meditation", ref)
        assertNotNull(result.recurrence)
        assertEquals("daily", result.recurrence?.freq)
        assertEquals("meditation", result.title)
    }

    @Test
    fun parsesEveryWeekdayRecurrence() {
        val result = NaturalLanguageParser.parse("every monday gym", ref)
        assertNotNull(result.recurrence)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(listOf("MO"), result.recurrence?.byday)
        assertEquals("gym", result.title)
        // "every monday" must not also trigger the bare-weekday due-date rule.
        assertNull(result.due)
    }

    @Test
    fun parsesWeekdaysAndWeekendsRecurrence() {
        val weekdays = NaturalLanguageParser.parse("weekdays commute", ref)
        assertEquals(listOf("MO", "TU", "WE", "TH", "FR"), weekdays.recurrence?.byday)

        val weekends = NaturalLanguageParser.parse("weekends chores", ref)
        assertEquals(listOf("SA", "SU"), weekends.recurrence?.byday)
    }

    @Test
    fun parsesMonthlyAndYearlyRecurrence() {
        assertEquals("monthly", NaturalLanguageParser.parse("monthly rent", ref).recurrence?.freq)
        assertEquals("yearly", NaturalLanguageParser.parse("yearly checkup", ref).recurrence?.freq)
        assertEquals("yearly", NaturalLanguageParser.parse("annually renew license", ref).recurrence?.freq)
    }

    @Test
    fun noRecurrenceWhenNotMentioned() {
        val result = NaturalLanguageParser.parse("buy milk", ref)
        assertNull(result.recurrence)
    }

    @Test
    fun parsesEverySundayRecurrence() {
        val result = NaturalLanguageParser.parse("every sunday laundry", ref)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(listOf("SU"), result.recurrence?.byday)
        assertEquals("laundry", result.title)
        assertNull(result.due)
    }

    @Test
    fun parsesEveryAlternateDay() {
        val result = NaturalLanguageParser.parse("every alternate day water plants", ref)
        assertEquals("daily", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("water plants", result.title)
    }

    @Test
    fun parsesEveryOtherWeek() {
        val result = NaturalLanguageParser.parse("every other week team retro", ref)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("team retro", result.title)
    }

    @Test
    fun parsesEveryNDays() {
        val result = NaturalLanguageParser.parse("every 3 days water fish", ref)
        assertEquals("daily", result.recurrence?.freq)
        assertEquals(3, result.recurrence?.interval)
        assertEquals("water fish", result.title)
    }

    @Test
    fun parsesEveryNWeeks() {
        val result = NaturalLanguageParser.parse("every 2 weeks haircut", ref)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("haircut", result.title)
    }

    @Test
    fun parsesEveryNMonths() {
        val result = NaturalLanguageParser.parse("every 6 months dentist", ref)
        assertEquals("monthly", result.recurrence?.freq)
        assertEquals(6, result.recurrence?.interval)
        assertEquals("dentist", result.title)
    }

    @Test
    fun highlightRangesCoverRecognizedSpansInOriginalText() {
        val raw = "tomorrow 3pm buy milk"
        val result = NaturalLanguageParser.parse(raw, ref)
        assertEquals(2, result.highlightRanges.size)
        // Each recognized range, sliced from the original raw string, should be the phrase itself.
        val slices = result.highlightRanges.map { raw.substring(it.first, it.last + 1) }
        assertTrue(slices.any { it.equals("tomorrow", ignoreCase = true) })
        assertTrue(slices.any { it.equals("3pm", ignoreCase = true) })
    }

    @Test
    fun highlightRangesIncludeRecurrencePhrase() {
        val raw = "every monday gym"
        val result = NaturalLanguageParser.parse(raw, ref)
        assertEquals(1, result.highlightRanges.size)
        val range = result.highlightRanges.first()
        assertEquals("every monday", raw.substring(range.first, range.last + 1))
    }

    @Test
    fun escapedKeywordIsKeptAsLiteralText() {
        val result = NaturalLanguageParser.parse("call mom \\today", ref)
        assertNull(result.due)
        assertEquals("call mom today", result.title)
    }

    @Test
    fun escapedKeywordHasNoHighlightRange() {
        val result = NaturalLanguageParser.parse("call mom \\today", ref)
        assertTrue(result.highlightRanges.isEmpty())
    }

    @Test
    fun escapeProtectsWholeMultiWordPhrase() {
        // Escaping "monday" alone should also stop "every monday" from matching as a whole.
        val result = NaturalLanguageParser.parse("every \\monday gym", ref)
        assertNull(result.recurrence)
        assertEquals("every monday gym", result.title)
    }

    @Test
    fun unescapedKeywordsElsewhereStillParse() {
        val result = NaturalLanguageParser.parse("tomorrow review \\today notes", ref)
        assertEquals("2026-07-05", result.due)
        assertEquals("review today notes", result.title)
    }

    @Test
    fun parsesRemindAtTimeKeyword() {
        val result = NaturalLanguageParser.parse("tomorrow remind at time pay rent", ref)
        assertEquals("At time", result.reminder)
        assertEquals("pay rent", result.title)
    }

    @Test
    fun parsesRemindMinutesBefore() {
        val result = NaturalLanguageParser.parse("tomorrow remind me 15 min before standup", ref)
        assertEquals("15 min before", result.reminder)
        assertEquals("standup", result.title)
    }

    @Test
    fun parsesRemindHourAndDayBefore() {
        assertEquals("1 hour before", NaturalLanguageParser.parse("remind an hour before flight", ref).reminder)
        assertEquals("1 day before", NaturalLanguageParser.parse("remind a day before anniversary", ref).reminder)
    }

    @Test
    fun parsesRemindAtCustomClockTime() {
        val result = NaturalLanguageParser.parse("tomorrow remind me at 5:30pm submit report", ref)
        assertEquals("5:30 PM", result.reminder)
        assertEquals("submit report", result.title)
        // The reminder's own clock time must not also get picked up as the task's due time.
        assertNull(result.time)
    }

    @Test
    fun remindDoesNotMatchWithoutKeyword() {
        val result = NaturalLanguageParser.parse("tomorrow 5:30pm submit report", ref)
        assertNull(result.reminder)
        assertEquals("5:30 PM", result.time)
    }

    @Test
    fun parsesPriorityShorthand() {
        assertEquals("high", NaturalLanguageParser.parse("!1 pay taxes", ref).priority)
        assertEquals("med", NaturalLanguageParser.parse("!2 pay taxes", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("!3 pay taxes", ref).priority)
        assertEquals("high", NaturalLanguageParser.parse("!!1 pay taxes", ref).priority)
    }

    @Test
    fun priorityShorthandStrippedFromTitle() {
        val result = NaturalLanguageParser.parse("pay taxes !1", ref)
        assertEquals("high", result.priority)
        assertEquals("pay taxes", result.title)
    }

    @Test
    fun noPriorityWhenNotMentioned() {
        assertNull(NaturalLanguageParser.parse("buy milk", ref).priority)
    }
}
