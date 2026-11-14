package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDraftIntegrationTest {
    @Test fun draftRestoresVerbatimAndOversizeInputIsRejected() = runBlocking {
        val store = testAiStore()
        val first = AiViewModel(store)
        withTimeout(10_000) { first.uiState.first { !it.historyLoading } }
        val draft = "\n  first\n\n😀 second  \n"
        first.updateDraft(draft)
        withTimeout(10_000) { while (store.loadHistory().drafts.values.none { it == draft }) delay(100) }
        val restored = AiViewModel(store)
        val loaded = withTimeout(10_000) { restored.uiState.first { !it.historyLoading } }
        assertEquals(draft, loaded.draft)
        assertEquals(32_000, inputLength("a".repeat(31_999) + "😀"))
        val oversized = "a".repeat(32_000) + "😀"
        restored.updateDraft(oversized)
        assertFalse(restored.send(oversized))
        assertEquals(oversized, restored.uiState.value.draft)
        assertNotNull(restored.uiState.value.error)
        restored.updateDraft("")
        withTimeout(10_000) { while (store.loadHistory().drafts.values.any { it.isNotEmpty() }) delay(100) }
    }

    @Test fun explicitExitStartsFreshAndHistoryRetainsItsDraft() = runBlocking {
        val store = testAiStore()
        val conversation = AiConversation("old", "previous", listOf(AiMessage(role = "user", text = "previous")), listOf(Turn.User("previous")))
        store.saveHistory(AiHistory("old", listOf(conversation), mapOf("old" to "unfinished")))
        store.startNewSessionAfterExit()
        val vm = AiViewModel(store)
        val fresh = withTimeout(10_000) { vm.uiState.first { !it.historyLoading } }
        assertTrue(fresh.messages.isEmpty())
        assertEquals("", fresh.draft)
        assertEquals("previous", fresh.conversations.single().title)
        vm.selectConversation("old")
        assertEquals("unfinished", vm.uiState.value.draft)
        assertEquals("previous", vm.uiState.value.messages.single().text)
        vm.clear()
        assertEquals("", vm.uiState.value.draft)
        vm.selectConversation("old")
        assertEquals("unfinished", vm.uiState.value.draft)
    }
}
