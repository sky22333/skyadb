package com.sky22333.skyadb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiFastbootCommandTest {
    @Test fun normalizesCommandsAndKeepsOemExtensions() {
        assertEquals("getvar:product", fastbootWireCommand("fastboot getvar product"))
        assertEquals("oem device-info", fastbootWireCommand("fastboot oem device-info"))
        assertEquals("set_active:b", fastbootWireCommand("set_active b"))
    }

    @Test fun dataCommandsUseTransferTools() {
        for (command in listOf("download:00001000", "upload", "fetch:boot", "fastboot flash boot image.img")) {
            assertThrows(IllegalArgumentException::class.java) { fastbootWireCommand(command) }
        }
    }
}
