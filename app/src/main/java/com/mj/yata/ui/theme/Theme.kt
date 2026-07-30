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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
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
 * LAB lightness a color may be moved by, per 1.0 of chroma factor above 1x, when its own lightness
 * leaves no room to get more colorful.
 */
private const val CHROMA_LIGHTNESS_BUDGET = 13.0

/** How close to the requested chroma counts as reaching it, in LAB units — 8-bit round-trip noise. */
private const val CHROMA_TOLERANCE = 1.0

/**
 * Chroma below which a color counts as having no hue of its own to scale.
 *
 * Comfortably above conversion noise rather than at it: pure white comes back from LAB as
 * C = 0.012 pointing an arbitrary direction, and an 8-bit grey like AMOLED's `#0F0F0F` is no
 * better. Treating that as a hue and scaling it turned white cards blue and AMOLED containers
 * purple. Well under any deliberate surface tint, which starts around C 3.
 */
private const val NEUTRAL_CHROMA = 1.0

/** LAB lightness a surface may be darkened by, at most, to find room for the tint it was asked for. */
private const val TINT_LIGHTNESS_BUDGET = 8.0

/** Headroom considered enough to tint at, so lightness is only spent when there is really none. */
private const val TINT_MIN_HEADROOM = 8.0

/** This color's LAB chroma, its perceptual distance from grey. */
private fun Color.chroma(): Double {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    return hypot(lab[1], lab[2])
}

/** This color's hue as a unit vector in the LAB a/b plane, or null if it is neutral grey. */
private fun Color.chromaDirection(): Pair<Double, Double>? {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    return if (chroma < NEUTRAL_CHROMA) null else lab[1] / chroma to lab[2] / chroma
}

private fun labColor(l: Double, unitA: Double, unitB: Double, chroma: Double): Color =
    Color(androidx.core.graphics.ColorUtils.LABToColor(l, unitA * chroma, unitB * chroma))

/**
 * The most chroma sRGB can actually show at lightness [l] along one hue.
 *
 * Everything here is built on this rather than on multiply-and-let-it-clip, because clipping is
 * not a gentle no-op: it pins a channel and drags the other two with it, so the color that lands
 * on screen is both a different hue and a different lightness from the one asked for. That is
 * precisely what made Clean look like the odd stop out — Clean is exact, while Rich and Deep were
 * both clamped onto the same gamut wall at a lightness 3 LAB units brighter than the surface they
 * were supposed to be tinting.
 *
 * Binary search over a round-trip through sRGB, rather than a hand-rolled gamut boundary: the
 * conversion is the same one the result will go through, so the answer is exact by construction,
 * and 8-bit quantisation is the only error term ([CHROMA_TOLERANCE]). It also answers ~0 at L 0,
 * which is what keeps AMOLED black at every stop with no special case.
 *
 * The fit is checked as a distance in the a/b plane and on lightness, not as a chroma magnitude:
 * a clamped color can measure plenty of chroma while sitting at a completely different hue, so
 * comparing magnitudes alone accepts exactly the results this is meant to reject.
 */
private fun maxChromaAt(l: Double, unitA: Double, unitB: Double): Double {
    var lo = 0.0
    var hi = 150.0
    repeat(12) {
        val mid = (lo + hi) / 2
        if (fitsInGamut(l, unitA, unitB, mid)) lo = mid else hi = mid
    }
    return lo
}

/** Whether sRGB can render exactly this lightness, hue and chroma, within [CHROMA_TOLERANCE]. */
private fun fitsInGamut(l: Double, unitA: Double, unitB: Double, chroma: Double): Boolean {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(labColor(l, unitA, unitB, chroma).toArgb(), lab)
    return hypot(lab[1] - unitA * chroma, lab[2] - unitB * chroma) <= CHROMA_TOLERANCE &&
        abs(lab[0] - l) <= CHROMA_TOLERANCE
}

/** Scales a color's chroma toward grey. Always inside the gamut, so lightness and hue are exact. */
private fun Color.scaleChroma(factor: Float): Color {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    if (chroma < NEUTRAL_CHROMA) return this
    return labColor(lab[0], lab[1] / chroma, lab[2] / chroma, chroma * factor)
}

