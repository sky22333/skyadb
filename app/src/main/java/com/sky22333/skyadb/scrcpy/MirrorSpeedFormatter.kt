package com.sky22333.skyadb.scrcpy

import kotlin.math.floor

sealed interface MirrorSpeed {
    data class KilobytesPerSecond(val value: Long) : MirrorSpeed

    data class MegabytesPerSecond(val value: Double) : MirrorSpeed
}

object MirrorSpeedFormatter {
    private const val BYTES_PER_KILOBYTE = 1024.0
    private const val BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * 1024.0

    fun format(bytesPerSecond: Double): MirrorSpeed {
        val normalized = bytesPerSecond.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        return if (normalized < BYTES_PER_MEGABYTE) {
            val kilobytes = floor(normalized / BYTES_PER_KILOBYTE + 0.5).toLong()
            MirrorSpeed.KilobytesPerSecond(kilobytes)
        } else {
            MirrorSpeed.MegabytesPerSecond(normalized / BYTES_PER_MEGABYTE)
        }
    }
}
