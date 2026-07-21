package com.sky22333.skyadb.discovery

import com.flyfishxu.kadb.mdns.MdnsServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbMdnsDiscoveryTest {
    @Test
    fun serviceTypes_mapFromKadbMdns() {
        assertEquals(AdbMdnsServiceType.Pairing, AdbMdnsServiceType.from(MdnsServiceType.TLS_PAIRING))
        assertEquals(AdbMdnsServiceType.Connect, AdbMdnsServiceType.from(MdnsServiceType.TLS_CONNECT))
        assertEquals(AdbMdnsServiceType.Legacy, AdbMdnsServiceType.from(MdnsServiceType.ADB))
    }

    @Test
    fun serviceTypes_exposeUserActionsByRole() {
        assertEquals("配对", AdbMdnsServiceType.Pairing.actionLabel)
        assertEquals("连接", AdbMdnsServiceType.Connect.actionLabel)
        assertEquals("连接", AdbMdnsServiceType.Legacy.actionLabel)
        assertTrue(AdbMdnsServiceType.Pairing.description.contains("配对码"))
    }

    @Test
    fun endpoint_exposesStableIdAndEndpointText() {
        val endpoint = AdbMdnsEndpoint(
            name = "Redmi",
            host = "192.168.1.23",
            port = 37125,
            type = AdbMdnsServiceType.Pairing,
        )

        assertEquals("Pairing:192.168.1.23:37125", endpoint.id)
        assertEquals("192.168.1.23:37125", endpoint.endpoint)
    }
}
