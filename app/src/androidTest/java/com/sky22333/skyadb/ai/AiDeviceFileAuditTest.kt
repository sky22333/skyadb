package com.sky22333.skyadb.ai

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDeviceFileAuditTest {
    @Test fun legacyShellReportsExitCode() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("targetHost")
        val port = args.getString("targetPort")?.toIntOrNull()
        assumeTrue("Requires an authorized target device", !host.isNullOrBlank() && port != null)
        val manager = KadbManager()
        assertTrue(manager.connect(requireNotNull(host), requireNotNull(port)) is AdbOperationResult.Success)
        try {
            val clientField = KadbManager::class.java.getDeclaredField("activeKadb").apply { isAccessible = true }
            val client = requireNotNull(clientField.get(manager))
            val connectionField = client.javaClass.getDeclaredField("connection").apply { isAccessible = true }
            val connection = (requireNotNull(connectionField.get(client)) as Pair<*, *>).first!!
            val featuresField = connection.javaClass.getDeclaredField("supportedFeatures").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val features = featuresField.get(connection) as Set<String>
            featuresField.set(connection, features - "shell_v2")
            val success = (manager.shell("printf legacy_ok; exit 0") as AdbOperationResult.Success).data
            val failure = (manager.shell("exit 37") as AdbOperationResult.Success).data
            assertEquals("legacy_ok", success.output)
            assertEquals(0, success.exitCode)
            assertEquals(37, failure.exitCode)
            val large = (manager.shell("dd if=/dev/zero bs=1024 count=5120 2>/dev/null; exit 23", maxOutputBytes = 4096) as AdbOperationResult.Success).data
            assertEquals(23, large.exitCode)
            assertTrue(large.output.contains("[output truncated]"))
            assertTrue(large.output.length <= 4200)
        } finally { manager.disconnect() }
    }

    @Test fun textDocumentKeepsRequestedName() = runBlocking {
        val context = AppServices.context
        val config = AiConfigStore(context).load()
        assumeTrue("Requires an already authorized document directory", config.documentTree.isNotBlank())
        val tree = Uri.parse(config.documentTree)
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val folderName = "skyadb-audit-${UUID.randomUUID()}"
        val folder = requireNotNull(DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, folderName))
        try {
            val docs = AiDocuments(context, tree)
            docs.write("$folderName/config.json", "{}")
            assertTrue(docs.list(folderName).lines().contains("file config.json"))
            assertEquals("{}", docs.read("$folderName/config.json").bufferedReader().use { it.readText() })
            docs.write("$folderName/README", "test")
            assertTrue(docs.list(folderName).lines().contains("file README"))
        } finally { DocumentsContract.deleteDocument(resolver, folder) }
    }
    @Test fun shellRetainsExitCodeAfterLargeOutput() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("targetHost")
        val port = args.getString("targetPort")?.toIntOrNull()
        assumeTrue("Requires an authorized target device", !host.isNullOrBlank() && port != null)
        val manager = KadbManager()
        val connected = manager.connect(requireNotNull(host), requireNotNull(port))
        assertTrue("ADB connection failed: $connected", connected is AdbOperationResult.Success)
        try {
            val result = manager.shell("dd if=/dev/zero bs=1024 count=5120 2>/dev/null; exit 37", maxOutputBytes = 4096, timeoutMillis = 30000)
            assertTrue("Shell failed: $result", result is AdbOperationResult.Success)
            val shell = (result as AdbOperationResult.Success).data
            println("AUDIT_LARGE_OUTPUT exit=${shell.exitCode} truncated=${shell.output.contains("[output truncated]")}")
            assertEquals("Exit status must be available even after output truncation", 37, shell.exitCode)
            assertTrue(shell.output.contains("[output truncated]"))
            assertTrue(shell.output.length <= 4200)
        } finally { manager.disconnect() }
    }

    @Test fun binaryMimeAndInterruptedOverwriteOnSelectedProvider() = runBlocking {
        val context = AppServices.context
        val config = AiConfigStore(context).load()
        assumeTrue("Requires an already authorized document directory", config.documentTree.isNotBlank())
        val tree = Uri.parse(config.documentTree)
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val folderName = "skyadb-audit-${UUID.randomUUID()}"
        val folderUri = requireNotNull(DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, folderName))
        val folderId = DocumentsContract.getDocumentId(folderUri)
        val source = File.createTempFile("skyadb-audit-source-", ".bin", context.cacheDir)
        val created = mutableListOf<Uri>()
        fun find(name: String, prefix: Boolean = false): Uri? {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
            return requireNotNull(resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)).use { cursor ->
                var found: Uri? = null
                while (cursor.moveToNext()) if (cursor.getString(1) == name || (prefix && cursor.getString(1).startsWith(name))) {
                    println("AUDIT_NAME requested=$name actual=${cursor.getString(1)}")
                    found = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)); break
                }
                found
            }
        }
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            AgentTools.transfer(config, "app", source.absolutePath, "authorized", "$folderName/sample.apk", emptyMap())
            val apk = requireNotNull(find("sample.apk", prefix = true)); created += apk
            val mime = resolver.getType(apk)
            println("AUDIT_PROVIDER authority=${tree.authority} mime=$mime")
            val original = "skyadb-original-file".toByteArray()
            val destination = requireNotNull(DocumentsContract.createDocument(resolver, folderUri, "application/octet-stream", "overwrite.bin")); created += destination
            requireNotNull(resolver.openOutputStream(destination, "wt")).use { it.write(original) }
            RandomAccessFile(source, "rw").use { it.setLength(64L * 1024 * 1024) }
            val job = async(Dispatchers.IO) { AgentTools.transfer(config, "app", source.absolutePath, "authorized", "$folderName/overwrite.bin", emptyMap()) }
            var observedPartial = false
            withTimeout(30000) {
                while (job.isActive) {
                    val staging = find(".skyadb-transfer-", prefix = true)
                    val size = staging?.let { runCatching { resolver.openFileDescriptor(it, "r")?.use { fd -> fd.statSize } ?: -1L }.getOrDefault(-1L) } ?: -1L
                    if (size in 1 until source.length()) {
                        observedPartial = true
                        job.cancelAndJoin()
                        break
                    }
                    delay(1)
                }
            }
            if (job.isActive) job.cancelAndJoin() else runCatching { job.await() }
            val after = requireNotNull(resolver.openInputStream(destination)).use { input ->
                val buffer = ByteArray(original.size + 1)
                val count = input.read(buffer).coerceAtLeast(0)
                buffer.copyOf(count)
            }
            println("AUDIT_INTERRUPTED partial=$observedPartial originalPreserved=${after.contentEquals(original)}")
            assertEquals("APK document must have an APK MIME type", "application/vnd.android.package-archive", mime)
            assertTrue("Did not observe a transfer in progress; retry on this provider", observedPartial)
            assertArrayEquals("Interrupted overwrite must preserve the original file", original, after)
            source.writeBytes("replacement".toByteArray())
            AgentTools.transfer(config, "app", source.absolutePath, "authorized", "$folderName/overwrite.bin", emptyMap())
            val replacement = requireNotNull(find("overwrite.bin"))
            assertArrayEquals("replacement".toByteArray(), requireNotNull(resolver.openInputStream(replacement)).use { it.readBytes() })
        } finally {
            source.delete()
            created.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            runCatching { DocumentsContract.deleteDocument(resolver, folderUri) }
        }
    }
}
