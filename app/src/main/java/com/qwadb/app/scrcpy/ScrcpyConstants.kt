package com.qwadb.app.scrcpy

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

    /**
     * 每个会话使用独立的远端 JAR 路径：双摄/多会话同时运行时，各自推送并加载
     * 自己的服务端 JAR，避免相互覆盖导致已运行的会话崩溃或加载失败。
     */
    fun serverRemotePath(scid: UInt): String =
        "/data/local/tmp/skyadb-scrcpy-server-${formatScid(scid)}.jar"
}

/** 镜像默认最长边（自定义分辨率解析失败时的兜底值）。 */
const val MirrorDefaultMaxSize = ScrcpyConstants.DefaultMaxSize

/**
 * 解析形如 "1920x1080" 的分辨率字符串，返回最长边（scrcpy max_size 语义）。
 * 无法解析时返回 [fallback]。
 */
fun parseResolutionSize(resolution: String, fallback: Int): Int {
    if (resolution.isBlank()) return fallback
    val (w, h) = resolution.split("x", "X", "×", ignoreCase = true, limit = 2)
    val width = w.trim().toIntOrNull() ?: return fallback
    val height = h.trim().toIntOrNull() ?: return fallback
    if (width <= 0 || height <= 0) return fallback
    return maxOf(width, height)
}

/** 摄像头采集画质配置（与屏幕镜像画质相互独立）。 */
data class CameraQuality(
    /** 分辨率字符串，形如 "1280x720"，直接用于 camera_size。 */
    val resolution: String = "1280x720",
    val maxFps: Int = ScrcpyConstants.DefaultMaxFps,
    val videoBitRate: Int = ScrcpyConstants.DefaultVideoBitRate,
) {
    val width: Int
        get() = parseResolutionWidth(resolution, 1280)

    val height: Int
        get() = parseResolutionHeight(resolution, 720)
}

/** 解析形如 "1920x1080" 的分辨率字符串的宽度，失败时返回 [fallback]。 */
fun parseResolutionWidth(resolution: String, fallback: Int): Int {
    if (resolution.isBlank()) return fallback
    val (w, _) = resolution.split("x", "X", "×", ignoreCase = true, limit = 2)
    return w.trim().toIntOrNull()?.takeIf { it > 0 } ?: fallback
}

/** 解析形如 "1920x1080" 的分辨率字符串的高度，失败时返回 [fallback]。 */
fun parseResolutionHeight(resolution: String, fallback: Int): Int {
    if (resolution.isBlank()) return fallback
    val (_, h) = resolution.split("x", "X", "×", ignoreCase = true, limit = 2)
    return h.trim().toIntOrNull()?.takeIf { it > 0 } ?: fallback
}

/** 摄像头朝向。 */
enum class CameraFace(val facingArg: String, val defaultLabelRes: Int) {
    Back("back", com.qwadb.app.R.string.camera_face_back),
    Front("front", com.qwadb.app.R.string.camera_face_front),
}

/** 摄像头采集的 scrcpy 启动参数（在屏幕镜像基础上增加 video_source=camera）。 */
data class CameraCaptureOptions(
    val face: CameraFace = CameraFace.Back,
    val quality: CameraQuality = CameraQuality(),
) {
    fun diagnosticText(): String {
        return "video_source=camera, camera_facing=${face.facingArg}, " +
            "camera_size=${quality.width}x${quality.height}, max_fps=${quality.maxFps}, " +
            "video_bit_rate=${quality.videoBitRate}"
    }
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
