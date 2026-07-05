package com.mj.yata.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * The OS status bar is a separate system-drawn strip with its own fixed color (set once,
 * theme-wide, in [YataTheme]) — it doesn't automatically pick up a screen's own tinted header,
 * which is why a colored header otherwise looks like it has a mismatched black bar above it.
 * Call this from a screen with a tinted header to extend that tint up into the status bar;
 * it reverts to the theme's normal background color when the screen leaves composition.
 *
 * [color] must be fully opaque. `window.statusBarColor` composites over the OS's own backdrop,
 * not over the app's background — passing a translucent tint directly would blend against the
 * wrong surface and render a different shade than the header actually shows on screen. Composite
 * your tint over `MaterialTheme.colorScheme.background` yourself first (`tint.compositeOver(bg)`).
 */
@Composable
fun StatusBarColor(color: Color) {
    val view = LocalView.current
    val defaultColor = MaterialTheme.colorScheme.background
    if (view.isInEditMode) return
    DisposableEffect(color) {
        val window = (view.context as Activity).window
        window.statusBarColor = color.toArgb()
        onDispose {
            window.statusBarColor = defaultColor.toArgb()
        }
    }
}
