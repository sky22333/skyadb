package com.sky22333.skyadb.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun mapsChineseLocalesByScriptAndRegion() {
        for (tag in listOf("zh-TW", "zh-Hant-TW", "zh-Hant", "zh-HK", "zh-MO")) {
            assertEquals(tag, AppLanguage.ChineseTraditional, AppLanguage.fromLanguageTag(tag))
        }
        for (tag in listOf("zh-CN", "zh-Hans", "zh-Hans-TW", "zh")) {
            assertEquals(tag, AppLanguage.Chinese, AppLanguage.fromLanguageTag(tag))
        }
    }

    @Test fun mapsOtherLocalesAndSystemDefault() {
        assertEquals(AppLanguage.English, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.FollowSystem, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.FollowSystem, AppLanguage.fromLanguageTag("fr-FR"))
    }

    @Test fun chineseChoicesUseDistinctLocales() {
        assertEquals("zh-CN", AppLanguage.Chinese.languageTag)
        assertEquals("zh-TW", AppLanguage.ChineseTraditional.languageTag)
        assertEquals(AppLanguage.ChineseTraditional, AppLanguage.fromLanguageTag(AppLanguage.ChineseTraditional.languageTag!!))
    }
}
