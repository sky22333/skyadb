package com.sky22333.skyadb.scrcpy

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorSpeedFormatterTest {
    @Test
    fun zeroBytesPerSecondFormatsAsZeroKilobytes() {
        assertKilobytes(0L, 0.0)
    }

    @Test
    fun valuesBelowOneMebibyteFormatAsRoundedKilobytes() {
        assertKilobytes(2L, 1_536.0)
    }

    @Test
    fun valueNearOneMebibyteStillFormatsAsKilobytes() {
        assertKilobytes(1_023L, 1_047_552.0)
    }

    @Test
    fun exactlyOneMebibyteFormatsAsOneMegabyte() {
        assertMegabytes(1.0, 1_048_576.0)
    }

    @Test
    fun valuesAboveOneMebibyteFormatAsMegabytes() {
        assertMegabytes(2.5, 2_621_440.0)
    }

    @Test
    fun negativeBytesPerSecondFormatsAsZeroKilobytes() {
        assertKilobytes(0L, -1_024.0)
    }

    private fun assertKilobytes(expected: Long, bytesPerSecond: Double) {
        assertEquals(
            MirrorSpeed.KilobytesPerSecond(expected),
            MirrorSpeedFormatter.format(bytesPerSecond),
        )
    }

    private fun assertMegabytes(expected: Double, bytesPerSecond: Double) {
        assertEquals(
            MirrorSpeed.MegabytesPerSecond(expected),
            MirrorSpeedFormatter.format(bytesPerSecond),
        )
    }
}
