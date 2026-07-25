package com.sky22333.skyadb.ui.screenshot

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreenshotUiState(
    val latestFileName: String? = null,
    val latestLocalPath: String? = null,
    val saveEnabled: Boolean = false,
    val operationStatus: OperationStatus = OperationStatus.Idle,
)

class ScreenshotViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ScreenshotUiState())
    val uiState: StateFlow<ScreenshotUiState> = state.asStateFlow()

    private var latestFile: File? = null

    fun capture(context: Context) {
        val fileName = "screenshot-${System.currentTimeMillis()}.png"
        val localFile = File(context.cacheDir, "screenshots/$fileName")
        state.value = state.value.copy(
            saveEnabled = false,
            operationStatus = OperationStatus.Running(appString(R.string.screenshot_capturing)),
        )

        viewModelScope.launch {
            when (val result = adbRepository.captureScreenshot(localFile)) {
                is AdbOperationResult.Success -> {
                    latestFile?.takeIf { it.absolutePath != result.data.absolutePath }?.delete()
                    latestFile = result.data
                    state.value = state.value.copy(
                        latestFileName = result.data.name,
                        latestLocalPath = result.data.absolutePath,
                        saveEnabled = true,
                        operationStatus = OperationStatus.Success(appString(R.string.screenshot_captured_choose_location)),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        saveEnabled = false,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun saveToUri(context: Context, uri: Uri?) {
        val file = latestFile
        if (file == null || uri == null) {
            state.value = state.value.copy(
                operationStatus = OperationStatus.Failed(
                    appString(R.string.screenshot_cannot_save),
                    appString(R.string.screenshot_finish_capture_hint),
                ),
            )
            return
        }

        state.value = state.value.copy(operationStatus = OperationStatus.Running(appString(R.string.screenshot_saving)))
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri).use { output ->
                        requireNotNull(output) { appString(R.string.screenshot_cannot_open_location) }
                        file.inputStream().use { input -> input.copyTo(output) }
                    }
                }
            }.fold(
                onSuccess = {
                    state.value = state.value.copy(operationStatus = OperationStatus.Success(appString(R.string.screenshot_saved)))
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(
                            text = appString(R.string.screenshot_save_failed),
                            suggestion = error.message ?: appString(R.string.screenshot_confirm_writable),
                        ),
                    )
                },
            )
        }
    }

    fun clearPreview() {
        latestFile?.delete()
        latestFile = null
        state.value = ScreenshotUiState()
    }

    override fun onCleared() {
        latestFile?.delete()
        latestFile = null
        super.onCleared()
    }
}
