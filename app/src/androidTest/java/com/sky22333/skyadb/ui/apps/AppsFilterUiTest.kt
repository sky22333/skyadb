package com.sky22333.skyadb.ui.apps

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsFilterUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun filtersWrapAndRemainSelectableAtLargeFontSize() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("am start -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                }
                if (activity == null) delay(50)
            }
        }
        val resumed = requireNotNull(activity)
        val apps = List(1000) { AppInfo("app.$it", "App $it", isSystem = it < 750) }
        instrumentation.runOnMainSync {
            resumed.setContent {
                var selected by androidx.compose.runtime.remember { mutableStateOf(AppFilter.All) }
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    AdbManagerTheme {
                        Box(Modifier.width(280.dp).testTag("filters")) {
                            AppFilterRow(selected, apps) { selected = it }
                        }
                    }
                }
            }
        }
        try {
            val bounds = compose.onNodeWithTag("filters").fetchSemanticsNode().boundsInRoot
            for ((filter, count) in listOf(AppFilter.All to 1000, AppFilter.User to 250, AppFilter.System to 750)) {
                val node = compose.onNodeWithText("${appString(filter.labelRes)} $count")
                node.assertIsDisplayed()
                val rect = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$filter extends beyond the filter row: $rect", rect.left >= bounds.left && rect.right <= bounds.right)
                node.performClick()
                node.assertIsSelected()
            }
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
