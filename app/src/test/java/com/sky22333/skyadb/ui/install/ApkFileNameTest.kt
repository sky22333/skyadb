package com.sky22333.skyadb.ui.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFileNameTest {
    @Test fun acceptsApksWithOptionalNumericSuffix() {
        listOf("app.apk", "app.APK.1", "app.apk.123").forEach { assertTrue(it, isApkFileName(it)) }
        listOf("app.apk.", "app.apk.bak", "app.apk.1.zip", "app.xapk", "app.1").forEach { assertFalse(it, isApkFileName(it)) }
    }
}
