package com.sky22333.skyadb.data

import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentDeviceCodecTest {
    @Test
    fun roundTripPreservesEscapedFieldsAndResetsConnection() {
        val device = AdbDevice(
            id = "device%1", name = "A|B\nC", host = "192.168.1.2", port = 5555,
            connectionState = ConnectionState.Connected,
        )
        assertEquals(device.copy(connectionState = ConnectionState.Disconnected), RecentDeviceCodec.decode(RecentDeviceCodec.encode(device)))
    }

    @Test
    fun rejectsRecordsWithIncorrectFieldCount() {
        assertNull(RecentDeviceCodec.decode("id|name|host|5555|extra"))
        assertNull(RecentDeviceCodec.decode("id|name|host"))
    }
}
