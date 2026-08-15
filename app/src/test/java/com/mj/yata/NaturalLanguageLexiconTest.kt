package com.mj.yata

import com.mj.yata.util.nl.NaturalLanguageLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class NaturalLanguageLexiconTest {
    @Test
    fun languagePackAliasesAreWellFormed() {
        assertTrue(NaturalLanguageLexicon.weekdayNames.keys.none { it.isBlank() })
        assertTrue(NaturalLanguageLexicon.monthNames.keys.none { it.isBlank() })
        assertTrue(NaturalLanguageLexicon.relativeDateWords.keys.none { it.isBlank() })
        assertTrue(NaturalLanguageLexicon.monthNames.values.all { it in 1..12 })
    }

    @Test
    fun latinScriptPackExposesRepresentativeAliases() {
        assertEquals(DayOfWeek.MONDAY, NaturalLanguageLexicon.weekdayNames["montag"])
        assertEquals(DayOfWeek.MONDAY, NaturalLanguageLexicon.weekdayNames["senin"])
        assertEquals(10, NaturalLanguageLexicon.monthNames["ottobre"])
        assertEquals(11, NaturalLanguageLexicon.monthNames["thang muoi mot"])
    }

    @Test
    fun relativeDateAliasesResolveAgainstReferenceDate() {
        val ref = LocalDate.of(2026, 7, 4)
        assertEquals(LocalDate.of(2026, 7, 5), NaturalLanguageLexicon.relativeDateWords.getValue("morgen")(ref))
        assertEquals(LocalDate.of(2026, 7, 3), NaturalLanguageLexicon.relativeDateWords.getValue("gisteren")(ref))
        assertEquals(LocalDate.of(2026, 7, 4), NaturalLanguageLexicon.relativeDateWords.getValue("hari ini")(ref))
    }

    @Test
    fun lexiconDoesNotExposeKnownBlankOrInvalidEntries() {
        assertFalse(NaturalLanguageLexicon.weekdayNames.containsKey(""))
        assertFalse(NaturalLanguageLexicon.monthNames.containsKey(""))
        assertFalse(NaturalLanguageLexicon.relativeDateWords.containsKey(""))
    }
}
