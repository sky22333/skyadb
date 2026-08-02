package com.sky22333.skyadb.apps

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
}
