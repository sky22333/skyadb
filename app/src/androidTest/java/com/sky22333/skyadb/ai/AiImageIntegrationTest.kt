package com.sky22333.skyadb.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiImageIntegrationTest {
    @Test fun imageUsesChatCompletionsContentParts() = exercise(false)
    @Test fun imageUsesResponsesContentParts() = exercise(true)

    @Test fun rejectedImageDoesNotBlockNextMessage() = runBlocking {
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream(); val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; head.append(n.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                    var remaining = length
                    while (remaining > 0) { val n = input.read(ByteArray(minOf(remaining, 8192))); if (n < 0) break; remaining -= n }
                    val delta = """{"index":0,"delta":{"content":"received"},"finish_reason":null}"""
                    val done = """{"index":0,"delta":{},"finish_reason":"stop"}"""
                    val body = ("data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$delta]}\n\ndata: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$done]}\n\ndata: [DONE]\n\n").toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val image = File(AppServices.context.cacheDir, "rejected-image.png")
        java.io.RandomAccessFile(image, "rw").use { it.setLength((MAX_IMAGE_BYTES + 1).toLong()) }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            vm.updateDraft("Image")
            vm.attachFiles(Uri.fromFile(image)); assertTrue(vm.send("Image"))
            val rejected = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNotNull(rejected.error)
            assertEquals("Image", rejected.draft)
            assertEquals(Uri.fromFile(image).toString(), rejected.attachments.single().uri)
            assertFalse(rejected.messages.any { it.text == "Image" })
            vm.removeAttachment(rejected.attachments.single().id); vm.dismissError(); vm.updateDraft("Text only"); assertTrue(vm.send("Text only"))
            val result = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNull(result.error)
            assertEquals("", result.draft)
            assertTrue(result.messages.any { it.text == "received" })
        } finally { vm.stop(); image.delete(); server.close(); worker.join(1000) }
    }

    @Test fun imageMimeFallsBackForGenericContentAndHonorsExplicitTypes() {
        assertEquals("image/png", imageMime("application/octet-stream", "PHOTO.PNG"))
        assertEquals("image/jpeg", imageMime("image/jpg", "photo.jpg"))
        assertEquals("text/plain", imageMime("text/plain", "photo.png"))
    }

    @Test fun imageSizeLimitsAreEnforcedBeforeSending() {
        val file = File(AppServices.context.cacheDir, "image-large.png")
        try {
            java.io.RandomAccessFile(file, "rw").use { it.setLength((MAX_IMAGE_BYTES + 1).toLong()) }
            val tooLarge = runCatching { readImage(AppServices.context, Uri.fromFile(file), file.name) }.exceptionOrNull()
            assertEquals(com.sky22333.skyadb.i18n.appString(com.sky22333.skyadb.R.string.ai_image_too_large), tooLarge?.message)
            java.io.RandomAccessFile(file, "rw").use { it.setLength(8) }
            val totalTooLarge = runCatching { readImage(AppServices.context, Uri.fromFile(file), file.name, 4) }.exceptionOrNull()
            assertEquals(com.sky22333.skyadb.i18n.appString(com.sky22333.skyadb.R.string.ai_images_too_large), totalTooLarge?.message)
        } finally { file.delete() }
    }

    @Test fun multipleImagesRemainInConversationContext() = runBlocking {
        val server = ServerSocket(0)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val worker = thread(isDaemon = true) {
            try {
                repeat(2) {
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val input = socket.getInputStream(); val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                        val bytes = ByteArray(length); var offset = 0
                        while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                        requests.add(bytes.toString(Charsets.UTF_8))
                        val delta = """{"index":0,"delta":{"content":"received"},"finish_reason":null}"""
                        val done = """{"index":0,"delta":{},"finish_reason":"stop"}"""
                        val payload = ("data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$delta]}\n\ndata: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$done]}\n\ndata: [DONE]\n\n").toByteArray()
                        socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
                    }
                }
            } catch (_: java.io.IOException) { } finally { server.close() }
        }
        val bytes = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7xkAAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
        val first = File(AppServices.context.cacheDir, "image-first.png").apply { writeBytes(bytes) }
        val second = File(AppServices.context.cacheDir, "image-second.png").apply { writeBytes(bytes) }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            vm.attachFiles(Uri.fromFile(first)); assertTrue(vm.send("First image"))
            assertNull(withTimeout(20000) { vm.uiState.first { !it.busy } }.error)
            vm.attachFiles(Uri.fromFile(second)); assertTrue(vm.send("Compare with the first image"))
            assertNull(withTimeout(20000) { vm.uiState.first { !it.busy } }.error)
            assertEquals(2, Regex("data:image/png;base64,").findAll(requests[1]).count())
        } finally { vm.stop(); first.delete(); second.delete(); server.close(); worker.join(1000) }
    }

    private fun exercise(responses: Boolean) = runBlocking {
        val server = ServerSocket(0)
        val request = AtomicReference("")
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream(); val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) { val n = input.read(); if (n < 0) break; header.append(n.toChar()) }
                    val length = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                    val bytes = ByteArray(length); var offset = 0
                    while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
                    request.set(bytes.toString(Charsets.UTF_8))
                    val payload = if (responses) {
                        val delta = """{"type":"response.output_text.delta","item_id":"item","output_index":0,"content_index":0,"delta":"image received","sequence_number":1}"""
                        val done = """{"type":"response.completed","sequence_number":2,"response":{"id":"r1","created_at":1,"model":"test","object":"response","output":[],"parallel_tool_calls":false,"status":"completed","text":{}}}"""
                        "data: $delta\n\ndata: $done\n\n"
                    } else {
                        val delta = """{"index":0,"delta":{"content":"image received"},"finish_reason":null}"""
                        val done = """{"index":0,"delta":{},"finish_reason":"stop"}"""
                        "data: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$delta]}\n\ndata: {\"id\":\"id\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[$done]}\n\ndata: [DONE]\n\n"
                    }
                    val body = payload.toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
                }
            } finally { server.close() }
        }
        val file = File(AppServices.context.cacheDir, "image-test.png").apply { writeBytes(android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7xkAAAAASUVORK5CYII=", android.util.Base64.DEFAULT)) }
        val second = File(AppServices.context.cacheDir, "image-test-2.png").apply { writeBytes(file.readBytes()) }
        val vm = AiViewModel(testAiStore())
        try {
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test", responses = responses)
            vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
            vm.attachFiles(Uri.fromFile(file), Uri.fromFile(second)); assertTrue(vm.send("Describe the images"))
            val result = withTimeout(20000) { vm.uiState.first { !it.busy } }
            assertNull(result.error)
            assertTrue(request.get().contains(if (responses) "input_image" else "image_url"))
            assertEquals(2, Regex("data:image/png;base64,").findAll(request.get()).count())
            assertEquals(2, result.messages.first { it.role == "user" }.attachments.size)
            assertTrue(result.messages.any { it.text.contains("image received") })
        } finally { vm.stop(); file.delete(); second.delete(); server.close(); worker.join(1000) }
    }
}
