package com.mj.yata

import com.mj.yata.util.capitalizeTaskSentence
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTitleFormatterTest {

    @Test
    fun capitalizesFirstLetterOnly() {
        assertEquals("Send invoice to NASA", capitalizeTaskSentence("send invoice to NASA"))
    }

    @Test
    fun skipsLeadingPunctuationAndNumbers() {
        assertEquals("1. Send invoice", capitalizeTaskSentence("1. send invoice"))
        assertEquals("\"Send invoice\"", capitalizeTaskSentence("\"send invoice\""))
    }

    @Test
    fun leavesTitlesWithoutLettersAlone() {
        assertEquals("123 !!", capitalizeTaskSentence("123 !!"))
    }
}
