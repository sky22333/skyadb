package com.sky22333.skyadb.ai

import com.sky22333.skyadb.validation.FileNamePolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun processFirmwareArchive(archive: File, action: String, entryName: String, downloads: File): String =
    ZipFile(archive).use { zip ->
        if (action == "list") {
            val entries = zip.entries().asSequence().take(501).toList()
            entries.take(500).joinToString("\n") { "${if (it.isDirectory) "dir" else "file"} ${it.name.replace('\n', ' ')} (${it.size} bytes)" } + if (entries.size > 500) "\n[truncated]" else ""
        } else {
            val entry = requireNotNull(zip.getEntry(entryName)) { "Archive entry not found" }
            require(!entry.isDirectory) { "Archive entry is a directory" }
            val directory = File(downloads, UUID.randomUUID().toString())
            check(directory.mkdirs()) { "Cannot create download directory" }
            val file = File(directory, FileNamePolicy.sanitize(entry.name.substringAfterLast('/')))
            var complete = false
            try {
                zip.getInputStream(entry).use { input -> file.outputStream().use { output -> copyFirmwareData(input, output) } }
                complete = true
                "Extracted ${entry.name} to ${file.absolutePath} (${file.length()} bytes)"
            } finally {
                if (!complete) { file.delete(); directory.delete() }
            }
        }
    }

internal suspend fun copyFirmwareData(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(1024 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) return
        output.write(buffer, 0, count)
    }
}
