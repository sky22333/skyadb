package com.sky22333.skyadb.discovery

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRangeParserTest {
    @Test
    fun parseConfiguredRanges_acceptsPrivateIpv4AndCidrs() {
        val ranges = ScanRangeParser.parseConfiguredRanges("192.168.1.23/30, 10.0.0.8/32")

        assertEquals(2, ranges.size)
        assertEquals("192.168.1.20/30", ranges[0].subnetLabel)
        assertEquals(listOf("192.168.1.21", "192.168.1.22"), ranges[0].expandHosts())
        assertEquals("10.0.0.8/32", ranges[1].subnetLabel)
        assertEquals(listOf("10.0.0.8"), ranges[1].expandHosts())
    }

    @Test
    fun parseConfiguredRanges_rejectsPublicAndTooLargeRanges() {
        val ranges = ScanRangeParser.parseConfiguredRanges("8.8.8.8/24\n192.168.1.1/23\n172.16.1.9")

        assertEquals(1, ranges.size)
        assertEquals("172.16.1.0/24", ranges.single().subnetLabel)
    }

    @Test
    fun subnetForLocalAddress_excludesOwnAddressFromDefaultScanHosts() {
        val range = ScanRangeParser.subnetForLocalAddress(
            "192.168.1.23",
            sourceLabelRes = R.string.discovery_source_current_network,
        )

        requireNotNull(range)
        assertEquals("192.168.1.0/24", range.subnetLabel)
        assertEquals(253, range.hostCount)
        val hosts = range.expandHosts()
        assertFalse(hosts.contains("192.168.1.23"))
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
        assertEquals(253, hosts.size)
    }

    @Test
    fun validationError_reportsInvalidRangeAndTooManyEntries() {
        assertEquals(
            AppText.Res(R.string.scan_range_error_invalid, "192.168.1.1/23"),
            ScanRangeParser.validationError("192.168.1.1/23"),
        )
        assertEquals(
            AppText.Res(R.string.scan_range_error_too_many, 6),
            ScanRangeParser.validationError(
                "192.168.1.1,192.168.2.1,192.168.3.1,192.168.4.1,192.168.5.1,192.168.6.1,192.168.7.1",
            ),
        )
        assertNull(ScanRangeParser.validationError("192.168.1.1/24；10.0.0.1/32"))
    }
}
