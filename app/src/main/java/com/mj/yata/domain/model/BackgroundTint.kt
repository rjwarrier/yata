package com.mj.yata.domain.model

/**
 * How much of the theme's hue carries into the page and card surfaces, from neutral grey to a
 * strongly coloured background.
 *
 * [level] is not a multiplier like [ColorIntensity]'s. Negative interpolates the surface's own
 * chroma toward grey, so [CLEAN] at -1f is neutral grey by definition whatever the source scheme,
 * and [SOFT] at 0f leaves a stock theme exactly as it was. Positive moves a fraction of the way to
 * the most chroma sRGB can show at that lightness.
 *
 * Headroom rather than a multiple because a surface has so little chroma to multiply: a dark page
 * sits near L 13, where a typical hue tops out around C 21, so multiplying its C 5 by 3x and 6x
 * asked for well past the gamut and both stops were clamped onto the same wall — Rich and Deep
 * indistinguishable, and the clamp dragging lightness up with it. As a fraction of headroom every
 * stop is reachable by construction, in light or dark, on Material You or a custom seed.
 *
 * Only chroma moves; lightness is untouched, so raising the tint never makes the background darker
 * or lighter, and it composes with AMOLED for free — at zero lightness the headroom is itself
 * zero, so those surfaces stay black at every stop.
 */
/*
 * Ten stops rather than the original four. The four are unchanged — same names, same levels — so
 * an existing setting keeps rendering exactly as it did; the six additions only fill the gaps
 * between them and extend the top end to the full headroom the maths already supported.
 *
 * The enum's declaration order is the slider's order: the setting reads `.ordinal` and writes back
 * `entries[index]`. It is stored by name, so these may be reordered only if the stored value's
 * meaning is preserved — which is the other reason the original four keep their positions relative
 * to one another.
 */
enum class BackgroundTint(val level: Float) {
    CLEAN(-1f),
    PALE(-0.5f),
    SOFT(0f),
    MILD(0.12f),
    MEDIUM(0.22f),
    RICH(0.35f),
    FULL(0.5f),
    DEEP(0.7f),
    BOLD(0.85f),
    // Full headroom: the most chroma sRGB can show at that surface's lightness. Reachable rather
    // than clamped, which is what the fraction-of-headroom scheme buys over a plain multiplier.
    MAX(1f)
}
