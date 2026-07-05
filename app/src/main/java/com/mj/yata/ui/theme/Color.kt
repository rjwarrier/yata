package com.mj.yata.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    inverseOnSurface = Color(0xFFFFF8F6)
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
    inverseOnSurface = Color(0xFF191110)
)

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
    fun getAccent(key: String): Color {
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

val LightAccents = YataAccents(
    accentA = Color(0xFFE8886B),
    accentB = Color(0xFF9DAE55),
    accentC = Color(0xFF8C7BE0),
    accentD = Color(0xFFE0A93A),
    accentE = Color(0xFF4FA97D),
    accentF = Color(0xFFDB6FA0),
    accentG = Color(0xFF4A93C7),
    accentH = Color(0xFFC77B4A),
    accentI = Color(0xFF3C8F8A),
    accentJ = Color(0xFFC1454B),
    accentK = Color(0xFF5A64C4),
    accentL = Color(0xFFC9A227),
    accentM = Color(0xFFD97A9C),
    accentN = Color(0xFF6B7A8F),
    accentO = Color(0xFFD2632E),
    accentP = Color(0xFF3FAE8A),
    onAccent = Color(0xFFFFFFFF)
)

val DarkAccents = YataAccents(
    accentA = Color(0xFFE8886B),
    accentB = Color(0xFF9DAE55),
    accentC = Color(0xFFA99BEE),
    accentD = Color(0xFFE0A93A),
    accentE = Color(0xFF5CBB8C),
    accentF = Color(0xFFE080AC),
    accentG = Color(0xFF5CA3D4),
    accentH = Color(0xFFD48C5C),
    accentI = Color(0xFF4FA8A2),
    accentJ = Color(0xFFDE5E64),
    accentK = Color(0xFF7B84DE),
    accentL = Color(0xFFE0BC3E),
    accentM = Color(0xFFE894B2),
    accentN = Color(0xFF8394AA),
    accentO = Color(0xFFE67A44),
    accentP = Color(0xFF52C29E),
    onAccent = Color(0xFF1A1110)
)

val LocalYataAccents = staticCompositionLocalOf { LightAccents }
