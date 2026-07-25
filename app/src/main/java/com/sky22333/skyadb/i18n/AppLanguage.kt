package com.sky22333.skyadb.i18n

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sky22333.skyadb.R

/**
 * Per-app language preference backed by [AppCompatDelegate.setApplicationLocales].
 * Empty locale list means follow the system language.
 */
enum class AppLanguage(
    val languageTag: String?,
    @param:StringRes val labelRes: Int,
) {
    FollowSystem(languageTag = null, labelRes = R.string.language_follow_system),
    Chinese(languageTag = "zh-CN", labelRes = R.string.language_chinese),
    English(languageTag = "en", labelRes = R.string.language_english),
    ;

    fun toLocaleList(): LocaleListCompat {
        return if (languageTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
    }

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return FollowSystem
            val tag = locales[0]?.toLanguageTag().orEmpty()
            return when {
                tag.startsWith("zh", ignoreCase = true) -> Chinese
                tag.startsWith("en", ignoreCase = true) -> English
                else -> FollowSystem
            }
        }

        /** Apply on the main thread; AppCompat recreates activities automatically. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(language.toLocaleList())
        }
    }
}
