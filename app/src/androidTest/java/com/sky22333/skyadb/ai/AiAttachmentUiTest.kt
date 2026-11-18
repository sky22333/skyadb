package com.sky22333.skyadb.ai

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.ui.ai.AiScreen
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiAttachmentUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun attachmentsCanBeRemovedIndividually() {
        val directory = File(AppServices.context.cacheDir, "attachments-ui-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val first = File(directory, "first.txt").apply { writeText("first") }
        val second = File(directory, "second.txt").apply { writeText("second") }
        val vm = AiViewModel(testAiStore())
        try {
            runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } }; vm.attachFiles(Uri.fromFile(first), Uri.fromFile(second)) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.navigationBarsPadding()) { AiScreen(vm) } } } }
                compose.waitForIdle()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val screenshots = File(instrumentation.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
                File(screenshots, "attachments.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset").close()
                compose.onNodeWithContentDescription(appString(R.string.ai_remove_attachment_named, "first.txt")).assertIsDisplayed().performClick()
                compose.waitUntil(10000) { vm.uiState.value.attachments.size == 1 }
                assertEquals("second.txt", vm.uiState.value.attachments.single().name)
                compose.onNodeWithContentDescription(appString(R.string.ai_remove_attachment_named, "second.txt")).assertIsDisplayed()
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${instrumentation.targetContext.packageName}")).use { input ->
                    File(screenshots, "attachments-frames.txt").outputStream().use { input.copyTo(it) }
                }
            }
        } finally { first.delete(); second.delete(); directory.delete() }
    }
}
