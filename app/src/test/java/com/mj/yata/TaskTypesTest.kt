package com.mj.yata

import com.mj.yata.domain.model.MAX_TASK_TYPES
import com.mj.yata.domain.model.MAX_TASK_TYPE_LENGTH
import com.mj.yata.domain.model.normalizeTaskTypes
import com.mj.yata.domain.model.taskTypeSeparator
import com.mj.yata.domain.model.taskTypeSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTypesTest {
    private val types = listOf("ITR", "Tax Audit", "Stat Audit", "Project Report", "Partnership Deed", "Other")

    @Test fun separatorComesFromTheHintsPunctuation() {
        assertEquals(" : ", taskTypeSeparator("Work Type : Client Name"))
        assertEquals(": ", taskTypeSeparator("Type: Client"))
        assertEquals(" | ", taskTypeSeparator("Type | Client"))
        assertEquals(" - ", taskTypeSeparator("Type - Client"))
        assertEquals(" – ", taskTypeSeparator("Type – Client"))
    }

    @Test fun separatorFallsBackToASpace() {
        assertEquals(" ", taskTypeSeparator(null))
        assertEquals(" ", taskTypeSeparator("Client name"))
        assertEquals(" ", taskTypeSeparator("Follow-up for client"))
    }

    @Test fun blankTitleOffersEveryType() {
        assertEquals(types, taskTypeSuggestions(types, "", " : "))
        assertEquals(types, taskTypeSuggestions(types, "   ", " : "))
    }

    @Test fun typingNarrowsCaseInsensitivelyByWordStart() {
        assertEquals(listOf("Tax Audit"), taskTypeSuggestions(types, "ta", " : "))
        assertEquals(listOf("Tax Audit", "Stat Audit"), taskTypeSuggestions(types, "audit", " : "))
        assertEquals(listOf("Tax Audit"), taskTypeSuggestions(types, "tax au", " : "))
        assertEquals(listOf("Project Report", "Partnership Deed"), taskTypeSuggestions(types, "p", " : "))
        assertEquals(listOf("ITR"), taskTypeSuggestions(types, "itr", " : "))
    }

    @Test fun nothingIsOfferedOnceATypeIsChosenOrTheTitleIsFreeForm() {
        assertTrue(taskTypeSuggestions(types, "Tax Audit : ", " : ").isEmpty())
        assertTrue(taskTypeSuggestions(types, "tax audit : ABC Traders", " : ").isEmpty())
        assertTrue(taskTypeSuggestions(types, "GST: ABC", " : ").isEmpty())
        assertTrue(taskTypeSuggestions(types, "Call the bank", " : ").isEmpty())
        assertTrue(taskTypeSuggestions(types, "ITR ABC Traders", " ").isEmpty())
        assertTrue(taskTypeSuggestions(emptyList(), "", " : ").isEmpty())
    }

    @Test fun normalizationTrimsDedupesAndCaps() {
        assertEquals(listOf("ITR", "Tax Audit"), normalizeTaskTypes(listOf(" ITR ", "", "Tax Audit", "itr", "  ")))
        assertEquals(MAX_TASK_TYPES, normalizeTaskTypes((1..50).map { "Type $it" }).size)
        assertEquals(MAX_TASK_TYPE_LENGTH, normalizeTaskTypes(listOf("x".repeat(100))).single().length)
    }
}
