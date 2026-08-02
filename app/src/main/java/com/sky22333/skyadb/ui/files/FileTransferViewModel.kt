package com.sky22333.skyadb.ui.files

import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.adbTransferRunning
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.validation.DevicePathValidator
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FilePaneId { Local, Remote }

data class FilePaneState(
    val path: String,
    val entries: List<RemoteFileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canGoUp: Boolean
        get() {
            val normalized = path.trimEnd('\\', '/')
            return normalized.isNotEmpty() && normalized != "/" && File(normalized).parent != null
        }
}

data class FileTransferUiState(
    val local: FilePaneState = FilePaneState(path = ""),
    val remote: FilePaneState = FilePaneState(path = DefaultRemotePath),
    val activePane: FilePaneId = FilePaneId.Local,
    val selectedPaths: Set<String> = emptySet(),
    val jumpDialogVisible: Boolean = false,
    val jumpInput: String = "",
    val jumpError: String? = null,
    val pendingDeletePaths: Set<String> = emptySet(),
    val newFolderDialogVisible: Boolean = false,
    val renameDialogVisible: Boolean = false,
    val renameInput: String = "",
    val renameError: String? = null,
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val needsStoragePermission: Boolean = false,
) {
    fun pane(id: FilePaneId): FilePaneState = if (id == FilePaneId.Local) local else remote

    val active: FilePaneState get() = pane(activePane)

    val selectedEntries: List<RemoteFileEntry>
        get() = active.entries.filter { it.path in selectedPaths }

    val selectedFiles: List<RemoteFileEntry>
        get() = selectedEntries.filter { !it.isDirectory }

    val pendingDeleteLabel: String?
        get() {
            if (pendingDeletePaths.isEmpty()) return null
            if (pendingDeletePaths.size == 1) {
                return active.entries.firstOrNull { it.path in pendingDeletePaths }?.name
                    ?: pendingDeletePaths.first().substringAfterLast('/').substringAfterLast('\\')
            }
            return appString(R.string.files_items_count, pendingDeletePaths.size)
        }
}

private const val DefaultRemotePath = "/sdcard/Download"

