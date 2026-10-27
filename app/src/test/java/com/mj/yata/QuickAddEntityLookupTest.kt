package com.mj.yata

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.util.MemoizedEntityMatcher
import com.mj.yata.util.QuickAddEntityLookup
import com.mj.yata.util.findBestEntityMatch
import org.junit.Assert.*
import org.junit.Test

class QuickAddEntityLookupTest {
    @Test fun `exact matches do not scan candidates again after indexing`() {
        val tags = (0..499).map { Tag("id$it", "Tag$it", "accentA") }
        var nameReads = 0
        val matcher = MemoizedEntityMatcher(tags) { nameReads++; it.name }
        val indexedReads = nameReads
        repeat(1000) { assertEquals("id499", matcher.find(" TAG499 ")!!.id) }
        assertEquals(indexedReads, nameReads)
    }

    @Test fun `unknown queries are cached as misses rather than rescanned`() {
        var nameReads = 0
        val matcher = MemoizedEntityMatcher(listOf(Tag("a", "Red", "accentA"), Tag("b", "Blue", "accentB"))) {
            nameReads++; it.name
        }
        assertNull(matcher.find("xxxxxxxxxxxxxxxxxxxxxxxx"))
        val afterFirst = nameReads
        repeat(1000) { assertNull(matcher.find(" XXXXXXXXXXXXXXXXXXXXXXXX ")) }
        assertEquals(afterFirst, nameReads)
    }

    @Test fun `cached matching preserves exact prefix substring fuzzy and first-match rules`() {
        val tags = listOf(Tag("first", "School", "accentA"), Tag("duplicate", " school ", "accentB"),
            Tag("home", "Household", "accentC"), Tag("health", "Medical", "accentD"))
        val matcher = MemoizedEntityMatcher(tags) { it.name }
        listOf("school", " SCHool ", "sch", "chool", "hosuehold", "medical", "xxxxxxxxxxxxxxxxxxxx").forEach { query ->
            assertEquals(findBestEntityMatch(query, tags, { it.name }), matcher.find(query))
        }
        assertEquals("first", matcher.find("School")!!.id)
    }

    @Test fun `archived inclusion is scoped to its own lookup and keeps original order`() {
        val archivedProject = Project("old", "Work", "accentA", "Folder", archived = true)
        val activeProject = Project("live", "Work", "accentA", "Folder")
        val archivedList = YataList("old-list", "Inbox", "accentA", "List", archived = true)
        val activeList = YataList("live-list", "Inbox", "accentA", "List")
        val archivedPerson = Person("old-person", "Old owner", "O", "accentA", archived = true)
        val lookup = QuickAddEntityLookup(listOf(archivedList, activeList), listOf(archivedProject, activeProject), listOf(archivedPerson), emptyList())
        assertEquals(activeProject, lookup.project("Work"))
        assertEquals(archivedProject, lookup.project("Work", "old"))
        assertEquals(activeProject, lookup.project("Work"))
        assertEquals(activeList, lookup.list("Inbox"))
        assertEquals(archivedList, lookup.list("Inbox", "old-list"))
        assertNull(lookup.person("Old owner"))
        assertEquals(archivedPerson, lookup.person("Old owner", setOf("old-person")))
        assertNull(lookup.person("Old owner"))
    }

    @Test fun `lookup snapshots do not leak stale matches into a later entity snapshot`() {
        val tags = mutableListOf<Tag>()
        val old = QuickAddEntityLookup(emptyList(), emptyList(), emptyList(), tags)
        assertNull(old.tag("School"))
        val tag = Tag("school", "School", "accentA")
        tags += tag
        assertNull(old.tag("School"))
        val refreshed = QuickAddEntityLookup(emptyList(), emptyList(), emptyList(), tags)
        assertEquals(tag, refreshed.tag("School"))
    }
}
