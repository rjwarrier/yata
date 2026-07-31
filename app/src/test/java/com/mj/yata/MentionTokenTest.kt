package com.mj.yata

import com.mj.yata.ui.widgets.TRIGGER_LIST
import com.mj.yata.ui.widgets.TRIGGER_PERSON
import com.mj.yata.ui.widgets.TRIGGER_PROJECT
import com.mj.yata.ui.widgets.TRIGGER_TAG
import com.mj.yata.ui.widgets.detectMentionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionTokenTest {

    /** Detects at the end of the string, which is where the cursor sits while typing. */
    private fun detect(text: String) = detectMentionToken(text, text.length)

    @Test
    fun detectsAllFourTriggers() {
        assertEquals(TRIGGER_TAG, detect("buy milk #urg")?.trigger)
        assertEquals(TRIGGER_PERSON, detect("buy milk @sa")?.trigger)
        assertEquals(TRIGGER_PROJECT, detect("buy milk +kitc")?.trigger)
        assertEquals(TRIGGER_LIST, detect("buy milk =groc")?.trigger)
    }

    @Test
    fun capturesTheQueryAfterTheTrigger() {
        val token = detect("ship it +Q4launch")
        assertEquals("Q4launch", token?.query)
        assertEquals(TRIGGER_PROJECT, token?.trigger)
        assertEquals(8, token?.startIndex) // the '+' itself, so consuming removes it too
    }

    @Test
    fun triggerMustStartAWord() {
        // Otherwise "2+2" opens a project picker, and an email address opens a person one.
        assertNull(detect("what is 2+2"))
        assertNull(detect("mail bob@example"))
        assertNull(detect("total=42"))
    }

    @Test
    fun triggerAtTheVeryStartCounts() {
        assertEquals(TRIGGER_PROJECT, detect("+kitchen")?.trigger)
        assertEquals(0, detect("+kitchen")?.startIndex)
    }

    @Test
    fun aSpaceEndsTheToken() {
        // The query is a single word: once the user types a space they have moved on, and the
        // panel should close rather than keep matching against a growing sentence.
        assertNull(detect("buy milk +kitchen refit"))
    }

    @Test
    fun emptyQueryIsStillAToken() {
        // Typing the trigger alone opens the panel listing everything, which is the point.
        assertEquals("", detect("buy milk +")?.query)
    }

    @Test
    fun plainTextIsNotAToken() {
        assertNull(detect("buy milk tomorrow"))
        assertNull(detect(""))
    }

    @Test
    fun theNewTriggersDoNotCollideWithDateOrPrioritySyntax() {
        // The two characters were chosen to stay clear of the natural-language parser. A date and
        // a priority must not open an entity picker.
        assertNull(detect("pay taxes 7/20"))
        assertNull(detect("fix bug !1"))
        assertNull(detect("dentist 20.07.2026"))
    }

    @Test
    fun detectsAtACursorInsideTheText() {
        // Cursor placed mid-string, not at the end — the user editing something they typed earlier.
        val text = "buy +milk tomorrow"
        val token = detectMentionToken(text, 9) // just after "+milk"
        assertEquals(TRIGGER_PROJECT, token?.trigger)
        assertEquals("milk", token?.query)
    }
}
