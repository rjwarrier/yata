package com.mj.yata

import com.mj.yata.domain.model.*
import com.mj.yata.util.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MarkdownImportReviewTest {
    private fun task(id: String, title: String = "Review report") = Task(
        id, title, "list", null, "", "2026-11-01", time = null, reminder = null,
        priority = "none", flag = false, done = false, assigneeIds = emptyList(), tagIds = emptyList(),
        recurrence = null, subtasks = emptyList(), notes = null
    )
    private val context = MarkdownImportContext(
        projects = listOf(Project("project", "Work", "accentA", "Folder")),
        lists = listOf(YataList("list", "Inbox", "accentA", "List")),
        tags = listOf(Tag("tag", "Red", "accentA")),
        people = listOf(Person("person", "Maya", "M", "accentA"))
    )
    private fun previews(text: String, context: MarkdownImportContext = this.context) =
        MarkdownTaskParser.parse(text, LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), true)
            .prepareSmartImport(context, null, null, true)

    @Test fun `excluding an ancestor promotes retained children without losing nesting or completion`() {
        val root = Subtask("root", "Root", false)
        val middle = Subtask("middle", "Middle", false, "root")
        val child = Subtask("child", "Child", true, "middle")
        val leaf = Subtask("leaf", "Leaf", false, "child")
        val source = task("task").copy(subtasks = listOf(root, middle, child, leaf))
        val result = source.withExcludedMarkdownSubtasks(setOf(1)).subtasks
        assertEquals(listOf("root", "child", "leaf"), result.map { it.id })
        assertEquals(listOf(null, "root", "child"), result.map { it.parentSubtaskId })
        assertTrue(result[1].done)
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
        assertNull(source.withExcludedMarkdownSubtasks(setOf(0, 1)).subtasks.first().parentSubtaskId)
        assertTrue(source.withExcludedMarkdownSubtasks(setOf(0, 1, 2, 3)).subtasks.isEmpty())
    }

    @Test fun `one reference mapping applies to every occurrence and manual destination wins`() {
        val base = previews("- [ ] One +zzzzzzzz #xxxxxxxx @yyyyyyyy\n- [ ] Two +ZZZZZZZZ #XXXXXXXX @YYYYYYYY")
        assertEquals(3, base.first().unmatched.size)
        val review = MarkdownImportReview(references = mapOf("+zzzzzzzz" to "project", "#xxxxxxxx" to "tag", "@yyyyyyyy" to "person"))
        val mapped = reviewMarkdownTasks(base, review, context)
        mapped.forEach {
            assertEquals("project", it.task.projectId)
            assertEquals(listOf("tag"), it.task.tagIds)
            assertEquals(listOf("person"), it.task.assigneeIds)
            assertTrue(it.unmatched.isEmpty())
        }
        val manual = base.map { it.copy(task = it.task.copy(projectId = null, listId = "list")) }
        assertEquals("list", reviewMarkdownTasks(manual, review, context, true).first().task.listId)
    }

    @Test fun `mapped project due date follows the regular fallback but never overwrites explicit dates or disabled scheduling`() {
        val dated = context.copy(projects = context.projects.map { it.copy(due = "2026-12-01") })
        val review = MarkdownImportReview(references = mapOf("+zzzzzzzz" to "project"))
        val base = previews("- [ ] Review +zzzzzzzz", dated)
        assertEquals("2026-12-01", reviewMarkdownTasks(base, review, dated).single().task.due)
        val explicit = previews("- [ ] Review tomorrow +zzzzzzzz", dated)
        assertEquals("2026-10-28", reviewMarkdownTasks(explicit, review, dated).single().task.due)
        assertNull(reviewMarkdownTasks(base, review, dated, useDates = false).single().task.due)
    }

    @Test fun `explicit mapping still wins after a newly created source name starts matching automatically`() {
        val refreshed = context.copy(tags = context.tags + Tag("created", "xxxxxxxx", "accentA"))
        val base = previews("- [ ] Review #xxxxxxxx", refreshed)
        assertEquals(listOf("created"), base.single().task.tagIds)
        val mapped = reviewMarkdownTasks(base, MarkdownImportReview(references = mapOf("#xxxxxxxx" to "tag")), refreshed)
        assertEquals(listOf("tag"), mapped.single().task.tagIds)
    }

    @Test fun `invalid or archived mappings stay unresolved and row edits override file mappings`() {
        val base = previews("- [ ] Review +zzzzzzzz")
        val review = MarkdownImportReview(references = mapOf("+zzzzzzzz" to "gone"))
        assertEquals(listOf("+zzzzzzzz"), reviewMarkdownTasks(base, review, context).single().unmatched)
        val archived = context.copy(projects = context.projects.map { it.copy(archived = true) })
        assertNull(reviewMarkdownTasks(base, review.copy(references = mapOf("+zzzzzzzz" to "project")), archived).single().task.projectId)
        val edited = base.single().task.copy(title = "Corrected", listId = "list", due = null)
        val result = reviewMarkdownTasks(base, review.withTaskEdit(0, base.single().task, edited), context).single()
        assertEquals(edited, result.task)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test fun `duplicate detection includes completed exact matches prefers destination and ignores trash and archive`() {
        val live = task("live")
        val sameDestination = task("same").copy(done = true)
        val other = live.copy(id = "other", listId = "elsewhere")
        val found = markdownDuplicateMatches(listOf(task("new", " REVIEW REPORT ")), listOf(other, sameDestination))
        assertEquals("same", found[0]!!.id)
        assertTrue(markdownDuplicateMatches(listOf(live), listOf(live.copy(deletedAt = 1), live.copy(id = "archived", archived = true))).isEmpty())
    }

    @Test fun `near duplicate matches retain individual editor similarity rules`() {
        val existing = task("existing", "Review the school report")
        val incoming = task("new", "Review the school repotr")
        assertEquals(findSimilarTask(incoming.title, listOf(existing)), markdownDuplicateMatches(listOf(incoming), listOf(existing))[0])
        assertTrue(markdownDuplicateMatches(listOf(incoming), listOf(existing.copy(done = true))).isEmpty())
    }

    @Test fun `a duplicate decision never silently transfers to another matching task`() {
        val review = MarkdownImportReview(duplicateActions = mapOf(0 to MarkdownDuplicateDecision(MarkdownDuplicateAction.UPDATE, "approved")))
        assertEquals(MarkdownDuplicateAction.UPDATE, review.duplicateAction(0, task("approved")))
        assertEquals(MarkdownDuplicateAction.SKIP, review.duplicateAction(0, task("different")))
        assertEquals(MarkdownDuplicateAction.SKIP, review.duplicateAction(0, null))
    }

    @Test fun `title edits allow later whole file mappings and subsequent edits retain earlier choices`() {
        val base = previews("- [ ] Review @yyyyyyyy #xxxxxxxx +zzzzzzzz")
        val original = base.single().task
        val first = MarkdownImportReview().withTaskEdit(0, original, original.copy(title = "Corrected"))
        assertTrue(reviewMarkdownTasks(base, first, context).single().unmatched.isNotEmpty())
        val mapped = first.copy(references = mapOf("@yyyyyyyy" to "person", "#xxxxxxxx" to "tag", "+zzzzzzzz" to "project"))
        val approved = reviewMarkdownTasks(base, mapped, context).single().task
        assertEquals("Corrected", approved.title)
        assertEquals(listOf("person"), approved.assigneeIds)
        assertEquals(listOf("tag"), approved.tagIds)
        assertEquals("project", approved.projectId)
        val second = mapped.withTaskEdit(0, approved, approved.copy(priority = "high"))
        val result = reviewMarkdownTasks(base, second, context).single().task
        assertEquals("Corrected", result.title)
        assertEquals("high", result.priority)
        assertEquals(listOf("person"), result.assigneeIds)
    }

    @Test fun `duplicate update keeps existing identity notes nested subtask IDs and completion across repeat imports`() {
        val oldRoot = Subtask("old-root", "Outline", true)
        val oldChild = Subtask("old-child", "Draft", true, "old-root")
        val before = task("original").copy(done = true, createdAt = 100, trackedMinutes = 55,
            notes = "Original notes", subtasks = listOf(oldRoot, oldChild), tagIds = listOf("old"))
        fun incoming() = task("new").copy(notes = "Imported notes", tagIds = listOf("new"),
            subtasks = listOf(Subtask("new-root", "outline", false), Subtask("new-child", "Draft", false, "new-root"),
                Subtask("new-extra", "Send", false, "new-root")))
        val once = mergeMarkdownTask(before, incoming())
        val twice = mergeMarkdownTask(once, incoming())
        assertEquals("original", once.id)
        assertEquals(100L, once.createdAt)
        assertEquals(55, once.trackedMinutes)
        assertTrue(once.done)
        assertEquals(listOf("old-root", "old-child", "new-extra"), once.subtasks.map { it.id })
        assertEquals("old-root", once.subtasks.last().parentSubtaskId)
        assertEquals("Original notes\n\nImported notes", once.notes)
        assertEquals(once, twice)
        // A legacy completed task's unknown completion date must stay unknown.
        assertNull(mergeMarkdownTask(before, incoming().copy(done = true, completedAt = 999)).completedAt)
    }

    @Test fun `undo preserves later title notes and scheduling edits and restores unchanged imported fields`() {
        val before = task("existing").copy(notes = "Original")
        val after = mergeMarkdownTask(before, task("new").copy(due = "2026-12-01", priority = "high", tagIds = listOf("tag")))
        val later = after.copy(title = "Edited later", notes = "Edited notes", due = "2027-01-01", trackedMinutes = 45)
        val restored = undoMarkdownUpdate(before, after, later)
        assertEquals("Edited later", restored.title)
        assertEquals("Edited notes", restored.notes)
        assertEquals("2027-01-01", restored.due)
        assertEquals(45, restored.trackedMinutes)
        assertEquals(before.priority, restored.priority)
        assertEquals(before.tagIds, restored.tagIds)
        assertEquals(before, undoMarkdownUpdate(before, after, after))
    }
}
