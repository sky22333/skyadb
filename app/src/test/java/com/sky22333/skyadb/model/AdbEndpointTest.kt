package com.sky22333.skyadb.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEndpointTest {
    @Test
    fun authorityBracketsIpv6AndKeepsIpv4Readable() {
        assertEquals("192.168.1.2:5555", AdbEndpoint("192.168.1.2", 5555).authority)
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
    fun sameAuthorityComparesHostAndPort() {
        assertTrue(AdbEndpoint.sameAuthority("[2001:0DB8::1]:5555", "[2001:db8::1]:5555"))
        assertFalse(AdbEndpoint.sameAuthority("[2001:db8::1]:5555", "[2001:db8::1]:5556"))
        assertFalse(AdbEndpoint.sameAuthority("192.168.1.2:5555", "[2001:db8::1]:5555"))
    }
}
