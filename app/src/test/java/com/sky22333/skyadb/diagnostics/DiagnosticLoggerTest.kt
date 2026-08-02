package com.sky22333.skyadb.diagnostics

import com.sky22333.skyadb.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLoggerTest {
    @Test
    fun record_keepsLatestLogsWithinLimit() {
        DiagnosticLogger.clear()

        repeat(505) { index ->
            DiagnosticLogger.record(
                module = DiagnosticModule.WifiAdb,
                operation = "连接设备",
                target = "192.168.1.$index:5555",
                message = "失败 $index",
                suggestion = "检查网络",
            )
        }

        val logs = DiagnosticLogger.logs.value
        assertEquals(500, logs.size)
        assertEquals("失败 504", logs.first().message)
        assertEquals("失败 5", logs.last().message)
    }

    @Test
    fun record_dropsImmediateDuplicate() {
        DiagnosticLogger.clear()

        repeat(2) {
            DiagnosticLogger.record(
                module = DiagnosticModule.App,
                operation = "初始化 ADB 身份",
                message = "ADB 身份初始化失败",
                suggestion = "重试",
            )
        }

        assertEquals(1, DiagnosticLogger.logs.value.size)
    }

    @Test
    fun module_exposesLocalizedLabelResource() {
        assertEquals(R.string.diagnostic_module_files, DiagnosticModule.Files.labelRes)
        assertEquals(R.string.diagnostic_module_wifi_adb, DiagnosticModule.WifiAdb.labelRes)
        assertEquals(R.string.diagnostic_module_app, DiagnosticModule.App.labelRes)
    }

    @Test
    fun formatTime_usesStandardDateTimePattern() {
        val formatted = DiagnosticFormatter.formatTime(0)

        assertEquals(10, formatted.indexOf(' '))
        assertEquals(19, formatted.length)
    }
}
