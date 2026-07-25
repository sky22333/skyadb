package com.sky22333.skyadb.scrcpy

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorQualityPresetTest {
    @Test
    fun fromName_fallsBackToBalanced() {
        assertEquals(MirrorQualityPreset.Balanced, MirrorQualityPreset.fromName(null))
        assertEquals(MirrorQualityPreset.Balanced, MirrorQualityPreset.fromName("Unknown"))
    }

    @Test
    fun presets_keepExpectedScrcpyOptions() {
        assertEquals(ScrcpyOptions(1024, 30, 2_000_000), MirrorQualityPreset.Smooth.options)
        assertEquals(ScrcpyOptions(), MirrorQualityPreset.Balanced.options)
        assertEquals(ScrcpyOptions(1920, 60, 8_000_000), MirrorQualityPreset.High.options)
    }
}
