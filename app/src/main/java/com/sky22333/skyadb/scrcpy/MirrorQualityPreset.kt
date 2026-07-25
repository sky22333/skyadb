package com.sky22333.skyadb.scrcpy

enum class MirrorQualityPreset(
    val label: String,
    val options: ScrcpyOptions,
) {
    Smooth(
        label = "流畅",
        options = ScrcpyOptions(maxSize = 1024, maxFps = 30, videoBitRate = 2_000_000),
    ),
    Balanced(
        label = "均衡",
        options = ScrcpyOptions(),
    ),
    High(
        label = "高清",
        options = ScrcpyOptions(maxSize = 1920, maxFps = 60, videoBitRate = 8_000_000),
    );

    companion object {
        fun fromName(name: String?): MirrorQualityPreset {
            return entries.firstOrNull { it.name == name } ?: Balanced
        }
    }
}
