package com.sky22333.skyadb.ui.usb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.usb.AndroidUsbAdbRepository
import com.sky22333.skyadb.usb.UsbAdbConnectResult
import com.sky22333.skyadb.usb.UsbAdbDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsbAdbUiState(
    val devices: List<UsbAdbDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val status: OperationStatus = OperationStatus.Idle,
    val connecting: Boolean = false,
    val connected: Boolean = false,
)

class UsbAdbViewModel(
    private val repository: AndroidUsbAdbRepository = AppServices.usbAdbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(UsbAdbUiState())
    val uiState: StateFlow<UsbAdbUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val devices = repository.listDevices()
        state.value = state.value.copy(
            devices = devices,
            selectedDeviceId = state.value.selectedDeviceId?.takeIf { id -> devices.any { it.id == id } }
                ?: devices.firstOrNull()?.id,
            status = if (devices.isEmpty()) {
                OperationStatus.Failed(
                    text = "未发现 USB ADB 设备",
                    suggestion = "请使用支持数据传输的 USB 线连接目标设备，并开启 USB 调试。",
                )
            } else {
                OperationStatus.Idle
            },
        )
    }

    fun selectDevice(deviceId: String) {
        state.value = state.value.copy(selectedDeviceId = deviceId, status = OperationStatus.Idle)
    }

    fun connectSelected() {
        val deviceId = state.value.selectedDeviceId ?: return
        state.value = state.value.copy(
            connecting = true,
            connected = false,
            status = OperationStatus.Running("正在连接 USB ADB 设备"),
        )
        viewModelScope.launch {
            when (val result = repository.connect(deviceId)) {
                is UsbAdbConnectResult.Connected -> {
                    state.value = state.value.copy(
                        connecting = false,
                        connected = true,
                        status = OperationStatus.Success("USB ADB 已连接：${result.endpoint}"),
                    )
                }
                is UsbAdbConnectResult.Failed -> {
                    state.value = state.value.copy(
                        connecting = false,
                        connected = false,
                        status = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }
}
