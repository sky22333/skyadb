package com.sky22333.skyadb.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEndpointTest {
    @Test
    fun authorityBracketsIpv6AndKeepsIpv4Readable() {
        assertEquals("192.168.1.2:5555", AdbEndpoint("192.168.1.2", 5555).authority)
        assertEquals("adb.example.com:5555", AdbEndpoint("adb.example.com", 5555).authority)
        assertEquals("[2001:db8::1]:5555", AdbEndpoint("2001:db8::1", 5555).authority)
        assertEquals("[fe80::1%wlan0]:5555", AdbEndpoint.format("[fe80::1%wlan0]", 5555))
    }

    @Test
    fun hostForSocketRemovesOnlyOuterBrackets() {
        assertEquals("2001:db8::1", AdbEndpoint.hostForSocket("[2001:db8::1]"))
        assertEquals("2001:db8::1", AdbEndpoint.hostForSocket(" 2001:db8::1 "))
        assertEquals("[2001:db8::1", AdbEndpoint.hostForSocket("[2001:db8::1"))
    }

    @Test
    fun sameHostIgnoresIpv6CompressionAndCase() {
        assertTrue(AdbEndpoint.sameHost("2001:db8::1", "2001:0DB8:0:0:0:0:0:1"))
        assertTrue(AdbEndpoint.sameHost("[FE80::1%WLAN0]", "fe80::1%wlan0"))
        assertTrue(AdbEndpoint.sameHost("192.168.1.2", "192.168.1.2"))
        assertFalse(AdbEndpoint.sameHost("2001:db8::1", "2001:db8::2"))
    }

    @Test
    fun normalizeHostAcceptsIpv4Ipv6AndDnsNames() {
        assertEquals("192.168.1.2", AdbEndpoint.normalizeHost("192.168.1.2"))
        assertEquals("2001:db8::1", AdbEndpoint.normalizeHost("2001:db8::1"))
        assertEquals("2001:db8::1", AdbEndpoint.normalizeHost("[2001:db8::1]"))
        assertEquals("fe80::1%wlan0", AdbEndpoint.normalizeHost("[fe80::1%wlan0]"))
        assertEquals("adb.example.com", AdbEndpoint.normalizeHost("ADB.Example.COM"))
        assertEquals("adb.example.com", AdbEndpoint.normalizeHost("ADB.Example.COM."))
        assertEquals("localhost", AdbEndpoint.normalizeHost("LocalHost."))
        assertEquals("xn--bcher-kva.example", AdbEndpoint.normalizeHost("BÜCHER.example"))
        assertEquals("${"a".repeat(63)}.example", AdbEndpoint.normalizeHost("${"a".repeat(63)}.example"))
        val maxLengthHost = "${"a".repeat(63)}.${"b".repeat(63)}.${"c".repeat(63)}.${"d".repeat(61)}"
        assertEquals(maxLengthHost, AdbEndpoint.normalizeHost(maxLengthHost))
    }

    @Test
    fun normalizeHostRejectsInvalidIpv4InsteadOfTreatingItAsDns() {
        listOf("192.168.1.999", "192.168.1", "999", "1.2.3.4.5", "192.168.1.2.").forEach { value ->
            assertNull(value, AdbEndpoint.normalizeHost(value))
        }
    }

    @Test
    fun normalizeHostRejectsInvalidDnsAndUrls() {
        val tooLong = List(4) { "a".repeat(63) }.joinToString(".")
        val length254 = "${"a".repeat(63)}.${"b".repeat(63)}.${"c".repeat(63)}.${"d".repeat(62)}"
        listOf(
            "-adb.example.com",
            "adb-.example.com",
            "adb..example.com",
            "adb_example.com",
            "a".repeat(64) + ".example.com",
            tooLong,
            length254,
            "https://adb.example.com",
            "adb.example.com/path",
            "adb.example.com:5555",
            "[adb.example.com]",
            "adb example.com",
        ).forEach { value ->
            assertNull(value, AdbEndpoint.normalizeHost(value))
        }
    }

    @Test
    fun hostValidationKeepsIpAndDnsSemanticsSeparate() {
        assertTrue(AdbEndpoint.isValidHost("adb.example.com"))
        assertTrue(AdbEndpoint.isValidHost("[2001:db8::1]"))
        assertFalse(AdbEndpoint.isValidIp("adb.example.com"))
        assertTrue(AdbEndpoint.isValidIp("[2001:db8::1]"))
    }

    @Test
    fun sameHostNormalizesDnsCaseTrailingDotAndIdn() {
        assertTrue(AdbEndpoint.sameHost("ADB.Example.COM.", "adb.example.com"))
        assertTrue(AdbEndpoint.sameHost("BÜCHER.example", "xn--bcher-kva.example"))
        assertFalse(AdbEndpoint.sameHost("adb.example.com", "adb.example.net"))
    }

    @Test
    fun sameAuthorityComparesHostAndPort() {
        assertTrue(AdbEndpoint.sameAuthority("[2001:0DB8::1]:5555", "[2001:db8::1]:5555"))
        assertTrue(AdbEndpoint.sameAuthority("ADB.Example.COM.:5555", "adb.example.com:5555"))
        assertFalse(AdbEndpoint.sameAuthority("[2001:db8::1]:5555", "[2001:db8::1]:5556"))
        assertFalse(AdbEndpoint.sameAuthority("192.168.1.2:5555", "[2001:db8::1]:5555"))
    }
}
