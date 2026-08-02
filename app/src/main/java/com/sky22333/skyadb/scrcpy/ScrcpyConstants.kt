package com.sky22333.skyadb.scrcpy

object ScrcpyConstants {
    const val ServerVersion = "4.1"
    const val ServerAssetPath = "scrcpy/scrcpy-server-v4.1"
    const val RemoteServerPath = "/data/local/tmp/skyadb-scrcpy-server-v4.1.jar"
    const val DefaultMaxSize = 1280
    const val DefaultMaxFps = 30
    const val DefaultVideoBitRate = 4_000_000
    const val ConnectRetryCount = 80
    const val ConnectRetryDelayMillis = 100L

    fun formatScid(scid: UInt): String = scid.toString(16).padStart(8, '0')
}

data class ScrcpyOptions(
    val maxSize: Int = ScrcpyConstants.DefaultMaxSize,
    val maxFps: Int = ScrcpyConstants.DefaultMaxFps,
    val videoBitRate: Int = ScrcpyConstants.DefaultVideoBitRate,
) {
    fun diagnosticText(): String {
        return "max_size=$maxSize, max_fps=$maxFps, video_bit_rate=$videoBitRate"
    }
}
