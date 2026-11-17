package com.mj.yata

import com.mj.yata.util.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LiteralTitleEscapeTest {
    private val ref = LocalDate.of(2026, 11, 17)
    private fun parse(text: String) = NaturalLanguageParser.parse(text, ref)

    @Test fun literalPositionsCaseAndPunctuationRemainStable() {
        for (literal in listOf("AM", "PM", "a.m.", "p.m.", "AM/PM", "3:30PM", "_PM_", "-AM-")) {
            for ((raw, expected) in listOf(
                "\\$literal Review" to "$literal Review",
                "Review \\$literal notes" to "Review $literal notes",
                "Review \\$literal" to "Review $literal",
                "2day Review \\$literal at 4pm" to "Review $literal"
            )) {
                val parsed = parse(raw)
                assertEquals(raw, expected, parsed.title)
                if (!raw.startsWith("2day")) assertNull(raw, parsed.time)
                else {
                    assertEquals(raw, "4:00 PM", parsed.time)
                    assertEquals(raw, "2026-11-17", parsed.due)
                }
            }
        }
    }

    @Test fun escapedMetadataAndConnectorsSurviveAllCleanupStages() {
        for (literal in listOf("AM/PM", "#AM", "@PM", "+PM", "=AM", "rcvd", "tomorrow", "for", "to", "by")) {
            val parsed = parse("2day \\$literal at 4pm Review")
            assertEquals(literal, "$literal Review", parsed.title)
            assertEquals(literal, "2026-11-17", parsed.due)
            assertEquals(literal, "4:00 PM", parsed.time)
            assertTrue(literal, parsed.tagNames.isEmpty())
            assertTrue(literal, parsed.assigneeNames.isEmpty())
            assertNull(literal, parsed.projectName)
            assertNull(literal, parsed.listName)
        }
        val doubleEscape = parse("Review \\\\PM")
        assertEquals("Review \\PM", doubleEscape.title)
        assertNull(doubleEscape.time)
    }

    @Test fun escapedMeridiemWordsRemainLiteralWithoutQuotes() {
        for (literal in listOf("AM", "PM", "am", "pm", "AM/PM", "P.M.", "a.m.", "_AM/PM_")) {
            val parsed = parse("Review \\$literal")
            assertEquals(literal, "Review $literal", parsed.title)
            assertNull(literal, parsed.time)
            assertTrue(literal, parsed.highlightRanges.isEmpty())
        }
        assertEquals("AM", parse("\\AM").title)
        assertEquals("Review AM / PM", parse("Review \\AM / \\PM").title)
        assertNull(parse("Review \\AM / \\PM").time)
    }

    @Test fun escapedWholeClockTokenCannotLeaveAnotherTimeFragment() {
        for (literal in listOf("3:30pm", "3.30PM", "15:30", "3PM")) {
            val parsed = parse("Review \\$literal")
            assertEquals(literal, "Review $literal", parsed.title)
            assertNull(literal, parsed.time)
        }
    }

    @Test fun realSchedulingAndNormalizationStillWorkAlongsideLiterals() {
        for (day in listOf("tomorrow", "2morrow")) {
            val parsed = parse("Call \\PM $day at 3pm")
            assertEquals(day, "Call PM", parsed.title)
            assertEquals(day, "2026-11-18", parsed.due)
            assertEquals(day, "3:00 PM", parsed.time)
        }
        val parsed = parse("2day review \\AM/PM received yesterday")
        assertEquals("review AM/PM", parsed.title)
        assertEquals("2026-11-17", parsed.due)
        assertEquals("2026-11-16", parsed.receivedDate)
        assertNull(parsed.time)
    }

    @Test fun escapedConnectorsAndEdgePunctuationAreProtectedDuringCleanup() {
        val parsed = parse("tomorrow \\for 5pm Review")
        assertEquals("for Review", parsed.title)
        assertEquals("2026-11-18", parsed.due)
        assertEquals("5:00 PM", parsed.time)
        assertEquals("Review P.M.", parse("Review \\P.M. tomorrow").title)
        assertEquals(".PM.", parse("\\.PM.").title)
    }

    @Test fun recognizedTimesAreStillRemovedWithoutBlanketMeridiemDeletion() {
        for (phrase in listOf("3pm", "at 3 PM", "at 3 p.m.", "three pm", "PM")) {
            val parsed = parse("Review $phrase")
            assertEquals(phrase, "Review", parsed.title)
            assertNotNull(phrase, parsed.time)
        }
        val parsed = parse("Review \\PM at 3pm")
        assertEquals("Review PM", parsed.title)
        assertEquals("3:00 PM", parsed.time)
    }
}
