package com.mj.yata

import com.mj.yata.domain.model.*
import com.mj.yata.util.*
import com.mj.yata.data.sync.SnapshotMerger
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MarkdownReviewToolsTest {
    private val context = MarkdownImportContext(lists = listOf(YataList("list", "Inbox", "accentA", "List")))
    private fun previews(text: String) = MarkdownTaskParser.parse(text, LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), true)
        .prepareSmartImport(context, null, "list", true)

    @Test fun `mapping memory round trips Unicode punctuation and normalized names`() {
        val mappings = mapOf("#School: office" to "id:α/1", "@മാല" to "person-2", "+हिंदी" to "project")
        val restored = MarkdownReferenceCodec.decode(MarkdownReferenceCodec.encode(mappings))
        assertEquals(mappings.mapKeys { it.key.lowercase() }, restored)
        assertTrue(MarkdownReferenceCodec.decode(setOf("bad", "@@@:@@@", ":")).isEmpty())
    }

    @Test fun `memory is bounded and keeps the most recently assigned keys`() {
        val mappings = (0..510).associate { "#tag$it" to "id$it" }
        val restored = MarkdownReferenceCodec.decode(MarkdownReferenceCodec.encode(mappings))
        assertEquals(500, restored.size)
        assertFalse("#tag0" in restored)
        assertEquals("id510", restored["#tag510"])
    }

    @Test fun `saved IDs survive renames but not deletion archive or another entity type`() {
        val c = context.copy(people = listOf(Person("person", "Renamed", "R", "accentA")))
        assertTrue(validMarkdownReference("@original", "person", c))
        assertFalse(validMarkdownReference("#original", "person", c))
        assertFalse(validMarkdownReference("@original", "missing", c))
        assertFalse(validMarkdownReference("@original", "person", c.copy(people = c.people.map { it.copy(archived = true) })))
    }

    @Test fun `saved mapping settings stay device local in sync`() {
        assertTrue(SnapshotMerger.isDeviceLocalSetting("markdown_reference_mappings"))
        assertTrue(SnapshotMerger.isDeviceLocalSetting("markdown_remember_mappings"))
    }

    @Test fun `bulk duplicate actions bind current targets and permit individual overrides`() {
        val rows = previews("- One\n- Two\n- Three")
        val matches = mapOf(0 to rows[0].task.copy(id = "a"), 2 to rows[2].task.copy(id = "b"))
        val bulk = MarkdownImportReview().withBulkDuplicates(matches, MarkdownDuplicateAction.COPY)
        assertEquals(MarkdownDuplicateAction.COPY, bulk.duplicateAction(0, matches[0]))
        val overridden = bulk.copy(duplicateActions = bulk.duplicateActions + (2 to MarkdownDuplicateDecision(MarkdownDuplicateAction.SKIP, "b")))
        assertEquals(MarkdownDuplicateAction.COPY, overridden.duplicateAction(0, matches[0]))
        assertEquals(MarkdownDuplicateAction.SKIP, overridden.duplicateAction(2, matches[2]))
        assertEquals(MarkdownDuplicateAction.SKIP, bulk.duplicateAction(0, matches[0]!!.copy(id = "different")))
    }

    @Test fun `filters preserve source indices without changing selection or import totals`() {
        val rows = previews("- One\n- Two #xxxxxxxx\n- Three")
        val selected = setOf(0, 2)
        val matches = mapOf(2 to rows[2].task)
        assertEquals(listOf(0, 1, 2), markdownVisibleIndices(rows, selected, matches, MarkdownPreviewFilter.ALL))
        assertEquals(listOf(0, 2), markdownVisibleIndices(rows, selected, matches, MarkdownPreviewFilter.SELECTED))
        assertEquals(listOf(2), markdownVisibleIndices(rows, selected, matches, MarkdownPreviewFilter.DUPLICATES))
        assertEquals(listOf(1), markdownVisibleIndices(rows, selected, matches, MarkdownPreviewFilter.UNRESOLVED))
        assertEquals(setOf(0, 2), selected)
        assertEquals(MarkdownImportSummary(1, 0, 2, 0), markdownImportSummary(rows, selected, matches, MarkdownImportReview()))
    }

    @Test fun `summary counts new updates skipped and only references that will be imported`() {
        val rows = previews("- One #xxxxxxxx\n- Two\n- Three @yyyyyyyy\n- Four")
        val existing = rows[1].task.copy(id = "existing", title = "Old title")
        val matches = mapOf(1 to existing, 2 to rows[2].task.copy(id = "skip"))
        val review = MarkdownImportReview(duplicateActions = mapOf(1 to MarkdownDuplicateDecision(MarkdownDuplicateAction.UPDATE, existing.id)))
        assertEquals(MarkdownImportSummary(1, 1, 2, 1), markdownImportSummary(rows, setOf(0, 1, 2), matches, review))
    }

    @Test fun `unchanged updates count as skipped and repeated target updates count once`() {
        val rows = previews("- One\n- Two")
        val existing = rows[0].task.copy(id = "existing")
        val matches = mapOf(0 to existing, 1 to existing)
        val review = MarkdownImportReview().withBulkDuplicates(matches, MarkdownDuplicateAction.UPDATE)
        assertEquals(MarkdownImportSummary(0, 0, 1, 0), markdownImportSummary(rows.take(1), setOf(0), matches, review))
        assertEquals(1, markdownImportSummary(rows, setOf(0, 1), matches, review).updatedTasks)
    }

    @Test fun `change review reflects additive merge rules and never advertises deleting retained work`() {
        val row = previews("- Review").single().task
        val before = row.copy(done = true, notes = "Keep this", tagIds = listOf("old"), subtasks = listOf(Subtask("s", "Keep subtask", true)))
        val (after, fields) = markdownChangedFields(before, row.copy(due = "2026-12-01", tagIds = listOf("new"), notes = null))
        assertEquals(setOf(MarkdownUpdateField.DUE, MarkdownUpdateField.TAGS), fields)
        assertTrue(after.done)
        assertEquals(before.subtasks, after.subtasks)
        assertEquals(before.notes, after.notes)
        assertEquals(listOf("old", "new"), after.tagIds)
    }

    @Test fun `missing saved mapping appears unresolved even if normal matching finds a different item`() {
        val c = context.copy(tags = listOf(Tag("other", "School", "accentA")))
        val base = MarkdownTaskParser.parse("- Review #School", LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), true)
            .prepareSmartImport(c, null, "list", true)
        assertTrue(base.single().unmatched.isEmpty())
        val reviewed = reviewMarkdownTasks(base, MarkdownImportReview(references = mapOf("#school" to "deleted")), c)
        assertEquals(listOf("#school"), reviewed.single().unmatched)
        assertEquals(listOf(0), markdownVisibleIndices(reviewed, setOf(0), emptyMap(), MarkdownPreviewFilter.UNRESOLVED))
    }

    @Test fun `clearing a reference explicitly omits it for this file without dropping other mentions`() {
        val c = context.copy(tags = listOf(Tag("school", "School", "accentA"), Tag("home", "Home", "accentB")))
        val base = MarkdownTaskParser.parse("- Review #School #Home", LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), true)
            .prepareSmartImport(c, null, "list", true)
        val result = reviewMarkdownTasks(base, MarkdownImportReview(ignoredSavedReferences = setOf("#school")), c).single()
        assertEquals(listOf("home"), result.task.tagIds)
        assertTrue(result.unmatched.isEmpty())
    }
}
