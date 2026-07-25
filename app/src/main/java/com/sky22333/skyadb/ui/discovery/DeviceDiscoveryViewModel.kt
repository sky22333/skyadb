package com.sky22333.skyadb.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.discovery.AdbMdnsEndpoint
import com.sky22333.skyadb.discovery.AdbScanResult
import com.sky22333.skyadb.discovery.LanAdbScanner
import com.sky22333.skyadb.discovery.LocalNetwork
import com.sky22333.skyadb.discovery.NetworkInfoProvider
import com.sky22333.skyadb.discovery.ScanRangeParser
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceDiscoveryUiState(
    val networks: List<LocalNetwork> = emptyList(),
    val ports: List<Int> = listOf(5555),
    val scanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val results: List<AdbScanResult> = emptyList(),
    val mdnsRunning: Boolean = false,
    val mdnsEndpoints: List<AdbMdnsEndpoint> = emptyList(),
    val mdnsError: String? = null,
    val status: OperationStatus = OperationStatus.Idle,
) {
    val network: LocalNetwork? = networks.firstOrNull()
}

class DeviceDiscoveryViewModel(
    private val networkInfoProvider: NetworkInfoProvider = AppServices.networkInfoProvider,
    private val scanner: LanAdbScanner = AppServices.lanAdbScanner,
    private val mdnsDiscovery: AdbMdnsDiscovery = AppServices.adbMdnsDiscovery,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DeviceDiscoveryUiState())
    val uiState: StateFlow<DeviceDiscoveryUiState> = state.asStateFlow()
    private var scanJob: Job? = null
    private var recentDevices: List<AdbDevice> = emptyList()
    private var configuredScanRanges: String = ""
    private var discoveryActive = false

    init {
        viewModelScope.launch {
            mdnsDiscovery.state.collect { mdns ->
                state.value = state.value.copy(
                    mdnsRunning = mdns.running,
                    mdnsEndpoints = mdns.endpoints,
                    mdnsError = mdns.error,
                )
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                configuredScanRanges = settings.scanRanges
                state.value = state.value.copy(ports = listOf(5555, settings.defaultPort).distinct())
                if (!state.value.scanning) refreshNetworkInfoOnly()
            }
        }
        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                recentDevices = devices
                if (!state.value.scanning) refreshNetworkInfoOnly()
            }
        }
    }

    fun startDiscovery() {
        discoveryActive = true
        refreshNetwork()
    }

    fun stopDiscovery() {
        discoveryActive = false
        mdnsDiscovery.stop()
    }

    fun refreshNetwork() {
        if (discoveryActive) {
            mdnsDiscovery.stop()
            mdnsDiscovery.start()
        }
        refreshNetworkInfoOnly()
    }

    private fun refreshNetworkInfoOnly() {
        val networks = buildScanNetworks()
        state.value = state.value.copy(
            networks = networks,
            status = if (networks.isEmpty()) {
                OperationStatus.Failed(
                    appString(R.string.discovery_cannot_scan_lan),
                    appString(R.string.discovery_connect_wifi_hint),
                )
            } else {
                OperationStatus.Idle
            },
        )
    }

    fun startScan() {
        val networks = state.value.networks.ifEmpty {
            refreshNetworkInfoOnly()
            return
        }
        scanJob?.cancel()
        state.value = state.value.copy(
            scanning = true,
            scannedCount = 0,
            totalCount = 0,
            results = emptyList(),
            status = OperationStatus.Running(appString(R.string.discovery_preparing_scan, networks.size)),
        )
        scanJob = viewModelScope.launch {
            try {
                val hosts = withContext(Dispatchers.Default) {
                    networks.flatMap { it.expandHosts() }.distinct()
                }
                if (!state.value.scanning) return@launch
                state.value = state.value.copy(
                    totalCount = hosts.size * state.value.ports.size,
                    status = OperationStatus.Running(appString(R.string.discovery_scanning, networks.size)),
                )
                scanner.scan(
                    hosts = hosts,
                    ports = state.value.ports,
                    onProgress = { progress ->
                        state.value = state.value.copy(
                            scannedCount = progress.scanned,
                            totalCount = progress.total,
                        )
                    },
                    onResult = { result ->
                        state.value = state.value.copy(
                            results = (state.value.results + result)
                                .distinctBy { it.endpoint }
                                .sortedWith(compareBy<AdbScanResult> { it.host }.thenBy { it.port }),
                        )
                    },
                )
                if (!state.value.scanning) return@launch
                val found = state.value.results.size
                state.value = state.value.copy(
                    scanning = false,
                    status = OperationStatus.Success(appString(R.string.discovery_scan_complete, found)),
                )
                scanJob = null
            } catch (_: CancellationException) {
                // 用户主动取消时由 stopScan 负责更新界面。
            } catch (error: Throwable) {
                state.value = state.value.copy(
                    scanning = false,
                    status = OperationStatus.Failed(
                        appString(R.string.discovery_scan_stopped),
                        error.message ?: appString(R.string.discovery_retry_later_hint),
                    ),
                )
                scanJob = null
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        state.value = state.value.copy(
            scanning = false,
            status = OperationStatus.Success(appString(R.string.discovery_scan_canceled)),
        )
    }

    override fun onCleared() {
        scanJob?.cancel()
        mdnsDiscovery.stop()
        super.onCleared()
    }

    private fun buildScanNetworks(): List<LocalNetwork> {
        val configured = ScanRangeParser.parseConfiguredRanges(configuredScanRanges)
        val current = networkInfoProvider.currentLocalNetworks()
        val recent = recentDevices.mapNotNull { device ->
            networkInfoProvider.subnetForHost(device.host, sourceLabelRes = R.string.discovery_source_recent_device)
        }
        return (configured + recent + current)
            .distinctBy { it.subnetLabel }
            .take(MaxScanRanges)
    }

    private companion object {
        const val MaxScanRanges = 6
    }
}
