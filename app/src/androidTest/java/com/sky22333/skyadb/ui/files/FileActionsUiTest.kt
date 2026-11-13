package com.sky22333.skyadb.ui.files

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.RemoteFileEntry
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
class FileActionsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun longPressShowsActionsForTheActualEntryAndFolderHasNoTransfer() = runBlocking {
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
        val file = RemoteFileEntry("test-file.txt", "/sdcard/Download/test-file.txt", false, 42)
        val folder = RemoteFileEntry("test-folder", "/sdcard/Download/test-folder", true, 0)
        var renamed: RemoteFileEntry? = null
        var newFolder = false
        var batchTransfer = false
        instrumentation.runOnMainSync {
            resumed.setContent {
                AdbManagerTheme {
                    Scaffold {
                        FileManagerContent(
                            uiState = FileTransferUiState(local = FilePaneState("/sdcard/Download", listOf(file, folder)), selectedPaths = setOf(file.path)),
                            onBackClick = {}, onRefreshClick = {}, onGoUpPane = {}, onSyncClick = {},
                            onTransferClick = { batchTransfer = true }, onCancelTransfer = {}, onNewFolderClick = { newFolder = true },
                            onRenameEntry = { _, entry -> renamed = entry }, onTransferEntry = { _, _ -> }, onDeleteSelected = {},
                            onPathClick = {}, onActivatePane = {}, onOpenEntry = { _, _ -> }, onSelectEntry = { _, _ -> },
                            onDeleteEntry = { _, _ -> }, onCancelDelete = {}, onConfirmDelete = {},
                            onDismissNewFolder = {}, onCreateFolder = {}, onDismissRename = {}, onRenameInputChanged = {},
                            onConfirmRename = {}, onDismissJump = {}, onJumpInputChanged = {}, onConfirmJump = {}, onRequestStoragePermission = {},
                        )
                    }
                }
            }
        }
        try {
            compose.onNodeWithText(file.name).performTouchInput { longClick() }
            compose.onNodeWithText(appString(R.string.files_copy_path)).assertIsDisplayed()
            compose.onNodeWithText(appString(R.string.files_details)).assertIsDisplayed().performClick()
            compose.onNodeWithText(file.path, substring = true).assertIsDisplayed()
            compose.onNodeWithText(appString(R.string.action_confirm)).performClick()
            compose.onNodeWithText(file.name).performTouchInput { longClick() }
            compose.onNodeWithText(appString(R.string.action_rename)).performClick()
            compose.waitForIdle()
            assertEquals(file, renamed)
            compose.onNodeWithText(folder.name).performTouchInput { longClick() }
            compose.onNodeWithText(appString(R.string.files_transfer_to_other_desc)).assertDoesNotExist()
            compose.onNodeWithText(appString(R.string.files_copy_name)).assertIsDisplayed()
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            compose.waitForIdle()
            compose.onNodeWithContentDescription(appString(R.string.files_more_actions)).performClick()
            compose.onNodeWithText(appString(R.string.files_new_folder_desc)).performClick()
            compose.waitForIdle()
            assertEquals(true, newFolder)
            compose.onNodeWithContentDescription(appString(R.string.files_more_actions)).performClick()
            compose.onNodeWithText(appString(R.string.files_transfer_selected)).performClick()
            compose.waitForIdle()
            assertEquals(true, batchTransfer)
        } finally {
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
