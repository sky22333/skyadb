package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class McpConnectionsIntegrationTest {
    @Test fun stoppingDuringMcpConnectionCancelsConversation() = runBlocking {
        val server = ServerSocket(0)
        val accepted = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = thread(isDaemon = true) {
            try { server.accept().use { socket -> accepted.set(true); socket.getInputStream().readBytes() } }
            catch (_: java.io.IOException) { } finally { server.close() }
        }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:1/v1", "test", "test", mcpServers = listOf(McpServerConfig("slow", "http://127.0.0.1:${server.localPort}/mcp")))
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Hello"))
            withTimeout(10000) { while (!accepted.get()) kotlinx.coroutines.delay(20) }
            vm.stop()
            val result = withTimeout(5000) { vm.uiState.first { !it.busy } }
            assertNull(result.error)
            assertFalse(result.messages.any { it.toolStatus == AiToolStatus.Failed })
        } finally { vm.stop(); server.close(); worker.join(1000) }
    }
    @Test fun failedDiscoveryDoesNotExposePartialTools() = runBlocking {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                    socket.use {
                        it.soTimeout = 10000
                        val input = it.getInputStream(); val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                        val bytes = ByteArray(length); var offset = 0
                        while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                        val request = if (bytes.isEmpty()) null else Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                        val method = request?.get("method")?.jsonPrimitive?.content
                        val id = request?.get("id")
                        val nextPage = request?.get("params")?.jsonObject?.get("cursor") != null
                        val result = when (method) {
                            "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"test","version":"1"}}"""
                            "tools/list" -> if (nextPage) null else """{"tools":[{"name":"partial","description":"Partial","inputSchema":{"type":"object","properties":{}}}],"nextCursor":"next"}"""
                            else -> null
                        }
                        val body = if (id != null && result != null) "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$result}" else ""
                        val status = if (head.startsWith("GET ") || head.startsWith("DELETE ")) "405 Method Not Allowed" else if (nextPage) "500 Internal Server Error" else if (body.isEmpty()) "202 Accepted" else "200 OK"
                        val payload = body.toByteArray()
                        it.getOutputStream().apply { write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val connections = McpConnections()
        try {
            val failed = runCatching { withTimeout(15000) { connections.connect(McpServerConfig("partial", "http://127.0.0.1:${server.localPort}/mcp")) } }
            assertTrue(failed.isFailure)
            assertTrue(connections.descriptors.isEmpty())
        } finally { connections.close(); server.close(); worker.join(1000) }
    }
    @Test fun unavailableServerDoesNotBlockConversation() = runBlocking {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val model = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                model.accept().use { socket ->
                    val input = socket.getInputStream(); val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                    var length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    while (length > 0) { val n = input.read(ByteArray(minOf(length, 8192))); if (n < 0) break; length -= n }
                    val delta = """{"index":0,"delta":{"content":"model reply"},"finish_reason":null}"""
                    val done = """{"index":0,"delta":{},"finish_reason":"stop"}"""
                    val body = ("data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$delta]}\n\ndata: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$done]}\n\ndata: [DONE]\n\n").toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
                }
            } catch (_: java.io.IOException) { } finally { model.close() }
        }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${model.localPort}/v1", "test", "test", mcpServers = listOf(McpServerConfig("offline", "http://127.0.0.1:$unavailablePort/mcp")))
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Hello"))
            val result = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNull(result.error)
            assertTrue(result.messages.any { it.text == "model reply" })
            assertTrue(result.messages.any { it.role == "tool" && it.text.contains("offline") && it.toolStatus == AiToolStatus.Failed })
        } finally { vm.stop(); model.close(); worker.join(1000) }
    }
    @Test fun discoversPaginatedToolsAndCallsWithBearerAuthentication() = runBlocking {
        val server = ServerSocket(0)
        val headers = CopyOnWriteArrayList<String>()
        val worker = thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                    thread(isDaemon = true) {
                        socket.use {
                            it.soTimeout = 10000
                            val input = it.getInputStream(); val head = StringBuilder()
                            while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                            headers.add(head.toString())
                            val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                            val bytes = ByteArray(length); var offset = 0
                            while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                            val raw = bytes.toString(Charsets.UTF_8)
                            val json = if (raw.isBlank()) null else Json.parseToJsonElement(raw).jsonObject
                            val method = json?.get("method")?.jsonPrimitive?.content
                            val id = json?.get("id")
                            val result = when (method) {
                                "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"test","version":"1"}}"""
                                "tools/list" -> {
                                    val cursor = json["params"]?.jsonObject?.get("cursor")?.jsonPrimitive?.content
                                    val name = if (cursor == null) "first" else "second"
                                    val next = if (cursor == null) ",\"nextCursor\":\"page2\"" else ""
                                    """{"tools":[{"name":"$name","description":"Echo a message","inputSchema":{"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}}]$next}"""
                                }
                                "tools/call" -> """{"content":[{"type":"text","text":"MCP_OK"}],"isError":false}"""
                                else -> null
                            }
                            val response = if (id != null && result != null) "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$result}" else ""
                            val code = if (head.startsWith("GET ") || head.startsWith("DELETE ")) "405 Method Not Allowed" else if (response.isEmpty()) "202 Accepted" else "200 OK"
                            val body = response.toByteArray()
                            it.getOutputStream().apply { write("HTTP/1.1 $code\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
                        }
                    }
                }
            } finally { server.close() }
        }
        val connections = McpConnections()
        try {
            withTimeout(15000) { connections.connect(McpServerConfig("test", "http://127.0.0.1:${server.localPort}/mcp", "secret")) }
            assertEquals(2, connections.descriptors.size)
            val tool = connections.descriptors.single { it.name.endsWith("_second") }
            assertTrue(connections.label(tool.name).contains("second"))
            assertTrue(withTimeout(10000) { connections.call(tool.name, """{"message":"hello"}""") }.contains("MCP_OK"))
            assertTrue(headers.filter { it.startsWith("POST") }.all { it.contains("Authorization: Bearer secret", ignoreCase = true) })
        } finally { connections.close(); server.close(); worker.join(1000) }
    }
    @Test fun agentRequestsApprovalAndReturnsMcpResultToModel() = exerciseAgent(true)
    @Test fun deniedMcpCallIsNotExecuted() = exerciseAgent(false)
    @Test fun stoppingPendingMcpCallClearsBusyState() = exerciseAgent(null)

    private fun exerciseAgent(allow: Boolean?) = runBlocking {
        val mcpServer = ServerSocket(0)
        val modelServer = ServerSocket(0)
        val modelRequests = CopyOnWriteArrayList<String>()
        val mcpCalls = java.util.concurrent.atomic.AtomicInteger()
        val mcpWorker = thread(isDaemon = true) {
            try {
                while (!mcpServer.isClosed) {
                    val socket = try { mcpServer.accept() } catch (_: java.io.IOException) { break }
                    thread(isDaemon = true) {
                        socket.use {
                            it.soTimeout = 10000
                            val input = it.getInputStream(); val head = StringBuilder()
                            while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                            val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                            val bytes = ByteArray(length); var offset = 0
                            while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                            val json = if (bytes.isEmpty()) null else Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                            val method = json?.get("method")?.jsonPrimitive?.content; val id = json?.get("id")
                            val result = when (method) {
                                "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"test","version":"1"}}"""
                                "tools/list" -> """{"tools":[{"name":"lookup","description":"Look up information","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}]}"""
                                "tools/call" -> { mcpCalls.incrementAndGet(); """{"content":[{"type":"text","text":"MCP_TOOL_RESULT"}],"isError":false}""" }
                                else -> null
                            }
                            val response = if (id != null && result != null) "{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$result}" else ""
                            val status = if (head.startsWith("GET ") || head.startsWith("DELETE ")) "405 Method Not Allowed" else if (response.isEmpty()) "202 Accepted" else "200 OK"
                            val body = response.toByteArray()
                            it.getOutputStream().apply { write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
                        }
                    }
                }
            } finally { mcpServer.close() }
        }
        val modelWorker = thread(isDaemon = true) {
            try {
                repeat(2) { index -> modelServer.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream(); val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    val bytes = ByteArray(length); var offset = 0
                    while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                    modelRequests.add(bytes.toString(Charsets.UTF_8))
                    val toolName = if (index == 0) Json.parseToJsonElement(modelRequests.last()).jsonObject["tools"]!!.jsonArray.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }.single { it.endsWith("_lookup") } else ""
                    val deltas = if (index == 0) listOf(
                        """{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"$toolName","arguments":"{\"query\":\"hello\"}"}}]},"finish_reason":null}""",
                        """{"index":0,"delta":{},"finish_reason":"tool_calls"}"""
                    ) else listOf("""{"index":0,"delta":{"content":"MCP completed"},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"stop"}""")
                    val payload = (deltas.joinToString("") { "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$it]}\n\n" } + "data: [DONE]\n\n").toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                } }
            } catch (_: java.io.IOException) { } finally { modelServer.close() }
        }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${modelServer.localPort}/v1", "test", "test", mcpServers = listOf(McpServerConfig("Test server", "http://127.0.0.1:${mcpServer.localPort}/mcp")))
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Use the MCP tool"))
            val pending = withTimeout(15000) { vm.uiState.first { it.approval != null || it.error != null } }
            assertNull(pending.error)
            val approval = requireNotNull(pending.approval)
            assertTrue(approval.title.contains("Test server"))
            assertTrue(approval.detail.contains("hello"))
            if (allow == null) vm.stop() else vm.respond(allow)
            val result = withTimeout(15000) { vm.uiState.first { !it.busy } }
            assertNull(result.error)
            assertTrue(result.messages.any { it.text.contains("Test server · lookup") })
            assertEquals(if (allow == true) 1 else 0, mcpCalls.get())
            if (allow != null) {
                assertTrue(result.messages.any { it.text.contains("MCP completed") })
                assertTrue(modelRequests[1].contains(if (allow) "MCP_TOOL_RESULT" else "declined"))
            }
        } finally { vm.stop(); mcpServer.close(); modelServer.close(); mcpWorker.join(1000); modelWorker.join(1000) }
    }

}
