package com.mj.yata.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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

@Composable
fun YataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
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
    val colorScheme = when {
        useDynamicColor && supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context)
        !useDynamicColor && customThemeSeedColor != null -> colorSchemeFromSeed(customThemeSeedColor, darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
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
