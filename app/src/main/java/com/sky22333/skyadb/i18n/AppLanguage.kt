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
    ChineseTraditional(languageTag = "zh-TW", labelRes = R.string.language_chinese_traditional),
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
            return fromLanguageTag(locales[0]?.toLanguageTag().orEmpty())
        }

        internal fun fromLanguageTag(tag: String): AppLanguage {
            val locale = java.util.Locale.forLanguageTag(tag)
            return when (locale.language) {
                "zh" -> when {
                    locale.script.equals("Hant", ignoreCase = true) -> ChineseTraditional
                    locale.script.equals("Hans", ignoreCase = true) -> Chinese
                    locale.country in setOf("TW", "HK", "MO") -> ChineseTraditional
                    else -> Chinese
                }
                "en" -> English
                else -> FollowSystem
            }
        }

        /** Apply on the main thread; AppCompat recreates activities automatically. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(language.toLocaleList())
        }
    }
}
