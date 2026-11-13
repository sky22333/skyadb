package com.sky22333.skyadb.ui.apps

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(AndroidJUnit4::class)
class AppActionMenuUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun menuHonorsDisabledItemsAndRunsTheSelectedAction() = exerciseMenu(darkTheme = false)

    @Test fun menuAlsoWorksWithDarkTheme() = exerciseMenu(darkTheme = true)

    private fun exerciseMenu(darkTheme: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n com.sky22333.skyadb/.MainActivity")).use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                }
                if (activity == null) delay(50)
            }
        }
        val resumed = requireNotNull(activity)
        var exported: String? = null
        instrumentation.runOnMainSync {
            resumed.setContent {
                AdbManagerTheme(darkTheme = darkTheme) {
                    Scaffold {
                        AppActionMenu(
                            app = AppInfo("example.app", "Example", isSystem = false, enabled = false),
                            onLaunchClick = {}, onStopClick = {}, onSetEnabledClick = { _, _ -> },
                            onExportClick = { exported = it }, onUninstallClick = {},
                        )
                    }
                }
            }
        }
        try {
            compose.onNodeWithContentDescription(appString(R.string.apps_action_menu_desc)).performClick()
            compose.onNodeWithText(appString(R.string.action_launch)).assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText(appString(R.string.action_export)).performClick()
            compose.waitForIdle()
            assertEquals("example.app", exported)
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
