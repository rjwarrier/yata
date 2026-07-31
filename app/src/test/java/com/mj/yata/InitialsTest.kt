package com.mj.yata

import com.mj.yata.ui.widgets.tagMonogramFor
import com.mj.yata.util.initialsFor
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun takesFirstLetterOfTheFirstTwoWords() {
        assertEquals("MJ", initialsFor("Mary Jane"))
        assertEquals("MJ", initialsFor("Mary Jane Watson")) // third word ignored
        assertEquals("M", initialsFor("Mary"))
    }

    @Test
    fun splitsOnMoreThanJustSpaces() {
        // The inline copies this replaced split on " " alone, so each of these collapsed to one
        // letter — the commonest way a two-part name is written without a space.
        assertEquals("MJ", initialsFor("mary-jane"))
        assertEquals("MJ", initialsFor("mary_jane"))
        assertEquals("MJ", initialsFor("mary.jane"))
    }

    @Test
    fun alwaysUppercase() {
        assertEquals("AB", initialsFor("alice bob"))
    }

    @Test
    fun toleratesUntidyWhitespace() {
        assertEquals("AB", initialsFor("  alice   bob  "))
    }

    @Test
    fun handlesCharactersOutsideTheBasicPlane() {
        // Taking the first Char rather than the first code point sliced these in half and left
        // the renderer with a lone surrogate to draw as tofu.
        // The emoji is a surrogate pair — two Chars but one code point — so taking the first Char
        // would emit a lone high surrogate for the renderer to draw as tofu.
        val result = initialsFor("🔥 urgent")
        assertEquals("🔥U", result)
        assertEquals(2, result.codePointCount(0, result.length))
    }

    @Test
    fun nonLatinScriptsSurvive() {
        assertEquals("ЯД", initialsFor("Ярослав Дмитрович"))
        assertEquals("अब", initialsFor("अजय बच्चन"))
    }

    @Test
    fun blankNameFallsBackRatherThanRenderingEmpty() {
        // An empty avatar reads as a rendering failure; a placeholder reads as a missing name.
        assertEquals("?", initialsFor(""))
        assertEquals("?", initialsFor("   "))
    }

    @Test
    fun tagMonogramSharesTheRuleButItsOwnFallback() {
        assertEquals("W", tagMonogramFor("work"))
        assertEquals("SP", tagMonogramFor("side project"))
        assertEquals("#", tagMonogramFor("  "))
    }
}
