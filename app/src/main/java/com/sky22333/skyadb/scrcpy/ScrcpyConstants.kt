package com.sky22333.skyadb.scrcpy

object ScrcpyConstants {
    const val ServerVersion = "5.0.1"
    const val ServerAssetPath = "scrcpy/scrcpy-server-v5.0.1"
    const val RemoteServerPath = "/data/local/tmp/skyadb-scrcpy-server-v5.0.1.jar"
    const val DefaultMaxSize = 1280
    const val DefaultMaxFps = 60
    const val DefaultVideoBitRate = 6_000_000
    const val ConnectRetryCount = 80
    const val ConnectRetryDelayMillis = 100L

    fun formatScid(scid: UInt): String = scid.toString(16).padStart(8, '0')
}

data class ScrcpyOptions(
    val maxSize: Int = ScrcpyConstants.DefaultMaxSize,
    val maxFps: Int = ScrcpyConstants.DefaultMaxFps,
    val videoBitRate: Int = ScrcpyConstants.DefaultVideoBitRate,
    val videoCodec: MirrorVideoCodec = MirrorVideoCodec.H264,
    val videoSource: ScrcpyVideoSource = ScrcpyVideoSource.Display,
    val cameraFacing: ScrcpyCameraFacing = ScrcpyCameraFacing.Back,
) {
    fun diagnosticText(): String {
        return serverArguments().joinToString(", ")
    }

    fun serverArguments(): List<String> {
        return buildList {
            add("max_size=$maxSize")
            add("max_fps=$maxFps")
            add("video_bit_rate=$videoBitRate")
            add("video_codec=${videoCodec.scrcpyValue}")
            add("video_source=${videoSource.scrcpyValue}")
            if (videoSource == ScrcpyVideoSource.Camera) {
                add("camera_facing=${cameraFacing.scrcpyValue}")
                add("camera_fps=$maxFps")
            }
        }
    }
}

enum class ScrcpyVideoSource(val scrcpyValue: String) {
    Display("display"),
    Camera("camera"),
}

enum class ScrcpyCameraFacing(val scrcpyValue: String) {
    Back("back"),
    Front("front"),
}
