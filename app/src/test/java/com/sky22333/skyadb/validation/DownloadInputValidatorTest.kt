package com.sky22333.skyadb.validation

import com.sky22333.skyadb.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadInputValidatorTest {
    @Test
    fun isHttpUrl_acceptsHttpAndHttps() {
        assertTrue(DownloadInputValidator.isHttpUrl("https://example.com/app.apk"))
        assertTrue(DownloadInputValidator.isHttpUrl("http://example.com/file.zip"))
    }

    @Test
    fun isHttpUrl_rejectsUnsupportedSchemes() {
        assertFalse(DownloadInputValidator.isHttpUrl("ftp://example.com/file.zip"))
        assertFalse(DownloadInputValidator.isHttpUrl("content://downloads/file.apk"))
    }

    @Test
    fun urlError_checksApkSuffixWhenRequired() {
        assertNull(DownloadInputValidator.urlError("https://example.com/app.apk", requireApk = true))
        assertNull(DownloadInputValidator.urlError("https://example.com/app.apk?token=abc", requireApk = true))
        assertEquals(
            R.string.error_url_apk_suffix,
            DownloadInputValidator.urlError("https://example.com/download?id=1", requireApk = true),
        )
    }

    @Test
    fun urlError_rejectsNonHttpUrl() {
        assertEquals(
            R.string.error_url_scheme,
            DownloadInputValidator.urlError("ftp://example.com/file.apk"),
        )
    }
}
