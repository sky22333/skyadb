package com.sky22333.skyadb.ai

import android.content.ContextWrapper
import com.sky22333.skyadb.AppServices
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first

internal fun testAiStore(): AiConfigStore {
    val directory = File(AppServices.context.cacheDir, "ai-test-${UUID.randomUUID()}").apply { mkdirs() }
    return AiConfigStore(object : ContextWrapper(AppServices.context) {
        override fun getNoBackupFilesDir(): File = directory
    })
}

internal suspend fun AiViewModel.attachFiles(vararg uris: android.net.Uri) {
    addAttachments(uris.toList())
    kotlinx.coroutines.withTimeout(10000) { uiState.first { !it.attachmentsLoading } }
    check(uiState.value.error == null) { uiState.value.error.orEmpty() }
}