class FileTransferViewModel(
    private val fileManager: LocalFileManager = AppServices.localFileManager,
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(
        FileTransferUiState(
            local = FilePaneState(path = fileManager.defaultBrowsePath()),
            needsStoragePermission = !hasAllFilesAccess(),
        ),
    )
    val uiState: StateFlow<FileTransferUiState> = state.asStateFlow()

    private var localLoadJob: Job? = null
    private var remoteLoadJob: Job? = null
    private var transferJob: Job? = null
    private var statusClearJob: Job? = null
    private var lastProgressEmitMs: Long = 0L

    fun refreshAll() {
        loadPane(FilePaneId.Local, state.value.local.path)
        loadPane(FilePaneId.Remote, state.value.remote.path)
    }

    fun onStoragePermissionResult() {
        val granted = hasAllFilesAccess()
        state.value = state.value.copy(needsStoragePermission = !granted)
        if (granted) {
            loadPane(FilePaneId.Local, state.value.local.path)
        }
    }

    fun setActivePane(pane: FilePaneId) {
        if (state.value.activePane == pane) return
        state.value = state.value.copy(
            activePane = pane,
            selectedPaths = emptySet(),
            operationStatus = OperationStatus.Idle,
        )
    }

    fun selectEntry(pane: FilePaneId, entry: RemoteFileEntry) {
        setActivePane(pane)
        if (entry.isDirectory) return
        val current = state.value.selectedPaths
        state.value = state.value.copy(
            selectedPaths = if (entry.path in current) current - entry.path else current + entry.path,
            operationStatus = OperationStatus.Idle,
        )
    }

    fun clearSelection() {
        state.value = state.value.copy(selectedPaths = emptySet())
    }

    fun openEntry(pane: FilePaneId, entry: RemoteFileEntry) {
        setActivePane(pane)
        if (entry.isDirectory) {
            state.value = state.value.copy(selectedPaths = emptySet())
            loadPane(pane, entry.path)
        } else {
            selectEntry(pane, entry)
        }
    }

    fun goUp(pane: FilePaneId = state.value.activePane) {
        if (state.value.activePane != pane) {
            state.value = state.value.copy(activePane = pane, selectedPaths = emptySet())
        }
        val current = state.value.pane(pane).path.trimEnd('/', '\\')
        val parent = File(current).parent ?: return
        if (pane == FilePaneId.Remote && (current == "/" || current.isBlank())) return
        state.value = state.value.copy(selectedPaths = emptySet())
        loadPane(pane, if (pane == FilePaneId.Remote) normalizeRemotePath(parent) else parent)
    }

    fun syncPathFromOther() {
        val current = state.value
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/', '\\')
        when (current.activePane) {
            FilePaneId.Local -> {
                val remote = current.remote.path.trimEnd('/')
                val mapped = when {
                    remote == "/sdcard" || remote.startsWith("/sdcard/") ->
                        storageRoot + remote.removePrefix("/sdcard")
                    remote.startsWith(storageRoot) -> remote
                    else -> remote
                }
                state.value = current.copy(selectedPaths = emptySet())
                loadPane(FilePaneId.Local, mapped.ifBlank { storageRoot })
            }
            FilePaneId.Remote -> {
                val local = current.local.path.trimEnd('/', '\\')
                val mapped = when {
                    local == storageRoot -> "/sdcard"
                    local.startsWith(storageRoot) ->
                        "/sdcard" + local.removePrefix(storageRoot).replace('\\', '/')
                    else -> local.replace('\\', '/')
                }
                state.value = current.copy(selectedPaths = emptySet())
                loadPane(FilePaneId.Remote, normalizeRemotePath(mapped))
            }
        }
    }

    fun showJumpDialog() {
        state.value = state.value.copy(
            jumpDialogVisible = true,
            jumpInput = state.value.active.path,
            jumpError = null,
            operationStatus = OperationStatus.Idle,
        )
    }

    fun dismissJumpDialog() {
        state.value = state.value.copy(jumpDialogVisible = false, jumpError = null)
    }

    fun onJumpInputChanged(value: String) {
        state.value = state.value.copy(jumpInput = value, jumpError = null)
    }

    fun confirmJump() {
        val pane = state.value.activePane
        val raw = state.value.jumpInput.trim()
        if (raw.isBlank()) {
            state.value = state.value.copy(jumpError = appString(R.string.files_path_empty))
            return
        }
        if (pane == FilePaneId.Remote) {
            val error = DevicePathValidator.pathError(raw)?.resolve(AppServices.context)
            if (error != null) {
                state.value = state.value.copy(jumpError = error)
                return
            }
        }
        state.value = state.value.copy(jumpDialogVisible = false, selectedPaths = emptySet())
        loadPane(pane, raw)
    }

    fun showNewFolderDialog() {
        state.value = state.value.copy(newFolderDialogVisible = true, operationStatus = OperationStatus.Idle)
    }

    fun dismissNewFolderDialog() {
        state.value = state.value.copy(newFolderDialogVisible = false)
    }

    fun createFolder(name: String) {
        val safeName = name.trim()
        if (safeName.isBlank() || safeName.contains('/') || safeName.contains('\\')) {
            publishStatus(
                OperationStatus.Failed(
                    appString(R.string.files_cannot_create_folder),
                    appString(R.string.files_folder_name_invalid),
                ),
            )
            return
        }
        val pane = state.value.activePane
        val parent = state.value.active.path
        state.value = state.value.copy(
            newFolderDialogVisible = false,
            operationStatus = OperationStatus.Running(appString(R.string.files_creating_folder, safeName)),
        )
        viewModelScope.launch {
            when (pane) {
                FilePaneId.Local -> {
                    val result = withContext(Dispatchers.IO) {
                        fileManager.createDirectory(parent, safeName)
                    }
                    result.fold(
                        onSuccess = {
                            publishStatus(OperationStatus.Success(appString(R.string.files_folder_created)))
                            loadPane(FilePaneId.Local, parent)
                        },
                        onFailure = { error ->
                            publishStatus(
                                OperationStatus.Failed(
                                    appString(R.string.files_create_failed),
                                    error.message ?: appString(R.string.files_confirm_dir_writable),
                                ),
                            )
                        },
                    )
                }
                FilePaneId.Remote -> {
                    when (val result = adbRepository.makeDirectory(buildRemotePath(parent, safeName))) {
                        is AdbOperationResult.Success -> {
                            publishStatus(OperationStatus.Success(appString(R.string.files_folder_created)))
                            loadPane(FilePaneId.Remote, parent)
                        }
                        is AdbOperationResult.Failure -> {
                            publishStatus(OperationStatus.Failed(result.message, result.suggestion))
                        }
                    }
                }
            }
        }
    }

    fun showRenameDialog() {
        val entry = state.value.selectedEntries.singleOrNull() ?: run {
            publishStatus(
                OperationStatus.Failed(
                    appString(R.string.files_cannot_rename),
                    appString(R.string.files_select_exactly_one),
                ),
            )
            return
        }
        state.value = state.value.copy(
            renameDialogVisible = true,
            renameInput = entry.name,
            renameError = null,
            operationStatus = OperationStatus.Idle,
        )
    }

    fun dismissRenameDialog() {
        state.value = state.value.copy(renameDialogVisible = false, renameError = null)
    }

    fun onRenameInputChanged(value: String) {
        state.value = state.value.copy(renameInput = value, renameError = null)
    }

    fun confirmRename() {
        val entry = state.value.selectedEntries.singleOrNull() ?: return
        val newName = state.value.renameInput.trim()
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) {
            state.value = state.value.copy(renameError = appString(R.string.files_rename_name_invalid))
            return
        }
        if (newName == entry.name) {
            state.value = state.value.copy(renameDialogVisible = false)
            return
        }
        val pane = state.value.activePane
        val parent = state.value.active.path
        state.value = state.value.copy(
            renameDialogVisible = false,
            operationStatus = OperationStatus.Running(appString(R.string.files_renaming)),
        )
        viewModelScope.launch {
            when (pane) {
                FilePaneId.Local -> {
                    val result = withContext(Dispatchers.IO) {
                        fileManager.rename(entry.path, newName)
                    }
                    result.fold(
                        onSuccess = {
                            publishStatus(OperationStatus.Success(appString(R.string.files_renamed_to, newName)))
                            state.value = state.value.copy(selectedPaths = emptySet())
                            loadPane(FilePaneId.Local, parent)
                        },
                        onFailure = { error ->
                            publishStatus(
                                OperationStatus.Failed(
                                    appString(R.string.error_rename_failed),
                                    error.message ?: appString(R.string.files_confirm_name_available),
                                ),
                            )
                        },
                    )
                }
                FilePaneId.Remote -> {
                    when (val result = adbRepository.renameFile(entry.path, newName)) {
                        is AdbOperationResult.Success -> {
                            publishStatus(OperationStatus.Success(appString(R.string.files_renamed_to, newName)))
                            state.value = state.value.copy(selectedPaths = emptySet())
                            loadPane(FilePaneId.Remote, parent)
                        }
                        is AdbOperationResult.Failure -> {
                            publishStatus(OperationStatus.Failed(result.message, result.suggestion))
                        }
                    }
                }
            }
        }
    }

    fun requestDelete(pane: FilePaneId, entry: RemoteFileEntry) {
        setActivePane(pane)
        val selected = state.value.selectedPaths
        val targets = if (selected.isNotEmpty() && entry.path in selected) selected else setOf(entry.path)
        state.value = state.value.copy(
            selectedPaths = targets,
            pendingDeletePaths = targets,
            operationStatus = OperationStatus.Idle,
        )
    }

    fun cancelDelete() {
        state.value = state.value.copy(pendingDeletePaths = emptySet())
    }

    fun confirmDelete() {
        val paths = state.value.pendingDeletePaths
        if (paths.isEmpty()) return
        val pane = state.value.activePane
        val parentPath = state.value.active.path
        val entries = state.value.active.entries.filter { it.path in paths }
        state.value = state.value.copy(
            pendingDeletePaths = emptySet(),
            selectedPaths = emptySet(),
            operationStatus = OperationStatus.Running(appString(R.string.files_deleting_count, paths.size)),
        )
        viewModelScope.launch {
            var failed: String? = null
            for (entry in entries) {
                ensureActive()
                when (pane) {
                    FilePaneId.Local -> {
                        val result = withContext(Dispatchers.IO) {
                            fileManager.delete(entry.path, entry.isDirectory)
                        }
                        if (result.isFailure) {
                            failed = result.exceptionOrNull()?.message
                            break
                        }
                    }
                    FilePaneId.Remote -> {
                        when (val result = adbRepository.deleteFile(entry.path, entry.isDirectory)) {
                            is AdbOperationResult.Failure -> {
                                failed = result.message
                                break
                            }
                            is AdbOperationResult.Success -> Unit
                        }
                    }
                }
            }
            if (failed == null) {
                publishStatus(OperationStatus.Success(appString(R.string.files_deleted_count, entries.size)))
            } else {
                publishStatus(OperationStatus.Failed(appString(R.string.files_delete_incomplete), failed))
            }
            loadPane(pane, parentPath)
        }
    }

    fun transferSelected() {
        val current = state.value
        val files = current.selectedFiles
        if (files.isEmpty()) {
            publishStatus(
                OperationStatus.Failed(
                    appString(R.string.files_no_file_selected),
                    appString(R.string.files_select_files_hint),
                ),
            )
            return
        }
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            try {
                when (current.activePane) {
                    FilePaneId.Local -> {
                        files.forEachIndexed { index, entry ->
                            ensureActive()
                            pushLocalFile(entry, current.remote.path, index + 1, files.size)
                        }
                        publishStatus(OperationStatus.Success(appString(R.string.files_uploaded_count, files.size)))
                        state.value = state.value.copy(selectedPaths = emptySet())
                        loadPane(FilePaneId.Remote, current.remote.path)
                    }
                    FilePaneId.Remote -> {
                        files.forEachIndexed { index, entry ->
                            ensureActive()
                            pullRemoteFile(entry, current.local.path, index + 1, files.size)
                        }
                        publishStatus(OperationStatus.Success(appString(R.string.files_downloaded_count, files.size)))
                        state.value = state.value.copy(selectedPaths = emptySet())
                        loadPane(FilePaneId.Local, current.local.path)
                    }
                }
            } catch (_: CancellationException) {
                publishStatus(OperationStatus.Success(appString(R.string.files_transfer_canceled)))
            } catch (error: Throwable) {
                publishStatus(
                    OperationStatus.Failed(
                        appString(R.string.files_transfer_interrupted),
                        error.message ?: appString(R.string.files_check_connection_hint),
                    ),
                )
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
    }

    private suspend fun pushLocalFile(
        entry: RemoteFileEntry,
        remoteDir: String,
        index: Int,
        totalCount: Int,
    ) {
        val localFile = File(entry.path)
        if (!localFile.isFile) error(appString(R.string.files_local_file_missing, entry.name))
        val remotePath = buildRemotePath(remoteDir, entry.name)
        state.value = state.value.copy(
            operationStatus = OperationStatus.Running(
                appString(R.string.files_uploading_progress, index, totalCount, entry.name),
            ),
        )
        when (
            val result = adbRepository.push(localFile, remotePath) { transferred, total ->
                emitTransferProgress(
                    appString(R.string.files_uploading_short, index, totalCount),
                    appString(R.string.files_upload_finishing, index, totalCount),
                    transferred,
                    total,
                )
            }
        ) {
            is AdbOperationResult.Success -> Unit
            is AdbOperationResult.Failure -> error(result.message)
        }
    }

    private suspend fun pullRemoteFile(
        entry: RemoteFileEntry,
        localDir: String,
        index: Int,
        totalCount: Int,
    ) {
        val dest = File(localDir, entry.name)
        state.value = state.value.copy(
            operationStatus = OperationStatus.Running(
                appString(R.string.files_downloading_progress, index, totalCount, entry.name),
            ),
        )
        withContext(Dispatchers.IO) { dest.parentFile?.mkdirs() }
        when (val result = adbRepository.pull(entry.path, dest)) {
            is AdbOperationResult.Success -> Unit
            is AdbOperationResult.Failure -> {
                withContext(Dispatchers.IO) { dest.delete() }
                error(result.message)
            }
        }
    }

    private fun emitTransferProgress(
        transferringLabel: String,
        finishingLabel: String,
        transferred: Long,
        total: Long,
    ) {
        val now = SystemClock.elapsedRealtime()
        val done = total > 0L && transferred >= total
        if (!done && now - lastProgressEmitMs < 50L) return
        lastProgressEmitMs = now
        state.value = state.value.copy(
            operationStatus = adbTransferRunning(
                transferringLabel = transferringLabel,
                finishingLabel = finishingLabel,
                transferred = transferred,
                total = total,
            ),
        )
    }

    private fun publishStatus(status: OperationStatus) {
        statusClearJob?.cancel()
        state.value = state.value.copy(operationStatus = status)
        if (status is OperationStatus.Success) {
            statusClearJob = viewModelScope.launch {
                delay(1_400)
                if (state.value.operationStatus is OperationStatus.Success) {
                    state.value = state.value.copy(operationStatus = OperationStatus.Idle)
                }
            }
        }
    }

    private fun loadPane(pane: FilePaneId, path: String) {
        when (pane) {
            FilePaneId.Local -> {
                val normalized = path.trimEnd('/', '\\').ifBlank { fileManager.defaultBrowsePath() }
                updatePane(FilePaneId.Local) {
                    it.copy(path = normalized, loading = true, error = null)
                }
                localLoadJob?.cancel()
                localLoadJob = viewModelScope.launch {
                    val result = withContext(Dispatchers.IO) { fileManager.listDirectory(normalized) }
                    ensureActive()
                    result.fold(
                        onSuccess = { entries ->
                            updatePane(FilePaneId.Local) {
                                it.copy(entries = entries, loading = false, error = null)
                            }
                            state.value = state.value.copy(needsStoragePermission = false)
                            if (state.value.activePane == FilePaneId.Local &&
                                state.value.operationStatus !is OperationStatus.Running
                            ) {
                                state.value = state.value.copy(operationStatus = OperationStatus.Idle)
                            }
                        },
                        onFailure = { error ->
                            val needsPermission = !hasAllFilesAccess()
                            updatePane(FilePaneId.Local) {
                                it.copy(entries = emptyList(), loading = false, error = error.message)
                            }
                            state.value = state.value.copy(
                                needsStoragePermission = needsPermission,
                                operationStatus = if (state.value.activePane == FilePaneId.Local) {
                                    OperationStatus.Failed(
                                        appString(R.string.files_cannot_read_local_dir),
                                        error.message ?: appString(R.string.files_grant_storage_permission),
                                    )
                                } else {
                                    state.value.operationStatus
                                },
                            )
                        },
                    )
                }
            }
            FilePaneId.Remote -> {
                val normalized = normalizeRemotePath(path)
                updatePane(FilePaneId.Remote) {
                    it.copy(path = normalized, loading = true, error = null)
                }
                remoteLoadJob?.cancel()
                remoteLoadJob = viewModelScope.launch {
                    when (val result = adbRepository.listFiles(normalized)) {
                        is AdbOperationResult.Success -> {
                            ensureActive()
                            updatePane(FilePaneId.Remote) {
                                it.copy(entries = result.data, loading = false, error = null)
                            }
                            if (state.value.activePane == FilePaneId.Remote &&
                                state.value.operationStatus !is OperationStatus.Running
                            ) {
                                state.value = state.value.copy(operationStatus = OperationStatus.Idle)
                            }
                        }
                        is AdbOperationResult.Failure -> {
                            ensureActive()
                            updatePane(FilePaneId.Remote) {
                                it.copy(entries = emptyList(), loading = false, error = result.message)
                            }
                            if (state.value.activePane == FilePaneId.Remote) {
                                publishStatus(OperationStatus.Failed(result.message, result.suggestion))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updatePane(pane: FilePaneId, transform: (FilePaneState) -> FilePaneState) {
        val current = state.value
        state.value = when (pane) {
            FilePaneId.Local -> current.copy(local = transform(current.local))
            FilePaneId.Remote -> current.copy(remote = transform(current.remote))
        }
    }

    private fun normalizeRemotePath(path: String): String {
        val trimmed = path.trim().ifBlank { "/" }
        return if (trimmed == "/") "/" else trimmed.trimEnd('/')
    }

    private fun buildRemotePath(parentPath: String, fileName: String): String {
        val parent = normalizeRemotePath(parentPath)
        return if (parent == "/") "/$fileName" else "$parent/$fileName"
    }

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}
