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
}
