package com.sky22333.skyadb.ui.download

import java.io.File
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.download.DownloadResult
import com.sky22333.skyadb.download.DownloadState
import com.sky22333.skyadb.download.DownloadTask
import com.sky22333.skyadb.download.NetworkDownloadManager
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.validation.DevicePathValidator
import com.sky22333.skyadb.validation.DownloadInputValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnlineDownloadMode(@param:StringRes val labelRes: Int) {
    InstallApk(R.string.download_mode_install_apk),
    PushFile(R.string.download_mode_push_file),
}

data class OnlineDownloadUiState(
    val mode: OnlineDownloadMode = OnlineDownloadMode.InstallApk,
    val url: String = "",
    val targetPath: String = "/sdcard/Download/",
    val urlError: String? = null,
    val targetPathError: String? = null,
    val actionEnabled: Boolean = false,
    val task: DownloadTask? = null,
    val operationStatus: OperationStatus = OperationStatus.Idle,
)

class OnlineDownloadViewModel(
    private val downloadManager: NetworkDownloadManager = AppServices.downloadManager,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(OnlineDownloadUiState())
    val uiState: StateFlow<OnlineDownloadUiState> = state.asStateFlow()

    private var downloadJob: Job? = null

    fun onModeChanged(mode: OnlineDownloadMode) {
        updateForm(mode = mode, url = state.value.url, targetPath = state.value.targetPath)
    }

    fun onUrlChanged(value: String) {
        updateForm(mode = state.value.mode, url = value.trim(), targetPath = state.value.targetPath)
    }

    fun onTargetPathChanged(value: String) {
        updateForm(mode = state.value.mode, url = state.value.url, targetPath = value.trim())
    }

    fun onStartClick() {
        val current = state.value
        val validation = validate(current.mode, current.url, current.targetPath)
        if (!validation.valid) {
            state.value = current.copy(
                urlError = validation.urlError,
                targetPathError = validation.targetPathError,
                actionEnabled = false,
                operationStatus = OperationStatus.Failed(
                    appString(R.string.download_cannot_start),
                    appString(R.string.download_check_url_path),
                ),
            )
            return
        }

        downloadJob?.cancel()
        state.value = current.copy(
            actionEnabled = false,
            operationStatus = OperationStatus.Running(appString(R.string.download_preparing)),
            task = DownloadTask(
                url = current.url,
                fileName = appString(R.string.download_waiting_filename),
                targetPath = current.targetPath,
                progress = 0f,
                state = DownloadState.Waiting,
            ),
        )

        downloadJob = viewModelScope.launch {
            val result = downloadManager.download(current.url) { task ->
                state.value = state.value.copy(
                    task = task.copy(targetPath = current.targetPath),
                    operationStatus = OperationStatus.Running(task.message),
                )
            }

            when (result) {
                is DownloadResult.Success -> handleDownloadedFile(current, result)
                is DownloadResult.Failure -> {
                    DiagnosticLogger.record(
                        module = DiagnosticModule.Download,
                        operation = appString(R.string.download_op_download),
                        target = current.url,
                        message = result.message,
                        suggestion = result.suggestion,
                        cause = result.cause,
                    )
                    state.value = state.value.copy(
                        actionEnabled = true,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                        task = state.value.task?.copy(state = DownloadState.Failed, message = result.message),
                    )
                }
                DownloadResult.Canceled -> {
                    state.value = state.value.copy(
                        actionEnabled = true,
                        operationStatus = OperationStatus.Failed(
                            appString(R.string.download_canceled),
                            appString(R.string.download_canceled_hint),
                        ),
                        task = state.value.task?.copy(
                            state = DownloadState.Canceled,
                            message = appString(R.string.download_canceled),
                        ),
                    )
                }
            }
        }
    }

    fun onCancelClick() {
        downloadManager.cancelCurrentDownload()
        downloadJob?.cancel()
        state.value = state.value.copy(
            actionEnabled = true,
            operationStatus = OperationStatus.Failed(
                appString(R.string.download_canceled),
                appString(R.string.download_canceled_hint),
            ),
            task = state.value.task?.copy(
                state = DownloadState.Canceled,
                message = appString(R.string.download_canceled),
            ),
        )
    }

    private suspend fun handleDownloadedFile(
        form: OnlineDownloadUiState,
        result: DownloadResult.Success,
    ) {
        val file = File(result.localPath)
        try {
            when (form.mode) {
                OnlineDownloadMode.InstallApk -> {
                    state.value = state.value.copy(
                        task = state.value.task?.copy(
                            fileName = result.fileName,
                            localPath = result.localPath,
                            state = DownloadState.Installing,
                            progress = 1f,
                            message = appString(R.string.download_complete_installing_apk),
                        ),
                        operationStatus = OperationStatus.Running(appString(R.string.download_complete_installing_apk)),
                    )
                    when (
                        val installResult = adbRepository.install(file) { transferred, total ->
                            val status = adbTransferRunning(
                                transferringLabel = appString(R.string.common_transferring_arg, "APK"),
                                finishingLabel = appString(R.string.common_installing_arg, "APK"),
                                transferred = transferred,
                                total = total,
                            )
                            state.value = state.value.copy(
                                task = state.value.task?.copy(
                                    state = DownloadState.Installing,
                                    progress = status.progress ?: 1f,
                                    message = status.text,
                                ),
                                operationStatus = status,
                            )
                        }
                    ) {
                        is AdbOperationResult.Success -> {
                            state.value = state.value.copy(
                                actionEnabled = true,
                                task = state.value.task?.copy(
                                    state = DownloadState.Success,
                                    message = appString(R.string.common_apk_install_success),
                                ),
                                operationStatus = OperationStatus.Success(appString(R.string.common_apk_install_success)),
                            )
                        }
                        is AdbOperationResult.Failure -> {
                            state.value = state.value.copy(
                                actionEnabled = true,
                                task = state.value.task?.copy(state = DownloadState.Failed, message = installResult.message),
                                operationStatus = OperationStatus.Failed(installResult.message, installResult.suggestion),
                            )
                        }
                    }
                }
                OnlineDownloadMode.PushFile -> {
                    val remotePath = form.targetPath.trimEnd('/') + "/" + result.fileName
                    state.value = state.value.copy(
                        task = state.value.task?.copy(
                            fileName = result.fileName,
                            localPath = result.localPath,
                            targetPath = remotePath,
                            state = DownloadState.Pushing,
                            progress = 1f,
                            message = appString(R.string.download_complete_pushing),
                        ),
                        operationStatus = OperationStatus.Running(appString(R.string.download_complete_pushing)),
                    )
                    when (
                        val pushResult = adbRepository.push(file, remotePath) { transferred, total ->
                            val status = adbTransferRunning(
                                transferringLabel = appString(R.string.download_pushing_file),
                                finishingLabel = appString(R.string.download_finishing_push),
                                transferred = transferred,
                                total = total,
                            )
                            state.value = state.value.copy(
                                task = state.value.task?.copy(
                                    state = DownloadState.Pushing,
                                    progress = status.progress ?: 1f,
                                    message = status.text,
                                ),
                                operationStatus = status,
                            )
                        }
                    ) {
                        is AdbOperationResult.Success -> {
                            state.value = state.value.copy(
                                actionEnabled = true,
                                task = state.value.task?.copy(
                                    state = DownloadState.Success,
                                    message = appString(R.string.download_push_complete),
                                ),
                                operationStatus = OperationStatus.Success(
                                    appString(R.string.download_pushed_to, remotePath),
                                ),
                            )
                        }
                        is AdbOperationResult.Failure -> {
                            state.value = state.value.copy(
                                actionEnabled = true,
                                task = state.value.task?.copy(state = DownloadState.Failed, message = pushResult.message),
                                operationStatus = OperationStatus.Failed(pushResult.message, pushResult.suggestion),
                            )
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun updateForm(mode: OnlineDownloadMode, url: String, targetPath: String) {
        val validation = validate(mode, url, targetPath)
        state.value = state.value.copy(
            mode = mode,
            url = url,
            targetPath = targetPath,
            urlError = validation.urlError,
            targetPathError = validation.targetPathError,
            actionEnabled = validation.valid,
            operationStatus = OperationStatus.Idle,
        )
    }

    private fun validate(mode: OnlineDownloadMode, url: String, targetPath: String): DownloadValidation {
        val urlError = DownloadInputValidator.urlError(
            value = url,
            requireApk = mode == OnlineDownloadMode.InstallApk,
        )?.let(::appString)

        val targetPathError = when {
            mode == OnlineDownloadMode.InstallApk -> null
            else -> DevicePathValidator.pathError(targetPath, labelRes = R.string.download_target_path_label)
                ?.resolve(AppServices.context)
        }

        return DownloadValidation(
            urlError = urlError,
            targetPathError = targetPathError,
            valid = url.isNotBlank() &&
                urlError == null &&
                (mode == OnlineDownloadMode.InstallApk || (targetPath.isNotBlank() && targetPathError == null)),
        )
    }
}

private data class DownloadValidation(
    val urlError: String?,
    val targetPathError: String?,
    val valid: Boolean,
)
