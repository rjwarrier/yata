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
}
