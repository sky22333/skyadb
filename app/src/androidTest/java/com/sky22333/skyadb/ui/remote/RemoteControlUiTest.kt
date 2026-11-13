package com.sky22333.skyadb.ui.remote

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteControlUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun allKeysFitWithoutScrollingAndDispatchCorrectly() = runBlocking {
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
        val viewport = mutableStateOf(320 to 400)
        val dark = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val clicks = mutableListOf<RemoteKey>()
        instrumentation.runOnMainSync {
            resumed.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.value)) {
                    AdbManagerTheme(darkTheme = dark.value) {
                        Box(
                            Modifier.requiredSize(viewport.value.first.dp, viewport.value.second.dp)
                                .testTag("viewport")
                        ) {
                            RemotePanel(Modifier.matchParentSize(), clicks::add)
                        }
                    }
                }
            }
        }
        try {
            // These are the remaining panel bounds after the title, status and system bars.
            for ((width, height) in listOf(320 to 400, 360 to 480, 384 to 680, 640 to 224)) {
                compose.runOnIdle {
                    viewport.value = width to height
                    dark.value = width == 640
                    fontScale.value = if (width == 360) 1.3f else 1f
                    clicks.clear()
                }
                compose.waitForIdle()
                val bounds = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
                compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
                val keyBounds =
                    RemoteKey.entries.associateWith { key ->
                        val node = compose.onNodeWithContentDescription(appString(key.labelRes))
                        node.assertIsDisplayed()
                        val rect = node.fetchSemanticsNode().boundsInRoot
                        assertTrue(
                            "$key too small in $width x $height: $rect",
                            rect.width >= 47.9f && rect.height >= 47.9f,
                        )
                        assertTrue(
                            "$key outside viewport $bounds: $rect",
                            bounds.contains(rect.topLeft) &&
                                rect.right <= bounds.right &&
                                rect.bottom <= bounds.bottom,
                        )
                        node.performClick()
                        rect
                    }
                assertEquals(RemoteKey.entries.toList(), clicks)
                val center = keyBounds.getValue(RemoteKey.Center).center
                assertTrue(keyBounds.getValue(RemoteKey.Up).center.y < center.y)
                assertTrue(keyBounds.getValue(RemoteKey.Down).center.y > center.y)
                assertTrue(keyBounds.getValue(RemoteKey.Left).center.x < center.x)
                assertTrue(keyBounds.getValue(RemoteKey.Right).center.x > center.x)
                keyBounds.entries.forEach { (key, rect) ->
                    keyBounds
                        .filterKeys { it != key }
                        .forEach { (other, otherRect) ->
                            assertFalse("$key overlaps $other", rect.overlaps(otherRect))
                        }
                }
            }
            // Verify feedback cannot move keys, and the full error remains accessible.
            val status = mutableStateOf<OperationStatus>(OperationStatus.Idle)
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        RemoteControlContent(
                            bottomPadding = 24.dp,
                            uiState = RemoteControlUiState(status.value),
                            onBackClick = {},
                            onKeyClick = {},
                        )
                    }
                }
            }
            compose.waitForIdle()
            val initialBounds =
                RemoteKey.entries.associateWith {
                    compose
                        .onAllNodesWithContentDescription(appString(it.labelRes))
                        .onLast()
                        .fetchSemanticsNode()
                        .boundsInRoot
                }
            for (feedback in
                listOf(
                    OperationStatus.Running("Sending"),
                    OperationStatus.Success("Sent"),
                    OperationStatus.Failed("Failed", "Error details ".repeat(20)),
                )) {
                compose.runOnIdle { status.value = feedback }
                RemoteKey.entries.forEach {
                    assertEquals(
                        initialBounds.getValue(it),
                        compose
                            .onAllNodesWithContentDescription(appString(it.labelRes))
                            .onLast()
                            .fetchSemanticsNode()
                            .boundsInRoot,
                    )
                }
            }
            val error = status.value as OperationStatus.Failed
            compose
                .onNode(hasText(error.suggestion, substring = true) and hasClickAction())
                .performClick()
            compose.onNodeWithText(error.suggestion).assertIsDisplayed()
            compose
                .onAllNodesWithText(appString(com.sky22333.skyadb.R.string.action_confirm))
                .onLast()
                .performClick()
            compose.runOnIdle { status.value = OperationStatus.Idle }
            compose.waitForIdle()
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
