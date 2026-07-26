package com.mj.yata.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** Named, curated seed colors offered as ready-made themes when Material You dynamic color is
 * off. Each one is just a seed fed through [colorSchemeFromSeed] — same generator the "Custom
 * color" picker uses — so presets and the custom picker are visually consistent. */
data class ThemePreset(val name: String, val seed: Color)

val THEME_PRESETS = listOf(
    ThemePreset("Ocean", Color(0xFF1565C0)),
    ThemePreset("Forest", Color(0xFF2E7D32)),
    ThemePreset("Sunset", Color(0xFFEF6C00)),
    ThemePreset("Grape", Color(0xFF6A1B9A)),
    ThemePreset("Rose", Color(0xFFC2185B)),
    ThemePreset("Lagoon", Color(0xFF00695C)),
    ThemePreset("Amber", Color(0xFFF9A825)),
    ThemePreset("Crimson", Color(0xFFC62828)),
    ThemePreset("Indigo", Color(0xFF3949AB)),
    ThemePreset("Emerald", Color(0xFF00897B)),
    ThemePreset("Charcoal", Color(0xFF455A64))
)

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

/**
 * Derives a full Material 3 [ColorScheme] from a single seed color — the same idea as Android's
 * wallpaper-based dynamic color, just seeded by a user-picked color instead. Uses HSL tonal steps
 * (a simplified stand-in for Google's HCT algorithm) rather than a color-science dependency:
 * primary keeps the seed's hue at high chroma, secondary reuses the hue at low chroma, tertiary
 * shifts +60° for a complementary accent, and neutrals desaturate almost to gray. Error stays a
 * fixed, standard M3 red regardless of seed — an error state shouldn't change color with the
 * theme.
 */
fun colorSchemeFromSeed(seed: Color, darkTheme: Boolean): ColorScheme {
    val hslOut = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hslOut)
    val hue = hslOut[0]
    val tertiaryHue = hue + 60f

    return if (!darkTheme) {
        lightColorScheme(
            primary = hsl(hue, 0.55f, 0.40f),
            onPrimary = hsl(hue, 0.10f, 1.00f),
            primaryContainer = hsl(hue, 0.55f, 0.90f),
            onPrimaryContainer = hsl(hue, 0.55f, 0.12f),
            secondary = hsl(hue, 0.22f, 0.40f),
            onSecondary = hsl(hue, 0.05f, 1.00f),
            secondaryContainer = hsl(hue, 0.22f, 0.90f),
            onSecondaryContainer = hsl(hue, 0.22f, 0.12f),
            tertiary = hsl(tertiaryHue, 0.35f, 0.40f),
            onTertiary = hsl(tertiaryHue, 0.05f, 1.00f),
            tertiaryContainer = hsl(tertiaryHue, 0.35f, 0.90f),
            onTertiaryContainer = hsl(tertiaryHue, 0.35f, 0.12f),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            background = hsl(hue, 0.06f, 0.985f),
            onBackground = hsl(hue, 0.10f, 0.12f),
            surface = hsl(hue, 0.06f, 0.985f),
            onSurface = hsl(hue, 0.10f, 0.12f),
            surfaceVariant = hsl(hue, 0.10f, 0.90f),
            onSurfaceVariant = hsl(hue, 0.10f, 0.30f),
            outline = hsl(hue, 0.08f, 0.48f),
            outlineVariant = hsl(hue, 0.10f, 0.82f),
            inversePrimary = hsl(hue, 0.45f, 0.80f),
            inverseSurface = hsl(hue, 0.08f, 0.20f),
            inverseOnSurface = hsl(hue, 0.06f, 0.97f),
            surfaceContainerLowest = hsl(hue, 0.06f, 1.00f),
            surfaceContainerLow = hsl(hue, 0.06f, 0.96f),
            surfaceContainer = hsl(hue, 0.07f, 0.94f),
            surfaceContainerHigh = hsl(hue, 0.07f, 0.92f),
            surfaceContainerHighest = hsl(hue, 0.08f, 0.90f)
        )
    } else {
        darkColorScheme(
            primary = hsl(hue, 0.40f, 0.80f),
            onPrimary = hsl(hue, 0.45f, 0.20f),
            primaryContainer = hsl(hue, 0.40f, 0.30f),
            onPrimaryContainer = hsl(hue, 0.40f, 0.90f),
            secondary = hsl(hue, 0.18f, 0.80f),
            onSecondary = hsl(hue, 0.18f, 0.20f),
            secondaryContainer = hsl(hue, 0.18f, 0.30f),
            onSecondaryContainer = hsl(hue, 0.18f, 0.90f),
            tertiary = hsl(tertiaryHue, 0.30f, 0.80f),
            onTertiary = hsl(tertiaryHue, 0.30f, 0.20f),
            tertiaryContainer = hsl(tertiaryHue, 0.30f, 0.30f),
            onTertiaryContainer = hsl(tertiaryHue, 0.30f, 0.90f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = hsl(hue, 0.08f, 0.08f),
            onBackground = hsl(hue, 0.06f, 0.92f),
            surface = hsl(hue, 0.08f, 0.08f),
            onSurface = hsl(hue, 0.06f, 0.92f),
            surfaceVariant = hsl(hue, 0.10f, 0.30f),
            onSurfaceVariant = hsl(hue, 0.10f, 0.80f),
            outline = hsl(hue, 0.08f, 0.58f),
            outlineVariant = hsl(hue, 0.08f, 0.30f),
            inversePrimary = hsl(hue, 0.55f, 0.40f),
            inverseSurface = hsl(hue, 0.06f, 0.92f),
            inverseOnSurface = hsl(hue, 0.08f, 0.15f),
            surfaceContainerLowest = hsl(hue, 0.08f, 0.05f),
            surfaceContainerLow = hsl(hue, 0.08f, 0.10f),
            surfaceContainer = hsl(hue, 0.08f, 0.13f),
            surfaceContainerHigh = hsl(hue, 0.08f, 0.18f),
            surfaceContainerHighest = hsl(hue, 0.08f, 0.23f)
        )
    }
}
