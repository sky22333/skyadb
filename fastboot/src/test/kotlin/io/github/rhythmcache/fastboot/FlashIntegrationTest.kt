package io.github.rhythmcache.fastboot

import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlashIntegrationTest {
    @Test fun resolvesCurrentSlotBeforeDownloading() = runBlocking {
        val device = FakeBootloader(16384, "16384", 4096, variables = mapOf("has-slot:boot" to "yes", "current-slot" to "b"))
        FastbootClient(device).flash("boot", ByteArray(1024))
        assertTrue("flash:boot_b" in device.commands)
        assertTrue(device.commands.indexOf("getvar:current-slot") < device.commands.indexOfFirst { it.startsWith("download:") })
    }

    @Test fun explicitSlotIsPreserved() = runBlocking {
        val device = FakeBootloader(16384, "16384", 4096)
        FastbootClient(device).flash("boot_a", ByteArray(1024))
        assertTrue("flash:boot_a" in device.commands)
        assertTrue(device.commands.none { it.startsWith("getvar:has-slot:") })
    }

    @Test fun missingCurrentSlotDoesNotDownload() = runBlocking {
        val device = FakeBootloader(16384, "16384", 4096, variables = mapOf("has-slot:boot" to "yes"))
        assertThrows(FastbootException.DeviceFail::class.java) { runBlocking { FastbootClient(device).flash("boot", ByteArray(1024)) } }
        assertTrue(device.downloadSizes.isEmpty())
    }

    @Test fun fetchesPartitionInChunks() = runBlocking {
        val device = FakeBootloader(16384, "16384", 13, variables = mapOf("has-slot:vendor_boot" to "yes", "current-slot" to "a", "max-fetch-size" to "5", "partition-size:vendor_boot_a" to "13"))
        device.partition.indices.forEach { device.partition[it] = it.toByte() }
        val output = java.io.ByteArrayOutputStream()
        val progress = mutableListOf<Progress>()
        FastbootClient(device).fetch("vendor_boot", { bytes, length -> output.write(bytes, 0, length) }, onProgress = { progress += it })
        assertArrayEquals(device.partition, output.toByteArray())
        assertEquals(3, device.commands.count { it.startsWith("fetch:vendor_boot_a:") })
        assertEquals(Progress(13, 13), progress.last())
    }
    @Test fun splitRawImageReconstructsPartition() = runBlocking {
        val source = ByteArray(256 * 1024).also { Random(2026).nextBytes(it) }
        val device = FakeBootloader(16384, "0x4000", source.size)
        FastbootClient(device).flash("test", source)
        assertTrue(device.downloadSizes.size > 1)
        assertTrue(device.downloadSizes.all { it <= 16384 })
        assertArrayEquals(source, device.partition)
    }
    @Test fun resplitsSparseStreamAndReconstructsPartition() = runBlocking {
        val raw = ByteArray(128 * 1024).also { Random(9).nextBytes(it) }
        val rawSource = io.github.rhythmcache.fastboot.sparse.ByteArraySource(raw)
        val layout = io.github.rhythmcache.fastboot.sparse.ImageAnalyzer.analyze(rawSource)
        val sparse = io.github.rhythmcache.fastboot.sparse.SparsePlanner.plan(layout, 1024 * 1024).single()
        val encoded = ByteArray(sparse.size.toInt())
        assertEquals(encoded.size, sparse.read(rawSource, 0, encoded, 0, encoded.size))
        val image = kotlin.io.path.createTempFile().toFile()
        try {
            image.writeBytes(encoded)
            val device = FakeBootloader(16384, "0x4000", raw.size)
            image.inputStream().use { FastbootClient(device).flash("system", it, image.length()) }
            assertTrue(device.downloadSizes.size > 1)
            assertArrayEquals(raw, device.partition)
        } finally { image.delete() }
    }

    @Test fun explicitDownloadLimitWorksWhenDeviceOmitsLimit() = runBlocking {
        val raw = ByteArray(64 * 1024).also { Random(11).nextBytes(it) }
        val image = kotlin.io.path.createTempFile().toFile()
        try {
            image.writeBytes(raw)
            val device = FakeBootloader(16384, null, raw.size)
            image.inputStream().use { FastbootClient(device).flash("system", it, image.length(), maxChunkSize = 16384) }
            assertTrue("getvar:max-download-size" !in device.commands)
            assertArrayEquals(raw, device.partition)
        } finally { image.delete() }
    }

    @Test fun streamSplitReportsCompletedPiecesBeforeDeviceFailure() = runBlocking {
        val bytes = ByteArray(64 * 1024).also { Random(7).nextBytes(it) }
        val image = kotlin.io.path.createTempFile().toFile()
        try {
            image.writeBytes(bytes)
            val device = FakeBootloader(16384, "0x4000", bytes.size, failFlashAt = 2)
            val messages = mutableListOf<String>()
            image.inputStream().use { stream ->
                assertThrows(FastbootException.DeviceFail::class.java) {
                    runBlocking { FastbootClient(device).flash("system", stream, image.length(), onInfo = { messages += it }) }
                }
            }
            assertEquals(2, device.commands.count { it == "flash:system" })
            assertTrue(messages.any { it.startsWith("Completed sparse 'system' 1/") })
            assertTrue(messages.any { it.startsWith("Writing 'system' 2/") })
        } finally { image.delete() }
    }
}
