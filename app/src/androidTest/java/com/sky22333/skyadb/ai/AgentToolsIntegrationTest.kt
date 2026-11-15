package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentToolsIntegrationTest {
    @Test fun connectionsAreReused() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("targetHost")
        val port = args.getString("targetPort")?.toIntOrNull()
        assumeTrue("Requires an authorized target device", !host.isNullOrBlank() && port != null)
        requireNotNull(host)
        val primary = KadbManager()
        val sessions = AgentSessions(primary)
        try {
            val connected = sessions.connect("first", host, requireNotNull(port)) as AdbOperationResult.Success
            val reused = sessions.connect("second", host, port) as AdbOperationResult.Success
            assertEquals(connected.data, reused.data)
            assertEquals(1, sessions.list().size)
            assertTrue(sessions.resolve(reused.data.id) === sessions.resolve(connected.data.id))
            val manager = sessions.resolve(connected.data.id)
            val client = KadbManager::class.java.getDeclaredField("activeKadb").apply { isAccessible = true }.get(manager) as AutoCloseable
            client.close()
            assertFalse(sessions.list().single().connected)
            val reconnected = sessions.connect("second", host, port) as AdbOperationResult.Success
            val shell = sessions.resolve(reconnected.data.id).shell("printf reconnected")
            assertTrue("Reused session must reconnect after transport loss: $shell", shell is AdbOperationResult.Success && shell.data.output == "reconnected")
            sessions.disconnect(connected.data.id)
            assertTrue(primary.connect(host, port) is AdbOperationResult.Success)
            val active = sessions.connect("third", host, port) as AdbOperationResult.Success
            assertEquals("active", active.data.id)
            assertTrue(sessions.resolve(active.data.id) === primary)
        } finally {
            runCatching { sessions.disconnect("first") }
            primary.disconnect()
        }
    }
    @Test fun modelCanDiscoverAndRunGenericShell() = runBlocking {
        val server = ServerSocket(0)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val worker = thread(isDaemon = true) {
            try {
                repeat(3) { index ->
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream(); val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        val data = ByteArray(length); var offset = 0
                        while (offset < length) { val n = input.read(data, offset, length - offset); if (n < 0) break; offset += n }
                        requests.add(data.toString(Charsets.UTF_8))
                        val calls = when (index) {
                            0 -> {
                                val calls = (0 until 9).joinToString(",") { index ->
                                    """{"index":$index,"id":"env_$index","type":"function","function":{"name":"agent_environments","arguments":"{}"}}"""
                                }
                                """{"index":0,"delta":{"role":"assistant","tool_calls":[$calls]},"finish_reason":null}"""
                            }
                            1 -> """{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"agent_shell","arguments":"{\"environment\":\"app\",\"command\":\"printf AGENT_OK\"}"}}]},"finish_reason":null}"""
                            else -> """{"index":0,"delta":{"content":"complete"},"finish_reason":null}"""
                        }
                        val reason = if (index < 2) "tool_calls" else "stop"
                        val chunks = listOf(calls, """{"index":0,"delta":{},"finish_reason":"$reason"}""")
                        val payload = (chunks.joinToString("") { "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$it]}\n\n" } + "data: [DONE]\n\n").toByteArray()
                        socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test", autoExecute = true)
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Inspect environments and run a command"))
            val done = withTimeout(30000) { vm.uiState.first { !it.busy } }
            assertNull(done.error)
            assertTrue(done.messages.any { it.text.contains("complete") })
            assertEquals(9, done.messages.count { it.role == "tool" && it.text.startsWith("agent_environments\n") })
            assertTrue(requests.last().contains("AGENT_OK"))
        } finally { vm.stop(); server.close(); worker.join(1000) }
    }
    @Test fun appShellReportsStdoutStderrAndExit() = runBlocking {
        val result = runAppShell("printf hello; printf problem >&2; exit 7")
        assertTrue(result.contains("exit=7"))
        assertTrue(result.contains("stdout=hello"))
        assertTrue(result.contains("stderr=problem"))
    }
    @Test fun appShellCanBeCancelled() = runBlocking {
        val job = async { runAppShell("sleep 30") }
        delay(200)
        job.cancel()
        withTimeout(5000) { try { job.await() } catch (_: CancellationException) { } }
        Unit
    }
    @Test fun transfersBinaryFilesInAppEnvironment() = runBlocking {
        val source = File.createTempFile("agent-source-", ".bin", AppServices.context.cacheDir)
        val target = File.createTempFile("agent-dest-", ".bin", AppServices.context.cacheDir)
        try {
            val bytes = ByteArray(100_000) { (it % 256).toByte() }
            source.writeBytes(bytes)
            AgentTools.transfer(AiConfig(), "app", source.absolutePath, "app", target.absolutePath, emptyMap(), null)
            assertArrayEquals(bytes, target.readBytes())
        } finally { source.delete(); target.delete() }
    }
    @Test fun transfersSelectedAttachmentToAppEnvironment() = runBlocking {
        val source = File.createTempFile("agent-selected-", ".bin", AppServices.context.cacheDir)
        val target = File.createTempFile("agent-dest-", ".bin", AppServices.context.cacheDir)
        try {
            val bytes = ByteArray(32_000) { it.toByte() }
            source.writeBytes(bytes)
            AgentTools.transfer(AiConfig(), "selected", "", "app", target.absolutePath, emptyMap(), android.net.Uri.fromFile(source))
            assertArrayEquals(bytes, target.readBytes())
        } finally { source.delete(); target.delete() }
    }
    @Test fun invalidSessionIsNotSilentlyRoutedToCurrentDevice() = runBlocking {
        val failure = runCatching { AgentTools.capture("does-not-exist", null) }
        assertTrue(failure.isFailure)
    }
}
