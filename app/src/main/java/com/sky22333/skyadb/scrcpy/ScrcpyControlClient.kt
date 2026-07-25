package com.sky22333.skyadb.scrcpy

import android.view.KeyEvent
import com.flyfishxu.kadb.stream.AdbStream

class ScrcpyControlClient(
    private val stream: AdbStream,
) {
    private val lock = Any()

    @Volatile
    var videoWidth: Int = 0
        private set

    @Volatile
    var videoHeight: Int = 0
        private set

    fun updateVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
    }

    fun sendTouch(event: MirrorTouchEvent) {
        val width = videoWidth
        val height = videoHeight
        val action = ScrcpyProtocol.motionAction(event.actionMasked) ?: return
        val point = MirrorCoordinateMapper.map(
            x = event.x,
            y = event.y,
            surfaceWidth = event.surfaceWidth,
            surfaceHeight = event.surfaceHeight,
            videoWidth = width,
            videoHeight = height,
        ) ?: return

        val pointerId = if (event.pointerId == 0) {
            ScrcpyProtocol.PointerMouse
        } else {
            event.pointerId.toLong()
        }
        send(
            ScrcpyProtocol.touch(
                action = action,
                pointerId = pointerId,
                x = point.x,
                y = point.y,
                screenWidth = point.screenWidth,
                screenHeight = point.screenHeight,
                pressure = event.pressure,
                actionButton = event.actionButton,
                buttons = event.buttons,
            ),
        )
    }

    fun sendKey(keyCode: Int) {
        synchronized(lock) {
            stream.sink.write(ScrcpyProtocol.keyEvent(KeyEvent.ACTION_DOWN, keyCode))
            stream.sink.write(ScrcpyProtocol.keyEvent(KeyEvent.ACTION_UP, keyCode))
            stream.sink.flush()
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        send(ScrcpyProtocol.setClipboard(text = text, paste = true))
    }

    fun resetVideo() {
        send(ScrcpyProtocol.resetVideo())
    }

    fun sendBackOrScreenOn() {
        synchronized(lock) {
            stream.sink.write(ScrcpyProtocol.backOrScreenOn())
            stream.sink.write(ScrcpyProtocol.backOrScreenOn(KeyEvent.ACTION_UP))
            stream.sink.flush()
        }
    }

    private fun send(bytes: ByteArray) {
        synchronized(lock) {
            stream.sink.write(bytes)
            stream.sink.flush()
        }
    }
}