/**
 * Multiplies a color's chroma, moving its lightness toward the hue's more colorful range when sRGB
 * has no room at the lightness it currently sits at.
 *
 * Scaling chroma alone is enough in light mode, where accents sit near L 40 with headroom to
 * spare. It is not enough in dark mode, and that is why the intensity slider still looked inert
 * after the move off HSL: dark accents are M3 tone 80-90, up at the top of the cone where the
 * gamut has almost nothing left. `#FFB4A2` sits at C 32 and cannot exceed C 34 at its own
 * lightness however large the factor. Four LAB units lower the same hue reaches C 42, and sixteen
 * lower, C 71.
 *
 * So lightness is spent, but only as much as needed and only when the request would otherwise be
 * unreachable: the no-shift candidate is tried first and returned whenever it satisfies the
 * target, which is what keeps light mode's behaviour exactly as it was. The budget scales with how
 * far past 1x the stop is, capping POP at roughly 10 LAB units — enough to matter, small enough
 * that every `on*` role stays comfortably legible against its container.
 *
 * Where even the full budget cannot reach the target, the result is the most colorful *in-gamut*
 * point found rather than an out-of-range request left for the conversion to clamp, so the hue
 * asked for is always the hue delivered.
 */
private fun Color.boostChroma(factor: Float): Color {
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    if (chroma < NEUTRAL_CHROMA) return this
    val target = chroma * factor
    val unitA = lab[1] / chroma
    val unitB = lab[2] / chroma
    val budget = (factor - 1f) * CHROMA_LIGHTNESS_BUDGET
    var bestL = lab[0]
    var bestChroma = chroma
    // Ascending shift, so the smallest lightness change that satisfies the target is the one taken.
    for (step in 0..4) {
        val shift = budget * step / 4.0
        val signs = if (step == 0) intArrayOf(-1) else intArrayOf(-1, 1)
        for (sign in signs) {
            val l = (lab[0] + sign * shift).coerceIn(0.0, 100.0)
            val ceiling = maxChromaAt(l, unitA, unitB)
            if (ceiling >= target - CHROMA_TOLERANCE) return labColor(l, unitA, unitB, target)
            if (ceiling > bestChroma) {
                bestChroma = ceiling
                bestL = l
            }
        }
    }
    return labColor(bestL, unitA, unitB, bestChroma)
}

/**
 * Moves a surface's chroma along a fixed lightness: toward grey for a negative [level], and a
 * fraction of the way to the gamut ceiling for a positive one.
 *
 * A fraction of the available headroom, not a multiple of the current chroma, because a surface
 * has so little headroom to spend. A dark page sits around L 13, where this hue tops out at
 * C 21.5; the old 3x and 6x stops both asked for well past that and were clamped onto the same
 * wall, which is why Rich and Deep looked alike and why the clamped lightness made Clean look like
 * a different color rather than the same surface with the color taken out. Expressed as headroom,
 * every stop is reachable by construction, in any scheme, at any lightness — and lightness never
 * moves, so raising the tint cannot brighten the page.
 *
 * [fallbackDirection] covers surfaces with no hue to scale — `surfaceContainerLowest` is pure
 * white in light mode — which borrow the scheme's own hue so cards move in step with the page
 * behind them. There is no lightness guard on that: at AMOLED's L 0 the ceiling is itself 0, so
 * black stays black however deep the tint.
 *
 * Lightness holds still except in the one case where holding it means no tint at all: a near-white
 * page at L 98 has room for about C 3 and no more, so the top stops would be indistinguishable
 * from the bottom. There a surface may be *darkened* within [TINT_LIGHTNESS_BUDGET] to reach
 * [TINT_MIN_HEADROOM] — a tinted page reading slightly deeper is what a tinted page looks like.
 * Never brightened, which is both the wrong direction for a background and the thing that keeps
 * near-black AMOLED surfaces where they are.
 */
private fun Color.tintBy(level: Float, fallbackDirection: Pair<Double, Double>?): Color {
    if (level == 0f) return this
    val lab = DoubleArray(3)
    androidx.core.graphics.ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    val direction = if (chroma < NEUTRAL_CHROMA) fallbackDirection else lab[1] / chroma to lab[2] / chroma
    val (unitA, unitB) = direction ?: return this
    if (level < 0f) return labColor(lab[0], unitA, unitB, (chroma * (1f + level)).coerceAtLeast(0.0))

    var lightness = lab[0]
    var headroom = maxChromaAt(lightness, unitA, unitB)
    if (headroom < TINT_MIN_HEADROOM) {
        for (step in 1..4) {
            val candidate = (lab[0] - TINT_LIGHTNESS_BUDGET * level * step / 4.0).coerceAtLeast(0.0)
            val candidateHeadroom = maxChromaAt(candidate, unitA, unitB)
            if (candidateHeadroom > headroom) {
                headroom = candidateHeadroom
                lightness = candidate
            }
            if (headroom >= TINT_MIN_HEADROOM) break
        }
    }
    // Never below where the surface already is. A measured ceiling is a lower bound — the search
    // stops within CHROMA_TOLERANCE of the boundary and 8-bit quantisation costs another unit — so
    // a surface sitting on the gamut wall can measure a ceiling under its own chroma. Interpolating
    // toward that runs the ladder backwards, and Rich comes out flatter than Soft: a real dynamic
    // dark surface at C 21.6 measured a ceiling of 20.8.
    val reachable = headroom.coerceAtLeast(chroma)
    return labColor(lightness, unitA, unitB, (chroma + (reachable - chroma) * level).coerceAtLeast(0.0))
}

