package com.mj.yata

import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.QuickAddHighlightType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceivedDateIsolationTest {
    private val ref = LocalDate.of(2026, 11, 17)
    private fun parse(text: String) = NaturalLanguageParser.parse(text, ref, dayFirst = true)
    private fun scheduling(value: ParsedQuickAdd) = value.copy(
        title = "", receivedDate = null, highlightRanges = emptyList(), highlightSpans = emptyList()
    )

    @Test fun receivedDateDoesNotChangeOtherFieldsInEitherOrder() {
        val tasks = listOf(
            "Review due tomorrow at 5pm", "Review starts Monday due Friday", "Review starts Monday", "Review every Monday at 9am",
            "Review every month until Dec 20", "Review tomorrow remind 15 minutes before",
            "Review tomorrow !1 #urgent @Alex +Work", "Review in 2 weeks", "Review in 3 business days",
            "Review vence mañana", "Review starts next week repeat weekly", "Review at 15:30 =Inbox"
        )
        val errors = mutableListOf<String>()
        for (task in tasks) {
            val original = parse(task)
            for (text in listOf("$task; received yesterday", "received yesterday; $task")) {
                val parsed = parse(text)
                if (parsed.receivedDate != "2026-11-16") errors.add("Missing receipt: $text")
                if (scheduling(original) != scheduling(parsed)) errors.add("Changed fields: $text expected=${scheduling(original)} actual=${scheduling(parsed)}")
                val receipt = parsed.highlightSpans.singleOrNull { it.type == QuickAddHighlightType.ReceivedDate }
                if (receipt != null && parsed.highlightSpans.filter { it != receipt }.any {
                    it.range.first <= receipt.range.last && receipt.range.first <= it.range.last
                }) errors.add("Overlapping spans: $text")
            }
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    @Test fun trailingTitleDatesDoNotOverrideTheReceiptDate() {
        for (separator in listOf(" ", ", ", "; ")) {
            val parsed = parse("received yesterday${separator}Review tomorrow")
            assertEquals(separator, "2026-11-16", parsed.receivedDate)
            assertEquals(separator, "2026-11-18", parsed.due)
        }
        assertNull(parse("Review received2026-11-12 tomorrow").receivedDate)
    }

    @Test fun futurePlansWithBroadSynonymsRemainScheduling() {
        for (text in listOf("Review got tomorrow off", "Review delivered tomorrow", "Review assigned in 2 weeks",
            "Review accepted next Friday", "Review picked up tomorrow")) {
            val parsed = parse(text)
            assertNull(text, parsed.receivedDate)
            assertNotNull(text, parsed.due)
        }
        assertEquals("2026-11-18", parse("Review received tomorrow").receivedDate)
        assertEquals("2026-11-18", parse("Review receipt date tomorrow").receivedDate)
        assertEquals("2026-11-16", parse("Review got on yesterday").receivedDate)
    }

    @Test fun entityNamesAreNotReceiptTriggers() {
        val project = parse("Review +Received tomorrow")
        assertEquals("Received", project.projectName)
        assertEquals("2026-11-18", project.due)
        assertNull(project.receivedDate)
        val person = parse("Review assign Got tomorrow")
        assertEquals(listOf("Got"), person.assigneeNames)
        assertNull(person.receivedDate)
        val tag = parse("Review #received tomorrow")
        assertEquals(listOf("received"), tag.tagNames)
        assertNull(tag.receivedDate)
        val quoted = parse("Review project \"Received Friday\" tomorrow")
        assertEquals("Received Friday", quoted.projectName)
        assertNull(quoted.receivedDate)
        val combined = parse("Review project Work received yesterday due tomorrow")
        assertEquals("Work", combined.projectName)
        assertEquals("2026-11-16", combined.receivedDate)
        assertEquals("2026-11-18", combined.due)
    }

    @Test fun negationAndConditionsAreNotConfirmedReceipt() {
        for (prefix in listOf("not", "never", "no", "not yet", "still not", "has not been", "hasn’t been", "hasn't", "haven’t", "wasn't",
            "if", "when", "once", "until", "after", "before", "without", "awaiting", "expecting", "expected to be", "will be", "remind me to")) {
            val text = "Review $prefix received yesterday"
            assertNull(text, parse(text).receivedDate)
        }
    }

    @Test fun invalidDatesAndClocksCannotBePartialReceiptDates() {
        for (phrase in listOf("12/11/202", "12/11/20277", "November 12 20277", "31/02/2026",
            "3.30 pm", "3:30pm", "Fridayish", "yesterdayish")) {
            assertNull(phrase, parse("Review received at $phrase").receivedDate)
        }
        val clock = parse("Review received at 3.30 pm tomorrow")
        assertNull(clock.receivedDate)
        assertEquals("2026-11-18", clock.due)
        assertEquals("3:30 PM", clock.time)
    }
}
