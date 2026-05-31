package com.sky22333.skyadb.fastboot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FastbootProtocolTest {
    @Test
    fun parseResponse_readsOkayFailInfoAndData() {
        assertEquals(FastbootResponse.Okay("done"), response("OKAYdone"))
        assertEquals(FastbootResponse.Fail("bad"), response("FAILbad"))
        assertEquals(FastbootResponse.Info("step"), response("INFOstep"))
        assertEquals(FastbootResponse.Data(4096), response("DATA00001000"))
    }

    @Test
    fun validateCommand_rejectsBlankAndTooLongCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            FastbootProtocol.validateCommand("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastbootProtocol.validateCommand("a".repeat(65))
        }
        assertEquals("getvar:all", FastbootProtocol.validateCommand(" getvar:all "))
    }

    @Test
    fun commandPolicy_marksDangerousCommands() {
        assertTrue(FastbootCommandPolicy.prepare("flash:boot").requiresConfirmation)
        assertTrue(FastbootCommandPolicy.prepare("erase:userdata").requiresConfirmation)
        assertTrue(FastbootCommandPolicy.prepare("flashing unlock").requiresConfirmation)
        assertTrue(FastbootCommandPolicy.prepare("boot").requiresConfirmation)
        assertFalse(FastbootCommandPolicy.prepare("getvar:all").requiresConfirmation)
        assertFalse(FastbootCommandPolicy.prepare("reboot").requiresConfirmation)
    }

    @Test
    fun shouldDownloadBeforeCommand_onlyForFileBackedCommands() {
        val file = File("boot.img")
        assertTrue(FastbootProtocol.shouldDownloadBeforeCommand("flash:boot", file))
        assertTrue(FastbootProtocol.shouldDownloadBeforeCommand("boot", file))
        assertTrue(FastbootProtocol.shouldDownloadBeforeCommand("download:00000004", file))
        assertFalse(FastbootProtocol.shouldDownloadBeforeCommand("getvar:all", file))
        assertFalse(FastbootProtocol.shouldDownloadBeforeCommand("flash:boot", null))
        assertFalse(FastbootProtocol.shouldExecuteAfterDownload("download:00000004"))
        assertTrue(FastbootProtocol.shouldExecuteAfterDownload("flash:boot"))
    }

    private fun response(value: String): FastbootResponse {
        val bytes = value.encodeToByteArray()
        return FastbootProtocol.parseResponse(bytes, bytes.size)
    }
}
