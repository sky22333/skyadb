package com.sky22333.skyadb.validation

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkInputValidatorTest {
    @org.junit.Test
    fun endpointParsesExplicitAndDefaultPortsAndRejectsInvalidInput() {
        val explicit = NetworkInputValidator.endpoint(" 192.168.1.20:12345 ", 5555)
        org.junit.Assert.assertTrue(explicit.isValid)
        org.junit.Assert.assertEquals("192.168.1.20", explicit.host)
        org.junit.Assert.assertEquals(12345, explicit.port)
        org.junit.Assert.assertEquals(5555, NetworkInputValidator.endpoint("192.168.1.20", 5555).port)
        listOf("", "192.168.1.20:", "192.168.1.20:0", "192.168.1.20:65536", "192.168.1.20:12:3", "192.168.1.999:5555", ":5555")
            .forEach { org.junit.Assert.assertFalse(it, NetworkInputValidator.endpoint(it, 5555).isValid) }
    }
    @Test
    fun ipv4Error_acceptsValidIpv4() {
        assertNull(NetworkInputValidator.ipv4Error("192.168.1.86"))
        assertNull(NetworkInputValidator.ipv4Error("0.0.0.0"))
        assertNull(NetworkInputValidator.ipv4Error("255.255.255.255"))
    }

    @Test
    fun ipv4Error_rejectsInvalidIpv4() {
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipv4Error("192.168.1.999"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipv4Error("192.168.1"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipv4Error("example.com"))
    }

    @Test
    fun portError_acceptsValidPort() {
        assertNull(NetworkInputValidator.portError("1"))
        assertNull(NetworkInputValidator.portError("5555"))
        assertNull(NetworkInputValidator.portError("65535"))
    }

    @Test
    fun portError_rejectsInvalidPort() {
        assertEquals(
            AppText.Res(R.string.error_port_not_number, AppText.Res(R.string.unit_port)),
            NetworkInputValidator.portError("abc"),
        )
        assertEquals(
            AppText.Res(R.string.error_port_out_of_range, AppText.Res(R.string.unit_port)),
            NetworkInputValidator.portError("0"),
        )
        assertEquals(
            AppText.Res(R.string.error_port_out_of_range, AppText.Res(R.string.unit_port)),
            NetworkInputValidator.portError("65536"),
        )
        assertEquals(
            AppText.Res(R.string.error_port_out_of_range, AppText.Res(R.string.pairing_port_label)),
            NetworkInputValidator.portError("70000", labelRes = R.string.pairing_port_label),
        )
    }
}
