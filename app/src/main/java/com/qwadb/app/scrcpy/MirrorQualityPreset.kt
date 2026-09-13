package com.qwadb.app.scrcpy

import androidx.annotation.StringRes
import com.qwadb.app.R

enum class MirrorQualityPreset(
    @param:StringRes val labelRes: Int,
    val options: ScrcpyOptions,
) {
    Smooth(
        labelRes = R.string.mirror_quality_smooth,
        options = ScrcpyOptions(maxSize = 1024, maxFps = 30, videoBitRate = 2_000_000),
    ),
    Balanced(
        labelRes = R.string.mirror_quality_balanced,
        options = ScrcpyOptions(),
    ),
    High(
        labelRes = R.string.mirror_quality_high,
        options = ScrcpyOptions(maxSize = 1920, maxFps = 60, videoBitRate = 8_000_000),
    ),
    Custom(
        labelRes = R.string.mirror_quality_custom,
        options = ScrcpyOptions(),
    );

    companion object {
        fun fromName(name: String?): MirrorQualityPreset {
            return entries.firstOrNull { it.name == name } ?: Balanced
        }
    }
}
