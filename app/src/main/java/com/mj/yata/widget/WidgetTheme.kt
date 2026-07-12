package com.mj.yata.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.color.ColorProviders
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.ui.theme.DarkAccents
import com.mj.yata.ui.theme.DarkColors
import com.mj.yata.ui.theme.LightAccents
import com.mj.yata.ui.theme.LightColors
import com.mj.yata.ui.theme.YataAccents
import com.mj.yata.util.isDarkNow
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalTime

data class WidgetTheme(val colorScheme: ColorScheme, val accents: YataAccents, val widgetBackground: Color) {
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
        ThemeMode.SCHEDULED -> isDarkNow(
            LocalTime.of(userPreferences.themeScheduleStartHourFlow.first(), userPreferences.themeScheduleStartMinuteFlow.first()),
            LocalTime.of(userPreferences.themeScheduleEndHourFlow.first(), userPreferences.themeScheduleEndMinuteFlow.first())
        )
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

    // Widget card fill: the system's *accent* tonal palette (system_accent1_800/100), not
    // Material3's neutral surface/surfaceContainer tones. Home-screen widgets from other apps
    // (e.g. Yaja) resolve their card background this same way, which is why a plain M3
    // colorScheme.surface reads as flat near-black next to them — surface/surfaceContainer are
    // deliberately desaturated, while system_accent1_* carries the wallpaper's actual hue.
    val widgetBackground = if (dynamicEnabled && supportsDynamic) {
        val res = if (isDark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100
        Color(context.getColor(res))
    } else if (isDark) {
        DarkColors.surfaceContainer
    } else {
        LightColors.surfaceContainer
    }

    return WidgetTheme(colorScheme, accents, widgetBackground)
}

/** Notification-only accent color: prefers the primary color actually last rendered by the
 * foreground Activity (cached via `UserPreferences.setLastPrimaryArgb`), falling back to
 * [resolveWidgetTheme]'s own resolution only if the app has never been opened. Background
 * receiver/worker contexts have been observed to resolve `dynamicDarkColorScheme`/
 * `dynamicLightColorScheme` differently than the live Activity does, so re-deriving it fresh
 * here isn't reliable — reading back what was actually shown on screen is. */
suspend fun resolveNotificationAccentColor(context: Context): Int {
    val userPreferences = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).userPreferences()
    return userPreferences.lastPrimaryArgbFlow.first()
        ?: resolveWidgetTheme(context).colorScheme.primary.toArgb()
}
