package com.mj.yata

import com.mj.yata.data.mapper.toDomain
import com.mj.yata.data.mapper.toEntity
import com.mj.yata.domain.model.Task
import com.mj.yata.util.TaskSortMode
import com.mj.yata.util.sortedByMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReceivedDateTest {
    private fun task(id: String, received: String? = null) = Task(
        id = id, title = id, listId = null, projectId = "project", section = "",
        due = "2026-12-01", time = null, reminder = null, priority = "none",
        flag = false, done = false, assigneeIds = emptyList(), tagIds = emptyList(),
        recurrence = null, subtasks = emptyList(), notes = null, receivedDate = received
    )

    @Test fun creationDefaultsDoNotLeakBetweenBulkRowsOrAcrossMidnight() {
        val yesterdayDetectedWhileTyping = "2026-10-01"
        val rows = listOf("2026-09-30", null, "2026-10-03")
        assertEquals(listOf("2026-09-30", "2026-11-18", "2026-10-03"), rows.map {
            com.mj.yata.util.receivedDateForCreation(yesterdayDetectedWhileTyping, false, it, "2026-11-18")
        })
        assertEquals("2026-11-18", com.mj.yata.util.receivedDateForCreation("2026-11-17", false, null, "2026-11-18"))
        assertEquals("2026-10-10", com.mj.yata.util.receivedDateForCreation("2026-10-10", true, "2026-10-01", "2026-11-18"))
        assertNull(com.mj.yata.util.receivedDateForCreation(null, true, "2026-10-01", "2026-11-18"))
    }

    @Test fun sortsOldestFirstWithUnsetDatesLastAndStableTies() {
        val tasks = listOf(task("unset"), task("new", "2026-11-01"),
            task("old", "2025-12-31"), task("same", "2026-11-01"), task("unset2"))
        assertEquals(listOf("old", "new", "same", "unset", "unset2"),
            tasks.sortedByMode(TaskSortMode.RECEIVED_DATE).map { it.id })
        assertEquals(tasks, tasks.sortedByMode(TaskSortMode.MANUAL))
    }

    @Test fun receivedDateSurvivesStorageAndCanBeCleared() {
        val original = task("task", "2026-10-30")
        assertEquals(original, original.toEntity().toDomain(emptyList(), emptyList()))
        assertNull(original.copy(receivedDate = null).toEntity().toDomain(emptyList(), emptyList()).receivedDate)
        assertEquals("2026-12-01", original.due)
    }
}
