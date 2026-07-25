package com.sky22333.skyadb.ui.localapps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.i18n.appString
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
            operationStatus = OperationStatus.Running(appString(R.string.localapps_loading)),
        )
        viewModelScope.launch {
            try {
                val apps = exporter.listUserApps()
                state.value = state.value.copy(
                    apps = apps,
                    loading = false,
                    operationStatus = OperationStatus.Success(appString(R.string.localapps_loaded_count, apps.size)),
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DiagnosticLogger.record(
                    module = DiagnosticModule.Apps,
                    operation = appString(R.string.localapps_op_read),
                    message = appString(R.string.localapps_read_failed),
                    suggestion = error.message ?: appString(R.string.localapps_confirm_read_permission),
                    cause = error,
                )
                state.value = state.value.copy(
                    loading = false,
                    operationStatus = OperationStatus.Failed(
                        text = appString(R.string.localapps_read_failed),
                        suggestion = error.message ?: appString(R.string.localapps_confirm_read_permission),
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
                    text = appString(R.string.localapps_unsupported_app),
                    suggestion = appString(R.string.localapps_split_apk_hint),
                ),
            )
            return
        }

        state.value = state.value.copy(
            operationStatus = OperationStatus.Running(appString(R.string.common_exporting_arg, app.label)),
        )
        viewModelScope.launch {
            val apkFile = try {
                exporter.exportSingleApk(app)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DiagnosticLogger.record(
                    module = DiagnosticModule.Apps,
                    operation = appString(R.string.localapps_op_export),
                    target = app.packageName,
                    message = appString(R.string.localapps_export_failed),
                    suggestion = error.message ?: appString(R.string.localapps_export_failed_hint),
                    cause = error,
                )
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = appString(R.string.localapps_export_failed),
                        suggestion = error.message ?: appString(R.string.localapps_export_failed_hint),
                    ),
                )
                return@launch
            }

            state.value = state.value.copy(
                operationStatus = OperationStatus.Running(appString(R.string.common_installing_arg, app.label)),
            )
            try {
                when (
                    val result = adbRepository.install(apkFile) { transferred, total ->
                        state.value = state.value.copy(
                            operationStatus = adbTransferRunning(
                                transferringLabel = appString(R.string.common_transferring_arg, app.label),
                                finishingLabel = appString(R.string.common_installing_arg, app.label),
                                transferred = transferred,
                                total = total,
                            ),
                        )
                    }
                ) {
                    is AdbOperationResult.Success -> {
                        state.value = state.value.copy(
                            operationStatus = OperationStatus.Success(
                                appString(R.string.localapps_install_success, app.label),
                            ),
                        )
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
                    operation = appString(R.string.localapps_op_install),
                    target = app.packageName,
                    message = appString(R.string.localapps_install_failed),
                    suggestion = error.message ?: appString(R.string.localapps_install_failed_hint),
                    cause = error,
                )
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = appString(R.string.localapps_install_failed),
                        suggestion = error.message ?: appString(R.string.localapps_install_failed_hint),
                    ),
                )
            } finally {
                apkFile.delete()
            }
        }
    }
}
