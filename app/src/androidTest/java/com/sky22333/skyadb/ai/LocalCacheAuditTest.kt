package com.sky22333.skyadb.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.files.LocalFileManager
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCacheAuditTest {
    @Test fun failedCopyRemovesItsTemporaryDirectory() {
        val context = AppServices.context
        val picked = File(context.cacheDir, "picked")
        val before = picked.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            val missing = File(context.cacheDir, "missing-${UUID.randomUUID()}")
            assertTrue(runCatching { LocalFileManager(context).copyToCache(Uri.fromFile(missing), "test.apk") }.isFailure)
            val after = picked.listFiles()?.map { it.name }?.toSet().orEmpty()
            assertEquals(before, after)
        } finally {
            picked.listFiles()?.filter { it.name !in before }?.forEach { it.deleteRecursively() }
        }
    }
}
