package com.sky22333.skyadb.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Environment
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageVisualReviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun capturePages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        val host = InstrumentationRegistry.getArguments().getString("reviewHost")
        val savedRecentDevices = if (host != null) runBlocking { AppServices.recentDeviceStore.devices.first() } else null
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), if (host == null) "visual-review" else "visual-review-connected").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            Thread.sleep(1200)
            File(directory, "$name.png").outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun click(id: Int) {
            val label = appString(id)
            if (compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isEmpty()) {
                compose.onAllNodes(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onLast().performScrollToNode(hasText(label))
            }
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        fun back() { shell("input keyevent KEYCODE_BACK"); compose.waitForIdle() }
        shell("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        try {
            if (host != null) {
                val port = requireNotNull(InstrumentationRegistry.getArguments().getString("reviewPort")).toInt()
                val result = runBlocking { AppServices.adbRepository.connect(host, port) }
                assertTrue("ADB connection failed: $result", result is AdbOperationResult.Success)
            }
            capture("01-home")
            compose.onNodeWithContentDescription(appString(R.string.action_wireless_pairing)).performClick(); capture("02-pairing")
            click(R.string.pairing_guide_title); capture("02-pairing-guide"); click(R.string.pairing_guide_title); back()
            compose.onNodeWithContentDescription(appString(R.string.action_discover_devices)).performClick(); capture("03-discovery"); back()
            compose.onNodeWithText(appString(R.string.nav_settings)).performClick(); capture("04-settings")
            click(R.string.settings_mirror_quality_title); capture("04-settings-options"); back()
            click(R.string.settings_diagnostics_title); capture("05-diagnostics"); back()
            compose.onNodeWithText(appString(R.string.nav_details)).performClick(); capture("06-device")
            if (host != null) {
                compose.onNodeWithText(appString(R.string.connection_state_connected)).performClick()
                compose.onNodeWithText(appString(R.string.section_system_info)).assertIsDisplayed()
                compose.onNodeWithText(appString(R.string.device_info_manufacturer)).assertIsDisplayed()
                capture("06-device-info")
                compose.onNodeWithText(appString(R.string.action_confirm)).performClick()
            }
            listOf(
                R.string.action_apps to "07-apps", R.string.nav_local_apps to "08-localapps",
                R.string.action_install_apk to "09-install", R.string.action_download_online to "10-download",
                R.string.action_file_manage to "11-files", R.string.nav_shell to "12-shell",
                R.string.action_mirror to "13-mirror", R.string.action_remote to "14-remote",
                R.string.action_system_log to "15-logs", R.string.nav_screenshot to "16-screenshot",
            ).forEach { (id, name) ->
                click(id)
                if (host != null) {
                    when (name) {
                        "07-apps", "08-localapps" -> {
                            Thread.sleep(1200)
                            compose.waitUntil(90_000) {
                                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                                    .fetchSemanticsNodes().isEmpty()
                            }
                        }
                        "13-mirror" -> compose.waitUntil(45_000) {
                            compose.onAllNodes(hasText(appString(R.string.mirror_starting))).fetchSemanticsNodes().isEmpty()
                        }
                        "12-shell" -> {
                            capture("12-shell-before-input")
                            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
                            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("getprop ro.build.version.sdk")
                            shell("input keyevent KEYCODE_BACK")
                            click(R.string.action_execute)
                            Thread.sleep(3_000)
                        }
                        "15-logs" -> {
                            compose.onNodeWithContentDescription(appString(R.string.syslog_refresh_desc)).performClick()
                            Thread.sleep(5_000)
                        }
                        "16-screenshot" -> {
                            compose.onAllNodes(hasText(appString(R.string.nav_screenshot))).onLast().performClick()
                            compose.waitUntil(30_000) {
                                compose.onAllNodes(hasContentDescription(appString(R.string.screenshot_preview_desc)))
                                    .fetchSemanticsNodes().isNotEmpty()
                            }
                        }
                    }
                }
                capture(name)
                if (name == "11-files") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        compose.onNodeWithText(appString(R.string.files_permission_required)).assertIsDisplayed()
                    }
                    compose.onNodeWithContentDescription(appString(R.string.files_more_actions)).performClick()
                    click(R.string.files_new_folder_desc)
                    capture("11-files-new-folder")
                    compose.onNodeWithText(appString(R.string.action_cancel)).performClick()
                    Thread.sleep(600)
                    compose.onNodeWithContentDescription(appString(R.string.action_back)).performClick()
                } else {
                    back()
                }
            }
        } finally {
            if (savedRecentDevices != null) runBlocking {
                AppServices.adbRepository.disconnect()
                AppServices.recentDeviceStore.clear()
                savedRecentDevices.asReversed().forEach { AppServices.recentDeviceStore.upsert(it) }
            }
        }
    }
}
