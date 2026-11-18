package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiInterruptedStreamTest {
    @Test fun runningToolRestoresAsInterrupted() = runBlocking {
        val store = testAiStore()
        val call = ToolCall("call", "agent_shell", "{}")
        val messages = listOf(AiMessage(role = "tool", text = "agent_shell\n{}", toolStatus = AiToolStatus.Running))
        store.saveHistory(AiHistory("interrupted", listOf(AiConversation("interrupted", "test", messages, listOf(Turn.User("test"), Turn.Assistant("", listOf(call)))))))
        val vm = AiViewModel(store)
        val restored = withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
        assertEquals(AiToolStatus.Interrupted, restored.messages.single().toolStatus)
    }

    @Test fun stoppingStreamKeepsPartialAnswerInConversationHistory() = runBlocking {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val byte = input.read(); if (byte < 0) break; header.append(byte.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    var remaining = length
                    val buffer = ByteArray(4096)
                    while (remaining > 0) { val n = input.read(buffer, 0, minOf(remaining, buffer.size)); if (n < 0) break; remaining -= n }
                    val event = "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"PARTIAL_OUTPUT\"},\"finish_reason\":null}]}\n\n"
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray()); write(event.toByteArray()); flush() }
                    while (input.read(buffer) >= 0) { }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val store = testAiStore()
        try {
            val vm = AiViewModel(store)
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            vm.saveConfig(config)
            withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("test"))
            withTimeout(20000) { vm.uiState.first { it.busy && it.messages.any { m -> m.text.contains("PARTIAL_OUTPUT") } } }
            vm.stop()
            val done = withTimeout(10000) { vm.uiState.first { !it.busy } }
            val expected = "PARTIAL_OUTPUT\n\n${appString(R.string.ai_response_interrupted)}"
            assertTrue(done.messages.any { it.role == "assistant" && it.text == expected })
            withTimeout(10000) { while (store.loadHistory().conversations.none { c -> c.turns.any { it is Turn.Assistant && it.text == expected } }) delay(100) }
        } finally { server.close(); worker.join(1000) }
    }
}
