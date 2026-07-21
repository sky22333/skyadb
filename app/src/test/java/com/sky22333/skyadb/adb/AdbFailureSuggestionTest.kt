package com.sky22333.skyadb.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbFailureSuggestionTest {
    @Test
    fun stripsInstallFailedPrefix() {
        val suggestion = adbFailureSuggestion(
            error = IllegalStateException("Install failed: INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
            fallback = "fallback",
        )
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", suggestion)
    }

    @Test
    fun stripsSyncFailedPrefix() {
        val suggestion = adbFailureSuggestion(
            error = IllegalStateException("Sync failed: Permission denied"),
            fallback = "fallback",
        )
        assertEquals("Permission denied", suggestion)
    }

    @Test
    fun fallsBackWhenMessageMissing() {
        val suggestion = adbFailureSuggestion(
            error = RuntimeException(),
            fallback = "请稍后重试",
        )
        assertEquals("请稍后重试", suggestion)
    }
}
