package com.mj.yata

import com.mj.yata.domain.model.isPostponedLater
import com.mj.yata.domain.model.nextPostponementCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPostponementTest {
    @Test
    fun movingDueDateLaterCountsAsPostponement() {
        assertTrue(isPostponedLater("2026-08-18", "2026-08-19"))
        assertEquals(3, nextPostponementCount("2026-08-18", "2026-08-19", 2))
    }

    @Test
    fun firstDueDateAndEarlierMovesDoNotCount() {
        assertFalse(isPostponedLater(null, "2026-08-19"))
        assertFalse(isPostponedLater("2026-08-19", "2026-08-18"))
        assertFalse(isPostponedLater("2026-08-19", null))
        assertEquals(2, nextPostponementCount("2026-08-19", "2026-08-18", 2))
    }
}
