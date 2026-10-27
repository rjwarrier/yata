package com.mj.yata

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.QuickAddEntityLookup
import com.mj.yata.util.resolveParsedQuickAddEntities
import com.mj.yata.util.withParsedQuickAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedQuickAddApplierTest {
    private fun task() = Task(
        id = "task", title = "Review", listId = "list_groceries", projectId = null, section = "",
        due = "2026-10-27", time = "8:00 AM", reminder = null, priority = "none", flag = false,
        done = false, assigneeIds = listOf("owner"), tagIds = listOf("existing"),
        recurrence = null, subtasks = emptyList(), notes = "Keep the original notes"
    )

    private val people = listOf(Person("p_alan", "Alan", "A", "accentA"))
    private val tags = listOf(Tag("tag_urgent", "Urgent", "accentB"))
    private val projects = listOf(Project("project_work", "Work", "accentC", "work"))
    private val lists = listOf(YataList("list_groceries", "Groceries", "accentD", "shopping_cart"))

    @Test fun allFieldsApplyInOneCopyAndBatchLookupMatchesTheRegularPath() {
        val base = task()
        val parsed = NaturalLanguageParser.parse("Review").copy(
            due = "2026-11-01", startDate = "2026-10-28", time = "3:00 PM",
            reminder = "15 min before", recurrence = Recurrence("weekly", 1), priority = "high", flag = true,
            projectName = "Work", tagNames = listOf("Urgent"), assigneeNames = listOf("Alan")
        )
        val expected = base.copy(
            due = parsed.due, startDate = parsed.startDate, time = parsed.time, reminder = parsed.reminder,
            recurrence = parsed.recurrence, priority = "high", flag = true, listId = null, projectId = "project_work",
            tagIds = listOf("existing", "tag_urgent"), assigneeIds = listOf("owner", "p_alan")
        )
        val lookup = QuickAddEntityLookup(lists, projects, people, tags)
        assertEquals(expected, base.withParsedQuickAdd(parsed, lists = lists, projects = projects, people = people,
            tags = tags, projectsEnabled = true, tagsEnabled = true, peopleEnabled = true))
        assertEquals(expected, base.withParsedQuickAdd(parsed, lists = lists, projects = projects, people = people,
            tags = tags, projectsEnabled = true, tagsEnabled = true, peopleEnabled = true, entityLookup = lookup))
        val ignored = setOf("due", "start", "time", "reminder", "recurrence", "priority", "flag", "project", "list", "tags", "people")
        assertEquals(base, base.withParsedQuickAdd(parsed, ignored, lists, projects, people, tags, true, true, true, lookup))
    }

    @Test fun projectDueFallbackAndIgnoredDueKeepTheirExistingPrecedence() {
        val base = task()
        val datedProjects = projects.map { it.copy(due = "2026-12-01") }
        val parsed = NaturalLanguageParser.parse("Review +Work")
        val lookup = QuickAddEntityLookup(lists, datedProjects, people, tags)
        fun apply(ignored: Set<String> = emptySet()) = base.withParsedQuickAdd(parsed, ignored,
            lists, datedProjects, people, tags, true, true, true, lookup)
        assertEquals("2026-12-01", apply().due)
        assertEquals(base.due, apply(setOf("due")).due)
    }

    @Test
    fun ignoredEntityFieldsDoNotResolvePartialMentionTokens() {
        val quickAdd = NaturalLanguageParser.parse("fix @a #u +w =g")

        val resolved = resolveParsedQuickAddEntities(
            quickAdd = quickAdd,
            baseListId = null,
            baseProjectId = null,
            baseTagIds = emptyList(),
            baseAssigneeIds = emptyList(),
            lists = lists,
            projects = projects,
            people = people,
            tags = tags,
            projectsEnabled = true,
            tagsEnabled = true,
            peopleEnabled = true,
            ignoredFields = setOf("people", "tags", "project", "list")
        )

        assertNull(resolved.projectId)
        assertNull(resolved.listId)
        assertEquals(emptyList<String>(), resolved.tagIds)
        assertEquals(emptyList<String>(), resolved.assigneeIds)
    }

    @Test
    fun unignoredEntityFieldsStillResolveFinishedMentions() {
        val quickAdd = NaturalLanguageParser.parse("fix @alan #urgent +work =groceries")

        val resolved = resolveParsedQuickAddEntities(
            quickAdd = quickAdd,
            baseListId = null,
            baseProjectId = null,
            baseTagIds = emptyList(),
            baseAssigneeIds = emptyList(),
            lists = lists,
            projects = projects,
            people = people,
            tags = tags,
            projectsEnabled = true,
            tagsEnabled = true,
            peopleEnabled = true
        )

        assertEquals("list_groceries", resolved.listId)
        assertNull(resolved.projectId)
        assertEquals(listOf("tag_urgent"), resolved.tagIds)
        assertEquals(listOf("p_alan"), resolved.assigneeIds)
    }
}
