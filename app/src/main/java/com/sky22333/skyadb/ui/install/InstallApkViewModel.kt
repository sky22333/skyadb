package com.sky22333.skyadb.ui.install

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstallApkUiState(
    val selectedName: String? = null,
    val selectedUriText: String? = null,
    val installEnabled: Boolean = false,
    val operationStatus: OperationStatus = OperationStatus.Idle,
)

class InstallApkViewModel(
    private val fileManager: LocalFileManager = AppServices.localFileManager,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(InstallApkUiState())
    val uiState: StateFlow<InstallApkUiState> = state.asStateFlow()

    private var selectedUri: Uri? = null

    fun onApkSelected(uri: Uri?) {
        if (uri == null) return
        selectedUri = uri
        val name = fileManager.displayName(uri)
        state.value = state.value.copy(
            selectedName = name,
            selectedUriText = uri.toString(),
            installEnabled = name.endsWith(".apk", ignoreCase = true),
            operationStatus = if (name.endsWith(".apk", ignoreCase = true)) {
                OperationStatus.Idle
            } else {
                OperationStatus.Failed("文件类型可能不正确", "请选择以 .apk 结尾的安装包文件。")
            },
        )
    }

    fun onInstallClick() {
        val uri = selectedUri
        if (uri == null) {
            state.value = state.value.copy(
                installEnabled = false,
                operationStatus = OperationStatus.Failed("未选择 APK", "请先选择一个本地 APK 文件。"),
            )
            return
        }

        state.value = state.value.copy(
            installEnabled = false,
            operationStatus = OperationStatus.Running("正在准备 APK 文件"),
        )

        viewModelScope.launch {
            runCatching {
                fileManager.copyToCache(uri)
            }.fold(
                onSuccess = { file ->
                    try {
                        state.value = state.value.copy(
                            operationStatus = OperationStatus.Running("正在传输 ${file.name}"),
                        )
                        when (
                            val result = adbRepository.install(file) { transferred, total ->
                                state.value = state.value.copy(
                                    operationStatus = adbTransferRunning(
                                        transferringLabel = "正在传输 ${file.name}",
                                        finishingLabel = "正在安装 ${file.name}",
                                        transferred = transferred,
                                        total = total,
                                    ),
                                )
                            }
                        ) {
                            is AdbOperationResult.Success -> {
                                state.value = state.value.copy(
                                    installEnabled = true,
                                    operationStatus = OperationStatus.Success("APK 安装完成"),
                                )
                            }
                            is AdbOperationResult.Failure -> {
                                state.value = state.value.copy(
                                    installEnabled = true,
                                    operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                                )
                            }
                        }
                    } finally {
                        runCatching { file.delete() }
                    }
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        installEnabled = true,
                        operationStatus = OperationStatus.Failed(
                            text = "读取 APK 失败",
                            suggestion = error.message ?: "请确认文件仍存在，并允许 App 读取该文件。",
                        ),
                    )
                },
            )
        }
    }
}
