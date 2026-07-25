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
        val socketId = ScrcpyConstants.formatScid(scid)
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
            "audio_codec=aac",
            "control=true",
            "tunnel_forward=true",
            "max_size=${options.maxSize}",
            "max_fps=${options.maxFps}",
            "video_bit_rate=${options.videoBitRate}",
        ).joinToString(" ")
    }
}
