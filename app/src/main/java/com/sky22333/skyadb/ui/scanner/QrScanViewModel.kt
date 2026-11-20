package com.sky22333.skyadb.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.qr.QrPayload
import com.sky22333.skyadb.qr.QrPayloadParser
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QrScanUiState(
    val status: OperationStatus = OperationStatus.Idle,
    val busy: Boolean = false,
    val scanLocked: Boolean = false,
    val connectedEndpoint: String? = null,
)

class QrScanViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val mdnsDiscovery: AdbMdnsDiscovery = AppServices.adbMdnsDiscovery,
) : ViewModel() {
    private val state = MutableStateFlow(QrScanUiState())
    val uiState: StateFlow<QrScanUiState> = state.asStateFlow()
    private var operationJob: Job? = null

    fun onQrScanned(raw: String) {
        if (state.value.scanLocked || state.value.connectedEndpoint != null) return
        state.value = state.value.copy(scanLocked = true)
        when (val payload = QrPayloadParser.parse(raw)) {
            null -> state.value = state.value.copy(
                status = OperationStatus.Failed(
                    appString(R.string.scan_qr_unsupported),
                    appString(R.string.scan_qr_unsupported_hint),
                )
            )
            is QrPayload.WirelessPairing -> pairAndConnect(payload)
            is QrPayload.DirectConnect -> connect(payload)
        }
    }

    fun reset() {
        operationJob?.cancel()
        state.value = QrScanUiState()
    }

    private fun pairAndConnect(payload: QrPayload.WirelessPairing) {
        state.value = state.value.copy(
            busy = true,
            status = OperationStatus.Running(appString(R.string.scan_finding_pairing_service)),
        )
        operationJob = viewModelScope.launch {
            val pairingEndpoint = mdnsDiscovery.findPairingEndpoint(payload.serviceName)
            if (pairingEndpoint == null) {
                state.value = state.value.copy(
                    busy = false,
                    status = OperationStatus.Failed(
                        appString(R.string.scan_pairing_service_not_found),
                        appString(R.string.scan_pairing_service_not_found_hint),
                    ),
                )
                return@launch
            }
            state.value = state.value.copy(
                status = OperationStatus.Running(appString(R.string.scan_pairing_with, pairingEndpoint.name)),
            )
            when (
                val pairResult =
                    adbRepository.pair(pairingEndpoint.host, pairingEndpoint.port, payload.pairingCode)
            ) {
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        busy = false,
                        status = OperationStatus.Failed(pairResult.message, pairResult.suggestion),
                    )
                    return@launch
                }
                is AdbOperationResult.Success -> Unit
            }

            state.value = state.value.copy(
                status = OperationStatus.Running(appString(R.string.scan_finding_connect_port)),
            )
            val connectPort = mdnsDiscovery.findConnectPort(pairingEndpoint.host, 8_000)
            if (connectPort == null) {
                state.value = state.value.copy(
                    busy = false,
                    status = OperationStatus.Failed(
                        appString(R.string.scan_connect_port_not_found),
                        appString(R.string.scan_connect_port_not_found_hint),
                    ),
                )
                return@launch
            }
            connectEndpoint(pairingEndpoint.host, connectPort)
        }
    }

    private fun connect(payload: QrPayload.DirectConnect) {
        state.value = state.value.copy(
            busy = true,
            status = OperationStatus.Running(
                appString(R.string.scan_connecting_endpoint, payload.endpoint.authority)
            ),
        )
        operationJob = viewModelScope.launch {
            connectEndpoint(payload.endpoint.host, payload.endpoint.port)
        }
    }

    private suspend fun connectEndpoint(host: String, port: Int) {
        when (val result = adbRepository.connect(host, port)) {
            is AdbOperationResult.Success -> state.value = state.value.copy(
                busy = false,
                connectedEndpoint = result.data,
                status = OperationStatus.Success(appString(R.string.scan_connected, result.data)),
            )
            is AdbOperationResult.Failure -> state.value = state.value.copy(
                busy = false,
                status = OperationStatus.Failed(result.message, result.suggestion),
            )
        }
    }
}
