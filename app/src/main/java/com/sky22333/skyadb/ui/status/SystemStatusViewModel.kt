package com.sky22333.skyadb.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.Job
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
    val processListVisible: Boolean = true,
)

class SystemStatusViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = state.asStateFlow()

    private val refreshMutex = Mutex()
    private var monitoringJob: Job? = null
    private var manualRefreshJob: Job? = null
    private var previousSample: SystemStatusSample? = null
    private var previousSampleAtNanos: Long? = null

    init {
        restartMonitoring()
    }

    fun setAutoRefresh(enabled: Boolean) {
        if (state.value.autoRefresh == enabled) return
        state.update { it.copy(autoRefresh = enabled) }
        if (enabled) {
            resetSamples()
            restartMonitoring()
        } else {
            monitoringJob?.cancel()
        }
    }

    fun setRefreshInterval(millis: Long) {
        if (state.value.refreshIntervalMillis == millis) return
        state.update { it.copy(refreshIntervalMillis = millis) }
        if (state.value.autoRefresh) {
            resetSamples()
            restartMonitoring()
        }
    }

    fun setProcessListVisible(visible: Boolean) {
        if (state.value.processListVisible == visible) return
        state.update { it.copy(processListVisible = visible) }
        resetSamples()
        if (state.value.autoRefresh) {
            restartMonitoring()
        } else {
            refreshNow()
        }
    }

    fun refreshNow() {
        if (manualRefreshJob?.isActive == true) return
        manualRefreshJob = viewModelScope.launch { refreshOnce() }
    }

    fun retry() {
        refreshNow()
    }

    private fun restartMonitoring() {
        monitoringJob?.cancel()
        if (!state.value.autoRefresh) return
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(state.value.refreshIntervalMillis)
            }
        }
    }

    private suspend fun refreshOnce() = refreshMutex.withLock {
        state.update { it.copy(loading = true) }
        val command = SystemStatusParser.buildCommand(includeProcesses = state.value.processListVisible)
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
                )
                previousSample = parsed.sample
                previousSampleAtNanos = sampledAtNanos
                state.update {
                    it.copy(
                        metrics = parsed.metrics,
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

    private fun resetSamples() {
        previousSample = null
        previousSampleAtNanos = null
    }
}
