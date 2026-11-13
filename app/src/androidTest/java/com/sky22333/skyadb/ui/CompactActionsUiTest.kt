package com.sky22333.skyadb.ui

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasNoClickAction
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
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.discovery.AdbMdnsEndpoint
import com.sky22333.skyadb.discovery.AdbMdnsServiceType
import com.sky22333.skyadb.discovery.AdbProbeState
import com.sky22333.skyadb.discovery.AdbScanResult
import com.sky22333.skyadb.discovery.ScanRangeParser
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.localapps.LocalInstalledApp
import com.sky22333.skyadb.ui.discovery.MdnsEndpointRow
import com.sky22333.skyadb.ui.discovery.ScanResultRow
import com.sky22333.skyadb.ui.discovery.ScanControlSection
import com.sky22333.skyadb.ui.discovery.DeviceDiscoveryUiState
import com.sky22333.skyadb.ui.install.InstallApkContent
import com.sky22333.skyadb.ui.install.InstallApkUiState
import com.sky22333.skyadb.ui.download.OnlineDownloadContent
import com.sky22333.skyadb.ui.download.OnlineDownloadUiState
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.ui.home.HomeViewModel
import com.sky22333.skyadb.ui.localapps.LocalAppRow
import com.sky22333.skyadb.ui.pairing.PairingFormSection
import com.sky22333.skyadb.ui.pairing.PairingUiState
import com.sky22333.skyadb.ui.screenshot.ScreenshotContent
import com.sky22333.skyadb.ui.screenshot.ScreenshotUiState
import com.sky22333.skyadb.ui.shell.ShellHistorySection
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
class CompactActionsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun compactActionsKeepTheirCallbacksAndUnsupportedState() = runBlocking {
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
        var installed = ""
        var paired = ""
        var used = ""
        var command = ""
        var cleared = false
        var pairStarted = false
        var scanStarted = false
        var screenshotCaptured = false
        var screenshotSaved = false
        var apkPicked = false
        var apkInstalled = false
        var downloadStarted = false
        var downloadCancelled = false
        var connectedEndpoint = ""
        instrumentation.runOnMainSync {
            resumed.setContent {
                AdbManagerTheme {
                    Scaffold {
                        Column {
                            LocalAppRow(LocalInstalledApp("example.good", "Example", "/a.apk", "1", true, 10), false) { installed = it.packageName }
                            LocalAppRow(LocalInstalledApp("example.split", "Split", "/b.apk", "1", false, 10), false) { installed = it.packageName }
                            MdnsEndpointRow(AdbMdnsEndpoint("Test", "192.168.1.2", 37000, AdbMdnsServiceType.Pairing), { _, _ -> }, { host, port -> paired = "$host:$port" })
                            ScanResultRow(AdbScanResult("192.168.1.3", 5555, AdbProbeState.AdbAvailable, 10)) { host, port -> used = "$host:$port" }
                            ShellHistorySection(listOf("getprop ro.build.version.sdk")) { command = it }
                        }
                    }
                }
            }
        }
        try {
            compose.onNodeWithText(appString(R.string.nav_install)).performClick()
            compose.onNodeWithText(appString(R.string.local_apps_install_unsupported)).assertIsDisplayed().assertHasNoClickAction()
            compose.onNodeWithText(appString(AdbMdnsServiceType.Pairing.actionLabelRes)).performClick()
            compose.onNodeWithText(appString(R.string.action_connect)).performClick()
            compose.onNodeWithText("getprop ro.build.version.sdk").performClick()
            compose.waitForIdle()
            assertEquals("example.good", installed)
            assertEquals("192.168.1.2:37000", paired)
            assertEquals("192.168.1.3:5555", used)
            assertEquals("getprop ro.build.version.sdk", command)
            val repository = object : AdbRepository by AppServices.adbRepository {
                override suspend fun connect(host: String, port: Int): AdbOperationResult<String> {
                    connectedEndpoint = "$host:$port"
                    return AdbOperationResult.Failure("test", "test")
                }
            }
            instrumentation.runOnMainSync {
                HomeViewModel(adbRepository = repository).onDiscoveredEndpointSelected("192.168.1.3", 5555)
            }
            compose.waitUntil(5_000) { connectedEndpoint.isNotEmpty() }
            assertEquals("192.168.1.3:5555", connectedEndpoint)
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        ScreenshotContent(uiState = ScreenshotUiState(), onBackClick = {}, onCaptureClick = {}, onSaveClick = {}, onClearClick = {})
                    }
                }
            }
            compose.onNodeWithContentDescription(appString(R.string.action_clear_preview)).assertHasNoClickAction()
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        ScreenshotContent(uiState = ScreenshotUiState(latestLocalPath = "/missing-preview", saveEnabled = true), onBackClick = {}, onCaptureClick = { screenshotCaptured = true }, onSaveClick = { screenshotSaved = true }, onClearClick = { cleared = true })
                    }
                }
            }
            compose.onNodeWithContentDescription(appString(R.string.action_clear_preview)).performClick()
            compose.onNodeWithText(appString(R.string.nav_screenshot)).performClick()
            compose.onNodeWithText(appString(R.string.action_save)).performClick()
            compose.waitForIdle()
            assertEquals(true, cleared)
            assertEquals(true, screenshotCaptured)
            assertEquals(true, screenshotSaved)
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        InstallApkContent(uiState = InstallApkUiState(installEnabled = true), onBackClick = {}, onPickClick = { apkPicked = true }, onInstallClick = { apkInstalled = true })
                    }
                }
            }
            compose.onNodeWithText(appString(R.string.action_pick_apk)).performClick()
            compose.onNodeWithText(appString(R.string.nav_install)).performClick()
            compose.waitForIdle()
            assertEquals(true, apkPicked)
            assertEquals(true, apkInstalled)
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        OnlineDownloadContent(uiState = OnlineDownloadUiState(actionEnabled = true, operationStatus = OperationStatus.Running("running")), onBackClick = {}, onModeChanged = {}, onUrlChanged = {}, onTargetPathChanged = {}, onStartClick = { downloadStarted = true }, onCancelClick = { downloadCancelled = true })
                    }
                }
            }
            compose.onNodeWithText(appString(R.string.action_start)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.action_cancel)).performClick()
            compose.waitForIdle()
            assertEquals(true, downloadStarted)
            assertEquals(true, downloadCancelled)
            val network = requireNotNull(ScanRangeParser.subnetForHost("192.168.1.2", R.string.discovery_source_current_network))
            instrumentation.runOnMainSync {
                resumed.setContent {
                    AdbManagerTheme {
                        Column {
                            PairingFormSection(PairingUiState(pairEnabled = true), {}, {}, {}, { pairStarted = true }, {})
                            ScanControlSection(DeviceDiscoveryUiState(networks = listOf(network)), { scanStarted = true }, {})
                        }
                    }
                }
            }
            compose.onNodeWithText(appString(R.string.action_start_pairing)).performClick()
            compose.onNodeWithText(appString(R.string.action_start_scan)).performClick()
            compose.onNodeWithText(appString(R.string.action_cancel)).assertIsNotEnabled()
            compose.waitForIdle()
            assertEquals(true, pairStarted)
            assertEquals(true, scanStarted)
            Unit
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
