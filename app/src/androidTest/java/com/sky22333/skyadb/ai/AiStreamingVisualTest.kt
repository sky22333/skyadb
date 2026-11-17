package com.sky22333.skyadb.ai

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.ui.ai.AiScreen
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import java.io.File
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
class AiStreamingVisualTest {
    @Test fun streamsLongAnswerWithoutLosingContent() = runBlocking {
        val server = ServerSocket(0)
        val chunk = "设备信息检查完成。中文、emoji 👨‍👩‍👧‍👦 和命令输出保持完整。 "
        val table = "\n| 项目 | 结果 |\n|---|---|\n| ADB | 无 |"
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n")) { val byte = input.read(); if (byte < 0) break; header.append(byte.toChar()) }
                var remaining = Regex("(?im)^content-length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                val buffer = ByteArray(4096)
                while (remaining > 0) { val n = input.read(buffer, 0, minOf(remaining, buffer.size)); if (n < 0) break; remaining -= n }
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                repeat(80) {
                    val json = org.json.JSONObject().put("id", "stream").put("object", "chat.completion.chunk").put("created", 1).put("model", "test")
                        .put("choices", org.json.JSONArray().put(org.json.JSONObject().put("index", 0).put("delta", org.json.JSONObject().put("content", chunk)).put("finish_reason", org.json.JSONObject.NULL)))
                    output.write("data: $json\n\n".toByteArray()); output.flush(); Thread.sleep(50)
                }
                val tableEvent = org.json.JSONObject().put("id", "stream").put("object", "chat.completion.chunk").put("created", 1).put("model", "test")
                    .put("choices", org.json.JSONArray().put(org.json.JSONObject().put("index", 0).put("delta", org.json.JSONObject().put("content", table)).put("finish_reason", org.json.JSONObject.NULL)))
                output.write("data: $tableEvent\n\n".toByteArray())
                output.write("data: {\"id\":\"stream\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toByteArray()); output.flush()
            }
        }
        val vm = AiViewModel(testAiStore())
        withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
        val config = AiConfig("http://127.0.0.1:${server.localPort}/v1", "test", "test")
        vm.saveConfig(config); withTimeout(10000) { vm.uiState.first { it.config == config } }
        val inst = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        try {
            var listState: LazyListState? = null
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.navigationBarsPadding()) {
                    val current = rememberLazyListState(); listState = current; AiScreen(vm, listState = current)
                } } } }
                shell("dumpsys gfxinfo ${inst.targetContext.packageName} reset")
                assertTrue(vm.send("stream test"))
                withTimeout(10000) { vm.uiState.first { it.phase == AiRunPhase.Answering } }
                delay(400)
                val screenshotDir = File(inst.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
                File(screenshotDir, "answer-running.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                withTimeout(20000) { vm.uiState.first { !it.busy } }
                delay(1000)
                withTimeout(10000) {
                    while (listState?.layoutInfo?.totalItemsCount != 3 || listState?.canScrollBackward != false) delay(50)
                }
                val result = vm.uiState.value
                assertNull(result.error)
                assertEquals(chunk.repeat(80) + table, result.messages.last { it.role == "assistant" }.text)
                fun containsTable(view: android.view.View): Boolean =
                    view.javaClass.simpleName == "TableContainerView" || (view is android.view.ViewGroup && (0 until view.childCount).any { containsTable(view.getChildAt(it)) })
                scenario.onActivity { assertTrue("Streaming response should render a table", containsTable(it.window.decorView)) }
                val dir = File(inst.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
                File(dir, "stream-gfxinfo.txt").writeText(shell("dumpsys gfxinfo ${inst.targetContext.packageName} framestats"))
            }
        } finally { vm.stop(); server.close(); worker.join(2000) }
    }
}
