package com.qwadb.app.scrcpy

import android.content.Context
import com.flyfishxu.kadb.Kadb
import okio.source

class ScrcpyServerManager(
    private val context: Context,
) {
    /**
     * 推送服务端 JAR 到设备上该会话独有的远端路径并返回该路径。
     * 使用按 scid 唯一的路径，保证双摄/多会话并发时互不覆盖。
     */
    fun pushServer(kadb: Kadb, scid: UInt): String {
        val remotePath = ScrcpyConstants.serverRemotePath(scid)
        context.assets.open(ScrcpyConstants.ServerAssetPath).use { input ->
            kadb.push(
                input.source(),
                remotePath,
                420,
                System.currentTimeMillis(),
            )
        }
        return remotePath
    }

    fun buildStartCommand(
        scid: UInt,
        options: ScrcpyOptions,
        audioEnabled: Boolean,
        serverPath: String = ScrcpyConstants.RemoteServerPath,
    ): String {
        val socketId = ScrcpyConstants.formatScid(scid)
        return listOf(
            "CLASSPATH=$serverPath",
            "app_process",
            "/",
            "com.genymobile.scrcpy.Server",
            ScrcpyConstants.ServerVersion,
            "scid=$socketId",
            "log_level=info",
            "video=true",
            "audio=${if (audioEnabled) "true" else "false"}",
            "audio_codec=aac",
            "control=true",
            "tunnel_forward=true",
            "max_size=${options.maxSize}",
            "max_fps=${options.maxFps}",
            "video_bit_rate=${options.videoBitRate}",
        ).joinToString(" ")
    }

    /** 摄像头采集启动命令：video_source=camera，音频与控制通道均关闭。 */
    fun buildCameraStartCommand(
        scid: UInt,
        options: CameraCaptureOptions,
        serverPath: String = ScrcpyConstants.RemoteServerPath,
    ): String {
        val socketId = ScrcpyConstants.formatScid(scid)
        val quality = options.quality
        return listOf(
            "CLASSPATH=$serverPath",
            "app_process",
            "/",
            "com.genymobile.scrcpy.Server",
            ScrcpyConstants.ServerVersion,
            "scid=$socketId",
            "log_level=info",
            "video=true",
            "audio=false",
            "control=true",
            "tunnel_forward=true",
            "video_source=camera",
            "camera_facing=${options.face.facingArg}",
            "camera_size=${quality.width}x${quality.height}",
            "camera_fps=${quality.maxFps}",
            "video_codec=h264",
            "video_bit_rate=${quality.videoBitRate}",
        ).joinToString(" ")
    }
}
