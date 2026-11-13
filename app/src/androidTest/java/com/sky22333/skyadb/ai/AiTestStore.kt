package com.sky22333.skyadb.ai

import android.content.ContextWrapper
import com.sky22333.skyadb.AppServices
import java.io.File
import java.util.UUID

internal fun testAiStore(): AiConfigStore {
    val directory = File(AppServices.context.cacheDir, "ai-test-${UUID.randomUUID()}").apply { mkdirs() }
    return AiConfigStore(object : ContextWrapper(AppServices.context) {
        override fun getNoBackupFilesDir(): File = directory
    })
}
