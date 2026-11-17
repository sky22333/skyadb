package com.sky22333.skyadb.discovery

import com.sky22333.skyadb.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbMdnsDiscoveryTest {
    @Test
    fun serviceTypes_exposeUserActionsByRole() {
        assertEquals(R.string.mdns_service_pairing_action, AdbMdnsServiceType.Pairing.actionLabelRes)
        assertEquals(R.string.mdns_service_connect_action, AdbMdnsServiceType.Connect.actionLabelRes)
        assertEquals(R.string.mdns_service_connect_action, AdbMdnsServiceType.Legacy.actionLabelRes)

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

    @Test
    fun endpoint_bracketsIpv6InIdAndEndpointText() {
        val endpoint = AdbMdnsEndpoint(
            name = "Redmi",
            host = "2001:db8::1",
            port = 37125,
            type = AdbMdnsServiceType.Connect,
        )

        assertEquals("Connect:[2001:db8::1]:37125", endpoint.id)
        assertEquals("[2001:db8::1]:37125", endpoint.endpoint)
    }

}
