package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDownloadFlowTest {
    @Test fun approvedDownloadIsSavedAndCanBeDeleted() = runBlocking { exercise("allow") }
    @Test fun declinedDownloadDoesNotContactServer() = runBlocking { exercise("deny") }
    @Test fun stoppingDownloadRemovesPartialFile() = runBlocking { exercise("cancel") }
    @Test fun serverFailureIsReported() = runBlocking { exercise("failure") }
    @Test fun automaticModeDoesNotRequireApproval() = runBlocking { exercise("auto") }

    private suspend fun exercise(mode: String) {
        val directory = File(AppServices.context.cacheDir, "ai-flow-${UUID.randomUUID()}")
        val exported = File(AppServices.context.cacheDir, "ai-export-${UUID.randomUUID()}")
        val modelServer = ServerSocket(0)
        val fileServer = ServerSocket(0).apply { soTimeout = 12000 }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var fileRequests = 0
        val fileWorker = thread {
            try {
                fileServer.accept().use { socket ->
                    fileRequests++
                    socket.getInputStream().bufferedReader().apply { while (!readLine().isNullOrEmpty()) {} }
                    val out = socket.getOutputStream()
                    if (mode == "cancel") {
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\npartial".toByteArray()); out.flush()
                        started.countDown(); release.await(15, TimeUnit.SECONDS)
                    } else if (mode == "failure") {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush()
                    } else {
                        val body = "downloaded content".toByteArray()
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush()
                    }
                }
            } catch (_: java.io.IOException) { } finally { fileServer.close() }
        }
        val modelWorker = thread {
            try {
                repeat(if (mode == "cancel") 1 else 2) { index ->
                    modelServer.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream(); val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        var remaining = length; val buffer = ByteArray(4096)
                        while (remaining > 0) { val n = input.read(buffer, 0, minOf(buffer.size, remaining)); if (n < 0) break; remaining -= n }
                        val choices = if (index == 0) arrayOf(
                            """{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_download","type":"function","function":{"name":"download_file","arguments":"{\"url\":\"http://127.0.0.1:${fileServer.localPort}/sample.txt\"}"}}]},"finish_reason":null}""",
                            """{"index":0,"delta":{},"finish_reason":"tool_calls"}"""
                        ) else arrayOf("""{"index":0,"delta":{"content":"Finished"},"finish_reason":null}""", """{"index":0,"delta":{},"finish_reason":"stop"}""")
                        val text = choices.joinToString("") { c -> "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$c]}\n\n" } + "data: [DONE]\n\n"
                        val payload = text.toByteArray()
                        socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } catch (_: java.io.IOException) { } finally { modelServer.close() }
        }
        val vm = AiViewModel(testAiStore(), directory)
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${modelServer.localPort}/v1", "test", "test", autoExecute = mode == "auto")
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            assertTrue(vm.send("Download the file"))
            if (mode != "auto") {
                withTimeout(20000) { vm.uiState.first { it.approval != null || it.error != null } }
                assertNotNull(vm.uiState.value.approval)
                assertEquals(0, fileRequests)
                vm.respond(mode != "deny")
            }
            if (mode == "cancel") {
                assertTrue(started.await(15, TimeUnit.SECONDS))
                withTimeout(10000) { vm.uiState.first { it.downloadProgress != null } }
                vm.stop()
            }
            val done = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNull(done.error)
            if (mode == "allow" || mode == "auto") {
                assertEquals(1, fileRequests)
                vm.refreshDownloads()
                val found = withTimeout(10000) { vm.uiState.first { it.downloads.isNotEmpty() } }.downloads.single()
                assertEquals("downloaded content", found.file.readText())
                vm.exportDownload(found, android.net.Uri.fromFile(exported))
                withTimeout(10000) { vm.uiState.first { !it.managingDownload && it.downloadNotice != null } }
                assertEquals("downloaded content", exported.readText())
                vm.deleteDownload(found)
                withTimeout(10000) { vm.uiState.first { !it.managingDownload && it.downloads.isEmpty() } }
                assertFalse(found.file.exists())
            } else {
                assertTrue(directory.walkTopDown().none { it.isFile })
                if (mode == "deny") assertEquals(0, fileRequests)
                if (mode == "failure") assertTrue(done.messages.any { it.role == "tool" && it.text.contains("Error:") })
            }
        } finally {
            vm.stop(); release.countDown(); fileServer.close(); modelServer.close()
            fileWorker.join(2000); modelWorker.join(2000); directory.deleteRecursively(); exported.delete()
        }
    }
}
