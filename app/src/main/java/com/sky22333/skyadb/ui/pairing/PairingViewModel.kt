package com.sky22333.skyadb.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.validation.NetworkInputValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PairingUiState(
    val ip: String = "",
    val pairingPort: String = "",
    val pairingCode: String = "",
    val ipError: String? = null,
    val portError: String? = null,
    val codeError: String? = null,
    val pairEnabled: Boolean = false,
    val readyToConnect: Boolean = false,
    val connectPort: Int? = null,
    val operationStatus: OperationStatus = OperationStatus.Idle,
)

class PairingViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val mdnsDiscovery: AdbMdnsDiscovery = AppServices.adbMdnsDiscovery,
) : ViewModel() {
    private val state = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = state.asStateFlow()
    private var pairJob: Job? = null

    fun onIpChanged(value: String) {
        updateForm(ip = value.trim(), pairingPort = state.value.pairingPort, pairingCode = state.value.pairingCode)
    }

    fun onPairingPortChanged(value: String) {
        updateForm(
            ip = state.value.ip,
            pairingPort = value.filter { it.isDigit() }.take(5),
            pairingCode = state.value.pairingCode,
        )
    }

    fun onPairingCodeChanged(value: String) {
        updateForm(
            ip = state.value.ip,
            pairingPort = state.value.pairingPort,
            pairingCode = value.filter { it.isDigit() }.take(6),
        )
    }

    fun onDiscoveredEndpointSelected(host: String, port: Int) {
        pairJob?.cancel()
        pairJob = null
        val currentCode = state.value.pairingCode
        val validation = validateForm(host, port.toString(), currentCode)
        state.value = state.value.copy(
            ip = host,
            pairingPort = port.toString(),
            ipError = validation.ipError,
            portError = validation.portError,
            codeError = validation.codeError,
            pairEnabled = validation.isValid,
            readyToConnect = false,
            connectPort = null,
            operationStatus = OperationStatus.Success("已填入自动发现的配对地址，请输入 6 位配对码。"),
        )
    }

    fun onPairClicked() {
        val current = state.value
        val validation = validateForm(current.ip, current.pairingPort, current.pairingCode)
        if (!validation.isValid) {
            state.value = current.copy(
                ipError = validation.ipError,
                portError = validation.portError,
                codeError = validation.codeError,
                pairEnabled = false,
                readyToConnect = false,
                connectPort = null,
                operationStatus = OperationStatus.Failed(
                    text = "无法发起配对",
                    suggestion = "请检查配对 IP、配对端口和 6 位配对码是否正确。",
                ),
            )
            return
        }

        pairJob?.cancel()
        state.value = current.copy(
            ipError = validation.ipError,
            portError = validation.portError,
            codeError = validation.codeError,
            pairEnabled = false,
            readyToConnect = false,
            connectPort = null,
            operationStatus = OperationStatus.Running("正在配对 ${current.ip}:${current.pairingPort}"),
        )

        pairJob = viewModelScope.launch {
            when (
                val result = adbRepository.pair(
                    host = current.ip,
                    port = current.pairingPort.toInt(),
                    pairingCode = current.pairingCode,
                )
            ) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Running("正在查找连接端口…"),
                    )
                    val connectPort = mdnsDiscovery.findConnectPort(current.ip)
                    state.value = state.value.copy(
                        pairEnabled = true,
                        readyToConnect = true,
                        connectPort = connectPort,
                        operationStatus = if (connectPort != null) {
                            OperationStatus.Success("配对成功，已找到连接端口 $connectPort。")
                        } else {
                            OperationStatus.Success(
                                "配对成功。未自动发现连接端口，请确认无线调试页的「IP 地址与端口」后连接。",
                            )
                        },
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        pairEnabled = true,
                        readyToConnect = false,
                        connectPort = null,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private fun updateForm(ip: String, pairingPort: String, pairingCode: String) {
        pairJob?.cancel()
        pairJob = null
        val validation = validateForm(ip, pairingPort, pairingCode)
        state.value = state.value.copy(
            ip = ip,
            pairingPort = pairingPort,
            pairingCode = pairingCode,
            ipError = validation.ipError,
            portError = validation.portError,
            codeError = validation.codeError,
            pairEnabled = validation.isValid,
            readyToConnect = false,
            connectPort = null,
            operationStatus = OperationStatus.Idle,
        )
    }

    override fun onCleared() {
        pairJob?.cancel()
        mdnsDiscovery.stop()
        super.onCleared()
    }

    private fun validateForm(ip: String, pairingPort: String, pairingCode: String): PairingValidationResult {
        val ipError = NetworkInputValidator.ipv4Error(ip)
        val portError = NetworkInputValidator.portError(pairingPort, label = "配对端口")

        val codeError = when {
            pairingCode.isBlank() -> null
            pairingCode.length != 6 -> "配对码通常为 6 位数字"
            else -> null
        }

        return PairingValidationResult(
            ipError = ipError,
            portError = portError,
            codeError = codeError,
            isValid = ip.isNotBlank() &&
                pairingPort.isNotBlank() &&
                pairingCode.isNotBlank() &&
                ipError == null &&
                portError == null &&
                codeError == null,
        )
    }
}

private data class PairingValidationResult(
    val ipError: String?,
    val portError: String?,
    val codeError: String?,
    val isValid: Boolean,
)
