package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiClientIntegrationTest {
    @Test fun agentCanContinuePastEightToolRounds() = runBlocking {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                repeat(10) { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream(); val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        var remaining = length; val buffer = ByteArray(4096)
                        while (remaining > 0) { val n = input.read(buffer, 0, minOf(buffer.size, remaining)); if (n < 0) break; remaining -= n }
                        val body = if (index < 9) stream(
                            """{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_$index","type":"function","function":{"name":"get_device_info","arguments":"{}"}}]},"finish_reason":null}""",
                            """{"index":0,"delta":{},"finish_reason":"tool_calls"}"""
                        ) else stream("""{"index":0,"delta":{"content":"CONTINUED"},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"stop"}""")
                        val payload = body.toByteArray()
                        socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Run a multi-step task"))
            val done = withTimeout(30000) { vm.uiState.first { !it.busy } }
            assertEquals(null, done.error)
            assertTrue(done.messages.any { it.text.contains("CONTINUED") })
        } finally { vm.stop(); server.close(); worker.join(1000) }
    }
    @Test fun modelDiscoveryAndStreamingToolRoundTrip() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0)
        val worker = thread {
            try {
                repeat(3) { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream()
                        val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        val body = ByteArray(length); var offset = 0
                        while (offset < length) { val n = input.read(body, offset, length - offset); if (n < 0) break; offset += n }
                        requests.add(header.toString() + String(body, StandardCharsets.UTF_8))
                        val payload = when (index) {
                            0 -> """{"object":"list","data":[{"id":"test-model","object":"model","created":1,"owned_by":"test"}]}"""
                            1 -> stream("""{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_device_info","arguments":"{}"}}]},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"tool_calls"}""")
                            else -> stream("""{"index":0,"delta":{"content":"Hello from test"},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"stop"}""")
                        }.toByteArray(StandardCharsets.UTF_8)
                        val type = if (index == 0) "application/json" else "text/event-stream"
                        socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } finally { server.close() }
        }
        try {
            val vm = AiViewModel(testAiStore())
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test-key", "test-model")
            vm.loadModels(config)
            withTimeout(20000) { vm.uiState.first { !it.loadingModels && (it.models.isNotEmpty() || it.error != null) } }
            assertEquals(listOf("test-model"), vm.uiState.value.models)
            vm.saveConfig(config)
            withTimeout(10000) { vm.uiState.first { it.config == config } }
            val input = "\n  Hello\n\nworld 😀  \n"
            assertTrue(vm.send(input))
            val result = withTimeout(30000) { vm.uiState.first { !it.busy } }
            assertEquals(null, result.error)
            assertEquals("Hello", result.conversations.first().title)
            assertTrue(result.messages.any { it.text.contains("Hello from test") })
            assertTrue(requests[0].startsWith("GET /v1/models"))
            assertTrue(requests[1].startsWith("POST /v1/chat/completions"))
            val body = kotlinx.serialization.json.Json.parseToJsonElement(requests[1].substringAfter("\r\n\r\n")) as kotlinx.serialization.json.JsonObject
            val messages = body["messages"] as kotlinx.serialization.json.JsonArray
            assertTrue(messages.any { (it as? kotlinx.serialization.json.JsonObject)?.get("content") == kotlinx.serialization.json.JsonPrimitive(input) })
            assertTrue(requests[2].contains("call_1"))
        } finally { server.close(); worker.join(1000) }
    }
    private fun stream(vararg choices: String): String = choices.joinToString("") { choice ->
        "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test-model\",\"choices\":[$choice]}\n\n"
    } + "data: [DONE]\n\n"
}
