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
    FRENCH("fr", "Français", "French"),
    PORTUGUESE("pt", "Português", "Portuguese"),
    GERMAN("de", "Deutsch", "German"),
    ITALIAN("it", "Italiano", "Italian"),
    DUTCH("nl", "Nederlands", "Dutch"),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian"),
    TURKISH("tr", "Türkçe", "Turkish"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese"),
    TAGALOG("tl", "Tagalog", "Tagalog"),
    POLISH("pl", "Polski", "Polish"),
    SWEDISH("sv", "Svenska", "Swedish"),
    ROMANIAN("ro", "Română", "Romanian"),
    CZECH("cs", "Čeština", "Czech"),
    SWAHILI("sw", "Kiswahili", "Swahili"),
    HINDI("hi", "हिन्दी", "Hindi"),
    BENGALI("bn", "বাংলা", "Bengali"),
    MARATHI("mr", "मराठी", "Marathi"),
    TELUGU("te", "తెలుగు", "Telugu"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "മലയാളം", "Malayalam"),
    PUNJABI("pa", "ਪੰਜਾਬੀ", "Punjabi");

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
                "fr" -> FRENCH
                "pt" -> PORTUGUESE
                "de" -> GERMAN
                "it" -> ITALIAN
                "nl" -> DUTCH
                "id" -> INDONESIAN
                "tr" -> TURKISH
                "vi" -> VIETNAMESE
                "tl" -> TAGALOG
                "pl" -> POLISH
                "sv" -> SWEDISH
                "ro" -> ROMANIAN
                "cs" -> CZECH
                "sw" -> SWAHILI
                "hi" -> HINDI
                "bn" -> BENGALI
                "mr" -> MARATHI
                "te" -> TELUGU
                "ta" -> TAMIL
                "gu" -> GUJARATI
                "kn" -> KANNADA
                "ml" -> MALAYALAM
                "pa" -> PUNJABI
                else -> SYSTEM
            }
        }
    }
}
