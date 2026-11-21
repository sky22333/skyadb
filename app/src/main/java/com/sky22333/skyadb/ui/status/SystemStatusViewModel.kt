package com.sky22333.skyadb.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SystemStatusRefreshInterval(val millis: Long, val seconds: Int) {
    OneSecond(1_000L, 1),
    TwoSeconds(2_000L, 2),
    FiveSeconds(5_000L, 5),
    TenSeconds(10_000L, 10),
}

data class SystemStatusUiState(
    val metrics: SystemStatusMetrics = SystemStatusMetrics(),
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAtMillis: Long = 0L,
    val autoRefresh: Boolean = true,
    val refreshIntervalMillis: Long = SystemStatusRefreshInterval.OneSecond.millis,
    val processListVisible: Boolean = false,
    val networkInterfaceOverride: String? = null,
)

class SystemStatusViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = state.asStateFlow()

    private val metricsMutex = Mutex()
    private val networkMutex = Mutex()
    private val processMutex = Mutex()
    private var monitoringJob: Job? = null
    private var networkMonitoringJob: Job? = null
    private var processMonitoringJob: Job? = null
    private var manualRefreshJob: Job? = null
    private var previousSample: SystemStatusSample? = null
    private var previousSampleAtNanos: Long? = null
    private var previousNetworkSample: SystemStatusSample? = null
    private var previousNetworkSampleAtNanos: Long? = null
    private var previousProcessSample: SystemStatusSample? = null
    private var previousProcessSampleAtNanos: Long? = null
    private val processNetworkRateTracker = SystemProcessNetworkRateTracker()

    init {
        restartMonitoring()
    }

    fun setAutoRefresh(enabled: Boolean) {
        if (state.value.autoRefresh == enabled) return
        state.update { it.copy(autoRefresh = enabled, loading = if (enabled) it.loading else false) }
        if (enabled) {
            resetMetricSamples()
            resetNetworkSamples()
            resetProcessSamples()
            restartMonitoring()
        } else {
            monitoringJob?.cancel()
            networkMonitoringJob?.cancel()
            processMonitoringJob?.cancel()
        }
    }

    fun setRefreshInterval(millis: Long) {
        if (state.value.refreshIntervalMillis == millis) return
        state.update { it.copy(refreshIntervalMillis = millis) }
        if (state.value.autoRefresh) {
            resetMetricSamples()
            resetNetworkSamples()
            resetProcessSamples()
            restartMonitoring()
        }
    }

    fun setProcessListVisible(visible: Boolean) {
        if (state.value.processListVisible == visible) return
        state.update {
            it.copy(
                processListVisible = visible,
                metrics = if (visible) it.metrics else it.metrics.copy(processes = emptyList()),
            )
        }
        resetProcessSamples()
        if (!visible) {
            processMonitoringJob?.cancel()
        } else if (state.value.autoRefresh) {
            startProcessMonitoring()
        } else {
            refreshNow()
        }
    }

    fun setNetworkInterface(interfaceName: String) {
        if (state.value.networkInterfaceOverride == interfaceName) return
        state.update { it.copy(networkInterfaceOverride = interfaceName) }
        resetNetworkSamples()
        refreshNow()
    }

    fun refreshNow() {
        if (manualRefreshJob?.isActive == true) return
        manualRefreshJob = viewModelScope.launch {
            coroutineScope {
                launch { refreshMetrics() }
                if (state.value.processListVisible) {
                    launch { refreshProcesses() }
                }
            }
        }
    }

    fun retry() {
        refreshNow()
    }

    private fun restartMonitoring() {
        monitoringJob?.cancel()
        networkMonitoringJob?.cancel()
        processMonitoringJob?.cancel()
        if (!state.value.autoRefresh) return
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                refreshMetrics()
                delay(state.value.refreshIntervalMillis)
            }
        }
        startNetworkMonitoring()
        startProcessMonitoring()
    }

    private fun startNetworkMonitoring() {
        networkMonitoringJob?.cancel()
        if (!state.value.autoRefresh) return
        networkMonitoringJob = viewModelScope.launch {
            while (isActive) {
                val startedAtNanos = System.nanoTime()
                refreshNetworkRate()
                val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
                delay((state.value.refreshIntervalMillis - elapsedMillis).coerceAtLeast(0L))
            }
        }
    }

    private fun startProcessMonitoring() {
        processMonitoringJob?.cancel()
        if (!state.value.autoRefresh || !state.value.processListVisible) return
        processMonitoringJob = viewModelScope.launch {
            while (isActive) {
                refreshProcesses()
                delay(state.value.refreshIntervalMillis)
            }
        }
    }

    private suspend fun refreshMetrics() = metricsMutex.withLock {
        state.update { it.copy(loading = true) }
        val command = SystemStatusParser.buildCommand(includeProcesses = false)
        when (val result = adbRepository.runShell(command, timeoutMillis = 20_000)) {
            is AdbOperationResult.Success -> {
                if (result.data.exitCode != 0 && result.data.output.isBlank()) {
                    state.update {
                        it.copy(
                            loading = false,
                            error = result.data.errorOutput.ifBlank { appString(R.string.system_status_read_failed) },
                        )
                    }
                    return@withLock
                }
                val sampledAtNanos = System.nanoTime()
                val elapsedMillis = previousSampleAtNanos?.let { previous ->
                    ((sampledAtNanos - previous) / 1_000_000).coerceAtLeast(1L)
                } ?: 0L
                val parsed = SystemStatusParser.parse(
                    raw = result.data.output,
                    previous = previousSample,
                    elapsedMillis = elapsedMillis,
                    preferredNetworkInterface = state.value.networkInterfaceOverride,
                )
                previousSample = parsed.sample
                previousSampleAtNanos = sampledAtNanos
                state.update { current ->
                    current.copy(
                        metrics = parsed.metrics.copy(processes = current.metrics.processes),
                        loading = false,
                        error = null,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
            }
            is AdbOperationResult.Failure -> state.update {
                it.copy(loading = false, error = "${result.message}：${result.suggestion}")
            }
        }
    }

    private suspend fun refreshNetworkRate() = networkMutex.withLock {
        when (val result = adbRepository.runShell(
            SystemStatusParser.buildNetworkCommand(),
            timeoutMillis = 10_000,
        )) {
            is AdbOperationResult.Success -> {
                if (result.data.exitCode != 0 && result.data.output.isBlank()) return@withLock
                val sampledAtNanos = System.nanoTime()
                val elapsedMillis = previousNetworkSampleAtNanos?.let { previous ->
                    ((sampledAtNanos - previous) / 1_000_000).coerceAtLeast(1L)
                } ?: 0L
                val parsed = SystemStatusParser.parse(
                    raw = result.data.output,
                    previous = previousNetworkSample,
                    elapsedMillis = elapsedMillis,
                    preferredNetworkInterface = state.value.networkInterfaceOverride,
                )
                previousNetworkSample = parsed.sample
                previousNetworkSampleAtNanos = sampledAtNanos
                state.update { current ->
                    current.copy(
                        metrics = current.metrics.copy(
                            activeNetworkInterface = parsed.metrics.activeNetworkInterface,
                            networkInterfaces = parsed.metrics.networkInterfaces,
                            networkInterface = parsed.metrics.networkInterface,
                            rxBytesPerSecond = parsed.metrics.rxBytesPerSecond,
                            txBytesPerSecond = parsed.metrics.txBytesPerSecond,
                            networkRxBytes = parsed.metrics.networkRxBytes,
                            networkTxBytes = parsed.metrics.networkTxBytes,
                        ),
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
            }
            is AdbOperationResult.Failure -> Unit
        }
    }

    private suspend fun refreshProcesses() {
        if (!state.value.processListVisible) return
        processMutex.withLock {
            if (!state.value.processListVisible) return@withLock
            when (val result = adbRepository.runShell(
                SystemStatusParser.buildProcessCommand(),
                timeoutMillis = 20_000,
            )) {
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode != 0 && result.data.output.isBlank()) return@withLock
                    val sampledAtNanos = System.nanoTime()
                    val elapsedMillis = previousProcessSampleAtNanos?.let { previous ->
                        ((sampledAtNanos - previous) / 1_000_000).coerceAtLeast(1L)
                    } ?: 0L
                    val parsed = SystemStatusParser.parseProcesses(
                        raw = result.data.output,
                        previous = previousProcessSample,
                        elapsedMillis = elapsedMillis,
                    )
                    previousProcessSample = parsed.sample
                    previousProcessSampleAtNanos = sampledAtNanos
                    val networkRates = processNetworkRateTracker.update(
                        current = parsed.sample.uidRxBytes.mapValues { (uid, rxBytes) ->
                            rxBytes to (parsed.sample.uidTxBytes[uid] ?: 0L)
                        },
                        nowMillis = System.nanoTime() / 1_000_000,
                    )
                    val processes = parsed.processes.map { process ->
                        val rate = process.uid?.let(networkRates::get) ?: return@map process
                        process.copy(
                            rxBytesPerSecond = rate.rxBytesPerSecond,
                            txBytesPerSecond = rate.txBytesPerSecond,
                        )
                    }
                    if (!state.value.processListVisible) return@withLock
                    state.update { current ->
                        current.copy(metrics = current.metrics.copy(processes = processes))
                    }
                }
                is AdbOperationResult.Failure -> Unit
            }
        }
    }

    private fun resetMetricSamples() {
        previousSample = null
        previousSampleAtNanos = null
    }

    private fun resetNetworkSamples() {
        previousNetworkSample = null
        previousNetworkSampleAtNanos = null
    }

    private fun resetProcessSamples() {
        previousProcessSample = null
        previousProcessSampleAtNanos = null
        processNetworkRateTracker.reset()
    }
}
