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
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceUiState(
    val deviceName: String = appString(R.string.device_no_device_selected),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessionKind: AdbSessionKind = AdbSessionKind.None,
    val info: DeviceInfo = DeviceInfo(),
    val refreshing: Boolean = false,
    val refreshStatus: OperationStatus = OperationStatus.Idle,
    val infoExpanded: Boolean = false,
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
                        connectionState = connected.connectionState,
                        sessionKind = adbRepository.sessionKind(),
                    )
                } else {
                    state.value = state.value.copy(
                        deviceName = appString(R.string.device_no_device_selected),
                        connectionState = ConnectionState.Disconnected,
                        sessionKind = AdbSessionKind.None,
                        info = DeviceInfo(),
                        refreshing = false,
                        refreshStatus = OperationStatus.Idle,
                    )
                }
            }
        }
    }

    fun toggleInfoExpanded() {
        state.value = state.value.copy(infoExpanded = !state.value.infoExpanded)
    }

    fun refreshDeviceInfo() {
        state.value = state.value.copy(
            refreshing = true,
            refreshStatus = OperationStatus.Running(appString(R.string.device_refreshing)),
        )
        viewModelScope.launch {
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
