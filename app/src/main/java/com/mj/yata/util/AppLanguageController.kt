package com.mj.yata.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mj.yata.domain.model.AppLanguage

object AppLanguageController {
    fun current(): AppLanguage =
        AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    fun apply(language: AppLanguage) {
        val locales = language.tag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()

        AppCompatDelegate.setApplicationLocales(locales)
    }
}
