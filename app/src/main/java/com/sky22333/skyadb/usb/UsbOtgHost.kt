package com.sky22333.skyadb.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbOtgAttachment(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val mode: UsbOtgMode,
    val hasPermission: Boolean,
)

class UsbOtgHost(context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val state = MutableStateFlow<List<UsbOtgAttachment>>(emptyList())

    val attachments: StateFlow<List<UsbOtgAttachment>> = state.asStateFlow()

    fun refresh() {
        state.value = usbManager.deviceList.values
            .mapNotNull(::toAttachment)
            .sortedBy { it.deviceName }
    }

    fun getDevice(deviceName: String): UsbDevice? = usbManager.deviceList[deviceName]

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice, permissionIntent: android.app.PendingIntent) {
        usbManager.requestPermission(device, permissionIntent)
    }

    fun usbManager(): UsbManager = usbManager

    private fun toAttachment(device: UsbDevice): UsbOtgAttachment? {
        val mode = AndroidUsbInterface.modeOf(device) ?: return null
        return UsbOtgAttachment(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            mode = mode,
            hasPermission = usbManager.hasPermission(device),
        )
    }
}
