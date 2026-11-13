package com.sky22333.skyadb.ai

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.MainActivity
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMarkdownUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun rendersMarkdownOnAndroid() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                AdbManagerTheme {
                    EnrichedMarkdownText(markdown = "# Result\n\n**Status:** OK\n\n```sh\necho test\n```")
                }
            } }
            compose.waitForIdle()
            compose.onRoot().assertExists()
        }
    }
}
