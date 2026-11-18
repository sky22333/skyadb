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

data class SystemStatusUiState(
    val metrics: SystemStatusMetrics = SystemStatusMetrics(),
    val loading: Boolean = true,
    val error: String? = null,
    val updatedAtMillis: Long = 0L,
)

class SystemStatusViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = state.asStateFlow()

    private var monitoringJob: Job? = null
    private var previousSample: SystemStatusSample? = null

    init {
        startMonitoring()
    }

    fun retry() {
        startMonitoring(force = true)
    }

    private fun startMonitoring(force: Boolean = false) {
        if (!force && monitoringJob?.isActive == true) return
        monitoringJob?.cancel()
        previousSample = null
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(RefreshIntervalMillis)
            }
        }
    }

    private suspend fun refreshOnce() {
        state.update { it.copy(loading = true) }
        val startedAt = System.nanoTime()
        val command = SystemStatusParser.buildCommand()
        when (val result = adbRepository.runShell(command, timeoutMillis = 15_000)) {
            is AdbOperationResult.Success -> {
                if (result.data.exitCode != 0 && result.data.output.isBlank()) {
                    state.update {
                        it.copy(
                            loading = false,
                            error = result.data.errorOutput.ifBlank { appString(R.string.system_status_read_failed) },
                        )
                    }
                    return
                }
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                val parsed = SystemStatusParser.parse(
                    raw = result.data.output,
                    previous = previousSample,
                    elapsedMillis = elapsedMillis,
                )
                previousSample = parsed.sample
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

    private companion object {
        const val RefreshIntervalMillis = 2_000L
    }
}
