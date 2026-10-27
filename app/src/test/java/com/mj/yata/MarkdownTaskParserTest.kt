package com.mj.yata

import com.mj.yata.util.MAX_MARKDOWN_BYTES
import com.mj.yata.util.MarkdownImportTooLargeException
import com.mj.yata.util.MarkdownTaskParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MarkdownTaskParserTest {
    private val today = LocalDate.of(2026, 10, 27)

    // School-style fixture without names, school identifiers, or form links from a real family.
    private val schoolChecklist = """
        # School preparation
        _Updated: 27 Oct 2026_

        ## 🔥 Today — Tue 27 Oct
        - [ ] Complete the form for both
        - [ ] **Child A:** Show and Tell for tomorrow — *Healthy food vs Junk food*
          - [ ] Make a chart
          - [ ] Practise sentences
        - [ ] Pack bags for tomorrow
          - [ ] Red clothes for both
          - [ ] Presentation chart
          - [ ] Payment for the show *(was due 26 Oct)*
          - [ ] Health card, if at home

        ## Wed 28 Oct
        - [ ] Send festival choices
          - Child A — Literary: ______ | Cultural: ______
          - Child B — Literary: ______ | Cultural: ______
          - *Literary:* song or storytelling
        - [ ] Pay school fees till October
        - [ ] **Child B:** Show and Tell for Thursday
          - [ ] Make a chart
          - [ ] Practise sentences
        - [ ] Food activity (Thu 29 Oct)
          - [ ] GO food pictures
          - [ ] GROW food pictures
          - [ ] GLOW food pictures
          - [ ] Teach the names

        ## By Sat 31 Oct
        - [ ] Parent app, for both children
          - [ ] Upload photos
          - [ ] Fill parent details
        - [ ] Confirm data form was filled

        ## By Thu 5 Nov
        - [ ] Send lamps for activity Fri 6 Nov

        ## By Sat 7 Nov
        - [ ] Festival prep — Literary Tue 10 Nov, Cultural Thu 12 Nov
          - [ ] Costumes and props
          - [ ] Practise literary item
          - [ ] Practise cultural item
        - [ ] Outfit for Fri 13 Nov *(optional)*

        ## By Sat 14 Nov
        - [ ] Sports Day (Mon 16 Nov) — clothes for both

        ---
        ### Key dates for reference
        | Date | Event |
        |---|---|
        | Wed 28 Oct | Red Colour Day |
        | Mon 16 Nov | Sports Day |
    """.trimIndent()

    @Test fun `school checklist preserves deadlines subtasks notes and reference table`() {
        val document = MarkdownTaskParser.parse(schoolChecklist, today.plusYears(1))
        assertEquals("School preparation", document.title)
        assertEquals(today, document.referenceDate)
        assertEquals(13, document.tasks.size)
        assertEquals(17, document.tasks.sumOf { it.subtasks.size })
        assertEquals(
            List(3) { "2026-10-27" } + List(4) { "2026-10-28" } + List(2) { "2026-10-31" } +
                "2026-11-05" + List(2) { "2026-11-07" } + "2026-11-14",
            document.tasks.map { it.due }
        )
        assertEquals("Child A: Show and Tell for tomorrow — Healthy food vs Junk food", document.tasks[1].title)
        assertEquals(4, document.tasks[2].subtasks.size)
        assertTrue(document.tasks[3].notes!!.contains("Literary: ______"))
        assertTrue(document.tasks.all { it.notes!!.contains("| Mon 16 Nov | Sports Day |") })
        assertFalse(document.tasks.any { it.title == "GO food pictures" })
    }

    @Test fun `nested checkboxes retain completion and sibling ancestry`() {
        val document = MarkdownTaskParser.parse("""
            - [X] Finished parent
              - [x] First child
                - [ ] Grandchild
              - [ ] Second child
            - [ ] Open parent
        """.trimIndent(), today)
        val parent = document.tasks.first()
        assertEquals(2, document.tasks.size)
        assertTrue(parent.done)
        assertEquals(listOf(true, false, false), parent.subtasks.map { it.done })
        assertEquals(listOf(null, 0, null), parent.subtasks.map { it.parentIndex })
    }

    @Test fun `code examples normal bullets quotes and tables do not become tasks`() {
        val document = MarkdownTaskParser.parse("""
            # Examples
            ```markdown
            _Updated: 1 Jan 2000_
            - [ ] Example
            ## 2020-01-01
            ```
            ~~~~md
            - [ ] Another example
            ~~~
            - [ ] Still code
            ~~~~
            - Ordinary bullet
            <!--
            - [ ] Hidden comment
            -->
            > - [ ] Quoted item
            | - [ ] Table cell |
            ## Today
            + [ ] Real task
        """.trimIndent(), today)
        assertEquals(listOf("Real task"), document.tasks.map { it.title })
        assertEquals(today, document.referenceDate)
        assertEquals(today.toString(), document.tasks.single().due)
    }

    @Test fun `relative dates use updated metadata and undated sibling resets scope`() {
        val document = MarkdownTaskParser.parse("""
            _Updated: 2025-12-30_
            ## Tomorrow
            - [ ] First
            ### Preparation
            - [ ] Second
            ## Someday
            - [ ] Third
        """.trimIndent(), today)
        assertEquals(listOf("2025-12-31", "2025-12-31", null), document.tasks.map { it.due })
    }

    @Test fun `dates support ISO English month first ordinals and year rollover`() {
        val document = MarkdownTaskParser.parse("""
            ## By 31st December 2026
            - [ ] Last day
            ## Jan 2
            - [ ] New year
            ## 2027-02-03
            - [ ] ISO date
            ## By Thu 31 Feb
            - [ ] Invalid date
        """.trimIndent(), today)
        assertEquals(listOf("2026-12-31", "2027-01-02", "2027-02-03", null), document.tasks.map { it.due })
    }

    @Test fun `inline event dates stay context while explicit due annotation overrides heading`() {
        val document = MarkdownTaskParser.parse("""
            ## By 7 Nov 2026
            - [ ] Prep for festival on 10 Nov
            - [ ] Buy clothes (due 2026-11-06)
        """.trimIndent(), today)
        assertEquals(listOf("2026-11-07", "2026-11-06"), document.tasks.map { it.due })
    }

    @Test fun `BOM CRLF ordered bullets tabs formatting and links survive`() {
        val document = MarkdownTaskParser.parse(
            "\uFEFF# Checklist\r\n1. [ ] **Read** [guide](https://example.org/a_b)\r\n\t* [x] _Done_\r\n2) [ ] `Write` notes\r\n",
            today
        )
        assertEquals("Checklist", document.title)
        assertEquals(listOf("Read guide (https://example.org/a_b)", "Write notes"), document.tasks.map { it.title })
        assertEquals("Done", document.tasks.first().subtasks.single().title)
    }

    @Test fun `materialization imports only selected tasks to either or both containers with fresh IDs`() {
        val document = MarkdownTaskParser.parse("""
            ## 2026-10-28
            - [ ] Skip this
            - [x] Parent
              - [ ] Child
                - [x] Grandchild
        """.trimIndent(), today)
        val first = document.createTasks(setOf(1), "project", "list", true, listOf("me"), "high", 20).single()
        val second = document.createTasks(setOf(1), null, "list", false).single()
        val third = document.createTasks(setOf(0), "project", null, true).single()
        assertEquals("Parent", first.title)
        assertEquals("project", first.projectId)
        assertEquals("list", first.listId)
        assertEquals("2026-10-28", first.due)
        assertEquals(listOf("me"), first.assigneeIds)
        assertEquals("high", first.priority)
        assertEquals(20, first.sortOrder)
        assertTrue(first.done)
        assertNull(first.completedAt) // Import time is not the historical completion time.
        assertEquals(first.subtasks.first().id, first.subtasks[1].parentSubtaskId)
        assertTrue(first.subtasks[1].done)
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.subtasks.first().id, second.subtasks.first().id)
        assertNull(second.projectId)
        assertNull(second.due)
        assertNull(second.reminder)
        assertNull(third.listId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `materialization requires a destination`() {
        MarkdownTaskParser.parse("- [ ] Task", today).createTasks(setOf(0), null, null, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `materialization rejects a stale selection`() {
        MarkdownTaskParser.parse("- [ ] Task", today).createTasks(setOf(1), "project", null, true)
    }

    @Test fun `prose-only input has no tasks`() {
        assertTrue(MarkdownTaskParser.parse("# Notes\nJust some notes\n- A bullet", today).tasks.isEmpty())
    }

    @Test(expected = MarkdownImportTooLargeException::class)
    fun `oversized UTF-8 input is rejected`() {
        MarkdownTaskParser.parse("₹".repeat(MAX_MARKDOWN_BYTES / 3 + 1), today)
    }

    @Test(expected = MarkdownImportTooLargeException::class)
    fun `too many tasks are rejected before any write`() {
        MarkdownTaskParser.parse(List(1001) { "- [ ] Task $it" }.joinToString("\n"), today)
    }

    @Test(expected = MarkdownImportTooLargeException::class)
    fun `too many subtasks are rejected before any write`() {
        MarkdownTaskParser.parse("- [ ] Parent\n" + List(201) { "  - [ ] Child $it" }.joinToString("\n"), today)
    }
}
