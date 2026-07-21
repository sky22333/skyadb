package com.sky22333.skyadb.scrcpy

import android.content.Context
import com.flyfishxu.kadb.Kadb
import okio.source

class ScrcpyServerManager(
    private val context: Context,
) {
    fun pushServer(kadb: Kadb) {
        context.assets.open(ScrcpyConstants.ServerAssetPath).use { input ->
            kadb.push(
                input.source(),
                ScrcpyConstants.RemoteServerPath,
                420,
                System.currentTimeMillis(),
            )
        }
    }

    fun buildStartCommand(
        scid: UInt,
        options: ScrcpyOptions,
        audioEnabled: Boolean,
    ): String {
        val socketId = scid.toString(16).padStart(8, '0')
        return listOf(
            "CLASSPATH=${ScrcpyConstants.RemoteServerPath}",
            "app_process",
            "/",
            "com.genymobile.scrcpy.Server",
            ScrcpyConstants.ServerVersion,
            "scid=$socketId",
            "log_level=info",
            "video=true",
            "audio=${if (audioEnabled) "true" else "false"}",
            // 电视端 Opus 编码器常缺失，AAC 与官方兜底一致且兼容性更好。
            "audio_codec=aac",
            "control=true",
            "tunnel_forward=true",
            "max_size=${options.maxSize}",
            "max_fps=${options.maxFps}",
            "video_bit_rate=${options.videoBitRate}",
        ).joinToString(" ")
    }
}
