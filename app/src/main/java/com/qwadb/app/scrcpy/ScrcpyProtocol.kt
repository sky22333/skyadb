package com.qwadb.app.scrcpy

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

    private const val TypeInjectKeycode = 0
    private const val TypeInjectTouchEvent = 2
    private const val TypeBackOrScreenOn = 4
    private const val TypeSetDisplayPower = 10
    private const val TypeResetVideo = 17
    // 与 scrcpy server v4.1 官方 ControlMessage 一致：TYPE_CAMERA_SET_TORCH = 18。
    private const val TypeCameraSetTorch = 18

    fun keyEvent(action: Int, keyCode: Int, repeat: Int = 0, metaState: Int = 0): ByteArray {
        return ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
            .put(TypeInjectKeycode.toByte())
            .put(action.toByte())
            .putInt(keyCode)
            .putInt(repeat)
            .putInt(metaState)
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

    /**
     * 隐私屏：通过 SetDisplayPower 控制被控端物理屏幕开关，画面流保持推送。
     * 与 scrcpy server v4.1 协议保持一致（type=10，布尔值：1=开，0=关）。
     */
    fun setDisplayPower(on: Boolean): ByteArray {
        return byteArrayOf(
            TypeSetDisplayPower.toByte(),
            if (on) ScreenPowerMode.On else ScreenPowerMode.Off,
        )
    }

    fun resetVideo(): ByteArray = byteArrayOf(TypeResetVideo.toByte())

    /**
     * 摄像头手电筒（后置闪光灯）：通过 CameraManager 的 torch 控制消息控制。
     * 与 scrcpy server v4.1 协议保持一致（type=18，后跟 1 字节布尔值）。
     */
    fun setCameraTorch(enabled: Boolean): ByteArray {
        return byteArrayOf(TypeCameraSetTorch.toByte(), if (enabled) 1 else 0)
    }

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

    object ScreenPowerMode {
        const val Off: Byte = 0
        const val On: Byte = 1
    }
}
