package com.sky22333.skyadb.ai

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.ui.ai.AiScreen
import com.sky22333.skyadb.ui.ai.AiSettingsScreen
import androidx.compose.runtime.mutableStateOf
import com.sky22333.skyadb.ui.ai.WorkProcess
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiVisualReviewTest {
    @Test fun timelineAndSettings() {
        val store = testAiStore()
        val messages = listOf(
            AiMessage(id = "u", role = "user", text = "请检查设备并总结结果", attachmentName = "device-log.txt"),
            AiMessage(id = "t1", role = "tool", text = "agent_environments\n{}\nPixel connected", toolStatus = AiToolStatus.Finished),
            AiMessage(id = "t2", role = "tool", text = "agent_shell\n{command: getprop}\nPermission denied", toolStatus = AiToolStatus.Failed),
            AiMessage(id = "a", role = "assistant", text = "## 检查结果\n\n当前连接状态\n| 项目 | 结果 |\n|---|---|\n| ADB 设备 | 无 |\n| 环境 | app |\n\n设备已连接，但命令执行失败。"),
        )
        store.save(AiConfig(mcpServers = listOf(McpServerConfig(name = "设备工具服务", url = "http://127.0.0.1/mcp"))))
        store.saveHistory(AiHistory("review", listOf(AiConversation("review", "review", messages, listOf(Turn.User("review"))))))
        val vm = AiViewModel(store)
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
        fun capture(name: String) {
            Thread.sleep(400)
            File(directory, "$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val showSettings = mutableStateOf(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.navigationBarsPadding().imePadding()) {
                if (showSettings.value) AiSettingsScreen(vm) { showSettings.value = false }
                else AiScreen(vm, onSettingsClick = { showSettings.value = true })
            } } } }
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?, label: String): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                for (index in 0 until node.childCount) find(node.getChild(index), label)?.let { return it }
                return null
            }
            fun await(label: String): android.view.accessibility.AccessibilityNodeInfo {
                repeat(100) {
                    find(instrumentation.uiAutomation.rootInActiveWindow, label)?.let { return it }
                    Thread.sleep(100)
                }
                error("Missing accessibility node: $label")
            }
            await(appString(R.string.ai_work_process))
            await(appString(R.string.ai_tool_failed))
            capture("timeline")
            val bounds = android.graphics.Rect()
            await(appString(R.string.ai_work_process)).getBoundsInScreen(bounds)
            instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").close()
            capture("timeline-collapsed")
            await(appString(R.string.ai_config)).getBoundsInScreen(bounds)
            instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").close()
            await(appString(R.string.ai_url))
            await(appString(R.string.ai_mcp_title))
            await("设备工具服务")
            capture("settings")
            scenario.onActivity { it.setContent { AdbManagerTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 320.dp).fillMaxHeight().navigationBarsPadding().imePadding()) { AiScreen(vm) }
            } } } }
            await(appString(R.string.ai_work_process))
            capture("timeline-narrow")
            scenario.onActivity { it.setContent { AdbManagerTheme { Column(Modifier.padding(20.dp)) {
                WorkProcess(listOf(AiMessage(id = "running", role = "tool", text = "usb_devices\n{}", toolStatus = AiToolStatus.Running)))
            } } } }
            await(appString(R.string.ai_work_process))
            capture("work-running")
        }
    }
}
