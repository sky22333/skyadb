package io.github.rhythmcache.fastboot

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadProtocolTest {
    private class Device(private val ack: String) : FastbootTransport {
        val writes = mutableListOf<String>()
        override fun write(buffer: ByteArray, offset: Int, length: Int) { writes += String(buffer, offset, length, Charsets.US_ASCII) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val bytes = ack.toByteArray(Charsets.US_ASCII)
            bytes.copyInto(buffer, offset)
            return bytes.size
        }
        override fun close() = Unit
    }

    @Test fun rejectsMismatchedDataLengthBeforeSendingPayload() = runBlocking {
        val device = Device("DATA00000001")
        val connection = FastbootConnection(device)
        assertThrows(FastbootException.Protocol::class.java) {
            runBlocking { connection.executeDownload(64, { _, _, _, _ -> 0 }) }
        }
        assertEquals(listOf("download:00000040"), device.writes)
    }

    @Test fun rejectsMalformedDataLength() = runBlocking {
        val device = Device("DATA1")
        assertThrows(FastbootException.Protocol::class.java) {
            runBlocking { FastbootConnection(device).executeDownload(64, { _, _, _, _ -> 0 }) }
        }
        assertEquals(listOf("download:00000040"), device.writes)
    }

    @Test fun boundsEmptyInfoResponses() = runBlocking {
        var count = 0
        val transport = object : FastbootTransport {
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val response = if (count++ < 70000) "INFO" else "OKAY"
                response.toByteArray().copyInto(buffer, offset)
                return response.length
            }
            override fun close() = Unit
        }
        val result = FastbootConnection(transport).rawCommand("getvar:all")
        assertEquals(4096, result.info.size)
    }

    @Test fun propagatesTransportCancellation() {
        val transport = object : FastbootTransport {
            override fun write(buffer: ByteArray, offset: Int, length: Int) { throw CancellationException("cancelled") }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
            override fun close() = Unit
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { FastbootConnection(transport).rawCommand("getvar:all") }
        }
    }
    @Test fun acceptsInfoBeforeDataAcknowledgement() = runBlocking {
        val responses = ArrayDeque(listOf("INFOpreparing", "DATA00000004", "OKAY"))
        val writes = mutableListOf<ByteArray>()
        val transport = object : FastbootTransport {
            override fun write(buffer: ByteArray, offset: Int, length: Int) { writes += buffer.copyOfRange(offset, offset + length) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val bytes = responses.removeFirst().toByteArray()
                bytes.copyInto(buffer, offset)
                return bytes.size
            }
            override fun close() = Unit
        }
        val image = byteArrayOf(1, 2, 3, 4)
        val info = mutableListOf<String>()
        FastbootConnection(transport).executeDownload(4, { offset, dest, destOffset, length ->
            image.copyInto(dest, destOffset, offset.toInt(), offset.toInt() + length)
            length
        }, onInfo = { info += it })
        assertEquals(listOf("preparing"), info)
        org.junit.Assert.assertArrayEquals(image, writes.last())
    }

    @Test fun streamsDeviceUploadAndWaitsForTerminalResponse() = runBlocking {
        val packets = ArrayDeque(listOf("DATA00000004".toByteArray(), byteArrayOf(1, 2, 3, 4), "OKAY".toByteArray()))
        val transport = object : FastbootTransport {
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val bytes = packets.removeFirst()
                bytes.copyInto(buffer, offset)
                return bytes.size
            }
            override fun close() = Unit
        }
        val output = java.io.ByteArrayOutputStream()
        FastbootClient(transport).upload({ data, length -> output.write(data, 0, length) })
        org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
        org.junit.Assert.assertTrue(packets.isEmpty())
    }
}
