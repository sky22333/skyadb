package com.sky22333.skyadb.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.usb.UsbOtgAttachment
import com.sky22333.skyadb.usb.UsbOtgMode
import com.sky22333.skyadb.usb.UsbPermissionEvent
import com.sky22333.skyadb.validation.NetworkInputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val ip: String = "",
    val port: String = "5555",
    val recentDevices: List<AdbDevice> = emptyList(),
    val usbAttachments: List<UsbOtgAttachment> = emptyList(),
    val ipError: String? = null,
    val portError: String? = null,
    val connectEnabled: Boolean = false,
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val connectingUsbDeviceName: String? = null,
)

class HomeViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                state.value = state.value.copy(recentDevices = devices)
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                val current = state.value
                if (current.port == HomeUiState().port) {
                    updateForm(ip = current.ip, port = settings.defaultPort.toString())
                }
            }
        }
        viewModelScope.launch {
            AppServices.usbOtgHost.attachments.collect { attachments ->
                state.value = state.value.copy(usbAttachments = attachments)
            }
        }
        viewModelScope.launch {
            AppServices.usbOtgActions.events.collect { event ->
                when (event) {
                    is UsbPermissionEvent.Granted -> connectUsbOtg(event.deviceName)
                    is UsbPermissionEvent.Denied -> {
                        state.value = state.value.copy(
                            connectingUsbDeviceName = null,
                            connectEnabled = true,
                            operationStatus = OperationStatus.Failed(
                                text = "USB 授权被拒绝",
                                suggestion = "请在系统弹窗中允许 sky adb 访问该 USB 设备后重试。",
                            ),
                        )
                    }
                    is UsbPermissionEvent.Detached -> {
                        if (adbRepository.sessionKind() != AdbSessionKind.None) {
                            adbRepository.disconnect()
                        }
                        state.value = state.value.copy(
                            connectingUsbDeviceName = null,
                            operationStatus = OperationStatus.Success("USB 设备已断开"),
                        )
                    }
                }
            }
        }
        refreshUsbDevices()
    }

    fun refreshUsbDevices() {
        AppServices.usbOtgActions.refresh()
    }

    fun onIpChanged(value: String) {
        updateForm(ip = value.trim(), port = state.value.port)
    }

    fun onPortChanged(value: String) {
        updateForm(ip = state.value.ip, port = value.filter { it.isDigit() }.take(5))
    }

    fun onRecentDeviceSelected(device: AdbDevice) {
        updateForm(ip = device.host, port = device.port.toString())
    }

    fun onDiscoveredEndpointSelected(host: String, port: Int) {
        val portText = port.toString()
        val validation = validateForm(host, portText)
        state.value = state.value.copy(
            ip = host,
            port = portText,
            ipError = validation.ipError,
            portError = validation.portError,
            connectEnabled = validation.isValid,
            operationStatus = OperationStatus.Success("已填入发现的地址，请确认后连接。"),
        )
    }

    fun onConnectClicked() {
        val current = state.value
        val validation = validateForm(current.ip, current.port)
        if (!validation.isValid) {
            state.value = current.copy(
                ipError = validation.ipError,
                portError = validation.portError,
                connectEnabled = false,
                operationStatus = OperationStatus.Failed(
                    text = "无法发起连接",
                    suggestion = "请先检查 IP 地址和端口是否正确。",
                ),
            )
            return
        }

        state.value = current.copy(
            ipError = validation.ipError,
            portError = validation.portError,
            connectEnabled = false,
            operationStatus = OperationStatus.Running("正在连接 ${current.ip}:${current.port}"),
        )

        viewModelScope.launch {
            when (val result = adbRepository.connect(current.ip, current.port.toInt())) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        connectEnabled = true,
                        operationStatus = OperationStatus.Success("设备连接成功：${result.data}"),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        connectEnabled = true,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun onUsbConnectClicked(deviceName: String) {
        val attachment = state.value.usbAttachments.firstOrNull { it.deviceName == deviceName } ?: return
        if (!attachment.hasPermission) {
            state.value = state.value.copy(
                connectingUsbDeviceName = deviceName,
                operationStatus = OperationStatus.Running("等待 USB 授权…"),
            )
            AppServices.usbOtgActions.askPermission(deviceName)
            return
        }
        connectUsbOtg(deviceName)
    }

    private fun connectUsbOtg(deviceName: String) {
        val attachment = state.value.usbAttachments.firstOrNull { it.deviceName == deviceName }
        val modeLabel = when (attachment?.mode) {
            UsbOtgMode.Adb -> "ADB"
            UsbOtgMode.Fastboot -> "Fastboot"
            null -> "USB"
        }
        state.value = state.value.copy(
            connectingUsbDeviceName = deviceName,
            connectEnabled = false,
            operationStatus = OperationStatus.Running("正在通过 USB OTG 连接 $modeLabel 设备…"),
        )
        viewModelScope.launch {
            when (val result = adbRepository.connectUsbOtg(deviceName)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        connectingUsbDeviceName = null,
                        connectEnabled = true,
                        operationStatus = OperationStatus.Success("USB 连接成功：${result.data}"),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        connectingUsbDeviceName = null,
                        connectEnabled = true,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private fun updateForm(ip: String, port: String) {
        val validation = validateForm(ip, port)
        state.value = state.value.copy(
            ip = ip,
            port = port,
            ipError = validation.ipError,
            portError = validation.portError,
            connectEnabled = validation.isValid,
            operationStatus = OperationStatus.Idle,
        )
    }

    private fun validateForm(ip: String, port: String): ValidationResult {
        val ipError = NetworkInputValidator.ipv4Error(ip)
        val portError = NetworkInputValidator.portError(port)

        return ValidationResult(
            ipError = ipError,
            portError = portError,
            isValid = ip.isNotBlank() && port.isNotBlank() && ipError == null && portError == null,
        )
    }
}

private data class ValidationResult(
    val ipError: String?,
    val portError: String?,
    val isValid: Boolean,
)
