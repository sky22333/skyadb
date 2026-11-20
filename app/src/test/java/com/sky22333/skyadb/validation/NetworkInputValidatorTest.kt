package com.sky22333.skyadb.validation

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkInputValidatorTest {
    @Test
    fun endpointParsesIpv4AndDefaultPorts() {
        val explicit = NetworkInputValidator.endpoint(" 192.168.1.20:12345 ", 5555)
        assertTrue(explicit.isValid)
        assertEquals("192.168.1.20", explicit.host)
        assertEquals(12345, explicit.port)
        val default = NetworkInputValidator.endpoint("192.168.1.20", 5555)
        assertEquals(5555, default.port)
        assertTrue(default.isValid)
    }

    @Test
    fun endpointParsesDnsAndIdnNames() {
        val explicit = NetworkInputValidator.endpoint("ADB.Example.COM.:12345", 5555)
        assertTrue(explicit.isValid)
        assertEquals("adb.example.com", explicit.host)
        assertEquals(12345, explicit.port)

        val defaultPort = NetworkInputValidator.endpoint("localhost", 5555)
        assertTrue(defaultPort.isValid)
        assertEquals("localhost", defaultPort.host)
        assertEquals(5555, defaultPort.port)

        val idn = NetworkInputValidator.endpoint("BÜCHER.example:37125", 5555)
        assertTrue(idn.isValid)
        assertEquals("xn--bcher-kva.example", idn.host)
        assertEquals(37125, idn.port)
    }

    @Test
    fun endpointParsesBracketedAndBareIpv6() {
        val explicit = NetworkInputValidator.endpoint("[2001:db8::1]:37125", 5555)
        assertTrue(explicit.isValid)
        assertEquals("2001:db8::1", explicit.host)
        assertEquals(37125, explicit.port)

        val bracketedDefault = NetworkInputValidator.endpoint("[2001:db8::1]", 5555)
        assertTrue(bracketedDefault.isValid)
        assertEquals("2001:db8::1", bracketedDefault.host)
        assertEquals(5555, bracketedDefault.port)

        val bareDefault = NetworkInputValidator.endpoint("2001:db8::1", 5555)
        assertTrue(bareDefault.isValid)
        assertEquals("2001:db8::1", bareDefault.host)
        assertEquals(5555, bareDefault.port)

        val bareWithPortLikeTail = NetworkInputValidator.endpoint("2001:db8::1:5555", 5555)
        assertTrue(bareWithPortLikeTail.isValid)
        assertEquals("2001:db8::1:5555", bareWithPortLikeTail.host)
        assertEquals(5555, bareWithPortLikeTail.port)
    }

    @Test
    fun endpointParsesLinkLocalZone() {
        val explicit = NetworkInputValidator.endpoint("[fe80::1%wlan0]:5555", 5555)
        assertTrue(explicit.isValid)
        assertEquals("fe80::1%wlan0", explicit.host)

        val bare = NetworkInputValidator.endpoint("fe80::1%wlan0", 5555)
        assertTrue(bare.isValid)
        assertEquals("fe80::1%wlan0", bare.host)

        val bracketedDefault = NetworkInputValidator.endpoint("[fe80::1%wlan0]", 5555)
        assertTrue(bracketedDefault.isValid)
        assertEquals("fe80::1%wlan0", bracketedDefault.host)
        assertEquals(5555, bracketedDefault.port)
    }

    @Test
    fun endpointRejectsMalformedAddressesAndPorts() {
        listOf(
            "",
            "192.168.1.20:",
            "192.168.1.20:0",
            "192.168.1.20:65536",
            "192.168.1.20:12:3",
            "192.168.1.999:5555",
            "192.168.1",
            "999",
            "-example.com",
            "example-.com",
            "example..com",
            "example_com:5555",
            "https://example.com:5555",
            "example.com/path:5555",
            "[example.com]:5555",
            ":5555",
            "[::1",
            "::1]",
            "[]:5555",
            "[192.168.1.1]:5555",
            "[::1]junk",
            "[::1]:5555]",
            "[::1]:",
            "[::1]:abc",
            "[::1]:0",
            "[::1]:65536",
            "2001:db8::1::2",
            "fe80::1%",
            "fe80::1%wlan 0",
            "fe80::1%wlan0:5555",
        ).forEach { assertFalse(it, NetworkInputValidator.endpoint(it, 5555).isValid) }
    }

    @Test
    fun ipError_acceptsValidIpv4AndIpv6() {
        assertNull(NetworkInputValidator.ipError("192.168.1.86"))
        assertNull(NetworkInputValidator.ipError("0.0.0.0"))
        assertNull(NetworkInputValidator.ipError("255.255.255.255"))
        assertNull(NetworkInputValidator.ipError("2001:db8::1"))
        assertNull(NetworkInputValidator.ipError("[2001:db8::1]"))
        assertNull(NetworkInputValidator.ipError("fe80::1%wlan0"))
        assertNull(NetworkInputValidator.ipError("ADB.Example.COM."))
        assertNull(NetworkInputValidator.ipError("BÜCHER.example"))
        assertNull(NetworkInputValidator.ipError("localhost"))
    }

    @Test
    fun ipError_rejectsInvalidAddressesAndBrackets() {
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("192.168.1.999"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("192.168.1"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("999"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("-example.com"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("example..com"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("https://example.com"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("example.com/path"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("2001:db8::1::2"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("[2001:db8::1"))
        assertEquals(R.string.error_ipv4_invalid, NetworkInputValidator.ipError("[192.168.1.1]"))
    }

    @Test
    fun normalizeHost_removesIpv6Brackets() {
        assertEquals("2001:db8::1", NetworkInputValidator.normalizeHost("[2001:db8::1]"))
        assertEquals("fe80::1%wlan0", NetworkInputValidator.normalizeHost("[fe80::1%wlan0]"))
        assertEquals("192.168.1.2", NetworkInputValidator.normalizeHost("192.168.1.2"))
        assertEquals("adb.example.com", NetworkInputValidator.normalizeHost("ADB.Example.COM."))
        assertEquals("xn--bcher-kva.example", NetworkInputValidator.normalizeHost("BÜCHER.example"))
        assertNull(NetworkInputValidator.normalizeHost("[::1]:5555"))
        assertNull(NetworkInputValidator.normalizeHost("192.168.1.999"))
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
