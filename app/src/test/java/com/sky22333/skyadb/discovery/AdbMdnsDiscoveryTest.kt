package com.sky22333.skyadb.discovery

import com.sky22333.skyadb.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbMdnsDiscoveryTest {
    @Test
    fun serviceTypes_useOfficialAdbDnsSdNames() {
        assertEquals("_adb-tls-pairing._tcp.", AdbMdnsServiceType.Pairing.nsdType)
        assertEquals("_adb-tls-connect._tcp.", AdbMdnsServiceType.Connect.nsdType)
        assertEquals("_adb._tcp.", AdbMdnsServiceType.Legacy.nsdType)
    }

    @Test
    fun serviceTypes_exposeUserActionsByRole() {
        assertEquals(R.string.mdns_service_pairing_action, AdbMdnsServiceType.Pairing.actionLabelRes)
        assertEquals(R.string.mdns_service_connect_action, AdbMdnsServiceType.Connect.actionLabelRes)
        assertEquals(R.string.mdns_service_connect_action, AdbMdnsServiceType.Legacy.actionLabelRes)
        assertEquals(R.string.mdns_service_pairing_desc, AdbMdnsServiceType.Pairing.descriptionRes)
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
    fun connectPortForHost_prefersConnectServiceOnSameHost() {
        val endpoints = listOf(
            AdbMdnsEndpoint("pair", "192.168.1.23", 37125, AdbMdnsServiceType.Pairing),
            AdbMdnsEndpoint("connect", "192.168.1.23", 41567, AdbMdnsServiceType.Connect),
            AdbMdnsEndpoint("other", "192.168.1.24", 5555, AdbMdnsServiceType.Connect),
        )

        assertEquals(41567, endpoints.connectPortForHost("192.168.1.23"))
        assertNull(endpoints.connectPortForHost("192.168.1.99"))
        assertNull(endpoints.connectPortForHost(""))
    }
}
