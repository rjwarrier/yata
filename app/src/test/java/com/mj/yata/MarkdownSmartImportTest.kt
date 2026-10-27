package com.mj.yata

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.util.MarkdownImportContext
import com.mj.yata.util.MarkdownTaskParser
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.prepareSmartImport
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MarkdownSmartImportTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val now = LocalTime.of(9, 0)
    private val context = MarkdownImportContext(
        projects = listOf(Project("work", "Work", "accentA", "Folder"), Project("school", "School", "accentB", "Folder")),
        lists = listOf(YataList("errands", "Errands", "accentA", "List")),
        people = listOf(Person("maya", "Maya", "M", "accentA")),
        tags = listOf(Tag("home", "home", "accentA"), Tag("school-tag", "school", "accentB"))
    )

    private fun parse(text: String, dayFirst: Boolean = true) = MarkdownTaskParser.parse(text.trimIndent(), today, now, dayFirst)

    @Test fun `inline tasks use exactly the individual smart add fields and entity resolver`() {
        val input = "Review draft tomorrow 3pm !1 #home @Maya +Work"
        val expected = NaturalLanguageParser.parse(input, today, now, true)
        val preview = parse("- [ ] $input").prepareSmartImport(context, null, null, true).single()
        val task = preview.task
        assertEquals(expected.title, task.title)
        assertEquals(expected.due, task.due)
        assertEquals(expected.time, task.time)
        assertEquals(expected.priority, task.priority)
        assertEquals("2026-10-28", task.due)
        assertEquals("work", task.projectId)
        assertEquals(listOf("home"), task.tagIds)
        assertEquals(listOf("maya"), task.assigneeIds)
        assertTrue(preview.unmatched.isEmpty())
        assertTrue(task.notes!!.contains(input))
    }

    @Test fun `start repeat reminder flag and numeric date order share the editor rules`() {
        val input = "Review due friday starts tomorrow every week 3pm remind 15 min before important #home +Work"
        val expected = NaturalLanguageParser.parse(input, today, now, true)
        val task = parse("- $input").prepareSmartImport(context, null, null, true).single().task
        assertEquals(expected.startDate, task.startDate)
        assertEquals(expected.recurrence, task.recurrence)
        assertEquals(expected.reminder, task.reminder)
        assertEquals(expected.flag, task.flag)
        assertNotNull(task.startDate)
        assertNotNull(task.recurrence)
        assertNotNull(task.reminder)
        assertTrue(task.flag)
        val dayFirst = parse("- [ ] Review 3/4 +Work", true).tasks.single().quickAdd!!
        val monthFirst = parse("- [ ] Review 3/4 +Work", false).tasks.single().quickAdd!!
        assertEquals(NaturalLanguageParser.parse("Review 3/4 +Work", today, now, true).due, dayFirst.due)
        assertEquals(NaturalLanguageParser.parse("Review 3/4 +Work", today, now, false).due, monthFirst.due)
        assertNotEquals(dayFirst.due, monthFirst.due)
    }

    @Test fun `headings supply metadata while task fields override defaults`() {
        val document = parse("""
            # Project: Work
            ## Tags: school
            ### Assigned to: Maya
            #### Tomorrow
            - [ ] Review #home
            - [ ] Pack today
        """)
        val tasks = document.prepareSmartImport(context, null, null, true).map { it.task }
        assertEquals(listOf("work", "work"), tasks.map { it.projectId })
        assertEquals(listOf("2026-10-28", "2026-10-27"), tasks.map { it.due })
        assertEquals(listOf("school-tag", "home"), tasks.first().tagIds)
        assertEquals(listOf("maya"), tasks.first().assigneeIds)
    }

    @Test fun `manual destination overrides detected project and list without losing other fields`() {
        val preview = parse("- [ ] Buy tomorrow #home @Maya +Work =Errands")
            .prepareSmartImport(context, "school", "errands", true).single()
        assertEquals("school", preview.task.projectId)
        assertEquals("errands", preview.task.listId)
        assertEquals(listOf("home"), preview.task.tagIds)
        assertEquals(listOf("maya"), preview.task.assigneeIds)
        assertEquals("2026-10-28", preview.task.due)
    }

    @Test fun `bare heading names match local projects and explicit list tokens resolve`() {
        val tasks = parse("""
            # School
            - [ ] Prep
            - [ ] Buy =Errands
        """).prepareSmartImport(context, null, null, true).map { it.task }
        assertEquals("school", tasks.first().projectId)
        assertEquals("errands", tasks[1].listId)
        assertNull(tasks[1].projectId)
    }

    @Test fun `inline project replaces heading list and inline list replaces heading project`() {
        val fromList = parse("# List: Errands\n- [ ] Review +Work")
            .prepareSmartImport(context, null, null, true).single().task
        assertEquals("work", fromList.projectId)
        assertNull(fromList.listId)
        val fromProject = parse("# Project: Work\n- [ ] Buy =Errands")
            .prepareSmartImport(context, null, null, true).single().task
        assertEquals("errands", fromProject.listId)
        assertNull(fromProject.projectId)
    }

    @Test fun `section properties provide shared defaults without becoming tasks`() {
        val task = parse("# Tasks\nProject: Work\nTags: home\n- Review tomorrow")
            .prepareSmartImport(context, null, null, true).single().task
        assertEquals("work", task.projectId)
        assertEquals(listOf("home"), task.tagIds)
        assertEquals("2026-10-28", task.due)
    }

    @Test fun `bullet numbered and indented list tasks keep their hierarchy`() {
        val document = parse("""
            # Work
            1. Review tomorrow #home @Maya
               - Check draft
                 * Check figures
            2) Send friday
            • Call today
        """)
        assertEquals(3, document.tasks.size)
        assertEquals(2, document.tasks.first().subtasks.size)
        assertEquals(listOf(null, 0), document.tasks.first().subtasks.map { it.parentIndex })
        val preview = document.prepareSmartImport(context, null, null, true)
        assertEquals("Review", preview.first().task.title)
        assertEquals("2026-10-28", preview.first().task.due)
    }

    @Test fun `plain task lines are supported without interpreting headings or code as tasks`() {
        val document = parse("""
            # Work
            Review tomorrow
            Call friday
            ```markdown
            Ignored today
            ```
        """)
        assertEquals(2, document.tasks.size)
        assertEquals(listOf("Review", "Call"), document.prepareSmartImport(context, null, null, true).map { it.task.title })
    }

    @Test fun `task tables parse labelled fields and preserve status notes and escaped pipes`() {
        val document = parse("""
            | Task | Due | Tags | Person | Project | Priority | Status | Notes |
            | --- | --- | --- | --- | --- | --- | --- | --- |
            | Review | tomorrow | home, school | Maya | Work | high | done | Read A \| B |
            | Buy supplies | 2026-11-01 | home | Maya | School | low | open | Keep receipt |
        """)
        val previews = document.prepareSmartImport(context, null, null, true)
        assertEquals(2, previews.size)
        val first = previews.first().task
        assertEquals("Review", first.title)
        assertEquals("2026-10-28", first.due)
        assertEquals("high", first.priority)
        assertTrue(first.done)
        assertEquals("work", first.projectId)
        assertEquals(listOf("home", "school-tag"), first.tagIds)
        assertEquals(listOf("maya"), first.assigneeIds)
        assertTrue(first.notes!!.contains("Read A | B"))
        assertEquals("school", previews[1].task.projectId)
        assertEquals("2026-11-01", previews[1].task.due)
    }

    @Test fun `indented properties supply fields without turning properties into subtasks`() {
        val preview = parse("""
            - [ ] Review
              - Due: tomorrow
              - **Tags:** home
              - Person: Maya
              - Project: Work
              - Priority: high
              - [ ] Read draft
        """).prepareSmartImport(context, null, null, true).single()
        assertEquals("2026-10-28", preview.task.due)
        assertEquals("high", preview.task.priority)
        assertEquals("work", preview.task.projectId)
        assertEquals(listOf("maya"), preview.task.assigneeIds)
        assertEquals(listOf("home"), preview.task.tagIds)
        assertEquals(listOf("Read draft"), preview.task.subtasks.map { it.title })
    }

    @Test fun `single column task tables support common action item headings`() {
        val document = parse("| Action Item |\n| --- |\n| Review tomorrow +Work |")
        val task = document.prepareSmartImport(context, null, null, true).single().task
        assertEquals("Review", task.title)
        assertEquals("2026-10-28", task.due)
        assertEquals("work", task.projectId)
    }

    @Test fun `unknown references stay visible and preserved without inventing entities`() {
        val input = "Review #unrecognized_tag @CompletelyUnknownPerson +UnrecognizedProject"
        val preview = parse("- [ ] $input").prepareSmartImport(context, null, null, true).single()
        assertEquals(setOf("#unrecognized_tag", "@CompletelyUnknownPerson", "+UnrecognizedProject"), preview.unmatched.toSet())
        assertTrue(preview.task.tagIds.isEmpty())
        assertTrue(preview.task.assigneeIds.isEmpty())
        assertNull(preview.task.projectId)
        assertTrue(preview.task.notes!!.contains(input))
    }

    @Test fun `schedule toggle clears every scheduling field while keeping entities and priority`() {
        val task = parse("- [ ] Review starts tomorrow every friday 3pm remind 15 min before !1 #home @Maya +Work")
            .prepareSmartImport(context, null, null, false).single().task
        assertNull(task.due)
        assertNull(task.startDate)
        assertNull(task.time)
        assertNull(task.recurrence)
        assertNull(task.reminder)
        assertEquals("high", task.priority)
        assertEquals("work", task.projectId)
        assertEquals(listOf("home"), task.tagIds)
    }

    @Test fun `disabled entity features follow the individual task rules`() {
        val disabled = context.copy(projectsEnabled = false, tagsEnabled = false, peopleEnabled = false)
        val task = parse("- [ ] Review #home @Maya +Work =Errands").prepareSmartImport(disabled, null, null, true).single().task
        assertNull(task.projectId)
        assertTrue(task.tagIds.isEmpty())
        assertTrue(task.assigneeIds.isEmpty())
        assertEquals("errands", task.listId)
    }

    @Test fun `document updated date anchors relative task dates even when imported a year later`() {
        val document = MarkdownTaskParser.parse("_Updated: 27 Oct 2026_\n- [ ] Review tomorrow +Work", today.plusYears(1), now, true)
        assertEquals("2026-10-28", document.prepareSmartImport(context, null, null, true).single().task.due)
    }

    @Test fun `links and inline code remain literal instead of supplying accidental metadata`() {
        val task = parse("- [ ] Read [guide](https://example.org/2026-11-12#home) and `tomorrow` +Work")
            .prepareSmartImport(context, null, null, true).single().task
        assertNull(task.due)
        assertTrue(task.tagIds.isEmpty())
        assertTrue(task.title.contains("https://example.org/2026-11-12#home"))
        assertTrue(task.title.contains("tomorrow"))
    }

    @Test fun `custom date aliases are parsed once and the approved schedule stays frozen`() {
        try {
            NaturalLanguageParser.configureDateAliases(setOf("schoolday|TOMORROW"))
            val document = parse("- [ ] Review schoolday 3pm +Work")
            // Settings/time may change after reading a file. Resolving entities must not reparse.
            NaturalLanguageParser.configureDateAliases(setOf("schoolday|NEXT_WEEK"))
            val task = document.prepareSmartImport(context, null, null, true).single().task
            assertEquals("2026-10-28", task.due)
            assertEquals("3:00 PM", task.time)
        } finally {
            NaturalLanguageParser.configureDateAliases(emptySet())
        }
    }

    @Test fun `setext headings are retained and provide the same project defaults`() {
        val document = parse("""
            School
            ======
            Tomorrow
            --------
            - Prepare
        """)
        assertEquals("School", document.title)
        val task = document.prepareSmartImport(context, null, null, true).single().task
        assertEquals("school", task.projectId)
        assertEquals("2026-10-28", task.due)
    }

    @Test fun `many protected literals restore without overlapping placeholder identities`() {
        val literals = (0..12).map { "https://example.org/$it#home" }
        val task = parse("- [ ] Read ${literals.joinToString(" ")} +Work")
            .prepareSmartImport(context, null, null, true).single().task
        literals.forEach { assertTrue(task.title.contains(it)) }
        assertTrue(task.tagIds.isEmpty())
    }
}
