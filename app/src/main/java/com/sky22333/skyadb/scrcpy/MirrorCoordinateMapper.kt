package com.sky22333.skyadb.scrcpy

import kotlin.math.roundToInt

data class RemotePoint(
    val x: Int,
    val y: Int,
    val screenWidth: Int,
    val screenHeight: Int,
)

object MirrorCoordinateMapper {
    fun map(
        x: Float,
        y: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): RemotePoint? {
        val viewport = viewport(surfaceWidth, surfaceHeight, videoWidth, videoHeight) ?: return null
        if (x < viewport.left || y < viewport.top) return null
        if (x > viewport.left + viewport.width || y > viewport.top + viewport.height) return null

        val remoteX = ((x - viewport.left) / viewport.width * videoWidth)
            .roundToInt()
            .coerceIn(0, videoWidth - 1)
        val remoteY = ((y - viewport.top) / viewport.height * videoHeight)
            .roundToInt()
            .coerceIn(0, videoHeight - 1)

        return RemotePoint(
            x = remoteX,
            y = remoteY,
            screenWidth = videoWidth,
            screenHeight = videoHeight,
        )
    }

    private fun viewport(
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): Viewport? {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return null
        }
        val scale = minOf(
            surfaceWidth.toFloat() / videoWidth.toFloat(),
            surfaceHeight.toFloat() / videoHeight.toFloat(),
        )
        val width = videoWidth * scale
        val height = videoHeight * scale
        return Viewport(
            left = (surfaceWidth - width) / 2f,
            top = (surfaceHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    private data class Viewport(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )
}
