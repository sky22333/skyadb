package com.sky22333.skyadb.ui.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.validation.DevicePathValidator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DefaultFileManagerPath = "/sdcard/Download"

data class FileTransferUiState(
    val currentPath: String = DefaultFileManagerPath,
    val pathInput: String = DefaultFileManagerPath,
    val pathError: String? = null,
    val entries: List<RemoteFileEntry> = emptyList(),
    val loading: Boolean = false,
    val pendingDelete: RemoteFileEntry? = null,
    val newFolderDialogVisible: Boolean = false,
    val operationStatus: OperationStatus = OperationStatus.Idle,
) {
    val canGoUp: Boolean = currentPath.trimEnd('/') != "/"
}

class FileTransferViewModel(
    private val fileManager: LocalFileManager = AppServices.localFileManager,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(FileTransferUiState())
    val uiState: StateFlow<FileTransferUiState> = state.asStateFlow()

    fun loadCurrentPath() {
        loadPath(state.value.currentPath)
    }

    fun onPathInputChanged(value: String) {
        val trimmed = value.trim()
        state.value = state.value.copy(
            pathInput = trimmed,
            pathError = DevicePathValidator.pathError(trimmed),
            operationStatus = OperationStatus.Idle,
        )
    }

    fun jumpToPath() {
        val path = state.value.pathInput.trim()
        val error = DevicePathValidator.pathError(path)
        if (path.isBlank() || error != null) {
            state.value = state.value.copy(
                pathError = error,
                operationStatus = OperationStatus.Failed("无法跳转路径", error ?: "请填写目标设备目录路径。"),
            )
            return
        }
        loadPath(path)
    }

    fun openEntry(entry: RemoteFileEntry) {
        if (entry.isDirectory) {
            loadPath(entry.path)
        }
    }

    fun goUp() {
        val current = state.value.currentPath.trimEnd('/')
        if (current == "/" || current.isBlank()) return
        loadPath(current.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" })
    }

    fun onLocalFileSelected(uri: Uri?) {
        if (uri == null) return
        val current = state.value
        state.value = current.copy(operationStatus = OperationStatus.Running("正在准备上传文件"))
        viewModelScope.launch {
            runCatching {
                val localFile = fileManager.copyToCache(uri)
                localFile to buildRemotePath(current.currentPath, localFile.name)
            }.fold(
                onSuccess = { (localFile, remotePath) ->
                    state.value = state.value.copy(operationStatus = OperationStatus.Running("正在上传到 $remotePath"))
                    try {
                        when (val result = adbRepository.push(localFile, remotePath)) {
                            is AdbOperationResult.Success -> {
                                state.value = state.value.copy(
                                    operationStatus = OperationStatus.Success("文件已上传到 $remotePath"),
                                )
                                loadPath(current.currentPath)
                            }
                            is AdbOperationResult.Failure -> {
                                state.value = state.value.copy(
                                    operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                                )
                            }
                        }
                    } finally {
                        localFile.delete()
                    }
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(
                            text = "读取本地文件失败",
                            suggestion = error.message ?: "请确认文件存在，并允许 App 读取该文件。",
                        ),
                    )
                },
            )
        }
    }

    fun downloadToUri(context: Context, entry: RemoteFileEntry, destinationUri: Uri?) {
        if (destinationUri == null) return
        state.value = state.value.copy(operationStatus = OperationStatus.Running("正在下载 ${entry.name}"))
        viewModelScope.launch {
            val tempFile = File(context.cacheDir, "pull/${entry.name}")
            tempFile.parentFile?.mkdirs()
            when (val result = adbRepository.pull(entry.path, tempFile)) {
                is AdbOperationResult.Success -> savePulledFile(context, destinationUri, tempFile)
                is AdbOperationResult.Failure -> {
                    tempFile.delete()
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun showNewFolderDialog() {
        state.value = state.value.copy(newFolderDialogVisible = true, operationStatus = OperationStatus.Idle)
    }

    fun dismissNewFolderDialog() {
        state.value = state.value.copy(newFolderDialogVisible = false)
    }

    fun createFolder(name: String) {
        val safeName = name.trim()
        if (safeName.isBlank() || safeName.contains("/")) {
            state.value = state.value.copy(
                operationStatus = OperationStatus.Failed("无法新建文件夹", "文件夹名称不能为空，也不能包含 /。"),
            )
            return
        }
        val targetPath = buildRemotePath(state.value.currentPath, safeName)
        state.value = state.value.copy(
            newFolderDialogVisible = false,
            operationStatus = OperationStatus.Running("正在新建 $safeName"),
        )
        viewModelScope.launch {
            when (val result = adbRepository.makeDirectory(targetPath)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(operationStatus = OperationStatus.Success("文件夹已创建"))
                    loadPath(state.value.currentPath)
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun requestDelete(entry: RemoteFileEntry) {
        state.value = state.value.copy(pendingDelete = entry, operationStatus = OperationStatus.Idle)
    }

    fun cancelDelete() {
        state.value = state.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val entry = state.value.pendingDelete ?: return
        state.value = state.value.copy(
            pendingDelete = null,
            operationStatus = OperationStatus.Running("正在删除 ${entry.name}"),
        )
        viewModelScope.launch {
            when (val result = adbRepository.deleteFile(entry.path, entry.isDirectory)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(operationStatus = OperationStatus.Success("已删除 ${entry.name}"))
                    loadPath(state.value.currentPath)
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private fun loadPath(path: String) {
        val normalized = normalizePath(path)
        state.value = state.value.copy(
            currentPath = normalized,
            pathInput = normalized,
            pathError = null,
            loading = true,
            operationStatus = OperationStatus.Running("正在读取 $normalized"),
        )
        viewModelScope.launch {
            when (val result = adbRepository.listFiles(normalized)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        entries = result.data,
                        loading = false,
                        operationStatus = OperationStatus.Success("已读取 ${result.data.size} 个项目"),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        entries = emptyList(),
                        loading = false,
                        operationStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private suspend fun savePulledFile(context: Context, destinationUri: Uri, tempFile: File) {
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destinationUri).use { output ->
                    requireNotNull(output) { "无法打开保存位置" }
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                }
            }
            state.value = state.value.copy(operationStatus = OperationStatus.Success("文件已保存到选择的位置"))
        } catch (error: Throwable) {
            state.value = state.value.copy(
                operationStatus = OperationStatus.Failed(
                    text = "保存文件失败",
                    suggestion = error.message ?: "请确认保存位置可写。",
                ),
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim().ifBlank { "/" }
        return if (trimmed == "/") "/" else trimmed.trimEnd('/')
    }

    private fun buildRemotePath(parentPath: String, fileName: String): String {
        val parent = normalizePath(parentPath)
        return if (parent == "/") "/$fileName" else "$parent/$fileName"
    }

}
