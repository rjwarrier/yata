package com.mj.yata.ui.sheets

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.util.export.SharedEntityRef
import com.mj.yata.util.export.SharedTaskDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [resolveAgainstLocalData], the bridge between a decoded share
 * ([com.mj.yata.util.export.SharedTaskDraft]) and what [SharedTaskImportScreen] shows before a
 * task is written — matching by name against local data, and reporting anything unmatched
 * instead of creating it. See docs/app-links-team-sharing-design.md §5.
 */
class SharedTaskResolverTest {

    @Test
    fun matchingListProjectAndTags_resolveToLocalIds_withNothingPending() {
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val project = Project(id = "proj1", name = "Launch", color = "accentB", icon = "layers")
        val tag = Tag(id = "tag1", name = "Urgent", color = "error")
        val draft = SharedTaskDraft(
            title = "Ship it",
            list = SharedEntityRef(name = "Work", color = "accentA"),
            project = SharedEntityRef(name = "Launch", color = "accentB"),
            tags = listOf(SharedEntityRef(name = "Urgent", color = "error"))
        )

        val resolved = draft.resolveAgainstLocalData(listOf(list), listOf(project), listOf(tag))

        assertEquals(list.id, resolved.draft.listId)
        assertEquals(project.id, resolved.draft.projectId)
        assertEquals(listOf(tag.id), resolved.draft.tagIds)
        assertTrue(resolved.pending.isEmpty)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val list = YataList(id = "list1", name = "work", color = "accentA", icon = "folder")
        val draft = SharedTaskDraft(title = "Ship it", list = SharedEntityRef(name = "Work", color = "accentA"))

        val resolved = draft.resolveAgainstLocalData(listOf(list), emptyList(), emptyList())

        assertEquals(list.id, resolved.draft.listId)
        assertTrue(resolved.pending.isEmpty)
    }

    @Test
    fun unmatchedNames_reportAsPending_ratherThanBeingSilentlyDropped() {
        val draft = SharedTaskDraft(
            title = "Ship it",
            list = SharedEntityRef(name = "Work", color = "accentA"),
            project = SharedEntityRef(name = "Launch", color = "accentB"),
            tags = listOf(SharedEntityRef(name = "Urgent", color = "error"), SharedEntityRef(name = "Blocked", color = "accentD"))
        )

        val resolved = draft.resolveAgainstLocalData(emptyList(), emptyList(), emptyList())

        assertNull(resolved.draft.listId)
        assertNull(resolved.draft.projectId)
        assertTrue(resolved.draft.tagIds.isEmpty())
        assertEquals("Work", resolved.pending.listName)
        assertEquals("Launch", resolved.pending.projectName)
        assertEquals(listOf("Urgent", "Blocked"), resolved.pending.tagNames)
        assertTrue(!resolved.pending.isEmpty)
    }

    @Test
    fun partialMatch_onlyReportsWhatWasMissing() {
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val tag = Tag(id = "tag1", name = "Urgent", color = "error")
        val draft = SharedTaskDraft(
            title = "Ship it",
            list = SharedEntityRef(name = "Work", color = "accentA"),
            project = SharedEntityRef(name = "Launch", color = "accentB"),
            tags = listOf(SharedEntityRef(name = "Urgent", color = "error"), SharedEntityRef(name = "Blocked", color = "accentD"))
        )

        val resolved = draft.resolveAgainstLocalData(listOf(list), emptyList(), listOf(tag))

        assertEquals(list.id, resolved.draft.listId)
        assertEquals(listOf(tag.id), resolved.draft.tagIds)
        assertNull(resolved.pending.listName)
        assertEquals("Launch", resolved.pending.projectName)
        assertEquals(listOf("Blocked"), resolved.pending.tagNames)
    }

    @Test
    fun assigneeIdsAreAlwaysEmpty_soTheSheetsOwnAssignToMeDefaultApplies() {
        // A shared task never resolves or creates a Person on this device — see
        // docs/app-links-team-sharing-design.md §4. Leaving assigneeIds empty rather than, say,
        // pointing at nothing is what lets NewTaskSheet's normal "assign to me" default kick in
        // exactly as it would for a task created by hand.
        val draft = SharedTaskDraft(title = "Ship it", assigneeNames = listOf("Ravi"))

        val resolved = draft.resolveAgainstLocalData(emptyList(), emptyList(), emptyList())

        assertTrue(resolved.draft.assigneeIds.isEmpty())
    }

    @Test
    fun scheduleFieldsPassThroughUnchanged() {
        val draft = SharedTaskDraft(
            title = "Ship it",
            due = "2026-08-20",
            startDate = "2026-08-18",
            time = "2:00 PM",
            estimateMinutes = 45,
            notes = "call first",
            subtaskTitles = listOf("Step one", "Step two")
        )

        val resolved = draft.resolveAgainstLocalData(emptyList(), emptyList(), emptyList())

        assertEquals("2026-08-20", resolved.draft.due)
        assertEquals("2026-08-18", resolved.draft.startDate)
        assertEquals("2:00 PM", resolved.draft.time)
        assertEquals(45, resolved.draft.estimateMinutes)
        assertEquals("call first", resolved.draft.notes)
        assertEquals(listOf("Step one", "Step two"), resolved.draft.subtasks.map { it.title })
        assertNull(resolved.draft.reminder)
    }
}
