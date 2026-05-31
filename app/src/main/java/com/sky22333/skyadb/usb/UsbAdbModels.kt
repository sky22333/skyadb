package com.sky22333.skyadb.usb

data class UsbAdbDevice(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
) {
    val description: String
        get() = "VID:%04x PID:%04x".format(vendorId, productId)
}

sealed interface UsbAdbConnectResult {
    data class Connected(val endpoint: String) : UsbAdbConnectResult
    data class Failed(val message: String, val suggestion: String, val cause: Throwable? = null) : UsbAdbConnectResult
}
