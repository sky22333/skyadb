package com.sky22333.skyadb.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SelectedArchiveTest {
    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
        return out.toByteArray()
    }
    @Test fun listsAndReadsSelectedEntryWithoutExtracting() = runBlocking {
        val zip = archive("logs/report.txt" to "hello 世界".toByteArray(), "data.bin" to byteArrayOf(0, 1))
        assertTrue(inspectArchive(ByteArrayInputStream(zip)).contains("logs/report.txt"))
        assertEquals("hello 世界", inspectArchive(ByteArrayInputStream(zip), "logs/report.txt"))
        assertEquals("Entry not found", inspectArchive(ByteArrayInputStream(zip), "missing.txt"))
        assertTrue(runCatching { inspectArchive(ByteArrayInputStream(zip), "data.bin") }.isFailure)
    }
    @Test fun oversizedEntryAndInvalidArchiveAreBounded() = runBlocking {
        val zip = archive("large.txt" to "x".repeat(5_000_000).toByteArray())
        assertTrue(inspectArchive(ByteArrayInputStream(zip)).contains("[archive listing truncated]"))
        assertTrue(inspectArchive(ByteArrayInputStream(zip), "large.txt").endsWith("[truncated]"))
        assertTrue(runCatching { inspectArchive(ByteArrayInputStream("not zip".toByteArray())) }.isFailure)
    }
}
