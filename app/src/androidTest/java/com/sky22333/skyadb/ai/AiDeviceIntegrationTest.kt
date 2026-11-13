package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDeviceIntegrationTest {
    @Test fun approvedShellExecutesOnTestDevice() = runBlocking {
        val port = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("adbPort")?.toIntOrNull()
        org.junit.Assume.assumeTrue("Requires an isolated ADB test device", port != null)
        val connected = AppServices.adbRepository.connect("127.0.0.1", requireNotNull(port))
        assertTrue(connected is com.sky22333.skyadb.model.AdbOperationResult.Success)
        try { exercise(true) } finally { AppServices.adbRepository.disconnect() }
    }

    private suspend fun exercise(allow: Boolean) {
        val requests = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0)
        
        val worker = thread {
            try {
                repeat(2) { index -> server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream(); val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val body = ByteArray(length); var offset = 0
                    while (offset < length) { val n = input.read(body, offset, length - offset); if (n < 0) break; offset += n }
                    requests.add(String(body, StandardCharsets.UTF_8))
                    val choices = if (index == 0) arrayOf(
                        """{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_read","type":"function","function":{"name":"run_device_shell","arguments":"{\"command\":\"echo SKYADB_TEST_OK\"}"}}]},"finish_reason":null}""",
                        """{"index":0,"delta":{},"finish_reason":"tool_calls"}"""
                    ) else arrayOf("""{"index":0,"delta":{"content":"Read complete"},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"stop"}""")
                    val text = choices.joinToString("") { c -> "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$c]}\n\n" } + "data: [DONE]\n\n"
                    val payload = text.toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                } }
            } finally { server.close() }
        }
        try {
            val vm = AiViewModel(testAiStore()); val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Run the test command"))
            withTimeout(20000) { vm.uiState.first { it.approval != null || it.error != null } }
            assertNotNull(vm.uiState.value.approval)
            
            vm.respond(allow)
            val result = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertEquals(null, result.error)
            assertTrue(result.messages.any { it.text.contains("Read complete") })
            assertTrue(requests[1].contains("stdout=SKYADB_TEST_OK"))
            
        } finally { server.close(); worker.join(1000) }
    }
}
