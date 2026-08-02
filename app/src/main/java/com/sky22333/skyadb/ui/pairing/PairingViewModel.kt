package com.sky22333.skyadb.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.i18n.appString
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
            operationStatus = OperationStatus.Success(appString(R.string.pairing_autofill_hint)),
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
                    text = appString(R.string.pairing_cannot_start),
                    suggestion = appString(R.string.pairing_check_fields),
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
            operationStatus = OperationStatus.Running(
                appString(R.string.pairing_pairing_with, current.ip, current.pairingPort),
            ),
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
                        operationStatus = OperationStatus.Running(appString(R.string.pairing_finding_port)),
                    )
                    val connectPort = mdnsDiscovery.findConnectPort(current.ip)
                    state.value = state.value.copy(
                        pairEnabled = true,
                        readyToConnect = true,
                        connectPort = connectPort,
                        operationStatus = if (connectPort != null) {
                            OperationStatus.Success(appString(R.string.pairing_success_port_found, connectPort))
                        } else {
                            OperationStatus.Success(appString(R.string.pairing_success_no_port))
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
        val ipError = NetworkInputValidator.ipv4Error(ip)?.let(::appString)
        val portError = NetworkInputValidator.portError(pairingPort, labelRes = R.string.pairing_port_label)
            ?.resolve(AppServices.context)

        val codeError = when {
            pairingCode.isBlank() -> null
            pairingCode.length != 6 -> appString(R.string.pairing_code_length_hint)
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
