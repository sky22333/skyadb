package com.sky22333.skyadb.scrcpy

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.ui.mirror.MirrorScreen
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MirrorControlsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun keyboardCanBeShownAndClosedIndependently() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "am start -n com.sky22333.skyadb/.MainActivity"
                )
            )
            .use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync {
                    activity =
                        ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>()
                            .firstOrNull()
                }
                if (activity == null) delay(50)
            }
        }
        val resumed = requireNotNull(activity)
        instrumentation.runOnMainSync {
            resumed.setContent { AdbManagerTheme { MirrorScreen(onBackClick = {}) } }
        }
        val toggle = appString(R.string.mirror_toggle_keyboard)
        val hide = appString(R.string.mirror_hide_keyboard)
        try {
            compose.onNodeWithContentDescription(hide).assertDoesNotExist()
            compose.onNodeWithContentDescription(toggle).performClick()
            compose.onNodeWithContentDescription(hide).assertExists().performClick()
            compose.onNodeWithContentDescription(hide).assertDoesNotExist()
            compose.onNodeWithContentDescription(toggle).performClick()
            compose.onNodeWithContentDescription(hide).assertExists()
            compose.onNodeWithContentDescription(toggle).performClick()
            compose.onNodeWithContentDescription(hide).assertDoesNotExist()
            compose.onNodeWithContentDescription(appString(R.string.mirror_recent_tasks_desc)).assertExists()
            compose.onNodeWithContentDescription(appString(R.string.mirror_hide_controls_desc)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.mirror_recent_tasks_desc)).assertDoesNotExist()
            compose.onNodeWithContentDescription(appString(R.string.mirror_show_controls_desc)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.mirror_recent_tasks_desc)).assertExists()
            Unit
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
