package com.sky22333.skyadb.ai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiResponsesIntegrationTest {
    @Test fun streamsResponsesApi() = runBlocking {
        val server = ServerSocket(0)
        var request = ""
        val worker = thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val body = ByteArray(length); var offset = 0
                    while (offset < length) { val n = input.read(body, offset, length - offset); if (n < 0) break; offset += n }
                    request = header.toString()
                    val delta = """{"type":"response.output_text.delta","item_id":"item","output_index":0,"content_index":0,"delta":"Responses works","sequence_number":1}"""
                    val done = """{"type":"response.completed","sequence_number":2,"response":{"id":"r1","created_at":1,"model":"test","object":"response","output":[],"parallel_tool_calls":false,"status":"completed","text":{}}}"""
                    val payload = "data: $delta\n\ndata: $done\n\n".toByteArray(StandardCharsets.UTF_8)
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                }
            } finally { server.close() }
        }
        val http = HttpClient(OkHttp)
        try {
            val settings = OpenAIClientSettings(baseUrl = "http://127.0.0.1:${server.localPort}/v1", responsesAPIPath = "responses")
            val frames = OpenAILLMClient("test-key", settings, KtorKoogHttpClient.Factory(http)).use { api ->
                val model = LLModel(LLMProvider.OpenAI, "test", listOf(LLMCapability.Completion, LLMCapability.OpenAIEndpoint.Responses))
                api.executeStreaming(Prompt.build("test", OpenAIResponsesParams(store = false)) { user("Hello") }, model).toList()
            }
            assertTrue(request.startsWith("POST /v1/responses"))
            assertEquals("Responses works", frames.filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text })
            assertTrue(frames.any { it is StreamFrame.End })
        } finally { http.close(); server.close(); worker.join(1000) }
    }
}
