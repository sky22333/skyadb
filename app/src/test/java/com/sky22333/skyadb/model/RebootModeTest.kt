package com.sky22333.skyadb.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RebootModeTest {
    @Test fun targetsMatchBothProtocols() {
        assertEquals(listOf("", "bootloader", "recovery", "fastboot"), RebootMode.entries.map { it.adbTarget })
        assertEquals(listOf("reboot", "reboot-bootloader", "reboot-recovery", "reboot-fastboot"), RebootMode.entries.map { it.fastbootCommand })
    }
}
