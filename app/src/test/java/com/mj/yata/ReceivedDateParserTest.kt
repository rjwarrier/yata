package com.mj.yata

import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.QuickAddHighlightType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceivedDateParserTest {
    private val ref = LocalDate.of(2026, 11, 17) // Tuesday

    @Test fun explicitFutureDatesAndRelativeDatesKeepTheirYear() {
        val cases = mapOf("2 weeks from now" to "2026-12-01", "3 days from today" to "2026-11-20",
            "in 2 weeks" to "2026-12-01", "November 12 27" to "2027-11-12", "12 Nov 27" to "2027-11-12",
            "12/11/27" to "2027-11-12", "2027-11-12" to "2027-11-12")
        for ((phrase, expected) in cases) {
            val parsed = NaturalLanguageParser.parse("Review received $phrase", ref, dayFirst = true)
            assertEquals(phrase, expected, parsed.receivedDate)
            assertNull(phrase, parsed.due)
            assertEquals(phrase, "Review", parsed.title)
        }
    }

    @Test fun yearlessReceiptDatesPreserveLeapDaysAndDateOrder() {
        for (phrase in listOf("February 29", "29 February", "29/02", "29.02")) {
            assertEquals(phrase, "2024-02-29", NaturalLanguageParser.parse("Review received $phrase", ref, dayFirst = true).receivedDate)
        }
        assertEquals("2024-02-29", NaturalLanguageParser.parse("Review received Feb 29", LocalDate.of(2024, 3, 1)).receivedDate)
        assertEquals("2024-02-29", NaturalLanguageParser.parse("Review received Feb 29", LocalDate.of(2024, 2, 29)).receivedDate)
        assertEquals("2023-12-31", NaturalLanguageParser.parse("Review received Dec 31", LocalDate.of(2024, 1, 1)).receivedDate)
        assertEquals("2026-03-04", NaturalLanguageParser.parse("Review received 3/4", ref, dayFirst = false).receivedDate)
        assertEquals("2026-04-03", NaturalLanguageParser.parse("Review received 3/4", ref, dayFirst = true).receivedDate)
    }

    @Test(timeout = 3000) fun malformedAndEscapedReceiptTextRemainsSafe() {
        for (text in listOf("Review received 99999999999999999999999 days ago",
            "Review received 9999999999999999 business days ago", "Review received February 30",
            "Review received 31/02/2026", "Review \\received yesterday")) {
            assertNull(text, NaturalLanguageParser.parse(text, ref).receivedDate)
        }
        val text = "Review " + "received on ".repeat(100)
        assertNull(NaturalLanguageParser.parse(text, ref).receivedDate)
    }

    @Test fun receiptIntroducersAndAbbreviations() {
        val phrases = listOf("received", "received on", "received date", "date received", "received:",
            "rcvd", "rcvd on", "rcvd.", "recvd on", "recd on", "recv on", "rcv on",
            "rec'd on", "recv'd on", "rcv'd on", "recieved on", "got", "got on", "got back on",
            "obtained on", "arrived on", "delivered on", "assigned on", "handed over on",
            "handed in on", "passed on", "passed over on", "came in on", "landed on")
        for (phrase in phrases) {
            val parsed = NaturalLanguageParser.parse("Review invoice $phrase yesterday", ref)
            assertEquals(phrase, "2026-11-16", parsed.receivedDate)
            assertNull(phrase, parsed.due)
            assertEquals(phrase, "Review invoice", parsed.title)
            assertTrue(phrase, parsed.highlightSpans.any { it.type == QuickAddHighlightType.ReceivedDate })
        }
    }

    @Test fun additionalReceiptPhrases() {
        val phrases = listOf("receipt date", "date of receipt", "receiving date", "date of receiving",
            "arrival date", "date of arrival", "delivery date", "date of delivery", "intake date",
            "date of intake", "assignment date", "date of assignment", "handover date", "date of handover",
            "reciept date", "rcpt date", "was received on", "were received on", "has been received on",
            "had been received on", "just received", "I received on", "we received on", "I have received on",
            "we had received on", "came through on", "came over on", "picked up on", "taken in on",
            "took delivery on", "took receipt on", "accepted on", "received as of", "received as at",
            "received since", "received at", "received on date", "in my inbox since", "in our inbox on",
            "in inbox since", "in hand since", "recived on", "RCVD ON")
        for (phrase in phrases) {
            val parsed = NaturalLanguageParser.parse("Review invoice $phrase yesterday", ref)
            assertEquals(phrase, "2026-11-16", parsed.receivedDate)
            assertNull(phrase, parsed.due)
            assertEquals(phrase, "Review invoice", parsed.title)
        }
    }

    @Test fun additionalHistoricalExpressions() {
        val dates = mapOf("earlier today" to "2026-11-17", "just now" to "2026-11-17",
            "this past Friday" to "2026-11-13", "past Monday" to "2026-11-16",
            "this last Friday" to "2026-11-13", "Friday last week" to "2026-11-13",
            "Monday of last week" to "2026-11-09", "last week on Friday" to "2026-11-13",
            "last week Friday" to "2026-11-13", "a couple of days ago" to "2026-11-15",
            "a couple weeks back" to "2026-11-03", "three nights earlier" to "2026-11-14",
            "a fortnight ago" to "2026-11-03", "last fortnight" to "2026-11-03",
            "last quarter" to "2026-08-17", "2 quarters ago" to "2026-05-17",
            "twenty-one days ago" to "2026-10-27", "3 working days ago" to "2026-11-12",
            "2 business days back" to "2026-11-13", "previous business day" to "2026-11-16",
            "last working day" to "2026-11-16", "end of last month" to "2026-10-31",
            "beginning of last month" to "2026-10-01", "the first day of last month" to "2026-10-01",
            "last day of previous year" to "2025-12-31", "start of last year" to "2025-01-01")
        for ((phrase, expected) in dates) {
            val parsed = NaturalLanguageParser.parse("Review receipt date: $phrase", ref)
            assertEquals(phrase, expected, parsed.receivedDate)
            assertNull(phrase, parsed.due)
            assertEquals(phrase, "Review", parsed.title)
        }
    }

    @Test fun businessDaysSkipWeekendsFromEveryReferenceDay() {
        for (offset in 0L..6L) {
            val date = ref.plusDays(offset)
            for (count in 0L..12L) {
                var expected = date
                var remaining = count
                while (remaining > 0) {
                    expected = expected.minusDays(1)
                    if (expected.dayOfWeek.value <= 5) remaining--
                }
                assertEquals("$date / $count", expected.toString(), NaturalLanguageParser.parse(
                    "Review received $count business days ago", date).receivedDate)
            }
        }
    }

    @Test fun additionalPhrasesDoNotEatOrdinaryTitlesOrScheduleFields() {
        for (text in listOf("Review receipt date format", "Check arrival date with client",
            "Pick up parcel", "Review accepted proposal", "Review delivery date options Friday",
            "Review received feedback on Friday")) {
            assertNull(text, NaturalLanguageParser.parse(text, ref).receivedDate)
        }
        val parsed = NaturalLanguageParser.parse(
            "Review starts tomorrow was received on Friday last week due Monday at 5pm #urgent", ref)
        assertEquals("2026-11-13", parsed.receivedDate)
        assertEquals("2026-11-18", parsed.startDate)
        assertEquals("2026-11-23", parsed.due)
        assertEquals("5:00 PM", parsed.time)
        assertEquals(listOf("urgent"), parsed.tagNames)
        assertEquals("Review due", parsed.title)
    }

    @Test fun receiptAndScheduleStayIndependentInEitherOrder() {
        for (text in listOf("Review received yesterday due tomorrow", "Review due tomorrow received yesterday")) {
            val parsed = NaturalLanguageParser.parse(text, ref)
            assertEquals(text, "2026-11-16", parsed.receivedDate)
            assertEquals(text, "2026-11-18", parsed.due)
            assertEquals(text, "Review due", parsed.title)
        }
        val parsed = NaturalLanguageParser.parse("Review starts tomorrow received yesterday due Friday at 5pm", ref)
        assertEquals("2026-11-16", parsed.receivedDate)
        assertEquals("2026-11-18", parsed.startDate)
        assertEquals("2026-11-20", parsed.due)
        assertEquals("5:00 PM", parsed.time)
        assertEquals("Review due", parsed.title)
    }

    @Test fun historicalAndAbsoluteDateForms() {
        val dates = mapOf("today" to "2026-11-17", "day before yesterday" to "2026-11-15",
            "last Friday" to "2026-11-13", "previous Monday" to "2026-11-16", "Friday" to "2026-11-13",
            "last week" to "2026-11-10", "last month" to "2026-10-17", "last year" to "2025-11-17",
            "3 days ago" to "2026-11-14", "two weeks back" to "2026-11-03", "a month ago" to "2026-10-17",
            "2026-10-29" to "2026-10-29", "12 Nov 2026" to "2026-11-12", "November 12" to "2026-11-12",
            "December 12" to "2025-12-12", "tomorrow" to "2026-11-18")
        for ((phrase, expected) in dates) {
            val parsed = NaturalLanguageParser.parse("Review received on $phrase", ref)
            assertEquals(phrase, expected, parsed.receivedDate)
            assertEquals(phrase, "Review", parsed.title)
            assertNull(phrase, parsed.due)
        }
    }

    @Test fun ordinaryVerbsArePreservedAndTrailingTitleSurvives() {
        for (text in listOf("Review received feedback", "Got to call client", "Check delivered parcel", "Assigned to Alex")) {
            val parsed = NaturalLanguageParser.parse(text, ref)
            assertNull(text, parsed.receivedDate)
            assertEquals(text, text, parsed.title)
        }
        val parsed = NaturalLanguageParser.parse("received yesterday Review invoice", ref)
        assertEquals("2026-11-16", parsed.receivedDate)
        assertEquals("Review invoice", parsed.title)
    }

    @Test fun receiptSyntaxSupportsPunctuationAndDateOrder() {
        val parsed = NaturalLanguageParser.parse("Review rcvd: 12/11/2026, due tomorrow", ref, dayFirst = true)
        assertEquals("2026-11-12", parsed.receivedDate)
        assertEquals("2026-11-18", parsed.due)
        assertEquals("Review due", parsed.title)
    }
}
