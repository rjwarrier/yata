package com.mj.yata.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.glance.color.ColorProviders
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.ui.theme.DarkAccents
import com.mj.yata.ui.theme.DarkColors
import com.mj.yata.ui.theme.LightAccents
import com.mj.yata.ui.theme.LightColors
import com.mj.yata.ui.theme.YataAccents
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

data class WidgetTheme(val colorScheme: ColorScheme, val accents: YataAccents) {
    /** Glance wants a light+dark pair, but we've already resolved which one actually applies
     * (respecting the user's theme-mode setting, not just raw system config) — so both sides of
     * the pair are the same resolved scheme, and Glance's own light/dark switch is a no-op. */
    val glanceColors: ColorProviders get() = androidx.glance.material3.ColorProviders(light = colorScheme, dark = colorScheme)
}

/** Resolves the exact same effective theme YataTheme computes for the main app — same dark/light
 * decision (respecting the user's ThemeMode, not just the system), and the same wallpaper-based
 * dynamic color when the user has that setting on. Widgets were previously always using the
 * fixed custom palette regardless of this setting, which is why they didn't match a device with
 * dynamic color enabled. */
suspend fun resolveWidgetTheme(context: Context): WidgetTheme {
    val userPreferences = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).userPreferences()
    val themeMode = userPreferences.themeModeFlow.first()
    val dynamicEnabled = userPreferences.dynamicColorEnabledFlow.first()
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicEnabled && supportsDynamic && isDark -> dynamicDarkColorScheme(context)
        dynamicEnabled && supportsDynamic -> dynamicLightColorScheme(context)
        isDark -> DarkColors
        else -> LightColors
    }
    // Entity accent swatches stay fixed regardless of dynamic color, same as the main app.
    val accents = if (isDark) DarkAccents else LightAccents
    return WidgetTheme(colorScheme, accents)
}
