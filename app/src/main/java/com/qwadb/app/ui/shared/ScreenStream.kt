package com.qwadb.app.ui.shared

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

/** 按视频宽高比与容器尺寸计算屏幕流（控制模式 / 观看屏幕）Surface 的布局修饰符。 */
internal fun Modifier.screenStreamSurface(
    containerWidth: Float,
    containerHeight: Float,
    videoWidth: Int,
    videoHeight: Int,
): Modifier {
    if (containerWidth <= 0f || containerHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) {
        return fillMaxSize()
    }
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val containerRatio = containerWidth / containerHeight
    return if (containerRatio > videoRatio) {
        fillMaxHeight().aspectRatio(videoRatio)
    } else {
        fillMaxWidth().aspectRatio(videoRatio)
    }
}
