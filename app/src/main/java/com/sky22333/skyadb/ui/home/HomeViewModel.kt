package com.sky22333.skyadb.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbEndpoint
import com.sky22333.skyadb.model.AdbLinkKind
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
    val activeDeviceId: String? = null,
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
            adbRepository.activeDeviceId.collect { deviceId ->
                state.value = state.value.copy(activeDeviceId = deviceId)
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
        val cancelledPendingConnection = connectionJob?.isActive == true
        connectionJob?.cancel()
        connectionJob = null
        state.value = state.value.copy(
            connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
            connectingUsbDeviceName =
                if (cancelledPendingConnection) null else state.value.connectingUsbDeviceName,
            operationStatus = if (cancelledPendingConnection) OperationStatus.Idle else state.value.operationStatus,
        )
        if (device.linkKind == AdbLinkKind.Wifi) {
            updateForm(AdbEndpoint.format(device.host, device.port))
        }
        when {
            device.connectionState == ConnectionState.Connected &&
                device.id != state.value.activeDeviceId -> selectDevice(device)
            device.connectionState != ConnectionState.Connected &&
                device.linkKind == AdbLinkKind.Wifi -> onConnectClicked()
            device.connectionState != ConnectionState.Connected &&
                device.linkKind == AdbLinkKind.UsbOtg ->
                onUsbConnectClicked(device.id.substringAfter(':'))
        }
    }

    fun onRecentDeviceDisconnect(device: AdbDevice) {
        viewModelScope.launch {
            val targetId = when (device.linkKind) {
                AdbLinkKind.Wifi -> AdbEndpoint.format(device.host, device.port)
                AdbLinkKind.UsbOtg -> device.id
            }
            val result = adbRepository.disconnectDevice(targetId)
            val snapshot = state.value
            val status = when (result) {
                    is AdbOperationResult.Success -> OperationStatus.Success(
                        appString(R.string.home_device_disconnected, device.name)
                    )
                    is AdbOperationResult.Failure -> OperationStatus.Failed(result.message, result.suggestion)
            }
            val devices = if (result is AdbOperationResult.Success) {
                snapshot.recentDevices.map { item ->
                    if (item.id == device.id || item.id == targetId) {
                        item.copy(connectionState = ConnectionState.Disconnected)
                    } else {
                        item
                    }
                }
            } else {
                snapshot.recentDevices
            }
            state.value = snapshot.copy(
                recentDevices = devices,
                canDisconnect = devices.any { it.connectionState == ConnectionState.Connected } ||
                    adbRepository.sessionKind() != AdbSessionKind.None,
                operationStatus = status,
            )
        }
    }

    fun onRecentDeviceRename(device: AdbDevice, name: String) {
        viewModelScope.launch {
            val result = adbRepository.renameDevice(device.id, name)
            state.value = state.value.copy(
                operationStatus = when (result) {
                    is AdbOperationResult.Success ->
                        OperationStatus.Success(
                            appString(R.string.home_device_renamed, name.trim())
                        )
                    is AdbOperationResult.Failure ->
                        OperationStatus.Failed(result.message, result.suggestion)
                }
            )
        }
    }

    fun onRecentDeviceDelete(device: AdbDevice) {
        viewModelScope.launch {
            val targetId = when (device.linkKind) {
                AdbLinkKind.Wifi -> AdbEndpoint.format(device.host, device.port)
                AdbLinkKind.UsbOtg -> device.id
            }
            val result = adbRepository.removeDevice(device.id)
            val snapshot = state.value
            val status = when (result) {
                    is AdbOperationResult.Success -> OperationStatus.Success(
                        appString(R.string.home_device_deleted, device.name)
                    )
                    is AdbOperationResult.Failure -> OperationStatus.Failed(result.message, result.suggestion)
            }
            val devices = if (result is AdbOperationResult.Success) {
                snapshot.recentDevices.filterNot {
                    it.id == device.id || it.id == targetId
                }
            } else {
                snapshot.recentDevices
            }
            state.value = snapshot.copy(
                recentDevices = devices,
                canDisconnect = devices.any { it.connectionState == ConnectionState.Connected } ||
                    adbRepository.sessionKind() != AdbSessionKind.None,
                operationStatus = status,
            )
        }
    }

    fun onDiscoveredEndpointSelected(host: String, port: Int) {
        updateForm(AdbEndpoint.format(AdbEndpoint.hostForSocket(host), port))
        onConnectClicked()
    }

    fun onPairedHostPrepared(host: String) {
        val current = state.value
        val port = NetworkInputValidator.endpoint(current.endpoint, current.defaultPort).port ?: current.defaultPort
        updateForm(AdbEndpoint.format(AdbEndpoint.hostForSocket(host), port))
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
            state.value.usbAttachments.firstOrNull { it.deviceName == deviceName }
                ?: run {
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(
                            appString(R.string.repo_usb_device_disconnected),
                            appString(R.string.repo_usb_device_disconnected_suggestion),
                        )
                    )
                    return
                }
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
        val attemptJob = connectionJob
        val connecting = state.value.connectingUsbDeviceName == deviceName
        val ownsUi = connectionJob === attemptJob && (!(attemptJob?.isActive == true) || connecting)
        val active = adbRepository.isActiveUsbDevice(deviceName)
        adbRepository.disconnectDevice("usb-otg:$deviceName")
        adbRepository.disconnectDevice("fastboot:$deviceName")
        if (active) {
            if (connecting) attemptJob?.cancel()
            if (ownsUi) {
                connectionJob = null
                state.value =
                    state.value.copy(
                        connectingUsbDeviceName = null,
                        connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                        operationStatus =
                            OperationStatus.Success(appString(R.string.repo_usb_device_disconnected)),
                    )
            }
            return
        }
        if (connecting) {
            attemptJob?.cancel()
            if (ownsUi) {
                connectionJob = null
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

    private fun selectDevice(device: AdbDevice) {
        connectionJob?.cancel()
        state.value = state.value.copy(
            connectEnabled = false,
            operationStatus = OperationStatus.Running(
                appString(R.string.home_switching_device, device.name)
            )
        )
        connectionJob = viewModelScope.launch {
            val result = adbRepository.selectDevice(device.id)
            state.value = state.value.copy(
                connectEnabled = validateForm(state.value.endpoint, state.value.defaultPort).isValid,
                operationStatus = when (result) {
                    is AdbOperationResult.Success ->
                        OperationStatus.Success(
                            appString(R.string.home_switch_device_success, device.name)
                        )
                    is AdbOperationResult.Failure ->
                        OperationStatus.Failed(result.message, result.suggestion)
                }
            )
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
