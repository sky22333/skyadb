package com.sky22333.skyadb.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RebootMode
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceUiState(
    val deviceName: String = appString(R.string.device_no_device_selected),
    val deviceId: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessionKind: AdbSessionKind = AdbSessionKind.None,
    val info: DeviceInfo = DeviceInfo(),
    val fastbootSummary: FastbootDeviceSummary? = null,
    val refreshing: Boolean = false,
    val rebooting: Boolean = false,
    val rebootStatus: OperationStatus = OperationStatus.Idle,
    val refreshStatus: OperationStatus = OperationStatus.Idle,
)

class DeviceViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            adbRepository.selectedDeviceInfo.collect { info ->
                state.value = state.value.copy(info = info)
            }
        }

        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                val connected = devices.firstOrNull { it.connectionState == ConnectionState.Connected }
                if (connected != null) {
                    state.value = state.value.copy(
                        deviceName = connected.name,
                        deviceId = connected.id,
                        connectionState = connected.connectionState,
                        sessionKind = adbRepository.sessionKind(),
                        rebootStatus = if (state.value.connectionState != ConnectionState.Connected && !state.value.rebooting) OperationStatus.Idle else state.value.rebootStatus,
                    )
                } else {
                    state.value = state.value.copy(
                        deviceName = appString(R.string.device_no_device_selected),
                        deviceId = null,
                        connectionState = ConnectionState.Disconnected,
                        sessionKind = AdbSessionKind.None,
                        info = DeviceInfo(),
                        fastbootSummary = null,
                        refreshing = false,
                        refreshStatus = OperationStatus.Idle,
                    )
                }
            }
        }
    }

    fun reboot(mode: RebootMode) {
        if (state.value.rebooting || state.value.connectionState != ConnectionState.Connected) return
        val deviceId = state.value.deviceId ?: return
        state.value = state.value.copy(rebooting = true, rebootStatus = OperationStatus.Running(appString(R.string.device_rebooting)))
        viewModelScope.launch {
            when (val result = adbRepository.reboot(mode, deviceId)) {
                is AdbOperationResult.Success -> state.value = state.value.copy(rebooting = false, rebootStatus = OperationStatus.Success(appString(R.string.fastboot_reboot_sent)))
                is AdbOperationResult.Failure -> state.value = state.value.copy(rebooting = false, rebootStatus = OperationStatus.Failed(result.message, result.suggestion))
            }
        }
    }

    fun refreshDeviceInfo() {
        if (state.value.refreshing || state.value.connectionState != ConnectionState.Connected) return
        state.value = state.value.copy(
            refreshing = true,
            refreshStatus = OperationStatus.Running(appString(R.string.device_refreshing)),
        )
        viewModelScope.launch {
            if (state.value.sessionKind == AdbSessionKind.UsbFastboot) {
                when (val result = adbRepository.fastbootSummary()) {
                    is AdbOperationResult.Success -> state.value = state.value.copy(refreshing = false, fastbootSummary = result.data, refreshStatus = OperationStatus.Success(appString(R.string.device_refresh_success)))
                    is AdbOperationResult.Failure -> state.value = state.value.copy(refreshing = false, refreshStatus = OperationStatus.Failed(result.message, result.suggestion))
                }
                return@launch
            }
            when (val result = adbRepository.refreshDeviceInfo()) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        info = result.data,
                        refreshStatus = OperationStatus.Success(appString(R.string.device_refresh_success)),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        refreshStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }
}
