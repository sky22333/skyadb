package com.sky22333.skyadb.scrcpy

import android.view.MotionEvent

data class MirrorTouchEvent(
    val actionMasked: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val pointerId: Int,
    val actionButton: Int,
    val buttons: Int,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
) {
    val isMove: Boolean
        get() = actionMasked == MotionEvent.ACTION_MOVE

    companion object {
        fun from(event: MotionEvent, surfaceWidth: Int, surfaceHeight: Int): MirrorTouchEvent? {
            if (ScrcpyProtocol.motionAction(event.actionMasked) == null) return null
            val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
            return MirrorTouchEvent(
                actionMasked = event.actionMasked,
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
                pressure = event.getPressure(pointerIndex),
                pointerId = event.getPointerId(pointerIndex),
                actionButton = event.actionButton,
                buttons = event.buttonState,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            )
        }
    }
}
