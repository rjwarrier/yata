package com.mj.yata

import com.mj.yata.domain.model.isPostponedLater
import com.mj.yata.domain.model.nextPostponementCount
import com.mj.yata.domain.model.postponementWarningThresholdFor
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

    @Test
    fun normalPriorityUsesConfiguredThreshold() {
        assertEquals(5, postponementWarningThresholdFor("none", 5))
        assertEquals(5, postponementWarningThresholdFor("low", 5))
        assertEquals(3, postponementWarningThresholdFor(null, 3))
    }

    @Test
    fun mediumPrioritySubtractsTwoWithFloorOfOne() {
        assertEquals(3, postponementWarningThresholdFor("med", 5))
        assertEquals(8, postponementWarningThresholdFor("medium", 10))
        assertEquals(1, postponementWarningThresholdFor("med", 3))
        assertEquals(1, postponementWarningThresholdFor("med", 2))
        assertEquals(1, postponementWarningThresholdFor("med", 1))
    }

    @Test
    fun highPriorityThresholdScaling() {
        assertEquals(6, postponementWarningThresholdFor("high", 10))
        assertEquals(1, postponementWarningThresholdFor("high", 5))
        assertEquals(1, postponementWarningThresholdFor("high", 4))
        assertEquals(1, postponementWarningThresholdFor("high", 3))
        assertEquals(0, postponementWarningThresholdFor("high", 2))
        assertEquals(0, postponementWarningThresholdFor("high", 1))
    }

    @Test
    fun normalThresholdIsCoercedBetweenOneAndTen() {
        assertEquals(1, postponementWarningThresholdFor("none", 0))
        assertEquals(1, postponementWarningThresholdFor("none", -5))
        assertEquals(10, postponementWarningThresholdFor("none", 15))
        assertEquals(6, postponementWarningThresholdFor("high", 15)) // 10 - 4 = 6
    }
}

