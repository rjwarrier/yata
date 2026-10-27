package com.mj.yata

import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class BulkTagEditTest {
    private fun task(id: String, tags: List<String> = emptyList()) = Task(id, "Task $id", null, null, "", "2026-11-01",
        time = "9:00 AM", reminder = "15 min before", priority = "high", flag = true, done = false,
        assigneeIds = emptyList(), tagIds = tags, recurrence = null, subtasks = emptyList(), notes = "Keep notes")

    private class Store(initial: List<Task>) {
        val tasks = initial.associateBy { it.id }.toMutableMap()
        val tags = mutableListOf(Tag("keep", "Keep", "accentA"), Tag("remove", "Remove", "accentB"))
        val projects = mutableListOf<Project>()
        var writes = 0
        var failWrite = false
        var pending: List<Tag> = emptyList()
        @Suppress("UNCHECKED_CAST")
        val repository = Proxy.newProxyInstance(YataRepository::class.java.classLoader, arrayOf(YataRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getTasksByIds" -> tasks.values.filter { it.id in args!![0] as Collection<String> }
                "getTags" -> MutableStateFlow(tags.toList())
                "getProjects" -> MutableStateFlow(projects.toList())
                "upsertTasks" -> {
                    if (failWrite) error("Transaction failed")
                    assertEquals(false, args!![2])
                    pending = args[5] as List<Tag>
                    val changed = args[0] as List<Task>
                    val valid = (tags + pending).map { it.id }.toSet()
                    assertTrue(changed.all { task -> task.tagIds.all { it in valid } })
                    tags.addAll(pending)
                    changed.forEach { tasks[it.id] = it }
                    writes++
                    Unit
                }
                else -> error("Unexpected non-batch operation: ${method.name}")
            }
        } as YataRepository
    }

    @Test fun `multiple additions removals and a new tag use one write only for selected tasks`() = runTest {
        val one = task("one", listOf("remove"))
        val two = task("two", listOf("keep", "remove"))
        val untouched = task("other", listOf("remove"))
        val store = Store(listOf(one, two, untouched))
        val newTag = Tag("new", "School", "accentG")
        val count = applyBulkTagEdit(store.repository, listOf("one", "two", "one"),
            BulkTagEdit(mapOf("remove" to false, "keep" to true, "new" to true), listOf(newTag)))
        assertEquals(2, count)
        assertEquals(1, store.writes)
        assertEquals(listOf(newTag), store.pending)
        assertEquals(one.copy(tagIds = listOf("keep", "new")), store.tasks[one.id])
        assertEquals(two.copy(tagIds = listOf("keep", "new")), store.tasks[two.id])
        assertEquals(untouched, store.tasks[untouched.id])
    }

    @Test fun `case insensitive staged names reuse live tags and preserve their color`() = runTest {
        val store = Store(listOf(task("one")))
        applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("draft" to true), listOf(Tag("draft", " KEEP ", "accentP"))))
        assertEquals(listOf("keep"), store.tasks.getValue("one").tagIds)
        assertTrue(store.pending.isEmpty())
        assertEquals("accentA", store.tags.single { it.id == "keep" }.color)
    }

    @Test fun `duplicate staged new names are created once and reference one ID`() = runTest {
        val store = Store(listOf(task("one")))
        val first = Tag("a", "School", "accentC")
        val second = Tag("b", "school", "accentD")
        applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("a" to true, "b" to true), listOf(first, second)))
        assertEquals(listOf(first), store.pending)
        assertEquals(listOf("a"), store.tasks.getValue("one").tagIds)
    }

    @Test fun `removal preserves inherited tags and unrelated fields`() = runTest {
        val inherited = task("one", listOf("remove")).copy(projectId = "p")
        val optional = task("two", listOf("remove"))
        val store = Store(listOf(inherited, optional))
        store.projects.add(Project("p", "Work", "accentA", "Folder", commonTagIds = listOf("remove")))
        assertEquals(1, applyBulkTagEdit(store.repository, listOf("one", "two"), BulkTagEdit(mapOf("remove" to false))))
        assertEquals(inherited, store.tasks[inherited.id])
        assertEquals(optional.copy(tagIds = emptyList()), store.tasks[optional.id])
    }

    @Test fun `write failure leaves new tags and existing tasks untouched`() = runTest {
        val original = task("one", listOf("remove"))
        val store = Store(listOf(original)).also { it.failWrite = true }
        try {
            applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("new" to true, "remove" to false), listOf(Tag("new", "New", "accentC"))))
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(original, store.tasks[original.id])
        assertEquals(2, store.tags.size)
        assertEquals(0, store.writes)
    }

    @Test fun `deleted IDs fail before writing or persisting pending tags`() = runTest {
        val store = Store(listOf(task("one")))
        try {
            applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("missing" to true, "new" to true), listOf(Tag("new", "New", "accentC"))))
            fail("Expected invalid reference")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, store.writes)
        assertEquals(2, store.tags.size)
    }

    @Test fun `cancelled staged creation and empty or unchanged edits produce no writes`() = runTest {
        val store = Store(listOf(task("one", listOf("keep"))))
        assertEquals(0, applyBulkTagEdit(store.repository, emptyList(), BulkTagEdit(mapOf("keep" to true))))
        assertEquals(0, applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("keep" to true))))
        assertEquals(0, applyBulkTagEdit(store.repository, listOf("one"), BulkTagEdit(mapOf("new" to false), listOf(Tag("new", "New", "accentC")))))
        assertEquals(0, store.writes)
        assertEquals(2, store.tags.size)
    }

    @Test fun `preview is reversible and does not mutate the selected task snapshots`() {
        val task = task("one", listOf("remove"))
        val preview = previewBulkTagEdit(listOf(task), emptyList(), mapOf("remove" to false, "keep" to true))
        assertEquals(listOf("keep"), preview.single().tagIds)
        assertEquals(listOf("remove"), task.tagIds)
        assertEquals(listOf(task), previewBulkTagEdit(listOf(task), emptyList(), emptyMap()))
    }
}
