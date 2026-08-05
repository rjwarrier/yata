package com.mj.yata

import com.mj.yata.ui.screen.search.SmartFilter
import com.mj.yata.ui.screen.search.parseSearchQuery
import org.junit.Assert.*
import org.junit.Test

class SearchQueryParserTest {

    @Test
    fun noMatchLeavesQueryUntouched() {
        val result = parseSearchQuery("buy milk")
        assertTrue(result.filters.isEmpty())
        assertEquals("buy milk", result.residualText)
    }

    @Test
    fun parsesHighPriority() {
        val result = parseSearchQuery("high priority")
        assertEquals(listOf(SmartFilter.HIGH_PRIORITY), result.filters)
        assertEquals("", result.residualText)
    }

    @Test
    fun parsesOverdue() {
        val result = parseSearchQuery("overdue")
        assertEquals(listOf(SmartFilter.OVERDUE), result.filters)
        assertEquals("", result.residualText)
    }

    @Test
    fun parsesFlagged() {
        val result = parseSearchQuery("flagged")
        assertEquals(listOf(SmartFilter.FLAGGED), result.filters)
    }

    @Test
    fun parsesDueToday() {
        val result = parseSearchQuery("due today")
        assertEquals(listOf(SmartFilter.DUE_TODAY), result.filters)
    }

    @Test
    fun parsesAssignedToMe() {
        val result = parseSearchQuery("assigned to me")
        assertEquals(listOf(SmartFilter.ASSIGNED_TO_ME), result.filters)
    }

    @Test
    fun parsesNoDueDateAndAliases() {
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("no due date").filters)
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("no date").filters)
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("undated").filters)
    }

    @Test
    fun noDueDatePhraseClaimsItselfWholeNotJustNoDate() {
        // "no due date" must not also leave a stray "due" dangling in the residual text via a
        // partial match of the shorter "no date" alternative.
        val result = parseSearchQuery("no due date report")
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), result.filters)
        assertEquals("report", result.residualText)
    }

    @Test
    fun stripsMatchedPhraseButKeepsRemainingText() {
        val result = parseSearchQuery("high priority report")
        assertEquals(listOf(SmartFilter.HIGH_PRIORITY), result.filters)
        assertEquals("report", result.residualText)
    }

    @Test
    fun matchesMultiplePhrasesAtOnce() {
        val result = parseSearchQuery("high priority flagged budget")
        assertEquals(setOf(SmartFilter.HIGH_PRIORITY, SmartFilter.FLAGGED), result.filters.toSet())
        assertEquals("budget", result.residualText)
    }

    @Test
    fun parsesSpanishPhrases() {
        val result = parseSearchQuery("alta prioridad atrasadas informe")
        assertEquals(setOf(SmartFilter.HIGH_PRIORITY, SmartFilter.OVERDUE), result.filters.toSet())
        assertEquals("informe", result.residualText)
    }

    @Test
    fun parsesSpanishSearchAliases() {
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("sin fecha límite").filters)
        assertEquals(listOf(SmartFilter.ASSIGNED_TO_ME), parseSearchQuery("asignadas a mí").filters)
        assertEquals(listOf(SmartFilter.DUE_TODAY), parseSearchQuery("vencen hoy").filters)
        assertEquals(listOf(SmartFilter.FLAGGED), parseSearchQuery("marcadas").filters)
    }

    @Test
    fun parsesPortuguesePhrases() {
        val result = parseSearchQuery("alta prioridade atrasadas relatório")
        assertEquals(setOf(SmartFilter.HIGH_PRIORITY, SmartFilter.OVERDUE), result.filters.toSet())
        assertEquals("relatório", result.residualText)
    }

    @Test
    fun parsesPortugueseSearchAliases() {
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("sem data").filters)
        assertEquals(listOf(SmartFilter.ASSIGNED_TO_ME), parseSearchQuery("atribuídas a mim").filters)
        assertEquals(listOf(SmartFilter.DUE_TODAY), parseSearchQuery("vencem hoje").filters)
        assertEquals(listOf(SmartFilter.FLAGGED), parseSearchQuery("sinalizadas").filters)
    }

    @Test
    fun parsesFrenchPhrases() {
        val result = parseSearchQuery("haute priorité en retard rapport")
        assertEquals(setOf(SmartFilter.HIGH_PRIORITY, SmartFilter.OVERDUE), result.filters.toSet())
        assertEquals("rapport", result.residualText)
    }

    @Test
    fun parsesFrenchSearchAliases() {
        assertEquals(listOf(SmartFilter.NO_DUE_DATE), parseSearchQuery("sans date").filters)
        assertEquals(listOf(SmartFilter.ASSIGNED_TO_ME), parseSearchQuery("assignées à moi").filters)
        assertEquals(listOf(SmartFilter.DUE_TODAY), parseSearchQuery("aujourd'hui").filters)
        assertEquals(listOf(SmartFilter.FLAGGED), parseSearchQuery("marquées").filters)
    }

    @Test
    fun isCaseInsensitive() {
        val result = parseSearchQuery("HIGH PRIORITY")
        assertEquals(listOf(SmartFilter.HIGH_PRIORITY), result.filters)
    }

    @Test
    fun doesNotDuplicateFilterWhenPhraseRepeated() {
        val result = parseSearchQuery("overdue overdue")
        assertEquals(listOf(SmartFilter.OVERDUE), result.filters)
    }

    @Test
    fun blankQueryProducesNoFilters() {
        val result = parseSearchQuery("")
        assertTrue(result.filters.isEmpty())
        assertEquals("", result.residualText)
    }
}
