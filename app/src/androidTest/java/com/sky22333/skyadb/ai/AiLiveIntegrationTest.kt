package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.model.AdbOperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiLiveIntegrationTest {
    @Test fun liveStreamPublishesBeforeCompletion() = runBlocking {
        val key = InstrumentationRegistry.getArguments().getString("apiKey")
        assumeTrue("Requires explicit live-test credentials", !key.isNullOrEmpty())
        val store = testAiStore()
        val vm = AiViewModel(store)
        val config = AiConfig("https://api.deepseek.com", requireNotNull(key), "deepseek-flash")
        withTimeout(15_000) { vm.uiState.first { !it.historyLoading } }
        vm.saveConfig(config)
        withTimeout(15_000) { vm.uiState.first { it.config == config } }
        try {
            assertTrue(vm.send("Write fifteen numbered lines describing common Android settings."))
            val partial = withTimeout(90_000) { vm.uiState.first { s -> s.busy && s.messages.any { it.role == "assistant" && it.text.isNotEmpty() } } }
            assertTrue(partial.busy)
            val completed = withTimeout(90_000) { vm.uiState.first { !it.busy } }
            assertNull("Live stream failed: ${completed.error}", completed.error)
            assertTrue(completed.messages.any { it.role == "assistant" && it.text.isNotEmpty() })
            assertTrue(vm.send("Reply with the exact text SECOND_TURN_OK."))
            val second = withTimeout(90_000) { vm.uiState.first { !it.busy } }
            assertNull("Second request failed: ${second.error}", second.error)
            assertTrue(second.messages.any { it.role == "assistant" && it.text.contains("SECOND_TURN_OK") })
        } finally { vm.stop(); store.save(AiConfig()); store.saveHistory(AiHistory()) }
    }
    @Test fun liveModelUploadsWithOriginalFilename() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val key = args.getString("apiKey")
        val port = args.getString("adbPort")?.toIntOrNull()
        assumeTrue("Requires explicit live credentials and isolated ADB device", !key.isNullOrEmpty() && port != null)
        val store = testAiStore()
        val vm = AiViewModel(store)
        val config = AiConfig("https://api.deepseek.com", requireNotNull(key), "deepseek-flash")
        withTimeout(15_000) { vm.uiState.first { !it.historyLoading } }
        vm.saveConfig(config)
        withTimeout(15_000) { vm.uiState.first { it.config == config } }
        val connection = AppServices.kadbManager.connect("127.0.0.1", requireNotNull(port), 5000, 5000)
        assertTrue("Isolated device connection failed: $connection", connection is AdbOperationResult.Success)
        val file = java.io.File(AppServices.context.cacheDir, "skyadb-upload-${java.util.UUID.randomUUID()}.txt").apply { writeText("SKYADB_UPLOAD_CONTENT") }
        val destination = "/data/local/tmp/${file.name}"
        try {
            vm.attachFiles(android.net.Uri.fromFile(file))
            assertTrue(vm.send("Upload my selected file to /data/local/tmp/ using its original filename."))
            var approved = 0
            withTimeout(120_000) {
                while (vm.uiState.value.busy) {
                    val state = vm.uiState.first { it.approval != null || !it.busy }
                    state.approval?.let { request ->
                        val allow = request.title == com.sky22333.skyadb.i18n.appString(com.sky22333.skyadb.R.string.ai_tool_upload) && request.detail.contains(destination)
                        if (allow) approved++
                        vm.respond(allow)
                    }
                }
            }
            assertNull("Live upload failed: ${vm.uiState.value.error}", vm.uiState.value.error)
            assertTrue("Original-filename upload was not requested", approved > 0)
            val result = AppServices.kadbManager.shell("cat $destination")
            assertTrue(result is AdbOperationResult.Success)
            assertEquals("SKYADB_UPLOAD_CONTENT", (result as AdbOperationResult.Success).data.output)
        } finally {
            AppServices.kadbManager.shell("rm -f $destination")
            AppServices.kadbManager.disconnect()
            file.delete(); store.save(AiConfig()); store.saveHistory(AiHistory())
        }
    }
    @Test fun liveModelReadsSharedStorageFile() = runBlocking {
        val key = InstrumentationRegistry.getArguments().getString("apiKey")
        assumeTrue("Requires explicit live-test credentials", !key.isNullOrEmpty())
        val store = testAiStore()
        val vm = AiViewModel(store)
        val config = AiConfig("https://api.deepseek.com", requireNotNull(key), "deepseek-flash")
        withTimeout(15_000) { vm.uiState.first { !it.historyLoading } }
        vm.saveConfig(config)
        withTimeout(15_000) { vm.uiState.first { it.config == config } }
        val file = java.io.File(requireNotNull(AppServices.context.getExternalFilesDir(null)), "skyadb-local-test.txt").apply { writeText("SKYADB_LOCAL_CONTENT") }
        try {
            assertTrue(vm.send("Use read_local_file to read ${file.absolutePath} and report its exact content."))
            var approvals = 0
            withTimeout(90_000) {
                while (vm.uiState.value.busy) {
                    val state = vm.uiState.first { it.approval != null || !it.busy }
                    if (state.approval != null) { approvals++; vm.respond(true) }
                }
            }
            val result = vm.uiState.value
            assertNull("Local file request failed: ${result.error}", result.error)
            assertTrue(approvals > 0)
            assertTrue(result.messages.any { it.role == "assistant" && it.text.contains("SKYADB_LOCAL_CONTENT") })
        } finally { file.delete(); store.save(AiConfig()); store.saveHistory(AiHistory()) }
    }
    @Test fun liveModelInspectsSelectedZip() = runBlocking {
        val key = InstrumentationRegistry.getArguments().getString("apiKey")
        assumeTrue("Requires explicit live-test credentials", !key.isNullOrEmpty())
        val directory = java.io.File(AppServices.context.cacheDir, "ai-live-zip").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(AppServices.context) {
            override fun getNoBackupFilesDir(): java.io.File = directory
        }
        val store = AiConfigStore(context)
        val vm = AiViewModel(store)
        val config = AiConfig("https://api.deepseek.com", requireNotNull(key), "deepseek-flash")
        withTimeout(15_000) { vm.uiState.first { !it.historyLoading } }
        vm.clear()
        vm.saveConfig(config)
        withTimeout(15_000) { vm.uiState.first { it.config == config } }
        val fileName = "skyadb-sample-${java.util.UUID.randomUUID()}.zip"
        val file = java.io.File(directory, fileName)
        val resolver = AppServices.context.contentResolver
        val contentUri = if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        } else null
        val stream = if (contentUri == null) file.outputStream() else requireNotNull(resolver.openOutputStream(contentUri))
        java.util.zip.ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("report.txt"))
            zip.write("SKYADB_ARCHIVE_CONTENT".toByteArray())
            zip.closeEntry()
        }
        try {
            vm.attachFiles(contentUri ?: android.net.Uri.fromFile(file))
            assertTrue(vm.send("Tell me the exact selected filename, then use archive tools to read report.txt and report its exact content."))
            var approvals = 0
            withTimeout(120_000) {
                while (vm.uiState.value.busy) {
                    val state = vm.uiState.first { it.approval != null || !it.busy }
                    if (state.approval != null) { approvals++; vm.respond(true) }
                }
            }
            val result = vm.uiState.value
            assertNull("Live ZIP request failed: ${result.error}", result.error)
            assertTrue("No archive operation was approved", approvals > 0)
            assertTrue(result.messages.any { it.role == "assistant" && it.text.contains(fileName, ignoreCase = true) })
            assertTrue(result.messages.any { it.role == "assistant" && it.text.contains("SKYADB_ARCHIVE_CONTENT") })
        } finally {
            if (contentUri != null) resolver.delete(contentUri, null, null) else file.delete()
            store.save(AiConfig())
            store.saveHistory(AiHistory())
        }
    }
    @Test fun boundedShellOnIsolatedDevice() = runBlocking {
        val port = InstrumentationRegistry.getArguments().getString("adbPort")?.toIntOrNull()
        assumeTrue("Requires an isolated ADB test device", port != null)
        val connection = AppServices.kadbManager.connect("127.0.0.1", requireNotNull(port), 5000, 5000)
        assertTrue("Test ADB connection failed: $connection", connection is AdbOperationResult.Success)
        try {
            val large = AppServices.kadbManager.shell("dd if=/dev/zero bs=4096 count=1500", maxOutputBytes = 20_000)
            assertTrue("Large output failed: $large", large is AdbOperationResult.Success)
            val output = (large as AdbOperationResult.Success).data.output
            assertTrue("length=${output.length}, stdout=${output.take(100)}, stderr=${large.data.errorOutput.take(200)}", output.length < 21_000 && output.contains("[output truncated]"))
            val short = AppServices.kadbManager.shell("printf SKYADB_LIVE_OK; printf ERROR_TEST >&2; exit 7")
            assertTrue("Short command failed: $short", short is AdbOperationResult.Success)
            val result = (short as AdbOperationResult.Success).data
            assertEquals("SKYADB_LIVE_OK", result.output)
            assertEquals("ERROR_TEST", result.errorOutput)
            assertEquals(7, result.exitCode)
        } finally { AppServices.kadbManager.disconnect() }
    }
    @Test fun liveModelAndFileTool() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val key = args.getString("apiKey")
        assumeTrue("Requires explicit live-test credentials", !key.isNullOrEmpty())
        val testDir = java.io.File(AppServices.context.cacheDir, "ai-live-test").apply { mkdirs() }
        val testContext = object : android.content.ContextWrapper(AppServices.context) {
            override fun getNoBackupFilesDir(): java.io.File = testDir
        }
        val store = AiConfigStore(testContext)
        val vm = AiViewModel(store)
        val config = AiConfig("https://api.deepseek.com", requireNotNull(key), "deepseek-flash")
        withTimeout(15_000) { vm.uiState.first { !it.historyLoading } }
        vm.clear()
        vm.loadModels(config)
        val discovered = withTimeout(30_000) { vm.uiState.first { !it.loadingModels } }
        assertTrue("Model discovery failed: ${discovered.error}", discovered.models.contains(config.model))
        vm.saveConfig(config)
        val saved = withTimeout(15_000) { vm.uiState.first { it.config == config || it.error != null } }
        assertTrue("Config save failed: ${saved.error}", saved.config == config)
        val file = java.io.File(testDir, "input.txt").apply { writeText("SKYADB_LIVE_OK") }
        try {
            vm.attachFiles(android.net.Uri.fromFile(file))
            assertTrue(vm.send("Use the read_selected_file tool to read my selected file and repeat its contents exactly."))
            val pending = withTimeout(90_000) { vm.uiState.first { it.approval != null || !it.busy } }
            assertNotNull("No file approval: ${pending.error}", pending.approval)
            vm.respond(true)
            val final = withTimeout(90_000) { vm.uiState.first { !it.busy } }
            assertNull("Live request failed: ${final.error}", final.error)
            assertTrue(final.messages.any { it.text.contains("SKYADB_LIVE_OK") && it.role == "assistant" })
            withTimeout(15_000) { while (store.loadHistory().conversations.none { c -> c.messages.any { it.role == "assistant" } }) kotlinx.coroutines.delay(100) }
            val restored = AiViewModel(store)
            val restoredState = withTimeout(15_000) { restored.uiState.first { !it.historyLoading } }
            assertTrue(restoredState.messages.any { it.role == "assistant" && it.text.contains("SKYADB_LIVE_OK") })
        } finally {
            file.delete()
            store.save(AiConfig())
            store.saveHistory(AiHistory())
        }
    }
}
