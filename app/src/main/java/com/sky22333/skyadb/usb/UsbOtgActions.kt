package com.sky22333.skyadb.usb

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class UsbOtgActions(
    private val host: UsbOtgHost,
) {
    private val permissionEvents = MutableSharedFlow<UsbPermissionEvent>(extraBufferCapacity = 1)

    val events: SharedFlow<UsbPermissionEvent> = permissionEvents.asSharedFlow()

    var requestPermission: (deviceName: String) -> Unit = {}

    fun refresh() {
        host.refresh()
    }

    fun askPermission(deviceName: String) {
        requestPermission(deviceName)
    }

    fun onPermissionGranted(deviceName: String) {
        host.refresh()
        permissionEvents.tryEmit(UsbPermissionEvent.Granted(deviceName))
    }

    fun onPermissionDenied(deviceName: String) {
        host.refresh()
        permissionEvents.tryEmit(UsbPermissionEvent.Denied(deviceName))
    }

    fun onDeviceDetached(deviceName: String) {
        host.refresh()
        permissionEvents.tryEmit(UsbPermissionEvent.Detached(deviceName))
    }
}

sealed interface UsbPermissionEvent {
    data class Granted(val deviceName: String) : UsbPermissionEvent
    data class Denied(val deviceName: String) : UsbPermissionEvent
    data class Detached(val deviceName: String) : UsbPermissionEvent
}
