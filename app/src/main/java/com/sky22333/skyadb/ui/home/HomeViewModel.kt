package com.sky22333.skyadb.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.ConnectionState
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
    val canDisconnect: Boolean = false,
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
                state.value = state.value.copy(
                    recentDevices = devices,
                    canDisconnect = devices.any { it.connectionState == ConnectionState.Connected } ||
                        adbRepository.sessionKind() != AdbSessionKind.None,
                )
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
                                text = appString(R.string.home_usb_permission_denied),
                                suggestion = appString(R.string.home_usb_permission_hint),
                            ),
                        )
                    }
                    is UsbPermissionEvent.Detached -> onUsbDeviceDetached(event.deviceName)
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
            operationStatus = OperationStatus.Success(appString(R.string.home_autofill_discovered)),
        )
    }

    fun onPairedHostPrepared(host: String) {
        val port = state.value.port
        val validation = validateForm(host, port)
        state.value = state.value.copy(
            ip = host,
            port = port,
            ipError = validation.ipError,
            portError = validation.portError,
            connectEnabled = validation.isValid,
            operationStatus = OperationStatus.Success(appString(R.string.home_pairing_success_ip_filled)),
        )
    }

    fun onDisconnectClicked() {
        viewModelScope.launch {
            adbRepository.disconnect()
            state.value = state.value.copy(
                canDisconnect = false,
                connectingUsbDeviceName = null,
                operationStatus = OperationStatus.Success(appString(R.string.home_disconnected)),
            )
        }
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
                    text = appString(R.string.home_cannot_connect),
                    suggestion = appString(R.string.home_check_ip_port),
                ),
            )
            return
        }

        state.value = current.copy(
            ipError = validation.ipError,
            portError = validation.portError,
            connectEnabled = false,
            operationStatus = OperationStatus.Running(appString(R.string.home_connecting, current.ip, current.port)),
        )

        viewModelScope.launch {
            when (val result = adbRepository.connect(current.ip, current.port.toInt())) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        connectEnabled = true,
                        canDisconnect = true,
                        operationStatus = OperationStatus.Success(appString(R.string.home_connect_success, result.data)),
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
                operationStatus = OperationStatus.Running(appString(R.string.home_waiting_usb_permission)),
            )
            AppServices.usbOtgActions.askPermission(deviceName)
            return
        }
        connectUsbOtg(deviceName)
    }

    /**
     * 官方 USB Host 文档：Detached 仅应清理与该 [UsbDevice] 的通信。
     * 根因：此前对任意拔出都 disconnect，会误杀 Wi‑Fi ADB 会话。
     */
    private suspend fun onUsbDeviceDetached(deviceName: String) {
        val connecting = state.value.connectingUsbDeviceName == deviceName
        if (adbRepository.isActiveUsbDevice(deviceName)) {
            adbRepository.disconnect()
            state.value = state.value.copy(
                connectingUsbDeviceName = null,
                canDisconnect = false,
                operationStatus = OperationStatus.Success(appString(R.string.repo_usb_device_disconnected)),
            )
            return
        }
        if (connecting) {
            state.value = state.value.copy(connectingUsbDeviceName = null)
        }
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
            operationStatus = OperationStatus.Running(appString(R.string.home_usb_otg_connecting, modeLabel)),
        )
        viewModelScope.launch {
            when (val result = adbRepository.connectUsbOtg(deviceName)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        connectingUsbDeviceName = null,
                        connectEnabled = true,
                        canDisconnect = true,
                        operationStatus = OperationStatus.Success(appString(R.string.home_usb_connect_success, result.data)),
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
        val ipError = NetworkInputValidator.ipv4Error(ip)?.let(::appString)
        val portError = NetworkInputValidator.portError(port)?.resolve(AppServices.context)

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
