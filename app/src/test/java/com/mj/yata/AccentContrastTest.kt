package com.mj.yata

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mj.yata.ui.theme.ALL_ACCENT_KEYS
import com.mj.yata.ui.theme.AccentInkDark
import com.mj.yata.ui.theme.AccentInkLight
import com.mj.yata.ui.theme.DarkAccents
import com.mj.yata.ui.theme.LightAccents
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContrastTest {

    /** WCAG relative-contrast ratio between two colours, 1.0 (identical) to 21.0 (black on white). */
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun helperAlwaysPicksTheBetterOfTheTwoInks() {
        // The real invariant of onAccentFor: whatever the palette, it must never return the ink
        // with the worse contrast. This holds regardless of any threshold, so it stays true even
        // if the accent colours are redesigned.
        for ((themeName, accents) in listOf("light" to LightAccents, "dark" to DarkAccents)) {
            for (key in ALL_ACCENT_KEYS) {
                val background = accents.getAccent(key)
                val chosen = accents.onAccentFor(background)
                val rejected = if (chosen.luminance() > 0.5f) AccentInkDark else AccentInkLight
                assertTrue(
                    "$themeName/$key picked the worse ink",
                    contrast(background, chosen) >= contrast(background, rejected)
                )
            }
        }
    }

    @Test
    fun everyAccentMeetsLargeTextContrast() {
        // 3:1 is the applicable WCAG bar here, not 4.5:1 — avatar initials are bold at 14sp and
        // up (large text, 1.4.3) and the FAB icons are graphics (non-text contrast, 1.4.11).
        //
        // The distinction matters: accentA, accentL and accentO sit in the mid-luminance band
        // where neither white nor near-black reaches 4.5:1 against them, topping out around
        // 4.2-4.4. That is a property of those three hues, not of the ink chosen for them, and
        // moving it would need the palette to change. They clear 3:1 comfortably.
        val failures = mutableListOf<String>()
        for ((themeName, accents) in listOf("light" to LightAccents, "dark" to DarkAccents)) {
            for (key in ALL_ACCENT_KEYS) {
                val background = accents.getAccent(key)
                val ratio = contrast(background, accents.onAccentFor(background))
                if (ratio < 3.0f) failures += "$themeName/$key = %.2f".format(ratio)
            }
        }
        assertTrue("Accents below the large-text contrast bar: $failures", failures.isEmpty())
    }

    @Test
    fun theBrightAccentsGetDarkInkNotWhite() {
        // The specific complaint: white initials on the yellow and lime accents. Those are the
        // brightest in the palette, so they must come back with the dark ink.
        val bright = listOf("accentC", "accentD", "accentE")
        for (key in bright) {
            val background = LightAccents.getAccent(key)
            val ink = LightAccents.onAccentFor(background)
            assertTrue(
                "$key should take dark ink (luminance ${background.luminance()})",
                ink.luminance() < 0.5f
            )
        }
    }

    @Test
    fun theDeepAccentsStillGetLightInk() {
        // The other direction: the fix must not flip everything to black. The deep blue is the
        // darkest in the light palette and has to keep white.
        val ink = LightAccents.onAccentFor(LightAccents.getAccent("accentK"))
        assertTrue("deep blue should keep light ink", ink.luminance() > 0.5f)
    }

    @Test
    fun customHexColoursAreHandled() {
        // getAccent takes raw hex for custom colours, which no fixed palette ink ever covered.
        val paleYellow = Color(0xFFFFF59D)
        val deepPurple = Color(0xFF311B92)
        assertTrue(LightAccents.onAccentFor(paleYellow).luminance() < 0.5f)
        assertTrue(LightAccents.onAccentFor(deepPurple).luminance() > 0.5f)
        assertTrue(contrast(paleYellow, LightAccents.onAccentFor(paleYellow)) >= 4.5f)
        assertTrue(contrast(deepPurple, LightAccents.onAccentFor(deepPurple)) >= 4.5f)
    }
}
