package com.sky22333.skyadb.ui.device

import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.RebootMode
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold

@RunWith(AndroidJUnit4::class)
class DeviceHeaderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun tapActiveWindowText(text: String) {
        val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInWindow
        tapScreen(bounds.center.x, bounds.center.y)
    }

    private fun tapScreen(x: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true))
            event.recycle()
        }
        compose.waitForIdle()
    }

    @Test fun nameShowsInfoAndStatusOffersReboot() = runBlocking {
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
        var refreshes = 0
        var reboot: RebootMode? = null
        instrumentation.runOnMainSync {
            requireNotNull(activity).setContent {
                AdbManagerTheme {
                    Scaffold {
                        DeviceContent(
                            uiState = DeviceUiState(deviceName = "Test device", connectionState = ConnectionState.Connected, sessionKind = AdbSessionKind.UsbAdb),
                            onAppsClick = {}, onLocalAppsClick = {}, onInstallClick = {}, onDownloadClick = {}, onFilesClick = {},
                            onScreenshotClick = {}, onShellClick = {}, onRemoteClick = {}, onMirrorClick = {}, onLogsClick = {},
                            onRefreshClick = { refreshes++ }, onReboot = { reboot = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Test device").performClick()
        compose.onNodeWithText(appString(R.string.section_system_info)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.action_confirm)).performClick()
        compose.onNodeWithText(appString(ConnectionState.Connected.labelRes)).performClick()
        compose.onNodeWithText(appString(R.string.fastboot_reboot_recovery)).performClick()
        compose.waitForIdle()
        assertEquals(1, refreshes)
        assertEquals(RebootMode.Recovery, reboot)
    }
    @Test fun fastbootDeviceShowsDistinctActions() = runBlocking {
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
        var chosen: FastbootPanel? = null
        instrumentation.runOnMainSync {
            requireNotNull(activity).setContent {
                AdbManagerTheme {
                    Scaffold {
                        DeviceContent(
                            uiState = DeviceUiState(deviceName = "Fastboot device", connectionState = ConnectionState.Connected, sessionKind = AdbSessionKind.UsbFastboot),
                            onAppsClick = {}, onLocalAppsClick = {}, onInstallClick = {}, onDownloadClick = {}, onFilesClick = {},
                            onScreenshotClick = {}, onShellClick = {}, onRemoteClick = {}, onMirrorClick = {}, onLogsClick = {},
                            onRefreshClick = {}, onReboot = {}, onFastbootAction = { chosen = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(appString(R.string.fb_flash)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.fb_boot)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.fb_archive)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.fb_slot)).assertIsDisplayed()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(com.sky22333.skyadb.AppServices.context.cacheDir, "fastboot-layout-test.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText(appString(R.string.fb_archive)).performClick()
        compose.waitForIdle()
        assertEquals(FastbootPanel.Archive, chosen)
    }
    @Test fun fastbootDialogsOpenAndAllowSlotSelection() = runBlocking {
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
        instrumentation.runOnMainSync {
            requireNotNull(activity).setContent {
                AdbManagerTheme {
                    Scaffold {
                        var panel by remember { mutableStateOf<FastbootPanel?>(null) }
                        DeviceContent(
                            uiState = DeviceUiState(deviceName = "Fastboot device", connectionState = ConnectionState.Connected, sessionKind = AdbSessionKind.UsbFastboot),
                            onAppsClick = {}, onLocalAppsClick = {}, onInstallClick = {}, onDownloadClick = {}, onFilesClick = {},
                            onScreenshotClick = {}, onShellClick = {}, onRemoteClick = {}, onMirrorClick = {}, onLogsClick = {},
                            onRefreshClick = {}, onReboot = {}, onFastbootAction = { panel = it },
                        )
                        FastbootActions(panel = panel, currentSlot = "a", onClosed = { panel = null }, onRefresh = {})
                    }
                }
            }
        }
        compose.onNodeWithText(appString(R.string.fb_flash)).performClick()
        compose.onNodeWithText(appString(R.string.fb_choose_image)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.fb_select_slot)).performClick()
        compose.onNodeWithText("B").assertIsDisplayed()
        tapActiveWindowText("B")
        compose.onNodeWithText(appString(R.string.fb_slot_default)).assertDoesNotExist()
        compose.onNodeWithText("B").assertIsDisplayed()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(com.sky22333.skyadb.AppServices.context.cacheDir, "fastboot-dialog-test.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText(appString(R.string.fb_done)).performClick()
        compose.onNodeWithText(appString(R.string.fb_slot)).performClick()
        compose.onNodeWithText(appString(R.string.fb_select_slot)).performClick()
        val menuItem = compose.onNodeWithText("B").fetchSemanticsNode().boundsInWindow
        tapScreen(instrumentation.targetContext.resources.displayMetrics.widthPixels * 0.1f, menuItem.center.y)
        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithText(appString(R.string.fb_select_slot)).performClick()
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithText(appString(R.string.fb_select_slot)).performClick()
        tapActiveWindowText("B")
        compose.onNodeWithText(appString(R.string.fb_start)).performClick()
        compose.onNodeWithText(appString(R.string.fb_slot_confirm, "B")).assertIsDisplayed()
        tapActiveWindowText(appString(R.string.action_cancel))
        compose.onNodeWithText(appString(R.string.fb_slot_confirm, "B")).assertDoesNotExist()
        compose.onNodeWithText(appString(R.string.fb_done)).performClick()

        val zip = File.createTempFile("popup-test", ".zip", instrumentation.targetContext.cacheDir)
        try {
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("images/boot.img"))
                output.write(byteArrayOf(1, 2, 3))
                output.closeEntry()
            }
            val model = FastbootActionsViewModel()
            instrumentation.runOnMainSync {
                requireNotNull(activity).setContent {
                    AdbManagerTheme { Scaffold { FastbootActions(panel = FastbootPanel.Archive, currentSlot = "a", onClosed = {}, onRefresh = {}, model = model) } }
                }
            }
            compose.waitUntil(5_000) { model.uiState.value.panel == FastbootPanel.Archive }
            withContext(Dispatchers.Main) { model.select(Uri.fromFile(zip)) }
            compose.waitUntil(10_000) { model.uiState.value.entries.isNotEmpty() }
            compose.onNodeWithText(appString(R.string.fb_open)).performClick()
            tapActiveWindowText(appString(R.string.fb_boot))
            compose.waitUntil(10_000) { model.uiState.value.panel == FastbootPanel.Boot }
            compose.onNodeWithText(appString(R.string.fb_choose_image)).assertIsDisplayed()
            withContext(Dispatchers.Main) { model.close() }
        } finally { zip.delete() }
        Unit
    }
}
