package com.mj.yata.domain.model

/**
 * How much of the theme's hue carries into the page and card surfaces, from neutral grey to a
 * strongly coloured background.
 *
 * Like [ColorIntensity] this scales the resolved scheme's own surface saturation rather than
 * setting an absolute one, so [SOFT] at 1f leaves a stock theme exactly as it was. [CLEAN] is the
 * one absolute end of the range — 0f is neutral grey by definition, whatever the source scheme.
 *
 * Only saturation moves; lightness is untouched, so raising the tint never makes the background
 * darker or lighter, and it composes with AMOLED for free — those surfaces are already fully
 * desaturated, so scaling zero saturation keeps them black.
 */
enum class BackgroundTint(val saturationFactor: Float) {
    CLEAN(0f),
    SOFT(1f),
    RICH(1.9f),
    DEEP(3f)
}
