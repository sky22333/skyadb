package com.sky22333.skyadb.ai

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FirmwareArchiveTest {
    @Test fun listsAndExtractsOnlyTheRequestedImage() = runBlocking {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val archive = File(root, "firmware.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                for ((name, text) in listOf("images/boot.img" to "boot", "images/system.img" to "system")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
            }
            val downloads = File(root, "downloads")
            val listing = processFirmwareArchive(archive, "list", "", downloads)
            assertTrue(listing.contains("images/boot.img"))
            assertTrue(listing.contains("images/system.img"))
            processFirmwareArchive(archive, "extract", "images/boot.img", downloads)
            val files = downloads.listFiles()!!.single().listFiles()!!
            assertEquals(listOf("boot.img"), files.map { it.name })
            assertEquals("boot", files.single().readText())
        } finally { root.deleteRecursively() }
    }
}
