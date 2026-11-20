package com.sky22333.skyadb.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadParserTest {
    @Test
    fun parsesAndroidWirelessPairingQrCode() {
        val payload = QrPayloadParser.parse("WIFI:T:ADB;S:Pixel 8;P:123456;;")

        assertEquals(
            QrPayload.WirelessPairing(serviceName = "Pixel 8", pairingCode = "123456"),
            payload,
        )
    }

    @Test
    fun unescapesWifiQrFields() {
        val payload = QrPayloadParser.parse("WIFI:T:ADB;S:adb\\;living-room;P:123456;;")

        assertEquals(
            QrPayload.WirelessPairing(serviceName = "adb;living-room", pairingCode = "123456"),
            payload,
        )
    }

    @Test
    fun roundTripsIpv6ConnectionPayload() {
        val encoded = ConnectionQrPayload.encode("[2001:db8::1]:5555")
        val payload = QrPayloadParser.parse(encoded.orEmpty())

        assertEquals(
            QrPayload.DirectConnect(com.sky22333.skyadb.model.AdbEndpoint("2001:db8::1", 5555)),
            payload,
        )
    }

    @Test
    fun parsesPlainAdbConnectText() {
        val payload = QrPayloadParser.parse("adb connect 192.168.1.20:5555")

        assertEquals(
            QrPayload.DirectConnect(com.sky22333.skyadb.model.AdbEndpoint("192.168.1.20", 5555)),
            payload,
        )
    }

    @Test
    fun rejectsUnrelatedQrCode() {
        assertNull(QrPayloadParser.parse("https://example.com"))
    }
}
