package com.qwadb.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.diagnostics.DiagnosticLogger
import com.qwadb.app.diagnostics.DiagnosticModule
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SystemLogUiState(
    val lineLimit: Int = 300,
    val query: String = "",
    val level: String = LogLevelAll,
    val logs: List<String> = emptyList(),
    val loading: Boolean = false,
    val status: OperationStatus = OperationStatus.Idle,
) {
    val filteredLogs: List<String>
        get() {
            val keyword = query.trim()
            return logs.filter { line ->
                val levelMatched = level == LogLevelAll ||
                    line.contains(" $level ") ||
                    line.contains("/$level")
                val queryMatched = keyword.isBlank() || line.contains(keyword, ignoreCase = true)
                levelMatched && queryMatched
            }
        }
}

/** Stable filter sentinel; UI localizes via [R.string.logs_level_all]. */
const val LogLevelAll = "ALL"
val LogLineLimits = listOf(100, 300, 1000)
val LogLevels = listOf(LogLevelAll, "E", "W", "I")

class SystemLogViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(SystemLogUiState())
    val uiState: StateFlow<SystemLogUiState> = state.asStateFlow()

    fun onQueryChanged(value: String) {
        state.value = state.value.copy(query = value)
    }

    fun onLevelSelected(level: String) {
        state.value = state.value.copy(level = level)
    }

    fun onLineLimitSelected(limit: Int) {
        state.value = state.value.copy(lineLimit = limit.coerceIn(MinLines, MaxLines))
    }

    fun clearLogs() {
        state.value = state.value.copy(
            logs = emptyList(),
            loading = false,
            status = OperationStatus.Idle,
        )
    }

    fun loadLogs() {
        if (state.value.loading) return

        val limit = state.value.lineLimit.coerceIn(MinLines, MaxLines)

        state.value = state.value.copy(
            loading = true,
            status = OperationStatus.Running(appString(R.string.logs_reading_recent, limit)),
        )

        viewModelScope.launch {
            val newState = when (val result = adbRepository.runShell("logcat -d -t $limit")) {
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        val lines = result.data.output
                            .lineSequence()
                            .map { it.trimEnd() }
                            .filter { it.isNotBlank() }
                            .toList()
                            .takeLast(MaxLines)

                        state.value.copy(
                            logs = lines,
                            loading = false,
                            status = OperationStatus.Success(appString(R.string.logs_read_success, lines.size)),
                        )
                    } else {
                        DiagnosticLogger.record(
                            module = DiagnosticModule.Logs,
                            operation = "读取系统日志",
                            message = "读取系统日志失败",
                            suggestion = result.data.errorOutput.ifBlank {
                                appString(R.string.logs_confirm_logcat_allowed)
                            },
                        )
                        state.value.copy(
                            loading = false,
                            status = OperationStatus.Failed(
                                text = appString(R.string.logs_read_failed),
                                suggestion = result.data.errorOutput.ifBlank {
                                    appString(R.string.logs_confirm_logcat_allowed)
                                },
                            ),
                        )
                    }
                }

                is AdbOperationResult.Failure -> {
                    state.value.copy(
                        loading = false,
                        status = OperationStatus.Failed(
                            text = result.message,
                            suggestion = result.suggestion,
                        ),
                    )
                }
            }

            state.value = newState
        }
    }

    private companion object {
        const val MinLines = 100
        const val MaxLines = 1000
    }
}
