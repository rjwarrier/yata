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
 * The upward stops are modest numbers because chroma is perceptual: a factor that would have been
 * unremarkable as an HSL multiplier pushes a mid-lightness accent straight out of sRGB, and the
 * clipping that follows distorts the hue rather than intensifying it.
 */
enum class ColorIntensity(val chromaFactor: Float) {
    MUTED(0.5f),
    NORMAL(1f),
    VIVID(1.3f),
    POP(1.6f)
}
