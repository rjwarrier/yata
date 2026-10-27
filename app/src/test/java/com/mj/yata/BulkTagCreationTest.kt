package com.mj.yata

import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.usecase.createAndApplyBulkTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class BulkTagCreationTest {
    private fun task(id: String, tags: List<String> = emptyList()) = Task(
        id = id, title = "Task $id", listId = null, projectId = null, section = "",
        due = "2026-11-01", time = "9:00 AM", reminder = "15 min before", priority = "high",
        flag = true, done = false, assigneeIds = listOf("owner"), tagIds = tags,
        recurrence = null, subtasks = emptyList(), notes = "Keep these notes"
    )

    /** Only the repository calls this operation uses; unrelated calls fail immediately. */
    private class Store(initialTasks: List<Task>, initialTags: List<Tag> = emptyList()) {
        val tasks = initialTasks.associateBy { it.id }.toMutableMap()
        val tags = initialTags.associateBy { it.id }.toMutableMap()
        val events = mutableListOf<String>()
        var failCreation = false
        var taskWrites = 0
        @Suppress("UNCHECKED_CAST")
        val repository = Proxy.newProxyInstance(
            YataRepository::class.java.classLoader, arrayOf(YataRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTasksByIds" -> {
                    val ids = args!![0] as Collection<String>
                    assertEquals(ids.size, ids.toSet().size)
                    tasks.values.filter { it.id in ids }
                }
                "getTags" -> MutableStateFlow(tags.values.toList())
                "upsertTag" -> {
                    if (failCreation) throw IllegalStateException("Cannot save tag")
                    val tag = args!![0] as Tag
                    tags[tag.id] = tag
                    events += "create"
                    Unit
                }
                "upsertTasks" -> {
                    val updated = args!![0] as List<Task>
                    assertEquals(true, args[1])
                    assertEquals(false, args[2]) // Tag changes must not reschedule reminders.
                    updated.forEach { task ->
                        assertTrue("Tag must exist before the task references it", task.tagIds.all { it in tags })
                        tasks[task.id] = task
                    }
                    taskWrites++
                    events += "apply"
                    Unit
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as YataRepository
    }

    @Test fun `create persists the tag before applying it only to selected tasks`() = runTest {
        val oldTag = Tag("old", "Existing", "accentB")
        val originals = listOf(task("one", listOf("old")), task("two"), task("unselected"))
        val store = Store(originals, listOf(oldTag))
        createAndApplyBulkTag(store.repository, listOf("one", "two", "one"), " #School ", "accentA")
        val newTag = store.tags.values.single { it.id != "old" }
        assertEquals("School", newTag.name)
        assertEquals("accentA", newTag.color)
        assertEquals(listOf("create", "apply"), store.events)
        assertEquals(originals[0].copy(tagIds = listOf("old", newTag.id)), store.tasks["one"])
        assertEquals(originals[1].copy(tagIds = listOf(newTag.id)), store.tasks["two"])
        assertEquals(originals[2], store.tasks["unselected"])
        assertEquals(1, store.taskWrites)
    }

    @Test fun `matching names reuse the existing tag and preserve its settings`() = runTest {
        val existing = Tag("school", "School", "accentB", starred = true, description = "Keep this")
        val store = Store(listOf(task("one")), listOf(existing))
        createAndApplyBulkTag(store.repository, listOf("one"), " school ", "accentA")
        assertEquals(listOf(existing), store.tags.values.toList())
        assertEquals(listOf("school"), store.tasks.getValue("one").tagIds)
        assertEquals(listOf("apply"), store.events)
    }

    @Test fun `an already assigned tag is not duplicated or rewritten`() = runTest {
        val store = Store(listOf(task("one", listOf("school"))), listOf(Tag("school", "School", "accentA")))
        createAndApplyBulkTag(store.repository, listOf("one"), "School", "accentA")
        assertTrue(store.events.isEmpty())
        assertEquals(listOf("school"), store.tasks.getValue("one").tagIds)
    }

    @Test fun `creation failure leaves all tasks untouched`() = runTest {
        val original = task("one")
        val store = Store(listOf(original)).also { it.failCreation = true }
        assertTrue(runCatching {
            createAndApplyBulkTag(store.repository, listOf("one"), "School", "accentA")
        }.isFailure)
        assertEquals(original, store.tasks["one"])
        assertTrue(store.tags.isEmpty())
        assertEquals(0, store.taskWrites)
    }

    @Test fun `a selection with no live tasks does not create an unused tag`() = runTest {
        val store = Store(emptyList())
        createAndApplyBulkTag(store.repository, listOf("deleted"), "School", "accentA")
        assertTrue(store.tags.isEmpty())
        assertTrue(store.events.isEmpty())
    }

    @Test fun `blank names are rejected without writes`() = runTest {
        val store = Store(listOf(task("one")))
        val result = runCatching { createAndApplyBulkTag(store.repository, listOf("one"), " # ", "accentA") }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(store.tags.isEmpty())
        assertTrue(store.events.isEmpty())
    }
}
