package com.sky22333.skyadb.ai

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiConfigSaveTest {
    @Test fun saveFailureDoesNotCallSuccessOrReplaceConfig() = runBlocking {
        val parent = File.createTempFile("ai-config-test", ".tmp", AppServices.context.cacheDir)
        val context = object : ContextWrapper(AppServices.context) {
            override fun getNoBackupFilesDir(): File = parent
        }
        try {
            val vm = AiViewModel(AiConfigStore(context))
            withTimeout(10000) { vm.uiState.first { !it.historyLoading } }
            var saved = false
            assertTrue(vm.saveConfig(AiConfig("https://example.com/v1", "test", "model")) { saved = true })
            val state = withTimeout(10000) { vm.uiState.first { !it.savingConfig && it.error != null } }
            assertFalse(saved)
            assertEquals(AiConfig(), state.config)
        } finally { parent.delete() }
    }
}
