package com.mj.yata

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.resolveParsedQuickAddEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsedQuickAddApplierTest {

    private val people = listOf(Person("p_alan", "Alan", "A", "accentA"))
    private val tags = listOf(Tag("tag_urgent", "Urgent", "accentB"))
    private val projects = listOf(Project("project_work", "Work", "accentC", "work"))
    private val lists = listOf(YataList("list_groceries", "Groceries", "accentD", "shopping_cart"))

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
