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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val endpoint: String = "",
    val defaultPort: Int = 5555,
    val recentDevices: List<AdbDevice> = emptyList(),
    val usbAttachments: List<UsbOtgAttachment> = emptyList(),
    val endpointError: String? = null,
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

    private var connectionJob: Job? = null

    init {
        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                state.value =
                    state.value.copy(
                        recentDevices = devices,
                        canDisconnect =
                            devices.any { it.connectionState == ConnectionState.Connected } ||
                                adbRepository.sessionKind() != AdbSessionKind.None,
                    )
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                val current = state.value
                val validation = validateForm(current.endpoint, settings.defaultPort)
                state.value = current.copy(
                    defaultPort = settings.defaultPort,
                    endpointError = validation.error,
                    connectEnabled = validation.isValid && connectionJob?.isActive != true,
                )
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
                    is UsbPermissionEvent.Granted -> if (state.value.connectingUsbDeviceName == event.deviceName) connectUsbOtg(event.deviceName)
                    is UsbPermissionEvent.Denied -> if (state.value.connectingUsbDeviceName == event.deviceName) {
                        state.value =
                            state.value.copy(
                                connectingUsbDeviceName = null,
                                connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                                operationStatus =
                                    OperationStatus.Failed(
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

    fun onEndpointChanged(value: String) {
        updateForm(value)
    }

    fun onRecentDeviceSelected(device: AdbDevice) {
        updateForm("${device.host}:${device.port}")
    }

    fun onDiscoveredEndpointSelected(host: String, port: Int) {
        updateForm("$host:$port")
        onConnectClicked()
    }

    fun onPairedHostPrepared(host: String) {
        val current = state.value
        val port = NetworkInputValidator.endpoint(current.endpoint, current.defaultPort).port ?: current.defaultPort
        updateForm("$host:$port")
        state.value = state.value.copy(
            operationStatus = OperationStatus.Success(appString(R.string.home_pairing_success_ip_filled)),
        )
    }

    fun onDisconnectClicked() {
        connectionJob?.cancel()
        viewModelScope.launch {
            adbRepository.disconnect()
            state.value =
                state.value.copy(
                    canDisconnect = false,
                    connectingUsbDeviceName = null,
                    operationStatus =
                        OperationStatus.Success(appString(R.string.home_disconnected)),
                )
        }
    }

    fun onConnectClicked() {
        val current = state.value
        val validation = validateForm(current.endpoint, current.defaultPort)
        if (!validation.isValid) {
            state.value =
                current.copy(
                    endpointError = validation.error,
                    connectEnabled = false,
                    operationStatus =
                        OperationStatus.Failed(
                            text = appString(R.string.home_cannot_connect),
                            suggestion = appString(R.string.home_check_ip_port),
                        ),
                )
            return
        }

        state.value =
            current.copy(
                endpointError = validation.error,
                connectEnabled = false,
                operationStatus =
                    OperationStatus.Running(
                        appString(R.string.home_connecting, validation.host, validation.port.toString())
                    ),
            )

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            when (val result = adbRepository.connect(validation.host, requireNotNull(validation.port))) {
                is AdbOperationResult.Success -> {
                    state.value =
                        state.value.copy(
                            connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                            canDisconnect = true,
                            operationStatus =
                                OperationStatus.Success(
                                    appString(R.string.home_connect_success, result.data)
                                ),
                        )
                }
                is AdbOperationResult.Failure -> {
                    state.value =
                        state.value.copy(
                            connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                            operationStatus =
                                OperationStatus.Failed(result.message, result.suggestion),
                        )
                }
            }
        }
    }

    fun onUsbConnectClicked(deviceName: String) {
        val attachment =
            state.value.usbAttachments.firstOrNull { it.deviceName == deviceName } ?: return
        if (!attachment.hasPermission) {
            state.value =
                state.value.copy(
                    connectingUsbDeviceName = deviceName,
                    operationStatus =
                        OperationStatus.Running(appString(R.string.home_waiting_usb_permission)),
                )
            AppServices.usbOtgActions.askPermission(deviceName)
            return
        }
        connectUsbOtg(deviceName)
    }

    /** 官方 USB Host 文档：Detached 仅应清理与该 [UsbDevice] 的通信。 根因：此前对任意拔出都 disconnect，会误杀 Wi‑Fi ADB 会话。 */
    private suspend fun onUsbDeviceDetached(deviceName: String) {
        val connecting = state.value.connectingUsbDeviceName == deviceName
        if (adbRepository.isActiveUsbDevice(deviceName)) {
            adbRepository.disconnect()
            state.value =
                state.value.copy(
                    connectingUsbDeviceName = null,
                    canDisconnect = false,
                    operationStatus =
                        OperationStatus.Success(appString(R.string.repo_usb_device_disconnected)),
                )
            return
        }
        if (connecting) {
            connectionJob?.cancel()
            state.value =
                state.value.copy(
                    connectingUsbDeviceName = null,
                    connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                    operationStatus =
                        OperationStatus.Failed(
                            appString(R.string.repo_usb_device_disconnected),
                            appString(R.string.usb_connection_hint),
                        ),
                )
        }
    }

    private fun connectUsbOtg(deviceName: String) {
        val attachment = state.value.usbAttachments.firstOrNull { it.deviceName == deviceName }
        val modeLabel =
            when (attachment?.mode) {
                UsbOtgMode.Adb -> "ADB"
                UsbOtgMode.Fastboot -> "Fastboot"
                null -> "USB"
            }
        state.value =
            state.value.copy(
                connectingUsbDeviceName = deviceName,
                connectEnabled = false,
                operationStatus =
                    OperationStatus.Running(appString(R.string.home_usb_otg_connecting, modeLabel)),
            )
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            when (val result = adbRepository.connectUsbOtg(deviceName)) {
                is AdbOperationResult.Success -> {
                    state.value =
                        state.value.copy(
                            connectingUsbDeviceName = null,
                            connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                            canDisconnect = true,
                            operationStatus =
                                OperationStatus.Success(
                                    appString(R.string.home_usb_connect_success, result.data)
                                ),
                        )
                }
                is AdbOperationResult.Failure -> {
                    state.value =
                        state.value.copy(
                            connectingUsbDeviceName = null,
                            connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                            operationStatus =
                                OperationStatus.Failed(result.message, result.suggestion),
                        )
                }
            }
        }
    }

    private fun updateForm(endpoint: String) {
        if (connectionJob?.isActive == true) return
        val validation = validateForm(endpoint, state.value.defaultPort)
        state.value = state.value.copy(
            endpoint = endpoint,
            endpointError = validation.error,
            connectEnabled = validation.isValid,
            operationStatus = OperationStatus.Idle,
        )
    }

    private fun validateForm(endpoint: String, defaultPort: Int): ValidationResult {
        val result = NetworkInputValidator.endpoint(endpoint, defaultPort)
        return ValidationResult(
            host = result.host,
            port = result.port,
            error = result.ipError?.let(::appString) ?: result.portError?.resolve(AppServices.context),
            isValid = result.isValid,
        )
    }
}

private data class ValidationResult(
    val host: String,
    val port: Int?,
    val error: String?,
    val isValid: Boolean,
)
