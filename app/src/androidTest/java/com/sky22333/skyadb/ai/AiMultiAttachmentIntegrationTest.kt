package com.sky22333.skyadb.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMultiAttachmentIntegrationTest {
    @Test fun failedSelectionKeepsAccessibleFilesAndReportsError() = runBlocking {
        val file = File.createTempFile("attachment-", ".txt", AppServices.context.cacheDir)
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            vm.addAttachments(listOf(Uri.fromFile(file), Uri.parse("content://invalid.skyadb.test/missing")))
            val state = withTimeout(10000) { vm.uiState.first { !it.attachmentsLoading && it.error != null } }
            assertEquals(listOf(file.name), state.attachments.map { it.name })
        } finally { file.delete() }
    }

    @Test fun selectsExactFileAmongDuplicateNamesAndRetainsDraft() = runBlocking {
        val root = File(AppServices.context.cacheDir, "multi-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val first = File(root, "one/same.txt").apply { parentFile!!.mkdirs(); writeText("FIRST_SECRET") }
        val second = File(root, "two/same.txt").apply { parentFile!!.mkdirs(); writeText("SECOND_SECRET") }
        val id = AtomicReference("")
        val requests = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                repeat(2) { index -> server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream(); val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                    var remaining = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val body = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (remaining > 0) { val n = input.read(buffer, 0, minOf(buffer.size, remaining)); if (n < 0) break; body.write(buffer, 0, n); remaining -= n }
                    requests.add(body.toString("UTF-8"))
                    val choice = if (index == 0) {
                        val args = org.json.JSONObject().put("attachment_id", id.get()).toString()
                        org.json.JSONObject().put("index", 0).put("delta", org.json.JSONObject().put("role", "assistant").put("tool_calls", org.json.JSONArray().put(org.json.JSONObject().put("index", 0).put("id", "call_file").put("type", "function").put("function", org.json.JSONObject().put("name", "read_selected_file").put("arguments", args))))).put("finish_reason", org.json.JSONObject.NULL).toString()
                    } else """{"index":0,"delta":{"content":"done"},"finish_reason":null}"""
                    val done = if (index == 0) "tool_calls" else "stop"
                    val payload = ("data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$choice]}\n\ndata: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"$done\"}]}\n\ndata: [DONE]\n\n").toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                } }
            } finally { server.close() }
        }
        val store = testAiStore(); val vm = AiViewModel(store)
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            vm.attachFiles(Uri.fromFile(first), Uri.fromFile(second))
            assertEquals(2, vm.uiState.value.attachments.size)
            id.set(vm.uiState.value.attachments[1].id)
            withTimeout(10000) { while (store.loadHistory().draftAttachments.values.none { it.size == 2 }) delay(50) }
            val reopened = AiViewModel(store)
            val restored = withTimeout(10000) { reopened.uiState.first { !it.historyLoading } }
            assertEquals(vm.uiState.value.attachments, restored.attachments)
            assertTrue(vm.send("Read the second file"))
            val pending = withTimeout(20000) { vm.uiState.first { it.approval != null || it.error != null } }
            assertNotNull(pending.approval)
            assertTrue(pending.approval!!.detail.contains("same.txt"))
            vm.respond(true)
            val done = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNull(done.error)
            assertEquals(2, done.messages.first { it.role == "user" }.attachments.size)
            assertTrue(requests[0].contains(id.get()))
            assertFalse(requests[0].contains("FIRST_SECRET"))
            assertTrue(requests[1].contains("SECOND_SECRET"))
            assertFalse(requests[1].contains("FIRST_SECRET"))
            withTimeout(10000) { while (store.loadHistory().conversations.none { c -> c.turns.any { it is Turn.User && it.attachments.size == 2 } }) delay(50) }
            val recovered = AiViewModel(store)
            val recoveredState = withTimeout(10000) { recovered.uiState.first { !it.historyLoading } }
            assertEquals(2, recoveredState.messages.first { it.role == "user" }.attachments.size)
        } finally { vm.stop(); server.close(); worker.join(1000); root.deleteRecursively() }
    }
}
