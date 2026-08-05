package com.mj.yata.domain.model

data class SavedThemePreset(
    val name: String,
    val themeMode: ThemeMode,
    val seedColorArgb: Int?,
    val colorIntensity: ColorIntensity,
    val backgroundTint: BackgroundTint,
    val appFont: AppFont,
    val dynamicColorEnabled: Boolean
) {
    fun encode(): String = listOf(
        name.trim().replace("|", " "),
        themeMode.name,
        seedColorArgb?.toString() ?: "",
        colorIntensity.name,
        backgroundTint.name,
        appFont.name,
        dynamicColorEnabled.toString()
    ).joinToString("|")

    companion object {
        fun decode(raw: String): SavedThemePreset? {
            val parts = raw.split("|")
            if (parts.size < 7) return null
            val name = parts[0].trim()
            if (name.isBlank()) return null
            return SavedThemePreset(
                name = name,
                themeMode = ThemeMode.entries.firstOrNull { it.name == parts[1] } ?: ThemeMode.SYSTEM,
                seedColorArgb = parts[2].toIntOrNull(),
                colorIntensity = ColorIntensity.entries.firstOrNull { it.name == parts[3] } ?: ColorIntensity.NORMAL,
                backgroundTint = BackgroundTint.entries.firstOrNull { it.name == parts[4] } ?: BackgroundTint.SOFT,
                appFont = AppFont.entries.firstOrNull { it.name == parts[5] } ?: AppFont.INTER,
                dynamicColorEnabled = parts[6].toBooleanStrictOrNull() ?: true
            )
        }
    }
}
