package com.sky22333.skyadb.ui.logs

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLogUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun filtersRemainUsableAndLogsAreVisible() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("am start -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull()
                }
                if (activity == null) delay(50)
            }
        }
        val resumed = requireNotNull(activity)
        instrumentation.runOnMainSync {
            resumed.setContent {
                var state by remember { mutableStateOf(SystemLogUiState(logs = listOf("01 I first log", "02 E second log", "03 W third log"))) }
                AdbManagerTheme {
                    SystemLogContent(
                        uiState = state, onBackClick = {}, onRefreshClick = {}, onClearClick = {}, onCopyClick = {},
                        onQueryChanged = { state = state.copy(query = it) },
                        onLevelSelected = { state = state.copy(level = it) },
                        onLineLimitSelected = { state = state.copy(lineLimit = it) },
                    )
                }
            }
        }
        try {
            compose.onNodeWithText("01 I first log").assertIsDisplayed()
            compose.onNodeWithText("100").performClick().assertIsSelected()
            compose.onNodeWithText("E").performClick().assertIsSelected()
            compose.onNodeWithText("02 E second log").assertIsDisplayed()
            compose.onNodeWithText("01 I first log").assertDoesNotExist()
            compose.onNode(hasSetTextAction()).performTextInput("missing")
            compose.onNodeWithText("02 E second log").assertDoesNotExist()
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
