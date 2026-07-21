package com.sky22333.skyadb.ui.localapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.localapps.LocalAppExporter
import com.sky22333.skyadb.localapps.LocalInstalledApp
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalAppsUiState(
    val query: String = "",
    val apps: List<LocalInstalledApp> = emptyList(),
    val loading: Boolean = false,
    val operationStatus: OperationStatus = OperationStatus.Idle,
) {
    val filteredApps: List<LocalInstalledApp>
        get() {
            val keyword = query.trim()
            return if (keyword.isBlank()) {
                apps
            } else {
                apps.filter {
                    it.label.contains(keyword, ignoreCase = true) ||
                        it.packageName.contains(keyword, ignoreCase = true)
                }
            }
        }
}

class LocalAppsViewModel(
    private val exporter: LocalAppExporter = AppServices.localAppExporter,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(LocalAppsUiState())
    val uiState: StateFlow<LocalAppsUiState> = state.asStateFlow()

    fun loadApps(force: Boolean = false) {
        if (!force && state.value.apps.isNotEmpty()) return
        state.value = state.value.copy(
            loading = true,
            operationStatus = OperationStatus.Running("正在读取本机应用"),
        )
        viewModelScope.launch {
            try {
                val apps = exporter.listUserApps()
                state.value = state.value.copy(
                    apps = apps,
                    loading = false,
                    operationStatus = OperationStatus.Success("已读取 ${apps.size} 个可启动用户应用"),
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DiagnosticLogger.record(
                    module = DiagnosticModule.Apps,
                    operation = "读取本机应用",
                    message = "读取本机应用失败",
                    suggestion = error.message ?: "请确认系统允许读取已安装应用列表。",
                    cause = error,
                )
                state.value = state.value.copy(
                    loading = false,
                    operationStatus = OperationStatus.Failed(
                        text = "读取本机应用失败",
                        suggestion = error.message ?: "请确认系统允许读取已安装应用列表。",
                    ),
                )
            }
        }
    }

    fun onQueryChanged(value: String) {
        state.value = state.value.copy(query = value)
    }

    fun installToDevice(app: LocalInstalledApp) {
        if (state.value.operationStatus is OperationStatus.Running) return
        if (!app.installable) {
            state.value = state.value.copy(
                operationStatus = OperationStatus.Failed(
                    text = "暂不支持该应用",
                    suggestion = "该应用可能是拆分安装包，当前仅支持单 APK 用户应用。",
                ),
            )
            return
        }

        state.value = state.value.copy(operationStatus = OperationStatus.Running("正在导出 ${app.label}"))
        viewModelScope.launch {
            val apkFile = try {
                exporter.exportSingleApk(app)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DiagnosticLogger.record(
                    module = DiagnosticModule.Apps,
                    operation = "导出本机应用",
                    target = app.packageName,
                    message = "导出应用失败",
                    suggestion = error.message ?: "该应用安装包无法读取或已被系统限制。",
                    cause = error,
                )
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = "导出应用失败",
                        suggestion = error.message ?: "该应用安装包无法读取或已被系统限制。",
                    ),
                )
                return@launch
            }

            state.value = state.value.copy(operationStatus = OperationStatus.Running("正在安装 ${app.label}"))
            try {
                when (
                    val result = adbRepository.install(apkFile) { transferred, total ->
                        state.value = state.value.copy(
                            operationStatus = adbTransferRunning(
                                transferringLabel = "正在传输 ${app.label}",
                                finishingLabel = "正在安装 ${app.label}",
                                transferred = transferred,
                                total = total,
                            ),
                        )
                    }
                ) {
                    is AdbOperationResult.Success -> {
                        state.value = state.value.copy(operationStatus = OperationStatus.Success("${app.label} 安装完成"))
                    }
                    is AdbOperationResult.Failure -> {
                        state.value = state.value.copy(
                            operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                        )
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DiagnosticLogger.record(
                    module = DiagnosticModule.Install,
                    operation = "安装本机应用",
                    target = app.packageName,
                    message = "安装应用失败",
                    suggestion = error.message ?: "请确认目标设备仍保持连接，并允许安装该应用。",
                    cause = error,
                )
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = "安装应用失败",
                        suggestion = error.message ?: "请确认目标设备仍保持连接，并允许安装该应用。",
                    ),
                )
            } finally {
                apkFile.delete()
            }
        }
    }
}
