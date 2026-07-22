package com.sky22333.skyadb.apps

import com.sky22333.skyadb.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDisplayEnricherTest {
    @Test
    fun weakLabel_detectsPackageTailFallback() {
        assertTrue(AppDisplayEnricher.isWeakLabel("mm", "com.tencent.mm"))
        assertTrue(AppDisplayEnricher.isWeakLabel("com.tencent.mm", "com.tencent.mm"))
        assertFalse(AppDisplayEnricher.isWeakLabel("微信", "com.tencent.mm"))
    }

    @Test
    fun mergeRemoteLabels_onlyFillsWeakOnes() {
        val apps = listOf(
            AppInfo("com.tencent.mm", "微信", isSystem = false),
            AppInfo("com.demo.app", "app", isSystem = false),
        )
        val merged = AppDisplayEnricher.mergeRemoteLabels(
            apps = apps,
            remoteLabels = mapOf(
                "com.tencent.mm" to "WeChat",
                "com.demo.app" to "Demo",
            ),
        )
        assertEquals("微信", merged.first { it.packageName == "com.tencent.mm" }.label)
        assertEquals("Demo", merged.first { it.packageName == "com.demo.app" }.label)
    }
}