/**
 * Applies [ColorIntensity] to the accent roles.
 *
 * `error` is deliberately excluded along with every `on*` role: an overdue badge and a delete
 * action have to stay the same reliable red at every setting, and leaving the `on*` tones fixed
 * while their partners move only within [boostChroma]'s bounded lightness budget is what keeps the
 * pairings legible without recomputing them.
 *
 * Muting is plain chroma scaling — toward grey is always inside the gamut. Only the upward stops
 * need [boostChroma].
 */
private fun ColorScheme.withColorIntensity(intensity: com.mj.yata.domain.model.ColorIntensity): ColorScheme {
    val f = intensity.chromaFactor
    if (f == 1f) return this
    val adjust: (Color) -> Color = if (f < 1f) { c -> c.scaleChroma(f) } else { c -> c.boostChroma(f) }
    return copy(
        primary = adjust(primary),
        primaryContainer = adjust(primaryContainer),
        inversePrimary = adjust(inversePrimary),
        // Defaults to primary and is composited over any Surface left at the default `surface`
        // colour with a tonal elevation, so leaving it behind gives those the old accent over the
        // new one. None of this app's own tonalElevation call sites take that path — they all pass
        // an explicit colour — but M3's own components can.
        surfaceTint = adjust(surfaceTint),
        secondary = adjust(secondary),
        secondaryContainer = adjust(secondaryContainer),
        tertiary = adjust(tertiary),
        tertiaryContainer = adjust(tertiaryContainer)
    )
}

/**
 * Applies [BackgroundTint] to the page and container surfaces. Runs after [toAmoled], so the
 * AMOLED palette — already fully desaturated — comes through black at every setting rather than
 * picking up a tint the mode exists to avoid.
 */
private fun ColorScheme.withBackgroundTint(tint: com.mj.yata.domain.model.BackgroundTint): ColorScheme {
    val level = tint.level
    if (level == 0f) return this
    // The hue the neutral tiers borrow. Taken from primary rather than from background, since
    // background is itself one of the colors that can be neutral.
    val hue = primary.chromaDirection()
    return copy(
        background = background.tintBy(level, hue),
        surface = surface.tintBy(level, hue),
        surfaceVariant = surfaceVariant.tintBy(level, hue),
        surfaceDim = surfaceDim.tintBy(level, hue),
        surfaceBright = surfaceBright.tintBy(level, hue),
        surfaceContainerLowest = surfaceContainerLowest.tintBy(level, hue),
        surfaceContainerLow = surfaceContainerLow.tintBy(level, hue),
        surfaceContainer = surfaceContainer.tintBy(level, hue),
        surfaceContainerHigh = surfaceContainerHigh.tintBy(level, hue),
        surfaceContainerHighest = surfaceContainerHighest.tintBy(level, hue)
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
    // Memoised as one unit, keyed on the inputs rather than on the resolved scheme: the intensity
    // and tint transforms binary-search the sRGB gamut per colour role, which at the top stops is
    // a few thousand LAB conversions, and this composable is not skippable — its content lambda
    // captures fresh values every pass, so the body re-ran on any recomposition of the Activity's
    // setContent block (any of ~25 collected preferences, or the app-lock state).
    //
    // The keys have to be the primitives. ColorScheme does not override equals, and
    // dynamic*ColorScheme() hands back a new instance on every call, so keying on the resolved
    // scheme would compare by identity and never hit. `context` is a key because that is what
    // changes when a configuration change makes the wallpaper palette change.
    val colorScheme = remember(
        context, darkTheme, useDynamicColor, supportsDynamicColor,
        amoledMode, customThemeSeedColor, colorIntensity, backgroundTint
    ) {
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
        darkAdjusted
            .withColorIntensity(colorIntensity)
            .withBackgroundTint(backgroundTint)
    }
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
