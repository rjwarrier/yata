package com.mj.yata.data.backup

import com.mj.yata.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDiffTest {

    @Test
    fun compareBackupJsonWithTasks_countsAddedRemovedAndChangedTasks() {
        val backup = backupJson(
            taskJson("kept", "Kept", done = false),
            taskJson("changed-title", "Old title", done = false),
            taskJson("changed-done", "Changed done", done = false),
            taskJson("removed", "Removed", done = true)
        )
        val current = listOf(
            task("kept", "Kept", done = false),
            task("changed-title", "New title", done = false),
            task("changed-done", "Changed done", done = true),
            task("added", "Added", done = false)
        )

        val diff = compareBackupJsonWithTasks(backup, current, "2026-08-04T10:15:30Z")

        assertEquals(3, diff.currentPending)
        assertEquals(1, diff.currentDone)
        assertEquals(3, diff.backupPending)
        assertEquals(1, diff.backupDone)
        assertEquals(listOf("Added"), diff.addedTitles)
        assertEquals(1, diff.addedCount)
        assertEquals(listOf("Removed"), diff.removedTitles)
        assertEquals(1, diff.removedCount)
        assertEquals(setOf("New title", "Changed done"), diff.changedTitles.toSet())
        assertEquals(2, diff.changedCount)
        assertEquals("2026-08-04T10:15:30Z", diff.backupCreatedTime)
    }

    @Test
    fun compareBackupJsonWithTasks_capsDisplayedTitlesButKeepsCounts() {
        val backup = backupJson()
        val current = (1..10).map { index -> task("added-$index", "Added $index") }

        val diff = compareBackupJsonWithTasks(backup, current, "now")

        assertEquals(10, diff.addedCount)
        assertEquals(8, diff.addedTitles.size)
    }

    private fun backupJson(vararg tasks: JSONObject): ByteArray = JSONObject()
        .put("tasks", JSONArray().apply { tasks.forEach(::put) })
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun taskJson(id: String, title: String, done: Boolean = false): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("done", done)

    private fun task(id: String, title: String, done: Boolean = false) = Task(
        id = id,
        title = title,
        listId = null,
        projectId = null,
        section = "Morning",
        due = null,
        time = null,
        reminder = null,
        priority = "none",
        flag = false,
        done = done,
        assigneeIds = emptyList(),
        tagIds = emptyList(),
        recurrence = null,
        subtasks = emptyList(),
        notes = null
    )
}
