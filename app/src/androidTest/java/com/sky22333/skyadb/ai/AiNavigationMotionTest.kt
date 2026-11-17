package com.sky22333.skyadb.ai

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.ui.AdbManagerApp
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiNavigationMotionTest {
    @Test fun measureRealTimeTransitions() {
        val store = testAiStore()
        val reply = (1..80).joinToString("\n\n") { "Paragraph $it" }
        store.saveHistory(AiHistory("motion", listOf(AiConversation("motion", "motion", listOf(AiMessage(role = "user", text = "hello"), AiMessage(role = "assistant", text = reply)), listOf(Turn.User("hello"), Turn.Assistant(reply, emptyList()))))))
        val vm = AiViewModel(store)
        runBlocking { withTimeout(10000) { vm.uiState.first { !it.historyLoading } } }
        val inst = InstrumentationRegistry.getInstrumentation()
        val ui = inst.uiAutomation
        fun shell(cmd: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).bufferedReader().use { it.readText() }
        fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
            if (node == null) return null
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val isTab = label == appString(R.string.ai_nav) || label == appString(R.string.nav_settings)
            if ((node.text?.toString() == label || node.contentDescription?.toString() == label) &&
                (!isTab || bounds.centerY() > inst.targetContext.resources.displayMetrics.heightPixels * 0.7f)) return node
            for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
            return null
        }
        fun tap(label: String) {
            var node: AccessibilityNodeInfo? = null
            repeat(100) { if (node == null) { node = find(ui.rootInActiveWindow, label); if (node == null) SystemClock.sleep(50) } }
            val bounds = android.graphics.Rect()
            requireNotNull(node) { "Missing $label" }.getBoundsInScreen(bounds)
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { ui.injectInputEvent(down, true); ui.injectInputEvent(up, true) } finally { down.recycle(); up.recycle() }
            SystemClock.sleep(450)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { AdbManagerTheme { AdbManagerApp(vm) } } }
            tap(appString(R.string.ai_nav))
            shell("dumpsys gfxinfo ${inst.targetContext.packageName} reset")
            repeat(3) { tap(appString(R.string.nav_settings)); tap(appString(R.string.ai_nav)) }
            tap(appString(R.string.ai_config)); tap(appString(R.string.action_back))
            val directory = File(inst.targetContext.getExternalFilesDir(null), "ai-visual-review").apply { mkdirs() }
            File(directory, "navigation-realtime-gfxinfo.txt").writeText(shell("dumpsys gfxinfo ${inst.targetContext.packageName} framestats"))
        }
    }
}
