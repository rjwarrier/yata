package com.mj.yata

import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class MarkdownImportOperationsTest {
    private fun task(id: String) = Task(id, "Review $id", "list", null, "", "2026-11-01",
        time = null, reminder = null, priority = "none", flag = false, done = false, createdAt = 123,
        assigneeIds = emptyList(), tagIds = emptyList(), recurrence = null, subtasks = emptyList(), notes = null)

    private class Store(initial: List<Task> = emptyList()) {
        val tasks = initial.associateBy { it.id }.toMutableMap()
        val projects = mutableListOf(Project("project", "Work", "accentA", "Folder"))
        val lists = mutableListOf(YataList("list", "Inbox", "accentA", "List"))
        val people = mutableListOf<Person>()
        val tags = mutableListOf<Tag>()
        var writes = 0
        var failWrites = false
        var failCreation = false
        @Suppress("UNCHECKED_CAST")
        val repository = Proxy.newProxyInstance(YataRepository::class.java.classLoader, arrayOf(YataRepository::class.java)) { _, method, args ->
            fun live() = tasks.values.filter { it.deletedAt == null && !it.archived }
            when (method.name) {
                "getProjects" -> MutableStateFlow(projects.toList())
                "getLists" -> MutableStateFlow(lists.toList())
                "getPeople" -> MutableStateFlow(people.toList())
                "getTags" -> MutableStateFlow(tags.toList())
                "getTasksByIds" -> live().filter { it.id in (args!![0] as Collection<String>) }
                "getTasksForProject" -> MutableStateFlow(live().filter { it.projectId == args!![0] })
                "getTasksForList" -> MutableStateFlow(live().filter { it.listId == args!![0] })
                "upsertTasks" -> {
                    if (failWrites) error("Write failed")
                    val values = args!![0] as List<Task>
                    assertEquals(values.size, values.map { it.id }.distinct().size)
                    values.forEach { tasks[it.id] = it.copy(createdAt = it.createdAt ?: 123) }
                    writes++
                    Unit
                }
                "upsertTag", "upsertPerson", "upsertProject", "upsertList" -> {
                    if (failCreation) error("Creation failed")
                    when (val value = args!![0]) {
                        is Tag -> tags.add(value)
                        is Person -> people.add(value)
                        is Project -> projects.add(value)
                        is YataList -> lists.add(value)
                    }
                    Unit
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as YataRepository
    }

    @Test fun `only approved rows are written once and batch undo restores updates and trashes new tasks`() = runTest {
        val old = task("existing").copy(sortOrder = 8, notes = "Keep this")
        val unrelated = task("unrelated")
        val store = Store(listOf(old, unrelated))
        val rows = listOf(task("new"), task("skipped"), task("update").copy(title = "Corrected", priority = "high"))
        val batch = applyMarkdownImport(store.repository, rows, setOf(0, 2), mapOf(2 to old.id))
        assertEquals(2, batch.count)
        assertEquals(1, store.writes)
        assertNull(store.tasks["skipped"])
        assertEquals(9, store.tasks.getValue("new").sortOrder)
        assertEquals("Corrected", store.tasks.getValue(old.id).title)
        assertEquals("Keep this", store.tasks.getValue(old.id).notes)
        assertEquals(unrelated, store.tasks[unrelated.id])
        undoMarkdownImport(store.repository, batch)
        assertEquals(old, store.tasks[old.id])
        assertNotNull(store.tasks.getValue("new").deletedAt)
        assertEquals(unrelated, store.tasks[unrelated.id])
        assertEquals(2, store.writes)
    }

    @Test fun `multiple rows approved for one existing task merge once without losing notes`() = runTest {
        val old = task("existing").copy(notes = "Original")
        val store = Store(listOf(old))
        val batch = applyMarkdownImport(store.repository,
            listOf(task("a").copy(notes = "First"), task("b").copy(notes = "Second")), setOf(0, 1), mapOf(0 to old.id, 1 to old.id))
        assertEquals(1, batch.count)
        assertEquals("Original\n\nFirst\n\nSecond", store.tasks.getValue(old.id).notes)
        assertEquals(listOf(old), batch.before)
    }

    @Test fun `batch undo keeps later edits to an updated task`() = runTest {
        val old = task("existing")
        val store = Store(listOf(old))
        val batch = applyMarkdownImport(store.repository, listOf(task("incoming").copy(priority = "high")), setOf(0), mapOf(0 to old.id))
        store.tasks[old.id] = store.tasks.getValue(old.id).copy(title = "Later title", notes = "Later note", trackedMinutes = 20)
        undoMarkdownImport(store.repository, batch)
        val result = store.tasks.getValue(old.id)
        assertEquals("Later title", result.title)
        assertEquals("Later note", result.notes)
        assertEquals(20, result.trackedMinutes)
        assertEquals(old.priority, result.priority)
    }

    @Test fun `removed update targets and archived destinations fail before any write`() = runTest {
        val store = Store(listOf(task("trashed").copy(deletedAt = 10)))
        try {
            applyMarkdownImport(store.repository, listOf(task("new")), setOf(0), mapOf(0 to "trashed"))
            fail("Deleted target should not be overwritten")
        } catch (_: IllegalArgumentException) { }
        store.lists[0] = store.lists[0].copy(archived = true)
        try {
            applyMarkdownImport(store.repository, listOf(task("new")), setOf(0), emptyMap())
            fail("Archived destination should be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, store.writes)
    }

    @Test fun `invalid selection and unapproved update indices cannot write`() = runTest {
        val store = Store()
        for ((selected, updates) in listOf(setOf(9) to emptyMap(), emptySet<Int>() to emptyMap(), setOf(0) to mapOf(1 to "x"))) {
            try {
                applyMarkdownImport(store.repository, listOf(task("new")), selected, updates)
                fail("Invalid selection accepted")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, store.writes)
    }

    @Test fun `batch failure leaves task data unchanged and does not return an undo batch`() = runTest {
        val old = task("existing")
        val store = Store(listOf(old)).also { it.failWrites = true }
        try {
            applyMarkdownImport(store.repository, listOf(task("new")), setOf(0), emptyMap())
            fail("Expected write failure")
        } catch (_: IllegalStateException) { }
        assertEquals(mapOf(old.id to old), store.tasks)
    }

    @Test fun `explicit reference creation supports all kinds and reuses case insensitive names`() = runTest {
        val store = Store()
        val tag = createMarkdownReference(store.repository, "#School", "chosen-tag")
        val person = createMarkdownReference(store.repository, "@Maya Rao")
        val project = createMarkdownReference(store.repository, "+School")
        val list = createMarkdownReference(store.repository, "=Supplies")
        assertEquals("chosen-tag", tag)
        assertEquals(tag, store.tags.single().id)
        assertEquals(person, store.people.single().id)
        assertEquals("MR", store.people.single().initials)
        assertEquals(project, store.projects.single { it.name == "School" }.id)
        assertEquals(list, store.lists.single { it.name == "Supplies" }.id)
        assertEquals(tag, createMarkdownReference(store.repository, "# school "))
        assertEquals(person, createMarkdownReference(store.repository, "@maya rao"))
        assertEquals(project, createMarkdownReference(store.repository, "+school"))
        assertEquals(list, createMarkdownReference(store.repository, "=supplies"))
        assertEquals(1, store.tags.size)
        assertEquals(1, store.people.size)
    }

    @Test fun `reference failures and unknown types do not produce a mapping`() = runTest {
        val store = Store().also { it.failCreation = true }
        for (token in listOf("#School", "@Maya", "+School", "=Supplies", "?bad")) {
            try { createMarkdownReference(store.repository, token); fail("Expected creation failure") }
            catch (_: IllegalStateException) { }
        }
        assertTrue(store.tags.isEmpty())
        assertTrue(store.people.isEmpty())
    }

    @Test fun `references removed after preview are rejected before task writes`() = runTest {
        val store = Store()
        try {
            applyMarkdownImport(store.repository, listOf(task("new").copy(tagIds = listOf("removed"))), setOf(0), emptyMap())
            fail("Missing reference should not be silently dropped")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, store.writes)
    }

    @Test fun `bulk removal only touches selected optional tags and preserves inherited tags`() = runTest {
        val inherited = task("inherited").copy(projectId = "project", listId = null, tagIds = listOf("tag", "keep"))
        val optional = task("optional").copy(tagIds = listOf("tag", "keep"))
        val unselected = task("unselected").copy(tagIds = listOf("tag"))
        val store = Store(listOf(inherited, optional, unselected))
        store.projects[0] = store.projects[0].copy(commonTagIds = listOf("tag"))
        removeBulkTag(store.repository, listOf(inherited.id, optional.id, optional.id, "missing"), "tag")
        assertEquals(inherited, store.tasks[inherited.id])
        assertEquals(optional.copy(tagIds = listOf("keep")), store.tasks[optional.id])
        assertEquals(unselected, store.tasks[unselected.id])
        assertEquals(1, store.writes)
        removeBulkTag(store.repository, listOf(inherited.id, optional.id), "tag")
        assertEquals(1, store.writes)
    }
}
