package com.sky22333.skyadb.scrcpy

import android.view.KeyEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScrcpyProtocolTest {
    @Test
    fun clipboardLimitPreservesUtf8CharacterBoundaries() {
        val text = "a" + "你".repeat(ScrcpyProtocol.ClipboardTextMaxBytes)
        val packet = ScrcpyProtocol.setClipboard(text)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.position(10)
        val size = buffer.int
        val bytes = ByteArray(size).also { buffer.get(it) }
        val decoded = bytes.toString(Charsets.UTF_8)
        assertEquals(decoded.toByteArray(Charsets.UTF_8).size, size)
        org.junit.Assert.assertFalse(decoded.contains('\uFFFD'))
    }

    @Test
    fun keyEvent_hasExpectedFields() {
        val packet = ScrcpyProtocol.keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        assertEquals(14, packet.size)
        assertEquals(0, buffer.get().toInt())
        assertEquals(KeyEvent.ACTION_DOWN, buffer.get().toInt())
        assertEquals(KeyEvent.KEYCODE_HOME, buffer.int)
    }

    @Test
    fun setClipboard_usesPasteFlagAndUtf8() {
        val packet = ScrcpyProtocol.setClipboard("你好scrcpy", paste = true, sequence = 0L)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        assertEquals(9, buffer.get().toInt())
        assertEquals(0L, buffer.long)
        assertEquals(1, buffer.get().toInt())
        assertEquals("你好scrcpy".toByteArray(Charsets.UTF_8).size, buffer.int)
    }

    @Test
    fun touch_hasExpectedSizeAndCoordinates() {
        val packet =
            ScrcpyProtocol.touch(
                action = 0,
                pointerId = -1L,
                x = 10,
                y = 20,
                screenWidth = 1920,
                screenHeight = 1080,
                pressure = 1f,
            )
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        assertEquals(32, packet.size)
        assertEquals(2, buffer.get().toInt())
        assertEquals(0, buffer.get().toInt())
        assertEquals(-1L, buffer.long)
        assertEquals(10, buffer.int)
        assertEquals(20, buffer.int)
    }

    @Test
    fun resetVideo_isSingleTypeByte() {
        val packet = ScrcpyProtocol.resetVideo()
        assertEquals(1, packet.size)
        assertEquals(17, packet[0].toInt())
    }

    @Test
    fun mapper_ignoresLetterboxAndMapsContent() {
        assertNull(
            MirrorCoordinateMapper.map(
                x = 10f,
                y = 10f,
                surfaceWidth = 390,
                surfaceHeight = 844,
                videoWidth = 1920,
                videoHeight = 1080,
            )
        )

        assertNotNull(
            MirrorCoordinateMapper.map(
                x = 195f,
                y = 422f,
                surfaceWidth = 390,
                surfaceHeight = 844,
                videoWidth = 1920,
                videoHeight = 1080,
            )
        )
    }
}
