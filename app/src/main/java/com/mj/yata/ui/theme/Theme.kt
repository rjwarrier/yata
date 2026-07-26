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

@Composable
fun YataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    amoledMode: Boolean = false,
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
    // mode would produce black surfaces under dark-on-light text.
    val colorScheme = if (darkTheme && amoledMode) baseColorScheme.toAmoled() else baseColorScheme
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
