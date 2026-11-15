package io.github.rhythmcache.fastboot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandProtocolTest {
    private class Transport(vararg replies: String) : FastbootTransport {
        val responses = ArrayDeque(replies.toList())
        val commands = mutableListOf<String>()
        override fun write(buffer: ByteArray, offset: Int, length: Int) { commands += String(buffer, offset, length, Charsets.US_ASCII) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val bytes = responses.removeFirst().toByteArray()
            bytes.copyInto(buffer, offset)
            return bytes.size
        }
        override fun close() = Unit
    }

    @Test fun drainsInfoAndTextWithoutRepeatingCommand() = runBlocking {
        val transport = Transport("INFOone", "TEXTtwo", "OKAYdone")
        val events = mutableListOf<String>()
        val result = FastbootConnection(transport).rawCommand("getvar:all", { events += it }, { events += it })
        assertEquals(listOf("one", "two"), events)
        assertEquals("done", result.response)
        assertEquals(listOf("getvar:all"), transport.commands)
    }

    @Test fun deviceFailureLeavesConnectionUsable() = runBlocking {
        val transport = Transport("FAILunsupported", "OKAYproduct")
        val connection = FastbootConnection(transport)
        assertThrows(FastbootException.DeviceFail::class.java) { runBlocking { connection.rawCommand("getvar:bad") } }
        assertEquals("product", connection.rawCommand("getvar:product").response)
    }

    @Test fun rejectsInvalidCommandsAndUnexpectedData() = runBlocking {
        val transport = Transport("DATA00000001")
        val connection = FastbootConnection(transport)
        assertThrows(FastbootException.BadArgument::class.java) { runBlocking { connection.rawCommand("bad\ncommand") } }
        assertTrue(transport.commands.isEmpty())
        assertThrows(FastbootException.Protocol::class.java) { runBlocking { connection.rawCommand("getvar:x") } }
        Unit
    }
}
