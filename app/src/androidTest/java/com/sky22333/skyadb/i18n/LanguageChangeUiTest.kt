package com.sky22333.skyadb.i18n

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ui.settings.SettingsScreen
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageChangeUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun languageChangeUpdatesResourcesWithoutRecreatingActivity() {
        val previous = AppCompatDelegate.getApplicationLocales()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity: MainActivity? = null
            try {
                scenario.onActivity {
                    activity = it
                    it.setContent { AdbManagerTheme { SettingsScreen() } }
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
                }
                compose.onNodeWithText("应用语言").assertIsDisplayed()
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-TW")) }
                compose.onNodeWithText("應用程式語言").assertIsDisplayed()
                compose.onNodeWithText("繁體中文").assertIsDisplayed()
                scenario.onActivity { assertSame(activity, it) }
                assertEquals("設定", appString(R.string.settings_title))
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                compose.onNodeWithText("App language").assertIsDisplayed()
                scenario.onActivity { assertSame(activity, it) }
                assertEquals("Settings", appString(R.string.settings_title))
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(previous) }
            }
        }
    }
}
