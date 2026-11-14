package com.sky22333.skyadb.ui.apps

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import android.graphics.Bitmap
import com.sky22333.skyadb.apps.DeviceAppsProbe
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppsUiState(
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
    val apps: List<AppInfo> = emptyList(),
    val iconRevision: Long = 0,
    val pendingAction: AppPendingAction? = null,
    val pendingExportPackage: String? = null,
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val loading: Boolean = false,
) {
    val filteredApps: List<AppInfo>
        get() {
            val typedApps =
                when (filter) {
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

enum class AppFilter(@param:StringRes val labelRes: Int) {
    All(R.string.apps_filter_all),
    User(R.string.apps_filter_user),
    System(R.string.apps_filter_system),
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
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null
    @Volatile private var probe: DeviceAppsProbe? = null

    fun onQueryChanged(value: String) {
        state.update { snapshot -> snapshot.copy(query = value) }
    }

    fun onFilterChanged(filter: AppFilter) {
        state.update { snapshot -> snapshot.copy(filter = filter) }
    }

    fun loadApps(force: Boolean = false) {
        if (!force && state.value.apps.isNotEmpty()) return
        val previous = loadJob
        previous?.cancel()
        loadJob = viewModelScope.launch {
            previous?.join()
            state.update { snapshot ->
                snapshot.copy(
                    loading = true,
                    operationStatus =
                        OperationStatus.Running(appString(R.string.apps_loading_list)),
                )
            }
            try {
                val apps = withContext(Dispatchers.IO + NonCancellable) {
                    runCatching { probe?.close() }
                    probe = null
                    val opened = DeviceAppsProbe.open(AppServices.context, AppServices.kadbManager)
                    probe = opened
                    opened.list()
                }
                state.update { snapshot ->
                    snapshot.copy(
                        apps = apps,
                        iconRevision = snapshot.iconRevision + 1,
                        loading = false,
                        operationStatus = OperationStatus.Success(appString(R.string.apps_loaded_count, apps.size)),
                    )
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                withContext(Dispatchers.IO + NonCancellable) {
                    runCatching { probe?.close() }
                    probe = null
                }
                val fallback = adbRepository.listPackages()
                state.update { snapshot ->
                    snapshot.copy(
                        apps = (fallback as? AdbOperationResult.Success)?.data.orEmpty(),
                        iconRevision = snapshot.iconRevision + 1,
                        loading = false,
                        operationStatus = when (fallback) {
                            is AdbOperationResult.Success -> OperationStatus.Failed(appString(R.string.apps_probe_unavailable), appString(R.string.apps_probe_unavailable_hint))
                            is AdbOperationResult.Failure -> OperationStatus.Failed(fallback.message, fallback.suggestion)
                        },
                    )
                }
            }
        }
    }

    suspend fun loadIcon(packageName: String): Bitmap? =
        try { probe?.icon(packageName) }
        catch (_: java.io.IOException) { null }
        catch (_: IllegalStateException) { null }

    override fun onCleared() {
        val loading = loadJob
        cleanupScope.launch {
            loading?.join()
            runCatching { probe?.close() }
            probe = null
        }
        super.onCleared()
    }

    fun launchApp(packageName: String) {
        runAppAction(appString(R.string.apps_launching, packageName)) {
            adbRepository.launchApp(packageName)
        }
    }

    fun forceStopApp(packageName: String) {
        runAppAction(appString(R.string.apps_stopping, packageName)) {
            adbRepository.forceStopApp(packageName)
        }
    }

    fun uninstallApp(packageName: String) {
        state.update { snapshot ->
            snapshot.copy(pendingAction = AppPendingAction.Uninstall(packageName))
        }
    }

    fun setAppEnabled(app: AppInfo, enabled: Boolean) {
        state.update { snapshot ->
            snapshot.copy(
                pendingAction =
                    AppPendingAction.SetEnabled(
                        packageName = app.packageName,
                        enabled = enabled,
                        isSystem = app.isSystem,
                    )
            )
        }
    }

    fun requestExport(packageName: String) {
        state.update { snapshot -> snapshot.copy(pendingExportPackage = packageName) }
    }

    fun exportPendingApp(uri: Uri?) {
        val packageName = state.value.pendingExportPackage ?: return
        state.update { snapshot -> snapshot.copy(pendingExportPackage = null) }
        if (uri == null) return

        state.update { snapshot ->
            snapshot.copy(
                operationStatus =
                    OperationStatus.Running(appString(R.string.common_exporting_arg, packageName))
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = fileManager.createExportApkFile(packageName)
            try {
                when (val result = adbRepository.exportAppApk(packageName, target)) {
                    is AdbOperationResult.Success -> saveExportedApk(result.data, uri)
                    is AdbOperationResult.Failure -> {
                        state.update { snapshot ->
                            snapshot.copy(
                                operationStatus =
                                    OperationStatus.Failed(result.message, result.suggestion)
                            )
                        }
                    }
                }
            } finally {
                runCatching { target.delete() }
            }
        }
    }

    fun cancelPendingAction() {
        state.update { snapshot -> snapshot.copy(pendingAction = null) }
    }

    fun confirmPendingAction() {
        val action = state.value.pendingAction ?: return
        state.update { snapshot -> snapshot.copy(pendingAction = null) }
        when (action) {
            is AppPendingAction.Uninstall ->
                runAppAction(
                    runningText = appString(R.string.apps_uninstalling, action.packageName),
                    refreshAfterSuccess = true,
                ) {
                    adbRepository.uninstall(action.packageName)
                }
            is AppPendingAction.SetEnabled ->
                runAppAction(
                    runningText =
                        if (action.enabled) {
                            appString(R.string.apps_enabling, action.packageName)
                        } else {
                            appString(R.string.apps_disabling, action.packageName)
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
        state.update { snapshot ->
            snapshot.copy(operationStatus = OperationStatus.Running(runningText))
        }
        viewModelScope.launch {
            when (val result = action()) {
                is AdbOperationResult.Success -> {
                    state.update { snapshot ->
                        snapshot.copy(
                            operationStatus =
                                OperationStatus.Success(appString(R.string.apps_operation_complete))
                        )
                    }
                    if (refreshAfterSuccess) {
                        loadApps(force = true)
                    }
                }
                is AdbOperationResult.Failure -> {
                    state.update { snapshot ->
                        snapshot.copy(
                            operationStatus =
                                OperationStatus.Failed(result.message, result.suggestion)
                        )
                    }
                }
            }
        }
    }

    private fun saveExportedApk(file: File, uri: Uri) {
        runCatching {
                fileManager.copyToUri(file, uri)
            }
            .fold(
                onSuccess = {
                    state.update { snapshot ->
                        snapshot.copy(
                            operationStatus =
                                OperationStatus.Success(appString(R.string.apps_export_complete))
                        )
                    }
                },
                onFailure = { error ->
                    state.update { snapshot ->
                        snapshot.copy(
                            operationStatus =
                                OperationStatus.Failed(
                                    text = appString(R.string.apps_save_apk_failed),
                                    suggestion =
                                        error.message ?: appString(R.string.apps_save_apk_hint),
                                )
                        )
                    }
                },
            )
    }
}
