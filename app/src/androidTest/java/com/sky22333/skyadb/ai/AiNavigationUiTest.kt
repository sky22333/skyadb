package com.sky22333.skyadb.ai

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.activity.compose.setContent
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.ui.ai.AiScreen
import com.sky22333.skyadb.ui.AdbManagerApp
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.concurrent.thread
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiNavigationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun aiTabOpensAndSettingsCanBeDismissed() {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("am start -f 0x10008000 -n com.sky22333.skyadb/.MainActivity")).use { it.readBytes() }
        compose.waitUntil(10000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(appString(R.string.ai_nav))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(appString(R.string.ai_nav)).performClick()
        compose.onNodeWithText(appString(R.string.ai_title)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("draft")
        compose.onNodeWithContentDescription(appString(R.string.ai_config)).performClick()
        compose.onNodeWithText(appString(R.string.ai_url)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.action_cancel)).performScrollTo().performClick()
        compose.onNodeWithText(appString(R.string.ai_title)).assertIsDisplayed()
        compose.onNodeWithText("draft").assertIsDisplayed()
    }

    @Test fun sendChangesToStopAndCancellationRestoresSend() {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try { server.accept().use { socket -> socket.getInputStream().readBytes() } } catch (_: java.io.IOException) { }
        }
        val vm = AiViewModel(testAiStore())
        val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
        runBlocking {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            vm.saveConfig(config)
            withTimeout(10000) { vm.uiState.first { it.config == config } }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { AdbManagerTheme { AiScreen(vm) } } }
                compose.onNode(hasSetTextAction()).performTextInput("Hello")
                compose.onNodeWithContentDescription(appString(R.string.ai_send)).performClick()
                compose.waitUntil(10000) { vm.uiState.value.busy }
                compose.onNodeWithContentDescription(appString(R.string.ai_stop)).performClick()
                compose.waitUntil(10000) { !vm.uiState.value.busy }
                compose.onNodeWithContentDescription(appString(R.string.ai_send)).assertExists()
            }
        } finally { server.close(); worker.join(1000) }
    }

    @Test fun longMultilineDraftRemainsEditable() {
        val vm = AiViewModel(testAiStore())
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { AiScreen(vm) } } }
            val input = (1..30).joinToString("\n") { "测试 line $it: " + "abc123".repeat(8) + " 😀" }
            compose.onNode(hasSetTextAction()).performTextReplacement(input)
            compose.waitUntil(10000) { vm.uiState.value.draft == input }
            compose.onNode(hasSetTextAction()).performTextReplacement(input + " end")
            compose.waitUntil(10000) { vm.uiState.value.draft == input + " end" }
            compose.onNodeWithContentDescription(appString(R.string.ai_send)).assertIsDisplayed()
        }
    }

    @Test fun multilineAndOversizeTextRemainsEditable() {
        val vm = AiViewModel(testAiStore())
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { AiScreen(vm) } } }
            val input = "first\n\nsecond 😀\nthird\nfourth\nfifth"
            compose.onNode(hasSetTextAction()).performTextReplacement(input)
            compose.waitUntil(10000) { vm.uiState.value.draft == input }
            val oversized = "a".repeat(AI_INPUT_LIMIT) + "😀"
            compose.onNode(hasSetTextAction()).performTextReplacement(oversized)
            compose.waitUntil(10000) { vm.uiState.value.draft == oversized }
            compose.onNodeWithText(appString(R.string.ai_input_over_limit, AI_INPUT_LIMIT + 1, AI_INPUT_LIMIT)).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextReplacement(input)
            compose.waitUntil(10000) { vm.uiState.value.draft == input }
        }
    }

    @Test fun deletingHistoryRequiresConfirmation() {
        val store = testAiStore()
        val conversation = AiConversation("test-id", "saved chat", listOf(AiMessage(role = "user", text = "saved chat")), listOf(Turn.User("saved chat")))
        store.saveHistory(AiHistory("test-id", listOf(conversation)))
        val vm = AiViewModel(store)
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { AiScreen(vm) } } }
            compose.onNodeWithContentDescription(appString(R.string.ai_history)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.action_delete)).performClick()
            compose.onNodeWithText(appString(R.string.ai_history_delete)).assertIsDisplayed()
            compose.onNodeWithText(appString(R.string.action_cancel)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.action_delete)).performClick()
            compose.onNodeWithText(appString(R.string.action_delete)).performClick()
            compose.waitUntil(10000) { vm.uiState.value.conversations.isEmpty() }
        }
    }

    @Test fun longAssistantReplyStartsAtTheEnd() {
        val store = testAiStore()
        val response = (1..100).joinToString("\n\n") { "Paragraph $it" } + "\n\nEND_OF_RESPONSE"
        val messages = listOf(AiMessage(role = "user", text = "hello"), AiMessage(role = "assistant", text = response))
        store.saveHistory(AiHistory("scroll-test", listOf(AiConversation("scroll-test", "hello", messages, listOf(Turn.User("hello"), Turn.Assistant(response, emptyList()))))))
        val vm = AiViewModel(store)
        var listState: LazyListState? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.fillMaxSize().imePadding()) { val current = rememberLazyListState(); listState = current; AiScreen(vm, listState = current) } } } }
            compose.waitUntil(15000) { !vm.uiState.value.historyLoading }
            compose.waitUntil(15000) { listState?.let { s -> s.layoutInfo.visibleItemsInfo.any { it.index == 1 && it.size > 1000 } && !s.canScrollBackward } == true }
            compose.onNodeWithTag("ai_messages").performTouchInput { swipeDown() }
            compose.waitUntil(10000) { listState?.canScrollBackward == true }
            compose.waitForIdle()
            assertTrue(listState?.canScrollBackward == true)
        }
    }
    @Test fun tabAndSettingsNavigationPreservesChatPosition() {
        val store = testAiStore()
        val response = (1..100).joinToString("\n\n") { "Paragraph $it" }
        val messages = listOf(AiMessage(role = "user", text = "hello"), AiMessage(role = "assistant", text = response))
        store.saveHistory(AiHistory("return-test", listOf(AiConversation("return-test", "hello", messages, listOf(Turn.User("hello"), Turn.Assistant(response, emptyList()))))))
        val vm = AiViewModel(store)
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        var listState: LazyListState? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme {
                val current = rememberLazyListState(); listState = current; AdbManagerApp(vm, current)
            } } }
            compose.onNodeWithContentDescription(appString(R.string.ai_nav)).performClick()
            compose.onNodeWithTag("ai_messages").assertIsDisplayed()
            compose.waitUntil(10000) { listState?.layoutInfo?.totalItemsCount == 3 && listState?.canScrollBackward == false }
            compose.onNodeWithContentDescription(appString(R.string.nav_settings)).performClick()
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithContentDescription(appString(R.string.ai_nav)).performClick()
                repeat(20) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle { assertTrue("Bottom anchor changed during return", listState!!.firstVisibleItemIndex == 0 && listState!!.firstVisibleItemScrollOffset == 0) }
                }
            } finally { compose.mainClock.autoAdvance = true }
            compose.onNodeWithTag("ai_messages").performTouchInput { swipeDown() }
            compose.onNodeWithContentDescription(appString(R.string.ai_back_to_latest)).assertIsDisplayed()
            compose.waitUntil(10000) { listState?.isScrollInProgress == false }
            val position = listState!!.firstVisibleItemIndex to listState!!.firstVisibleItemScrollOffset
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            fun gfx(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
            gfx("dumpsys gfxinfo ${instrumentation.targetContext.packageName} reset")
            compose.onNodeWithContentDescription(appString(R.string.nav_settings)).performClick()
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithContentDescription(appString(R.string.ai_nav)).performClick()
                repeat(20) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.runOnIdle {
                        assertTrue("Position changed during the return transition", position == (listState!!.firstVisibleItemIndex to listState!!.firstVisibleItemScrollOffset))
                    }
                }
            } finally { compose.mainClock.autoAdvance = true }
            compose.onNodeWithContentDescription(appString(R.string.ai_back_to_latest)).assertIsDisplayed()
            assertTrue("Tab return changed the reading position", position == (listState!!.firstVisibleItemIndex to listState!!.firstVisibleItemScrollOffset))
            compose.onNodeWithContentDescription(appString(R.string.ai_config)).performClick()
            compose.onNodeWithText(appString(R.string.ai_url)).assertIsDisplayed()
            compose.onNodeWithText(appString(R.string.action_cancel)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(appString(R.string.ai_back_to_latest)).assertIsDisplayed()
            assertTrue("Settings return changed the reading position", position == (listState!!.firstVisibleItemIndex to listState!!.firstVisibleItemScrollOffset))
            compose.onNodeWithContentDescription(appString(R.string.ai_back_to_latest)).performClick()
            compose.onNodeWithContentDescription(appString(R.string.ai_back_to_latest)).assertDoesNotExist()
            val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
            java.io.File(directory, "navigation-gfxinfo.txt").writeText(gfx("dumpsys gfxinfo ${instrumentation.targetContext.packageName} framestats"))
        }
    }

    @Test fun switchingConversationsStartsAtLatest() {
        val store = testAiStore()
        fun conversation(id: String): AiConversation {
            val response = (1..80).joinToString("\n\n") { "$id paragraph $it" }
            return AiConversation(id, id, listOf(AiMessage(role = "user", text = id), AiMessage(role = "assistant", text = response)), listOf(Turn.User(id), Turn.Assistant(response, emptyList())))
        }
        store.saveHistory(AiHistory("first", listOf(conversation("first"), conversation("second"))))
        val vm = AiViewModel(store)
        var listState: LazyListState? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.fillMaxSize().imePadding()) {
                val current = rememberLazyListState(); listState = current; AiScreen(vm, listState = current)
            } } } }
            compose.waitUntil(15000) { !vm.uiState.value.historyLoading && listState?.layoutInfo?.totalItemsCount == 3 && listState?.canScrollBackward == false }
            compose.onNodeWithTag("ai_messages").performTouchInput { swipeDown() }
            compose.waitUntil(10000) { listState?.canScrollBackward == true }
            compose.onNodeWithContentDescription(appString(R.string.ai_history)).performClick()
            compose.onNodeWithText("second").performClick()
            compose.waitUntil(15000) { vm.uiState.value.messages.firstOrNull()?.text == "second" && listState?.canScrollBackward == false }
        }
    }

}
