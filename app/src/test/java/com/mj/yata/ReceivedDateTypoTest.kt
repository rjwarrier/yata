package com.mj.yata

import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.QuickAddHighlightType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceivedDateTypoTest {
    private val ref = LocalDate.of(2026, 11, 17)
    private fun parse(text: String) = NaturalLanguageParser.parse(text, ref, dayFirst = true)

    @Test fun curatedReceiptSpellingsWorkWithTheSameContextGuards() {
        val aliases = listOf("recievd", "receivd", "receved", "recved", "recevied", "receieved",
            "recieived", "receivedd", "recievedd", "rcvdd", "rcvd.", "rec’d", "recv’d", "rcv’d")
        for (alias in aliases) {
            val text = "Review $alias on yesterday due tomorrow"
            val parsed = parse(text)
            assertEquals(alias, "2026-11-16", parsed.receivedDate)
            assertEquals(alias, "2026-11-18", parsed.due)
            assertEquals(alias, "Review due", parsed.title)
            val span = parsed.highlightSpans.single { it.type == QuickAddHighlightType.ReceivedDate }
            assertEquals(alias, "$alias on yesterday", text.substring(span.range))
            assertNull(alias, parse("Review not $alias yesterday").receivedDate)
            assertNull(alias, parse("Review #${alias.trimEnd('.')} tomorrow").receivedDate)
            assertEquals(alias, "2026-11-18", parse("Review $alias tomorrow").receivedDate)
        }
    }

    @Test fun compactDateLabelsRequireActualReceiptContext() {
        val labels = listOf("rcvd dt", "rcvd dt.", "recvd dte", "recievd dt", "dt received", "dt. rcvd",
            "dt of receipt", "date of recievd", "rcpt dt", "recipt date", "recepit dt", "receit date",
            "DOR:", "D.O.R.:", "DOR=", "rcvd dtd", "rcvd on dt.")
        for (label in labels) {
            val parsed = parse("Review $label 12 Nov 2026")
            assertEquals(label, "2026-11-12", parsed.receivedDate)
            assertEquals(label, "Review", parsed.title)
            assertNull(label, parsed.due)
        }
        for (text in listOf("Review dor tomorrow", "Review dt tomorrow", "Review rec tomorrow",
            "Review revised tomorrow", "Review reserved tomorrow", "Review reviewed tomorrow", "Review receiver tomorrow",
            "Review rcvd prev biz days", "Review rcvd last working days",
            "Review rcpt tomorrow", "Review recipe tomorrow", "Review receipt datebook tomorrow")) {
            assertNull(text, parse(text).receivedDate)
        }
    }

    @Test fun historicalDateShorthandIsScopedToReceiptPhrases() {
        val cases = mapOf("yest" to "2026-11-16", "ystrdy" to "2026-11-16", "ystrday" to "2026-11-16",
            "ystdy" to "2026-11-16", "y'day" to "2026-11-16", "y’day" to "2026-11-16", "ydy" to "2026-11-16",
            "tdy" to "2026-11-17", "tday" to "2026-11-17", "2dy" to "2026-11-17",
            "lst Fri" to "2026-11-13", "prv Mon" to "2026-11-16", "prev. Fri" to "2026-11-13",
            "Fri lst wk" to "2026-11-13", "lst wk Fri" to "2026-11-13", "prev wk on Fri" to "2026-11-13",
            "3 dys ago" to "2026-11-14", "2 wks back" to "2026-11-03", "3 mths back" to "2026-08-17",
            "2 mnths ago" to "2026-09-17", "1 qtr ago" to "2026-08-17", "2 yrs back" to "2024-11-17",
            "2 biz days ago" to "2026-11-13", "3 bus dys ago" to "2026-11-12", "2 bdays ago" to "2026-11-13",
            "2 bd ago" to "2026-11-13", "2 workdays ago" to "2026-11-13", "2 wrk days ago" to "2026-11-13",
            "2 buisness days ago" to "2026-11-13", "prev biz day" to "2026-11-16", "lst mth" to "2026-10-17",
            "lst qtr" to "2026-08-17", "day b4 yesterday" to "2026-11-15")
        for ((phrase, expected) in cases) {
            val parsed = parse("Review rcvd $phrase due tomorrow")
            assertEquals(phrase, expected, parsed.receivedDate)
            assertEquals(phrase, "2026-11-18", parsed.due)
            assertEquals(phrase, "Review due", parsed.title)
        }
    }

    @Test fun aliasesPreserveMixedSchedulingAndEntities() {
        val parsed = parse("Review every month until Dec 20; recievd dt ystrdy; starts Monday at 5pm #urgent +Work")
        assertEquals("2026-11-16", parsed.receivedDate)
        assertEquals("2026-11-23", parsed.startDate)
        assertEquals("5:00 PM", parsed.time)
        assertNotNull(parsed.recurrence)
        assertEquals("Work", parsed.projectName)
        assertEquals(listOf("urgent"), parsed.tagNames)
    }
}
