package com.sky22333.skyadb.ui

import android.os.ParcelFileDescriptor
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.R
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.scrcpy.MirrorQualityPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun settingEditorValidatesAndCancelDoesNotSave() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val original = runBlocking { AppServices.settingsStore.settings.first().defaultPort }
        val replacement = if (original == 5556) 5557 else 5556
        try {
            compose.onNodeWithText(appString(R.string.nav_settings)).performClick()
            compose.onNodeWithText(appString(R.string.settings_default_port_title)).performClick()
            Thread.sleep(500)
            compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("0")
            compose.onNodeWithText(appString(R.string.action_confirm)).assertIsNotEnabled()
            compose.onNodeWithText(appString(R.string.action_cancel)).performClick()
            Thread.sleep(500)
            org.junit.Assert.assertEquals(original, runBlocking { AppServices.settingsStore.settings.first().defaultPort })
            compose.onNodeWithText(appString(R.string.settings_default_port_title)).performClick()
            Thread.sleep(500)
            compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement(replacement.toString())
            compose.onNodeWithText(appString(R.string.action_confirm)).performClick()
            Thread.sleep(500)
            compose.waitUntil(5_000) { runBlocking { AppServices.settingsStore.settings.first().defaultPort } == replacement }
            listOf(
                R.string.settings_connection_timeout_title to "301",
                R.string.settings_command_timeout_title to "0",
                R.string.settings_scan_ranges_title to "invalid",
            ).forEach { (title, invalidValue) ->
                compose.onNodeWithText(appString(title)).performClick()
                Thread.sleep(500)
                compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement(invalidValue)
                compose.onNodeWithText(appString(R.string.action_confirm)).assertIsNotEnabled()
                compose.onNodeWithText(appString(R.string.action_cancel)).performClick()
                Thread.sleep(500)
            }
        } finally {
            runBlocking { AppServices.settingsStore.updateDefaultPort(original) }
        }
    }

    @Test
    fun endpointControlsConnectionAction() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        val connect = compose.onNodeWithText(appString(R.string.action_connect))
        field.performTextReplacement("192.168.1.20:70000")
        connect.assertIsNotEnabled()
        field.performTextReplacement("192.168.1.20:5555")
        connect.assertHasClickAction()
        field.performTextReplacement("192.168.1.20")
        connect.assertHasClickAction()
        field.performTextReplacement("192.168.1.20:")
        connect.assertIsNotEnabled()
    }

    @Test
    fun tabsAnimateAndRapidSwitchingSettlesOnLastTab() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText(appString(R.string.nav_settings)).performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithText(appString(R.string.home_manual_connect_title)).assertExists()
            compose.onNodeWithText(appString(R.string.settings_default_port_title)).assertExists()
            compose.onNodeWithText(appString(R.string.nav_details)).performClick()
            compose.onNodeWithText(appString(R.string.nav_devices)).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(appString(R.string.home_manual_connect_title)).assertIsDisplayed()
            compose.onNodeWithText(appString(R.string.settings_default_port_title)).assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun dropdownPreferenceChangesAndRestoresSelection() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val original = runBlocking { AppServices.settingsStore.settings.first().mirrorQualityPreset }
        val next = MirrorQualityPreset.entries.first { it != original }
        try {
            compose.onNodeWithText(appString(R.string.nav_settings)).performClick()
            val title = appString(R.string.settings_mirror_quality_title)
            if (compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isEmpty()) {
                compose.onAllNodes(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .onLast().performScrollToNode(hasText(title))
            }
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText(appString(next.labelRes)).performClick()
            compose.waitUntil(5_000) { runBlocking { AppServices.settingsStore.settings.first().mirrorQualityPreset } == next }
            compose.waitUntil(5_000) { compose.onAllNodes(hasText(appString(original.labelRes))).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(title).assertIsDisplayed().performClick()
            compose.onNodeWithText(appString(original.labelRes)).assertIsDisplayed()
            shell("input keyevent KEYCODE_BACK")
            compose.onNodeWithText(title).assertIsDisplayed()
        } finally {
            runBlocking { AppServices.settingsStore.updateMirrorQualityPreset(original) }
        }
    }

    @Test
    fun switchingTabsPreservesInputAndPairingCanReturn() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("192.168.1.99")
        shell("input keyevent KEYCODE_BACK")
        compose.onNodeWithText(appString(R.string.nav_settings)).performClick()
        compose.onNodeWithText(appString(R.string.settings_default_port_title)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.nav_devices)).performClick()
        compose.onNodeWithText("192.168.1.99").assertIsDisplayed()
        compose.onNodeWithContentDescription(appString(R.string.action_wireless_pairing)).performClick()
        compose.onNodeWithText(appString(R.string.pairing_info_title)).assertIsDisplayed()
        shell("input keyevent KEYCODE_BACK")
        compose.onNodeWithText("192.168.1.99").assertIsDisplayed()
    }

    @Test
    fun filePathTitleStillOpensJumpDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")
        ).use { it.readBytes() }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(appString(R.string.nav_details)).performClick()
        compose.onNodeWithText(appString(R.string.action_file_manage)).performScrollTo().performClick()
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val shortPath = if (path.length <= 32) path else "…" + path.takeLast(31)
        compose.onNodeWithText(shortPath).performTouchInput { click() }
        compose.onNodeWithText(appString(R.string.files_jump_local_title)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(shortPath).assertIsDisplayed()
    }
}
