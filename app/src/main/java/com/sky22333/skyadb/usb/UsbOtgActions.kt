package com.sky22333.skyadb.usb

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * USB 权限 / 插拔事件。
 *
 * 使用带缓冲的 [Channel] + [receiveAsFlow]，避免 [kotlinx.coroutines.flow.MutableSharedFlow.tryEmit]
 * 在缓冲不足时丢弃 Detached/Granted（见 kotlinx SharedFlow / Channel 事件建模建议）。
 */
class UsbOtgActions(
    private val host: UsbOtgHost,
) {
    private val permissionEvents = Channel<UsbPermissionEvent>(Channel.BUFFERED)

    val events: Flow<UsbPermissionEvent> = permissionEvents.receiveAsFlow()
    private val permissionResult = MutableStateFlow<UsbPermissionEvent?>(null)
    val latestPermissionResult: StateFlow<UsbPermissionEvent?> = permissionResult.asStateFlow()

    var requestPermission: (deviceName: String) -> Unit = {}

    fun refresh() {
        host.refresh()
    }

    fun askPermission(deviceName: String) {
        permissionResult.value = null
        requestPermission(deviceName)
    }

    fun onPermissionGranted(deviceName: String) {
        host.refresh()
        permissionResult.value = UsbPermissionEvent.Granted(deviceName)
        permissionEvents.trySend(UsbPermissionEvent.Granted(deviceName))
    }

    fun onPermissionDenied(deviceName: String) {
        host.refresh()
        permissionResult.value = UsbPermissionEvent.Denied(deviceName)
        permissionEvents.trySend(UsbPermissionEvent.Denied(deviceName))
    }

    fun onDeviceDetached(deviceName: String) {
        host.refresh()
        permissionResult.value = UsbPermissionEvent.Detached(deviceName)
        permissionEvents.trySend(UsbPermissionEvent.Detached(deviceName))
    }
}

sealed interface UsbPermissionEvent {
    data class Granted(val deviceName: String) : UsbPermissionEvent
    data class Denied(val deviceName: String) : UsbPermissionEvent
    data class Detached(val deviceName: String) : UsbPermissionEvent
}
