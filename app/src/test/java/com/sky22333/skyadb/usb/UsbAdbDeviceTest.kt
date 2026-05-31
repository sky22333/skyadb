package com.sky22333.skyadb.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbAdbDeviceTest {
    @Test
    fun descriptionFormatsVendorAndProductIds() {
        val device = UsbAdbDevice(
            id = "usb-1",
            name = "Pixel",
            vendorId = 0x18d1,
            productId = 0x4ee7,
            hasPermission = true,
        )

        assertEquals("VID:18d1 PID:4ee7", device.description)
    }
}
