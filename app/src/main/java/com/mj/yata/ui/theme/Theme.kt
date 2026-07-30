package com.mj.yata.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.hypot

val LocalEnhancedM3Theming = staticCompositionLocalOf { false }
val LocalFloatingBottomNav = staticCompositionLocalOf { false }
val LocalCompletionSoundEnabled = staticCompositionLocalOf { true }
val LocalBottomNavLabelsEnabled = staticCompositionLocalOf { true }

/**
 * Collapses a dark scheme's near-black surfaces to true black for OLED panels, where a #000000
 * pixel is switched off entirely rather than dimly lit — that's the power saving, and it also
 * makes the app disappear into the bezel.
 *
 * Applied as a transform over whatever dark scheme is already resolved, rather than as a separate
 * hand-written palette, so it composes with Material You dynamic color and custom seed colors
 * instead of overriding them. Only backgrounds and container tiers are flattened; primary,
 * secondary, error and every `on*` role keep their contrast pairing untouched.
 *
 * The container tiers stay very slightly separated (0xFF0A0A0A / 0xFF141414) instead of all going
 * to pure black, so cards, sheets and the nav bar remain distinguishable from the page behind
 * them — flattening everything to #000000 makes elevation vanish and the UI read as one void.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    surfaceBright = Color(0xFF1A1A1A),
    surfaceDim = Color.Black
)

/** Raises a color's HSL lightness, leaving hue and saturation exactly as they were. */
private fun Color.lightenBy(amount: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = (hsl[2] + amount).coerceIn(0f, 1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * Lifts a dark scheme's backgrounds and container tiers a few points off black. M3's dark
 * background sits around 7% lightness, which on a phone at night reads as a hole rather than a
 * surface; this makes the ordinary dark theme a comfortable dark instead of a near-black one, and
 * leaves AMOLED as the option for people who actually want the panel switched off.
 *
 * Like [toAmoled] this is a transform over the resolved scheme rather than a hand-written palette,
 * so it applies equally to Material You, a custom seed, and [DarkColors]. Lightness is raised in
 * HSL so a wallpaper-derived hue survives intact — blending toward white would have washed a
 * saturated dynamic scheme out. Every tier moves by the same amount, so the separation between
 * page, card and sheet is unchanged, and no `on*` role is touched.
 */
private fun ColorScheme.toSofterDark(amount: Float = 0.045f): ColorScheme = copy(
    background = background.lightenBy(amount),
    surface = surface.lightenBy(amount),
    surfaceDim = surfaceDim.lightenBy(amount),
    surfaceBright = surfaceBright.lightenBy(amount),
    surfaceContainerLowest = surfaceContainerLowest.lightenBy(amount),
    surfaceContainerLow = surfaceContainerLow.lightenBy(amount),
    surfaceContainer = surfaceContainer.lightenBy(amount),
    surfaceContainerHigh = surfaceContainerHigh.lightenBy(amount),
    surfaceContainerHighest = surfaceContainerHighest.lightenBy(amount)
)

/**
 * The chroma given to a color that has none of its own, at a factor of 1. Small enough that [SOFT]
 * leaves a white card looking white, large enough that [DEEP] reaches an obvious tint.
 */
private const val NEUTRAL_TINT_CHROMA = 2.0

/**
 * Lightness above which a hueless color is allowed to borrow one. Below it the LAB inverse's
 * linear branch turns a borrowed hue into a large relative shift — pushing AMOLED's #000000 to a
 * visible #190000 at the top stop — and a dark neutral surface is neutral on purpose anyway.
 */
private const val NEUTRAL_TINT_MIN_LIGHTNESS = 50.0

/**
 * Scales a color's LAB chroma — its perceptual distance from grey — holding lightness and hue.
 *
 * Deliberately not HSL saturation, which was the original implementation and the reason the
 * sliders did nothing: for the near-white surfaces M3 uses (`#FFF8F6`, `#FFDAD1`, `#FFB4A2`) HSL
 * reports S = 1.0 already, since *any* color with a channel at 0xFF is fully saturated by that
 * definition. Every upward factor clamped straight back to 1.0. Chroma is the quantity the
 * sliders actually claim to move and it stays meaningful at both ends of the lightness range.
 *
 * [fallbackDirection] covers colors carrying no hue at all — `surfaceContainerLowest` is pure
 * white in light mode — where scaling zero chroma stays zero however large the factor. Those tiers
 * borrow the scheme's own hue instead, so cards move in step with the page behind them. It applies
 * only above [NEUTRAL_TINT_MIN_LIGHTNESS], which is what keeps AMOLED black at every stop.
 */
private fun Color.scaleChroma(factor: Float, fallbackDirection: Pair<Double, Double>? = null): Color {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    val borrowsHue = chroma < NEUTRAL_TINT_CHROMA &&
        lab[0] >= NEUTRAL_TINT_MIN_LIGHTNESS &&
        fallbackDirection != null
    val (a, b) = if (borrowsHue && fallbackDirection != null) {
        fallbackDirection.first * NEUTRAL_TINT_CHROMA * factor to
            fallbackDirection.second * NEUTRAL_TINT_CHROMA * factor
    } else {
        lab[1] * factor to lab[2] * factor
    }
    return Color(androidx.core.graphics.ColorUtils.LABToColor(lab[0], a, b))
}

/** This color's hue as a unit vector in the LAB a/b plane, or null if it is neutral grey. */
private fun Color.chromaDirection(): Pair<Double, Double>? {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    return if (chroma < 0.001) null else lab[1] / chroma to lab[2] / chroma
}

/**
 * Applies [ColorIntensity] to the accent roles.
 *
 * `error` is deliberately excluded along with every `on*` role: an overdue badge and a delete
 * action have to stay the same reliable red at every setting, and holding lightness fixed while
 * only saturation moves is what keeps the `on*` pairings legible without recomputing them.
 */
private fun ColorScheme.withColorIntensity(intensity: com.mj.yata.domain.model.ColorIntensity): ColorScheme {
    val f = intensity.chromaFactor
    if (f == 1f) return this
    return copy(
        primary = primary.scaleChroma(f),
        primaryContainer = primaryContainer.scaleChroma(f),
        inversePrimary = inversePrimary.scaleChroma(f),
        secondary = secondary.scaleChroma(f),
        secondaryContainer = secondaryContainer.scaleChroma(f),
        tertiary = tertiary.scaleChroma(f),
        tertiaryContainer = tertiaryContainer.scaleChroma(f)
    )
}

/**
 * Applies [BackgroundTint] to the page and container surfaces. Runs after [toAmoled], so the
 * AMOLED palette — already fully desaturated — comes through black at every setting rather than
 * picking up a tint the mode exists to avoid.
 */
private fun ColorScheme.withBackgroundTint(tint: com.mj.yata.domain.model.BackgroundTint): ColorScheme {
    val f = tint.chromaFactor
    if (f == 1f) return this
    // The hue the neutral tiers borrow. Taken from primary rather than from background, since
    // background is itself one of the colors that can be neutral.
    val hue = primary.chromaDirection()
    return copy(
        background = background.scaleChroma(f, hue),
        surface = surface.scaleChroma(f, hue),
        surfaceVariant = surfaceVariant.scaleChroma(f, hue),
        surfaceDim = surfaceDim.scaleChroma(f, hue),
        surfaceBright = surfaceBright.scaleChroma(f, hue),
        surfaceContainerLowest = surfaceContainerLowest.scaleChroma(f, hue),
        surfaceContainerLow = surfaceContainerLow.scaleChroma(f, hue),
        surfaceContainer = surfaceContainer.scaleChroma(f, hue),
        surfaceContainerHigh = surfaceContainerHigh.scaleChroma(f, hue),
        surfaceContainerHighest = surfaceContainerHighest.scaleChroma(f, hue)
    )
}

@Composable
fun YataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    colorIntensity: com.mj.yata.domain.model.ColorIntensity = com.mj.yata.domain.model.ColorIntensity.NORMAL,
    backgroundTint: com.mj.yata.domain.model.BackgroundTint = com.mj.yata.domain.model.BackgroundTint.SOFT,
    customThemeSeedColor: Color? = null,
    appFont: com.mj.yata.domain.model.AppFont = com.mj.yata.domain.model.AppFont.INTER,
    enhancedM3Theming: Boolean = false,
    floatingBottomNav: Boolean = false,
    completionSound: Boolean = true,
    bottomNavLabelsEnabled: Boolean = true,
    edgeToEdge: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColorScheme = when {
        useDynamicColor && supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context)
        !useDynamicColor && customThemeSeedColor != null -> colorSchemeFromSeed(customThemeSeedColor, darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    // AMOLED is a dark-theme modifier, never a theme of its own — leaving it applied in light
    // mode would produce black surfaces under dark-on-light text. The two dark treatments are
    // mutually exclusive: AMOLED exists to reach true black, so softening it would defeat it.
    val darkAdjusted = when {
        darkTheme && amoledMode -> baseColorScheme.toAmoled()
        darkTheme -> baseColorScheme.toSofterDark()
        else -> baseColorScheme
    }
    val colorScheme = darkAdjusted
        .withColorIntensity(colorIntensity)
        .withBackgroundTint(backgroundTint)
    // Entity accent swatches (task/tag/person colors) stay fixed regardless of dynamic color —
    // only MaterialTheme.colorScheme (chrome, surfaces, primary/secondary) follows the wallpaper.
    val accents = if (darkTheme) DarkAccents else LightAccents

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (edgeToEdge) {
                // True edge-to-edge: the window paints nothing of its own behind the system bars,
                // so app content (which already reserves space via statusBarsPadding/
                // navigationBarsPadding) shows straight through — no separately-colored strip
                // behind the floating bottom nav pill or under the status bar.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT < 35) {
                    // On API 35+ (targetSdk 35) edge-to-edge is enforced and these setters are
                    // ignored; the transparent look is the platform default there.
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            } else if (Build.VERSION.SDK_INT < 35) {
                // Non-edge activities (widget config, Tasker config): opaque bars matching the
                // theme. On API 35+ these are no-ops — those screens use Scaffold/TopAppBar,
                // which extend their own container color behind the bars instead.
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalYataAccents provides accents,
        LocalEnhancedM3Theming provides enhancedM3Theming,
        LocalFloatingBottomNav provides floatingBottomNav,
        LocalCompletionSoundEnabled provides completionSound,
        LocalBottomNavLabelsEnabled provides bottomNavLabelsEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = createTypography(typographyFamilyFor(appFont)),
            shapes = Shapes,
            content = content
        )
    }
}
