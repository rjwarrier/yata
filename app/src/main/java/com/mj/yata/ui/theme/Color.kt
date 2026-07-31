package com.mj.yata.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LightColors = lightColorScheme(
    primary = Color(0xFF8E4A3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3A0B01),
    secondary = Color(0xFF5D6140),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E6BC),
    onSecondaryContainer = Color(0xFF1B1D04),
    tertiary = Color(0xFF5F5791),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5DEFF),
    onTertiaryContainer = Color(0xFF1B1148),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231916),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231916),
    surfaceVariant = Color(0xFFF0DED8),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    inversePrimary = Color(0xFFFFB4A2),
    inverseSurface = Color(0xFF231916),
    inverseOnSurface = Color(0xFFFFF8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EE),
    surfaceContainer = Color(0xFFFCEAE6),
    surfaceContainerHigh = Color(0xFFF6E4E0),
    surfaceContainerHighest = Color(0xFFF0DED8)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A2),
    onPrimary = Color(0xFF561F11),
    primaryContainer = Color(0xFF723524),
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Color(0xFFC6CA9C),
    onSecondary = Color(0xFF2F3213),
    secondaryContainer = Color(0xFF454929),
    onSecondaryContainer = Color(0xFFE2E6BC),
    tertiary = Color(0xFFC8BFFF),
    onTertiary = Color(0xFF31285F),
    tertiaryContainer = Color(0xFF484078),
    onTertiaryContainer = Color(0xFFE5DEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191110),
    onBackground = Color(0xFFF0DED8),
    surface = Color(0xFF191110),
    onSurface = Color(0xFFF0DED8),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    inversePrimary = Color(0xFF8E4A3B),
    inverseSurface = Color(0xFFF0DED8),
    inverseOnSurface = Color(0xFF191110),
    surfaceContainerLowest = Color(0xFF140D0C),
    surfaceContainerLow = Color(0xFF1F1716),
    surfaceContainer = Color(0xFF231B1A),
    surfaceContainerHigh = Color(0xFF2E2524),
    surfaceContainerHighest = Color(0xFF392F2E)
)

/** The two inks [YataAccents.onAccentFor] chooses between. Not pure black — the dark one matches
 * the palette's own near-black surface, so an icon on a pale accent still reads as part of the
 * theme rather than as a hole punched in it. */
val AccentInkLight = Color(0xFFFFFFFF)
val AccentInkDark = Color(0xFF1A1110)

/**
 * WCAG relative-contrast ratio, 1.0 for identical colours up to 21.0 for black on white.
 *
 * Both inks are measured for real rather than either being assumed to be pure black or white —
 * [AccentInkDark] is a near-black with a small but non-zero luminance, and treating it as 0
 * overstated its contrast enough to pick it over white on one of the mid-tone accents.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

@Immutable
data class YataAccents(
    val accentA: Color,
    val accentB: Color,
    val accentC: Color,
    val accentD: Color,
    val accentE: Color,
    val accentF: Color,
    val accentG: Color,
    val accentH: Color,
    val accentI: Color,
    val accentJ: Color,
    val accentK: Color,
    val accentL: Color,
    val accentM: Color,
    val accentN: Color,
    val accentO: Color,
    val accentP: Color,
    val onAccent: Color
) {
    /**
     * A readable ink for text or an icon drawn *on* [background].
     *
     * [onAccent] is one colour for the whole palette, which can't be right for all sixteen: the
     * accents span from a deep blue to a bright yellow, and white set on the yellow one is barely
     * visible. This picks per colour instead, by measuring rather than guessing — whichever of the
     * light and dark inks has the better contrast ratio against that particular background wins.
     *
     * It also covers the custom accent colours [getAccent] accepts as raw hex, which no fixed
     * palette ink could ever have accounted for.
     */
    fun onAccentFor(background: Color): Color {
        val withLight = contrastRatio(background, AccentInkLight)
        val withDark = contrastRatio(background, AccentInkDark)
        return if (withDark >= withLight) AccentInkDark else AccentInkLight
    }

    fun onAccentFor(key: String): Color = onAccentFor(getAccent(key))

    fun getAccent(key: String): Color {
        if (key.startsWith("#")) {
            return try {
                Color(android.graphics.Color.parseColor(key))
            } catch (e: IllegalArgumentException) {
                accentA
            }
        }
        return when (key) {
            "accentA" -> accentA
            "accentB" -> accentB
            "accentC" -> accentC
            "accentD" -> accentD
            "accentE" -> accentE
            "accentF" -> accentF
            "accentG" -> accentG
            "accentH" -> accentH
            "accentI" -> accentI
            "accentJ" -> accentJ
            "accentK" -> accentK
            "accentL" -> accentL
            "accentM" -> accentM
            "accentN" -> accentN
            "accentO" -> accentO
            "accentP" -> accentP
            else -> accentA
        }
    }
}

/** All accent keys in picker display order. */
val ALL_ACCENT_KEYS = listOf(
    "accentA", "accentB", "accentC", "accentD", "accentE", "accentF", "accentG", "accentH",
    "accentI", "accentJ", "accentK", "accentL", "accentM", "accentN", "accentO", "accentP"
)

// Hues are spaced evenly around the wheel (22.5deg apart, alternating a richer/softer
// saturation-lightness band) instead of the old ad hoc picks, which bunched 3-4 accents
// into the same narrow hue range (e.g. three near-identical oranges) and made them hard
// to tell apart at a glance in small dots/chips.
val LightAccents = YataAccents(
    accentA = Color(0xFFD54F34),
    accentB = Color(0xFFCC9D66),
    accentC = Color(0xFFD5C834),
    accentD = Color(0xFFAECC66),
    accentE = Color(0xFF6AD534),
    accentF = Color(0xFF66CC6A),
    accentG = Color(0xFF34D577),
    accentH = Color(0xFF66CCB7),
    accentI = Color(0xFF34BAD5),
    accentJ = Color(0xFF6695CC),
    accentK = Color(0xFF3441D5),
    accentL = Color(0xFF8466CC),
    accentM = Color(0xFFA034D5),
    accentN = Color(0xFFCC66C8),
    accentO = Color(0xFFD53492),
    accentP = Color(0xFFCC667B),
    onAccent = Color(0xFFFFFFFF)
)

val DarkAccents = YataAccents(
    accentA = Color(0xFFDC8674),
    accentB = Color(0xFFD8BA97),
    accentC = Color(0xFFDCD474),
    accentD = Color(0xFFC5D897),
    accentE = Color(0xFF97DC74),
    accentF = Color(0xFF97D899),
    accentG = Color(0xFF74DCA0),
    accentH = Color(0xFF97D8CB),
    accentI = Color(0xFF74CBDC),
    accentJ = Color(0xFF97B5D8),
    accentK = Color(0xFF747DDC),
    accentL = Color(0xFFAA97D8),
    accentM = Color(0xFFBA74DC),
    accentN = Color(0xFFD897D6),
    accentO = Color(0xFFDC74B1),
    accentP = Color(0xFFD897A4),
    onAccent = Color(0xFF1A1110)
)

val LocalYataAccents = staticCompositionLocalOf { LightAccents }
