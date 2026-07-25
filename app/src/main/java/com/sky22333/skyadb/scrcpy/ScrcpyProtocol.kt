package com.sky22333.skyadb.scrcpy

import android.view.KeyEvent
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object ScrcpyProtocol {
    const val CodecH264 = 0x68323634
    const val CodecH265 = 0x68323635
    const val CodecAv1 = 0x00617631
    const val CodecVp8 = 0x00767038
    const val CodecVp9 = 0x00767039
    const val CodecOpus = 0x6f707573
    const val CodecAac = 0x00616163
    const val CodecFlac = 0x666c6163
    const val CodecRaw = 0x00726177

    const val DeviceNameLength = 64
    const val PacketHeaderLength = 12
    const val PacketFlagSession = 1L shl 63
    const val PacketFlagConfig = 1L shl 62
    const val PacketFlagKeyFrame = 1L shl 61
    const val PacketPtsMask = (1L shl 61) - 1
    const val PointerMouse = -1L
    const val ClipboardTextMaxBytes = 240_000

    private const val TypeInjectKeycode = 0
    private const val TypeInjectTouchEvent = 2
    private const val TypeBackOrScreenOn = 4
    private const val TypeSetClipboard = 9
    private const val TypeResetVideo = 17

    fun keyEvent(action: Int, keyCode: Int, repeat: Int = 0, metaState: Int = 0): ByteArray {
        return ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
            .put(TypeInjectKeycode.toByte())
            .put(action.toByte())
            .putInt(keyCode)
            .putInt(repeat)
            .putInt(metaState)
            .array()
    }

    fun setClipboard(
        text: String,
        paste: Boolean = true,
        sequence: Long = 0L,
    ): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
            .let { if (it.size <= ClipboardTextMaxBytes) it else it.copyOf(ClipboardTextMaxBytes) }
        return ByteBuffer.allocate(14 + bytes.size).order(ByteOrder.BIG_ENDIAN)
            .put(TypeSetClipboard.toByte())
            .putLong(sequence)
            .put(if (paste) 1 else 0)
            .putInt(bytes.size)
            .put(bytes)
            .array()
    }

    fun touch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 0,
        buttons: Int = 0,
    ): ByteArray {
        return ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
            .put(TypeInjectTouchEvent.toByte())
            .put(action.toByte())
            .putLong(pointerId)
            .putInt(x)
            .putInt(y)
            .putShort(screenWidth.toShort())
            .putShort(screenHeight.toShort())
            .putShort(unsignedFixedPoint16(pressure))
            .putInt(actionButton)
            .putInt(buttons)
            .array()
    }

    fun backOrScreenOn(action: Int = KeyEvent.ACTION_DOWN): ByteArray {
        return byteArrayOf(TypeBackOrScreenOn.toByte(), action.toByte())
    }

    fun resetVideo(): ByteArray = byteArrayOf(TypeResetVideo.toByte())

    fun motionAction(action: Int): Int? = when (action) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> 0
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> 1
        MotionEvent.ACTION_MOVE -> 2
        else -> null
    }

    private fun unsignedFixedPoint16(value: Float): Short {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped >= 1f) {
            0xffff.toShort()
        } else {
            (clamped * 65536f).roundToInt().coerceIn(0, 0xfffe).toShort()
        }
    }
}
