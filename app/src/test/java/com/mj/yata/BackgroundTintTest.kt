package com.mj.yata

import com.mj.yata.domain.model.BackgroundTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTintTest {

    @Test
    fun tenStopsInSliderOrder() {
        assertEquals(10, BackgroundTint.entries.size)
    }

    @Test
    fun levelsRiseMonotonically() {
        // The enum's declaration order is the slider's order, so a stop that dipped below the one
        // before it would make dragging right lower the tint at that point.
        val levels = BackgroundTint.entries.map { it.level }
        assertEquals(levels.sorted(), levels)
        assertEquals(levels.distinct(), levels) // no two stops that look identical
    }

    @Test
    fun levelsStayWithinTheRangeTheTintMathAccepts() {
        // Below -1 would ask for negative chroma; above +1 asks past the sRGB gamut edge, where
        // the value would clamp and two stops would render the same.
        BackgroundTint.entries.forEach {
            assertTrue("${it.name} = ${it.level}", it.level in -1f..1f)
        }
    }

    @Test
    fun theOriginalFourStopsAreUnchanged() {
        // These are persisted by name, and an existing install must keep rendering exactly as it
        // did — the six additions fill gaps, they do not renumber what was already chosen.
        assertEquals(-1f, BackgroundTint.CLEAN.level)
        assertEquals(0f, BackgroundTint.SOFT.level)
        assertEquals(0.35f, BackgroundTint.RICH.level)
        assertEquals(0.7f, BackgroundTint.DEEP.level)
    }

    @Test
    fun softRemainsTheNoOpStop() {
        // 0f is what makes SOFT a genuine no-op and therefore a safe default: the tint pass
        // returns the scheme untouched at exactly this value.
        assertEquals(0f, BackgroundTint.SOFT.level)
    }
}
