package com.sky22333.skyadb.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidUsbInterfaceTest {
    @Test
    fun isAndroidDebugInterface_recognizesAdbAndFastboot() {
        assertEquals(
            UsbOtgMode.Adb,
            AndroidUsbInterface.isAndroidDebugInterface(0xFF, 0x42, 0x01),
        )
        assertEquals(
            UsbOtgMode.Fastboot,
            AndroidUsbInterface.isAndroidDebugInterface(0xFF, 0x42, 0x03),
        )
    }

    @Test
    fun isAndroidDebugInterface_rejectsNonAndroidInterfaces() {
        assertNull(AndroidUsbInterface.isAndroidDebugInterface(0xFF, 0x42, 0x02))
        assertNull(AndroidUsbInterface.isAndroidDebugInterface(0x08, 0x42, 0x01))
        assertNull(AndroidUsbInterface.isAndroidDebugInterface(0xFF, 0x00, 0x01))
    }
}
