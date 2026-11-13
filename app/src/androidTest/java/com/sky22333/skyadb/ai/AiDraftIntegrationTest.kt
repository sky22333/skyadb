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
        withTimeout(10_000) { while (store.loadDraft() != draft) delay(100) }
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
        withTimeout(10_000) { while (store.loadDraft().isNotEmpty()) delay(100) }
    }
}
