package com.sky22333.skyadb.scrcpy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorVideoCodecTest {
    @Test
    fun fromName_fallsBackToH264() {
        assertEquals(MirrorVideoCodec.H264, MirrorVideoCodec.fromName(null))
        assertEquals(MirrorVideoCodec.H264, MirrorVideoCodec.fromName("Unknown"))
    }

    @Test
    fun fromName_readsPersistedH265() {
        assertEquals(MirrorVideoCodec.H265, MirrorVideoCodec.fromName("H265"))
    }

    @Test
    fun scrcpyOptions_exposeCodecInArgumentsAndDiagnostics() {
        val options = ScrcpyOptions(
            maxSize = 1280,
            maxFps = 60,
            videoBitRate = 6_000_000,
            videoCodec = MirrorVideoCodec.H265,
        )

        assertEquals(
            listOf(
                "max_size=1280",
                "max_fps=60",
                "video_bit_rate=6000000",
                "video_codec=h265",
            ),
            options.serverArguments(),
        )
        assertTrue(options.diagnosticText().contains("video_codec=h265"))
    }
}
