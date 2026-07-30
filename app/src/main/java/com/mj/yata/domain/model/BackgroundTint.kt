package com.mj.yata.domain.model

/**
 * How much of the theme's hue carries into the page and card surfaces, from neutral grey to a
 * strongly coloured background.
 *
 * Like [ColorIntensity] this scales the resolved scheme's own LAB chroma rather than setting an
 * absolute one, so [SOFT] at 1f leaves a stock theme exactly as it was. [CLEAN] is the one absolute
 * end of the range — 0f is neutral grey by definition, whatever the source scheme.
 *
 * The upward stops are larger than [ColorIntensity]'s because they start from almost nothing: an
 * M3 surface sits a chroma unit or three off grey, so tripling it is still a pale tint, and there
 * is no gamut ceiling to run into at that lightness.
 *
 * Only chroma moves; lightness is untouched, so raising the tint never makes the background darker
 * or lighter, and it composes with AMOLED for free — those surfaces are black, and at zero
 * lightness the chroma terms round back to black whatever the factor.
 */
enum class BackgroundTint(val chromaFactor: Float) {
    CLEAN(0f),
    SOFT(1f),
    RICH(3f),
    DEEP(6f)
}
