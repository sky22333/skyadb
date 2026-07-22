package com.sky22333.skyadb.ui.apps

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.apps.AppDisplayEnricher
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
    val apps: List<AppInfo> = emptyList(),
    val pendingAction: AppPendingAction? = null,
    val pendingExportPackage: String? = null,
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val loading: Boolean = false,
) {
    val filteredApps: List<AppInfo>
        get() {
            val typedApps = when (filter) {
                AppFilter.All -> apps
                AppFilter.User -> apps.filterNot { it.isSystem }
                AppFilter.System -> apps.filter { it.isSystem }
            }
            return if (query.isBlank()) {
                typedApps
            } else {
                typedApps.filter {
                    it.packageName.contains(query, ignoreCase = true) ||
                        it.label.contains(query, ignoreCase = true)
                }
            }
        }
}

enum class AppFilter(val label: String) {
    All("全部"),
    User("用户应用"),
    System("系统应用"),
}

sealed interface AppPendingAction {
    val packageName: String

    data class Uninstall(override val packageName: String) : AppPendingAction
    data class SetEnabled(
        override val packageName: String,
        val enabled: Boolean,
        val isSystem: Boolean,
    ) : AppPendingAction
}

class AppsViewModel(
    private val fileManager: LocalFileManager = AppServices.localFileManager,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = state.asStateFlow()
    private var labelJob: Job? = null

    fun onQueryChanged(value: String) {
        state.value = state.value.copy(query = value)
    }

    fun onFilterChanged(filter: AppFilter) {
        state.value = state.value.copy(filter = filter)
    }

    fun loadApps(force: Boolean = false) {
        if (!force && state.value.apps.isNotEmpty()) return
        labelJob?.cancel()
        viewModelScope.launch {
            state.value = state.value.copy(
                loading = true,
                operationStatus = OperationStatus.Running("正在读取应用列表"),
            )
            when (val result = adbRepository.listApps()) {
                is AdbOperationResult.Success -> {
                    // 先出列表，再后台补真名，避免 PackageManager 扫包挡住首帧
                    state.value = state.value.copy(
                        apps = result.data,
                        loading = false,
                        operationStatus = OperationStatus.Success("已读取 ${result.data.size} 个应用"),
                    )
                    val enriched = withContext(Dispatchers.Default) {
                        AppDisplayEnricher.enrichWithLocal(AppServices.context, result.data)
                    }
                    if (enriched !== result.data) {
                        state.value = state.value.copy(apps = enriched)
                    }
                    enrichRemoteLabels(enriched)
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        loading = false,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private fun enrichRemoteLabels(apps: List<AppInfo>) {
        val pending = apps
            .asSequence()
            .filter { !it.isSystem && AppDisplayEnricher.needsRemoteLabel(it) }
            .map { it.packageName }
            .toList()
        if (pending.isEmpty()) return

        labelJob?.cancel()
        labelJob = viewModelScope.launch {
            when (val result = adbRepository.resolveAppLabels(pending)) {
                is AdbOperationResult.Success -> {
                    if (result.data.isEmpty()) return@launch
                    val merged = AppDisplayEnricher.mergeRemoteLabels(state.value.apps, result.data)
                    state.value = state.value.copy(apps = merged)
                }
                is AdbOperationResult.Failure -> Unit
            }
        }
    }

    fun launchApp(packageName: String) {
        runAppAction("正在启动 $packageName") { adbRepository.launchApp(packageName) }
    }

    fun forceStopApp(packageName: String) {
        runAppAction("正在停止 $packageName") { adbRepository.forceStopApp(packageName) }
    }

    fun uninstallApp(packageName: String) {
        state.value = state.value.copy(pendingAction = AppPendingAction.Uninstall(packageName))
    }

    fun setAppEnabled(app: AppInfo, enabled: Boolean) {
        state.value = state.value.copy(
            pendingAction = AppPendingAction.SetEnabled(
                packageName = app.packageName,
                enabled = enabled,
                isSystem = app.isSystem,
            ),
        )
    }

    fun requestExport(packageName: String) {
        state.value = state.value.copy(pendingExportPackage = packageName)
    }

    fun exportPendingApp(uri: Uri?) {
        val packageName = state.value.pendingExportPackage ?: return
        state.value = state.value.copy(pendingExportPackage = null)
        if (uri == null) return

        state.value = state.value.copy(operationStatus = OperationStatus.Running("正在导出 $packageName"))
        viewModelScope.launch(Dispatchers.IO) {
            val target = fileManager.createExportApkFile(packageName)
            try {
                when (val result = adbRepository.exportAppApk(packageName, target)) {
                    is AdbOperationResult.Success -> saveExportedApk(result.data, uri)
                    is AdbOperationResult.Failure -> {
                        state.value = state.value.copy(
                            operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                        )
                    }
                }
            } finally {
                runCatching { target.delete() }
            }
        }
    }

    fun cancelPendingAction() {
        state.value = state.value.copy(pendingAction = null)
    }

    fun confirmPendingAction() {
        val action = state.value.pendingAction ?: return
        state.value = state.value.copy(pendingAction = null)
        when (action) {
            is AppPendingAction.Uninstall -> runAppAction(
                runningText = "正在卸载 ${action.packageName}",
                refreshAfterSuccess = true,
            ) {
                adbRepository.uninstall(action.packageName)
            }
            is AppPendingAction.SetEnabled -> runAppAction(
                runningText = if (action.enabled) {
                    "正在启用 ${action.packageName}"
                } else {
                    "正在冻结 ${action.packageName}"
                },
                refreshAfterSuccess = true,
            ) {
                adbRepository.setAppEnabled(action.packageName, action.enabled)
            }
        }
    }

    private fun runAppAction(
        runningText: String,
        refreshAfterSuccess: Boolean = false,
        action: suspend () -> AdbOperationResult<Unit>,
    ) {
        state.value = state.value.copy(operationStatus = OperationStatus.Running(runningText))
        viewModelScope.launch {
            when (val result = action()) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(operationStatus = OperationStatus.Success("操作完成"))
                    if (refreshAfterSuccess) {
                        loadApps(force = true)
                    }
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private fun saveExportedApk(file: File, uri: Uri) {
        runCatching {
            fileManager.copyToUri(file, uri)
        }.fold(
            onSuccess = {
                state.value = state.value.copy(operationStatus = OperationStatus.Success("APK 导出完成"))
            },
            onFailure = { error ->
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = "保存 APK 失败",
                        suggestion = error.message ?: "请确认保存位置可写，并保持应用前台运行。",
                    ),
                )
            },
        )
    }

    override fun onCleared() {
        labelJob?.cancel()
        super.onCleared()
    }
}
