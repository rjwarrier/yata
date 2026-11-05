package com.mj.yata

import com.mj.yata.domain.usecase.duplicateTaskTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDuplicationTest {
    @Test
    fun duplicateTaskTitleAppendsDuplicate() {
        assertEquals("Buy groceries Duplicate", duplicateTaskTitle("Buy groceries"))
        assertEquals("Plan sprint Duplicate", duplicateTaskTitle("Plan sprint"))
    }

    @Test
    fun emptyOrBlankTitleBecomesDuplicate() {
        assertEquals("Duplicate", duplicateTaskTitle(""))
        assertEquals("Duplicate", duplicateTaskTitle("   "))
    }

    @Test
    fun whitespaceTrimmedBeforeAppending() {
        assertEquals("Review PR Duplicate", duplicateTaskTitle("  Review PR  "))
    }

    @Test fun nestedChecklistsHaveIndependentOwnershipAcrossCopies() {
        val original = listOf(
            com.mj.yata.domain.model.Subtask("parent", "Parent", true),
            com.mj.yata.domain.model.Subtask("child", "Child", false, "parent"),
            com.mj.yata.domain.model.Subtask("grandchild", "Grandchild", true, "child")
        )
        val first = com.mj.yata.domain.usecase.copySubtasksWithFreshIds(original)
        val second = com.mj.yata.domain.usecase.copySubtasksWithFreshIds(original)
        org.junit.Assert.assertEquals(9, (original + first + second).map { it.id }.toSet().size)
        for (copy in listOf(first, second)) {
            assertEquals(copy[0].id, copy[1].parentSubtaskId)
            assertEquals(copy[1].id, copy[2].parentSubtaskId)
            assertEquals(original.map { it.title to it.done }, copy.map { it.title to it.done })
        }
        assertEquals("parent", original[1].parentSubtaskId)
        assertEquals("child", original[2].parentSubtaskId)
    }
}
