package com.mj.yata.domain.model

/**
 * How saturated the app's accent colors are — primary, secondary, tertiary and their containers.
 *
 * [saturationFactor] multiplies the resolved scheme's existing saturation rather than setting an
 * absolute value, so this composes with Material You and custom seeds instead of overriding what
 * they picked: a muted wallpaper stays comparatively muted at POP, it just moves further along its
 * own range. [NORMAL] is exactly 1f and is therefore a genuine no-op, which is what makes it a safe
 * default for anyone upgrading — nothing about their theme changes until they move the slider.
 */
enum class ColorIntensity(val saturationFactor: Float) {
    MUTED(0.55f),
    NORMAL(1f),
    VIVID(1.45f),
    POP(2f)
}
