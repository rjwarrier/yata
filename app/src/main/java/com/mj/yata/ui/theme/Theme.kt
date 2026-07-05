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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun YataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    appFont: com.mj.yata.domain.model.AppFont = com.mj.yata.domain.model.AppFont.INTER,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor && supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context)
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
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalYataAccents provides accents
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = createTypography(typographyFamilyFor(appFont)),
            shapes = Shapes,
            content = content
        )
    }
}
