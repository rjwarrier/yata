package com.mj.yata

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.receivedDateEnabledFor
import com.mj.yata.data.mapper.toDomain
import com.mj.yata.data.mapper.toEntity
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.withParsedQuickAdd
import org.junit.Assert.*
import org.junit.Test

class ProjectReceivedDateTest {
    private val disabled = Project("off", "Off", "accentA", "work", receivedDateEnabled = false)
    private val projects = listOf(disabled, Project("on", "On", "accentA", "work"))
    private fun task(projectId: String? = null) = Task(
        id = "task", title = "Review", listId = null, projectId = projectId, section = "",
        due = null, time = null, reminder = null, priority = "none", flag = false, done = false,
        assigneeIds = emptyList(), tagIds = emptyList(), recurrence = null, subtasks = emptyList(), notes = null
    )
    private fun apply(base: Task, parsed: com.mj.yata.util.ParsedQuickAdd) = base.withParsedQuickAdd(
        parsed, lists = emptyList(), projects = projects, people = emptyList(), tags = emptyList(),
        projectsEnabled = true, tagsEnabled = true, peopleEnabled = true
    )
    @Test fun defaultsAndStorageKeepTheProjectChoice() {
        assertTrue(projects.receivedDateEnabledFor(null))
        assertTrue(projects.receivedDateEnabledFor("on"))
        assertFalse(projects.receivedDateEnabledFor("off"))
        assertEquals(disabled, disabled.toEntity().toDomain())
    }
    @Test fun parsedReceiptUsesTheResolvedDestinationAndPreservesHistory() {
        val parsed = NaturalLanguageParser.parse("Review received yesterday").copy(receivedDate = "2026-11-16")
        assertNull(apply(task("off"), parsed).receivedDate)
        assertEquals("2026-11-16", apply(task(), parsed).receivedDate)
        assertNull(apply(task(), parsed.copy(projectName = "Off")).receivedDate)
        val historical = task("off").copy(receivedDate = "2026-10-10")
        assertEquals("2026-10-10", apply(historical, parsed).receivedDate)
        assertEquals("2026-11-16", apply(historical, parsed.copy(projectName = "On")).receivedDate)
    }
}
