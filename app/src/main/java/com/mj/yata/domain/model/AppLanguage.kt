package com.mj.yata.domain.model

/**
 * Mirrors the locale resource folders shipped in the APK. To add a language, add values-<tag>
 * strings, then add one enum entry here with the same BCP-47 tag.
 */
enum class AppLanguage(
    val tag: String?,
    val nativeName: String,
    val englishName: String
) {
    SYSTEM(null, "System default", "System default"),
    ENGLISH("en", "English", "English"),
    SPANISH("es", "Español", "Spanish"),
    PORTUGUESE("pt", "Português", "Portuguese"),
    FRENCH("fr", "Français", "French");

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            val normalized = tag
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.substringBefore(',')
                ?.substringBefore('-')
                ?.lowercase()

            return when (normalized) {
                "en" -> ENGLISH
                "es" -> SPANISH
                "pt" -> PORTUGUESE
                "fr" -> FRENCH
                else -> SYSTEM
            }
        }
    }
}
