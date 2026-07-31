package com.mj.yata

import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.util.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

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
        assertEquals("6:00 PM", result.time)
        assertEquals("finish slides", result.title)
    }

    @Test
    fun parsesEobAbbreviationWithOwnTime() {
        val result = NaturalLanguageParser.parse("send invoice eob", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("5:00 PM", result.time)
        assertEquals("send invoice", result.title)
    }

    @Test
    fun eodDoesNotOverrideExplicitTime() {
        val result = NaturalLanguageParser.parse("finish slides eod 3pm", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("3:00 PM", result.time)
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

    @Test
    fun parsesWordBasedPriority() {
        assertEquals("high", NaturalLanguageParser.parse("urgent call client", ref).priority)
        assertEquals("high", NaturalLanguageParser.parse("asap fix bug", ref).priority)
        assertEquals("high", NaturalLanguageParser.parse("critical server down", ref).priority)
        assertEquals("high", NaturalLanguageParser.parse("high priority renew license", ref).priority)
        assertEquals("med", NaturalLanguageParser.parse("medium priority follow up", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("low priority read book", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("someday learn guitar", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("whenever organize garage", ref).priority)
    }

    @Test
    fun wordPriorityStrippedFromTitle() {
        val result = NaturalLanguageParser.parse("urgent call client", ref)
        assertEquals("call client", result.title)
    }

    @Test
    fun shorthandPriorityWinsOverWordPriority() {
        // Shouldn't realistically co-occur, but the explicit "!1" shorthand is checked first.
        val result = NaturalLanguageParser.parse("!3 urgent call client", ref)
        assertEquals("low", result.priority)
    }

    @Test
    fun parsesFlagPhrases() {
        assertTrue(NaturalLanguageParser.parse("flag this call client", ref).flag)
        assertTrue(NaturalLanguageParser.parse("flag it call client", ref).flag)
        assertTrue(NaturalLanguageParser.parse("flagged call client", ref).flag)
        assertTrue(NaturalLanguageParser.parse("star this call client", ref).flag)
        assertTrue(NaturalLanguageParser.parse("star it call client", ref).flag)
        assertTrue(NaturalLanguageParser.parse("important call client", ref).flag)
        assertFalse(NaturalLanguageParser.parse("buy milk", ref).flag)
    }

    @Test
    fun parsesBarePriorityShorthand() {
        assertEquals("high", NaturalLanguageParser.parse("p1 pay taxes", ref).priority)
        assertEquals("med", NaturalLanguageParser.parse("p2 pay taxes", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("p3 pay taxes", ref).priority)
        assertEquals("high", NaturalLanguageParser.parse("top priority pay taxes", ref).priority)
    }

    @Test
    fun flagStrippedFromTitle() {
        val result = NaturalLanguageParser.parse("important call client", ref)
        assertEquals("call client", result.title)
    }

    @Test
    fun flagAndPriorityAreIndependent() {
        val result = NaturalLanguageParser.parse("important low priority read book", ref)
        assertTrue(result.flag)
        assertEquals("low", result.priority)
        assertEquals("read book", result.title)
    }

    @Test
    fun parsesInNHoursWithReferenceTime() {
        val result = NaturalLanguageParser.parse("in 2 hours submit report", ref, LocalTime.of(10, 0))
        assertEquals("2026-07-04", result.due)
        assertEquals("12:00 PM", result.time)
        assertEquals("submit report", result.title)
    }

    @Test
    fun parsesInAnHourWithoutDigit() {
        val result = NaturalLanguageParser.parse("in an hour check oven", ref, LocalTime.of(10, 0))
        assertEquals("2026-07-04", result.due)
        assertEquals("11:00 AM", result.time)
    }

    @Test
    fun inHoursRollsOverToNextDay() {
        val result = NaturalLanguageParser.parse("in 3 hours call back", ref, LocalTime.of(23, 0))
        assertEquals("2026-07-05", result.due)
        assertEquals("2:00 AM", result.time)
    }

    @Test
    fun parsesNextWeekend() {
        // ref is Saturday 7/4 itself; "next weekend" should skip past this weekend to the one after.
        val result = NaturalLanguageParser.parse("next weekend camping trip", ref)
        assertEquals("2026-07-11", result.due)
        assertEquals("camping trip", result.title)
    }

    @Test
    fun parsesBeginningOfMonth() {
        val result = NaturalLanguageParser.parse("beginning of month pay rent", ref)
        assertEquals("2026-08-01", result.due)
        assertEquals("pay rent", result.title)
    }

    @Test
    fun parsesStartOfMonthAndBomAbbreviation() {
        assertEquals("2026-08-01", NaturalLanguageParser.parse("start of month review budget", ref).due)
        assertEquals("2026-08-01", NaturalLanguageParser.parse("bom review budget", ref).due)
    }

    @Test
    fun parsesInNMinutes() {
        val result = NaturalLanguageParser.parse("in 20 minutes check oven", ref, LocalTime.of(10, 0))
        assertEquals("2026-07-04", result.due)
        assertEquals("10:20 AM", result.time)
        assertEquals("check oven", result.title)
    }

    @Test
    fun parsesInAMinuteWithoutDigit() {
        val result = NaturalLanguageParser.parse("in a minute call back", ref, LocalTime.of(10, 0))
        assertEquals("10:01 AM", result.time)
    }

    @Test
    fun parsesHalfAnHour() {
        val result = NaturalLanguageParser.parse("in half an hour leave for airport", ref, LocalTime.of(10, 0))
        assertEquals("2026-07-04", result.due)
        assertEquals("10:30 AM", result.time)
        assertEquals("leave for airport", result.title)
    }

    @Test
    fun inMinutesRollsOverToNextDay() {
        val result = NaturalLanguageParser.parse("in 30 minutes call back", ref, LocalTime.of(23, 45))
        assertEquals("2026-07-05", result.due)
        assertEquals("12:15 AM", result.time)
    }

    @Test
    fun parsesBiweeklyAndQuarterlyRecurrence() {
        assertEquals("weekly", NaturalLanguageParser.parse("biweekly team sync", ref).recurrence?.freq)
        assertEquals(2, NaturalLanguageParser.parse("biweekly team sync", ref).recurrence?.interval)
        assertEquals("monthly", NaturalLanguageParser.parse("quarterly review", ref).recurrence?.freq)
        assertEquals(3, NaturalLanguageParser.parse("quarterly review", ref).recurrence?.interval)
    }

    @Test
    fun parsesHyphenatedBiweekly() {
        // Regression: a hyphen still counts as a \b word-boundary character, so the bare
        // "weekly" entry could otherwise match as a substring right inside "bi-weekly" before
        // the fuller phrase ever got a chance.
        val result = NaturalLanguageParser.parse("bi-weekly team sync", ref)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
    }

    @Test
    fun parsesSemiannuallyAndTwiceAYear() {
        for (phrase in listOf("semiannually pay premium", "semi-annually pay premium", "semi annually pay premium", "semianually pay premium", "twice a year pay premium")) {
            val result = NaturalLanguageParser.parse(phrase, ref)
            assertEquals(phrase, "monthly", result.recurrence?.freq)
            assertEquals(phrase, 6, result.recurrence?.interval)
        }
    }

    @Test
    fun parsesBiannuallyAsEveryTwoYears() {
        // "biannual" is ambiguous in English, but this file treats "bi-" consistently as
        // "interval of 2" (matching "biweekly"), so biannually = every 2 years, not 6 months.
        for (phrase in listOf("biannually renew passport", "bi-annually renew passport", "bi annually renew passport")) {
            val result = NaturalLanguageParser.parse(phrase, ref)
            assertEquals(phrase, "yearly", result.recurrence?.freq)
            assertEquals(phrase, 2, result.recurrence?.interval)
        }
    }

    @Test
    fun parsesFortnightlyRecurrence() {
        assertEquals("weekly", NaturalLanguageParser.parse("fortnightly haircut", ref).recurrence?.freq)
        assertEquals(2, NaturalLanguageParser.parse("fortnightly haircut", ref).recurrence?.interval)
        // Common typo, still recognized.
        assertEquals(2, NaturalLanguageParser.parse("fortnighly haircut", ref).recurrence?.interval)
    }

    @Test
    fun fortnightlyRecurrenceDistinctFromInAFortnightDueDate() {
        // "fortnightly" (recurrence) vs "in a fortnight" (one-off due date) must not collide.
        val recurring = NaturalLanguageParser.parse("fortnightly haircut", ref)
        assertNotNull(recurring.recurrence)
        assertNull(recurring.due)

        val oneOff = NaturalLanguageParser.parse("in a fortnight review contract", ref)
        assertNull(oneOff.recurrence)
        assertEquals("2026-07-18", oneOff.due)
    }

    @Test
    fun parsesEveryQuarterAndQtrAbbreviation() {
        assertEquals("monthly", NaturalLanguageParser.parse("every quarter review budget", ref).recurrence?.freq)
        assertEquals(3, NaturalLanguageParser.parse("every quarter review budget", ref).recurrence?.interval)
        assertEquals(3, NaturalLanguageParser.parse("every qtr review budget", ref).recurrence?.interval)
    }

    @Test
    fun parsesEveryNYears() {
        val result = NaturalLanguageParser.parse("every 2 years renew passport", ref)
        assertEquals("yearly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("renew passport", result.title)
    }

    @Test
    fun parsesEveryAlternateMonth() {
        val result = NaturalLanguageParser.parse("every other month pest control", ref)
        assertEquals("monthly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("pest control", result.title)
    }

    @Test
    fun parsesEveryAlternateYear() {
        val result = NaturalLanguageParser.parse("every alternate year eye exam", ref)
        assertEquals("yearly", result.recurrence?.freq)
        assertEquals(2, result.recurrence?.interval)
        assertEquals("eye exam", result.title)
    }

    // --- Start date ("not before") ---

    @Test
    fun parsesStartsWeekday() {
        val result = NaturalLanguageParser.parse("draft proposal starts monday", ref)
        assertEquals("2026-07-06", result.startDate)
        assertEquals("draft proposal", result.title)
        // The weekday belongs to the start phrase and must not also become a due date.
        assertNull(result.due)
    }

    @Test
    fun parsesNotBefore() {
        val result = NaturalLanguageParser.parse("chase invoice not before next week", ref)
        assertEquals("2026-07-11", result.startDate)
        assertEquals("chase invoice", result.title)
    }

    @Test
    fun parsesDeferUntil() {
        val result = NaturalLanguageParser.parse("book flights defer until tomorrow", ref)
        assertEquals("2026-07-05", result.startDate)
        assertEquals("book flights", result.title)
    }

    @Test
    fun parsesStartAndDueTogether() {
        // The two are independent: the start phrase is claimed first, leaving "due friday" to the
        // due rules. Getting this wrong in either direction silently swaps the dates.
        val result = NaturalLanguageParser.parse("tax return starts monday due friday", ref)
        assertEquals("2026-07-06", result.startDate)
        assertEquals("2026-07-10", result.due)
        // "due" survives in the title — the date rules claim the date word, not the "due" before
        // it (prepositionRegex covers for/on/at/by but deliberately not "due"). Pre-existing and
        // asserted by parsesBareOrdinalDayOfMonth ("rent due the 20th" -> "rent due").
        assertEquals("tax return due", result.title)
    }

    @Test
    fun doesNotTreatStartVerbAsStartDate() {
        // "start the report" is a title. Nothing date-like follows the keyword, so the rule must
        // decline rather than claim the words after it.
        val result = NaturalLanguageParser.parse("start the report", ref)
        assertNull(result.startDate)
        assertEquals("start the report", result.title)
    }

    @Test
    fun noStartDateWhenUnstated() {
        val result = NaturalLanguageParser.parse("buy milk tomorrow", ref)
        assertNull(result.startDate)
    }

    // ── Recurrence ────────────────────────────────────────────────────────

    @Test
    fun parsesEverySingularWeekdayAndWeekend() {
        // "every weekday" used to match nothing at all: the bare-word list only had the plurals.
        val weekdays = NaturalLanguageParser.parse("every weekday standup", ref)
        assertEquals(listOf("MO", "TU", "WE", "TH", "FR"), weekdays.recurrence?.byday)
        assertEquals("standup", weekdays.title)

        val weekend = NaturalLanguageParser.parse("every weekend chores", ref)
        assertEquals(listOf("SA", "SU"), weekend.recurrence?.byday)
        assertEquals("chores", weekend.title)
    }

    @Test
    fun parsesEachAsSynonymForEvery() {
        val result = NaturalLanguageParser.parse("each monday review", ref)
        assertEquals("weekly", result.recurrence?.freq)
        assertEquals(listOf("MO"), result.recurrence?.byday)
        // Without the synonym this fell through to the bare-weekday rule and became a one-off
        // due date on the next Monday instead of a repeat.
        assertNull(result.due)
        assertEquals("review", result.title)
    }

    @Test
    fun parsesMultipleWeekdaysInOneRecurrence() {
        val andForm = NaturalLanguageParser.parse("every monday and wednesday gym", ref)
        assertEquals(listOf("MO", "WE"), andForm.recurrence?.byday)
        assertNull(andForm.due)
        assertEquals("gym", andForm.title)

        val commaForm = NaturalLanguageParser.parse("every mon, wed, fri gym", ref)
        assertEquals(listOf("MO", "WE", "FR"), commaForm.recurrence?.byday)
        assertEquals("gym", commaForm.title)
    }

    @Test
    fun multiWeekdayDaysComeOutInWeekOrderAndDeduped() {
        val result = NaturalLanguageParser.parse("every friday, monday and monday standup", ref)
        assertEquals(listOf("MO", "FR"), result.recurrence?.byday)
    }

    @Test
    fun parsesMonthlyPinnedToADate() {
        val onThe = NaturalLanguageParser.parse("every month on the 15th rent", ref)
        assertEquals("monthly", onThe.recurrence?.freq)
        assertEquals(15, onThe.recurrence?.bymonthday)
        // The series says which day; the first one is still ahead, so it also seeds the due date.
        assertEquals("2026-07-15", onThe.due)
        assertEquals("rent", onThe.title)

        val ordinalFirst = NaturalLanguageParser.parse("every 1st of the month rent", ref)
        assertEquals(1, ordinalFirst.recurrence?.bymonthday)
        assertEquals("2026-08-01", ordinalFirst.due)
        assertEquals("rent", ordinalFirst.title)
    }

    @Test
    fun parsesLastDayOfMonthRecurrence() {
        val result = NaturalLanguageParser.parse("every last day of the month reconcile", ref)
        assertEquals("monthly", result.recurrence?.freq)
        assertEquals(-1, result.recurrence?.bymonthday) // the model's "last day, whatever it is"
        assertEquals("2026-07-31", result.due)
        assertEquals("reconcile", result.title)
    }

    @Test
    fun parsesRecurrenceEndDate() {
        val result = NaturalLanguageParser.parse("water plants every 3 days until august 15", ref)
        assertEquals("daily", result.recurrence?.freq)
        assertEquals(3, result.recurrence?.interval)
        assertEquals(RecurrenceEnds.On("2026-08-15"), result.recurrence?.ends)
        // The end date must not leak out as the due date — that's the first occurrence, not the last.
        assertNull(result.due)
        assertEquals("water plants", result.title)
    }

    @Test
    fun recurrenceEndDateDoesNotSwallowTrailingTitle() {
        // The captured phrase is lazy and stops only at end-of-input when nothing date-like
        // follows, so the claim has to end where the date does or the title is eaten whole.
        val result = NaturalLanguageParser.parse("every week until dec 20 sync", ref)
        assertEquals(RecurrenceEnds.On("2026-12-20"), result.recurrence?.ends)
        assertEquals("sync", result.title)
    }

    @Test
    fun parsesRecurrenceOccurrenceCount() {
        val result = NaturalLanguageParser.parse("daily standup for 10 times", ref)
        assertEquals(RecurrenceEnds.After(10), result.recurrence?.ends)
        assertEquals("standup", result.title)
    }

    @Test
    fun occurrenceCountIgnoredWithoutRecurrence() {
        // "10 times" on its own is part of the title, not an end condition for a series that
        // doesn't exist.
        val result = NaturalLanguageParser.parse("read it 10 times", ref)
        assertNull(result.recurrence)
        assertEquals("read it 10 times", result.title)
    }

    // ── Dates ─────────────────────────────────────────────────────────────

    @Test
    fun parsesAWeekTodayWithoutMatchingTodayInside() {
        // "today" sits inside the phrase; matching it first resolved this to the exact opposite.
        val result = NaturalLanguageParser.parse("a week today review", ref)
        assertEquals("2026-07-11", result.due)
        assertEquals("review", result.title)
    }

    @Test
    fun parsesTimeOfDayPhrasesAsTodayPlusClock() {
        val morning = NaturalLanguageParser.parse("this morning coffee", ref)
        assertEquals("2026-07-04", morning.due)
        assertEquals("9:00 AM", morning.time)
        assertEquals("coffee", morning.title)

        val afternoon = NaturalLanguageParser.parse("this afternoon call bob", ref)
        assertEquals("2026-07-04", afternoon.due)
        assertEquals("3:00 PM", afternoon.time)
    }

    @Test
    fun explicitTimeBeatsTimeOfDayPhrase() {
        val result = NaturalLanguageParser.parse("this morning at 7:30am gym", ref)
        assertEquals("2026-07-04", result.due)
        assertEquals("7:30 AM", result.time)
    }

    @Test
    fun parsesWeekdayNextWeek() {
        // Said back to front. "next week" alone used to claim its half and strand the weekday.
        val result = NaturalLanguageParser.parse("wednesday next week sync", ref)
        assertEquals("2026-07-08", result.due)
        assertEquals("sync", result.title)
    }

    @Test
    fun parsesBeginningOfNextMonth() {
        // Listed ahead of "next month", which would otherwise win and give the same day a month on.
        val result = NaturalLanguageParser.parse("beginning of next month rent", ref)
        assertEquals("2026-08-01", result.due)
        assertEquals("rent", result.title)
    }

    @Test
    fun parsesYearAndQuarterPhrases() {
        assertEquals("2027-07-04", NaturalLanguageParser.parse("next year plan", ref).due)
        assertEquals("2026-12-31", NaturalLanguageParser.parse("end of year review", ref).due)
        assertEquals("2026-10-01", NaturalLanguageParser.parse("next quarter planning", ref).due)
        assertEquals("2026-09-30", NaturalLanguageParser.parse("end of quarter close books", ref).due)
        assertEquals("2028-07-04", NaturalLanguageParser.parse("in 2 years renew passport", ref).due)
    }

    @Test
    fun parsesVagueCounts() {
        assertEquals("2026-07-06", NaturalLanguageParser.parse("in a couple of days follow up", ref).due)
        assertEquals("2026-07-25", NaturalLanguageParser.parse("in a few weeks checkup", ref).due)
        // "a" must not match inside "a few" and collapse the count back to 1.
        assertEquals("2026-07-05", NaturalLanguageParser.parse("in a day follow up", ref).due)
    }

    @Test
    fun parsesBusinessDaysSkippingTheWeekend() {
        // Saturday + 3 business days = Wednesday, not Tuesday.
        val result = NaturalLanguageParser.parse("in 3 business days ship it", ref)
        assertEquals("2026-07-08", result.due)
        assertEquals("ship it", result.title)
    }

    @Test
    fun parsesWordOrdinals() {
        assertEquals("2026-08-01", NaturalLanguageParser.parse("on the first pay rent", ref).due)
        assertEquals("2026-07-21", NaturalLanguageParser.parse("on the twenty first party", ref).due)
        assertEquals("2026-07-31", NaturalLanguageParser.parse("the thirty-first invoice", ref).due)
    }

    @Test
    fun bareOrdinalWordIsNotADate() {
        // Only ever behind "the" — "first draft" is a title, not the 1st of the month.
        val result = NaturalLanguageParser.parse("first draft of the proposal", ref)
        assertNull(result.due)
        assertEquals("first draft of the proposal", result.title)
    }

    @Test
    fun parsesDottedAndDashedDates() {
        assertEquals("2026-07-20", NaturalLanguageParser.parse("20.07.2026 dentist", ref).due)
        assertEquals("2026-07-20", NaturalLanguageParser.parse("20-07-2026 dentist", ref).due)
    }

    @Test
    fun twoPartDottedNumberIsNotADate() {
        // "1.5" is a version far more often than it is the 5th of January, so the year is required.
        val result = NaturalLanguageParser.parse("upgrade to 1.5 release", ref)
        assertNull(result.due)
        assertEquals("upgrade to 1.5 release", result.title)
    }

    @Test
    fun parsesMidMonth() {
        val result = NaturalLanguageParser.parse("mid january planning", ref)
        assertEquals("2027-01-15", result.due) // January has already passed this year
        assertEquals("planning", result.title)
    }

    @Test
    fun parsesWeekendPhrases() {
        assertEquals("2026-07-04", NaturalLanguageParser.parse("over the weekend garden", ref).due)
        assertEquals("2026-07-10", NaturalLanguageParser.parse("later this week report", ref).due)
    }

    @Test
    fun endOfDayDefersToAnExplicitDay() {
        // "eod"/"cob" say when in the day, not which day — so a named day has to win.
        val withDay = NaturalLanguageParser.parse("cob friday report", ref)
        assertEquals("2026-07-10", withDay.due)
        assertEquals("5:00 PM", withDay.time)
        assertEquals("report", withDay.title)

        val alone = NaturalLanguageParser.parse("eod send invoice", ref)
        assertEquals("2026-07-04", alone.due)
        assertEquals("6:00 PM", alone.time)
    }

    // ── Times ─────────────────────────────────────────────────────────────

    @Test
    fun parsesIshTimes() {
        // Same hour convention as every other bare-hour rule here: 1..7 reads as PM, 8..12 as AM.
        assertEquals("5:00 PM", NaturalLanguageParser.parse("5ish drinks", ref).time)
        assertEquals("8:00 AM", NaturalLanguageParser.parse("8ish coffee", ref).time)
        val noonish = NaturalLanguageParser.parse("noon-ish lunch", ref)
        assertEquals("12:00 PM", noonish.time)
        assertEquals("lunch", noonish.title) // the "ish" goes with the word it qualifies
    }

    @Test
    fun parsesFirstThingAsATimeNotADate() {
        // Time only, so a day named alongside it still gets to set the date.
        val result = NaturalLanguageParser.parse("first thing monday standup", ref)
        assertEquals("9:00 AM", result.time)
        assertEquals("2026-07-06", result.due)
        assertEquals("standup", result.title)
    }

    @Test
    fun parsesMealTimesOnlyBehindAPreposition() {
        val withPreposition = NaturalLanguageParser.parse("call the bank at lunch", ref)
        assertEquals("12:30 PM", withPreposition.time)
        assertEquals("call the bank", withPreposition.title)

        // Bare "lunch" is the task itself — claiming it would set a time and eat the word.
        val bare = NaturalLanguageParser.parse("lunch with sam", ref)
        assertNull(bare.time)
        assertEquals("lunch with sam", bare.title)
    }

    // ── Priority ──────────────────────────────────────────────────────────

    @Test
    fun parsesNegatedPriorityAsLow() {
        // "not urgent" contains "urgent"; whichever is listed first wins, so this asserts the
        // negations are ahead of the words they negate.
        assertEquals("low", NaturalLanguageParser.parse("not urgent fix typo", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("backburner idea", ref).priority)
        assertEquals("low", NaturalLanguageParser.parse("when i can sort photos", ref).priority)
    }

    @Test
    fun negatedImportantDoesNotAlsoFlag() {
        // Priority claims the phrase first, so the flag rule never sees a bare "important".
        val result = NaturalLanguageParser.parse("not important tidy desk", ref)
        assertEquals("low", result.priority)
        assertFalse(result.flag)
    }

    // ── Caching ───────────────────────────────────────────────────────────

    @Test
    fun cacheKeyIncludesReferenceTime() {
        // Same text, same day, different clock — "in 30 minutes" has to re-resolve rather than
        // serve the first answer back.
        val first = NaturalLanguageParser.parse("in 30 minutes call", ref, LocalTime.of(10, 0))
        val second = NaturalLanguageParser.parse("in 30 minutes call", ref, LocalTime.of(14, 0))
        assertEquals("10:30 AM", first.time)
        assertEquals("2:30 PM", second.time)
    }
}
