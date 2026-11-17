package com.sky22333.skyadb.scrcpy

import androidx.annotation.StringRes
import com.sky22333.skyadb.R

enum class MirrorVideoCodec(
    @param:StringRes val labelRes: Int,
    val scrcpyValue: String,
) {
    H264(
        labelRes = R.string.mirror_video_codec_h264,
        scrcpyValue = "h264",
    ),
    H265(
        labelRes = R.string.mirror_video_codec_h265,
        scrcpyValue = "h265",
    );

    companion object {
        fun fromName(name: String?): MirrorVideoCodec {
            return entries.firstOrNull { it.name == name } ?: H264
        }
    }
}
