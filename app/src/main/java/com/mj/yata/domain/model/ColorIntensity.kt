package com.mj.yata.domain.model

/**
 * How saturated the app's accent colors are — primary, secondary, tertiary and their containers.
 *
 * [chromaFactor] multiplies the resolved scheme's existing LAB chroma rather than setting an
 * absolute value, so this composes with Material You and custom seeds instead of overriding what
 * they picked: a muted wallpaper stays comparatively muted at POP, it just moves further along its
 * own range. [NORMAL] is exactly 1f and is therefore a genuine no-op, which is what makes it a safe
 * default for anyone upgrading — nothing about their theme changes until they move the slider.
 *
 * The upward stops stay modest numbers because chroma is perceptual — these are multiples of a
 * distance from grey, not of an HSL percentage, so 1.8x is already a large move.
 */
enum class ColorIntensity(val chromaFactor: Float) {
    MUTED(0.5f),
    NORMAL(1f),
    VIVID(1.35f),
    POP(1.8f)
}
