package com.qwadb.app.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.data.AppSettingsStore
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.status.SystemStatus
import com.qwadb.app.status.SystemStatusCollector
import com.qwadb.app.ui.settings.StatusRefreshIntervalMaxMs
import com.qwadb.app.ui.settings.StatusRefreshIntervalMinMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StatusUiState(
    val status: SystemStatus = SystemStatus(),
    val refreshing: Boolean = false,
    val lastUpdatedAt: Long? = null,
    val refreshIntervalMs: Int = StatusRefreshIntervalMinMs,
    val errorMessage: String? = null,
    val errorSuggestion: String? = null,
)

/** 系统状态：按设置中的刷新间隔（默认 5000ms，范围 500-10000ms）定时采集并展示。 */
class StatusViewModel(
    private val collector: SystemStatusCollector = AppServices.systemStatusCollector,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = state.asStateFlow()

    private var intervalMs: Int = StatusRefreshIntervalMinMs
    private var loopJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                intervalMs = settings.statusRefreshIntervalMs
                    .coerceIn(StatusRefreshIntervalMinMs, StatusRefreshIntervalMaxMs)
                state.value = state.value.copy(refreshIntervalMs = intervalMs)
                restartLoop()
            }
        }
    }

    /** 立即刷新一次并重启定时循环。 */
    fun refreshNow() {
        restartLoop()
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(intervalMs.toLong())
            }
        }
    }

    private suspend fun refresh() {
        state.value = state.value.copy(refreshing = true)
        when (val result = collector.collect()) {
            is AdbOperationResult.Success -> {
                state.value = state.value.copy(
                    status = result.data,
                    lastUpdatedAt = System.currentTimeMillis(),
                    refreshing = false,
                    errorMessage = null,
                    errorSuggestion = null,
                )
            }
            is AdbOperationResult.Failure -> {
                state.value = state.value.copy(
                    refreshing = false,
                    errorMessage = result.message,
                    errorSuggestion = result.suggestion,
                )
            }
        }
    }

    override fun onCleared() {
        loopJob?.cancel()
        super.onCleared()
    }
}
