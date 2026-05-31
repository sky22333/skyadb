package com.sky22333.skyadb.ui.fastboot

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.fastboot.FastbootCommandPolicy
import com.sky22333.skyadb.fastboot.FastbootOperationResult
import com.sky22333.skyadb.fastboot.FastbootRepository
import com.sky22333.skyadb.fastboot.FastbootUsbDevice
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.model.OperationStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingFastbootCommand(
    val command: String,
    val imageUri: Uri?,
    val imageName: String?,
)

data class FastbootUiState(
    val devices: List<FastbootUsbDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val connectedDevice: FastbootUsbDevice? = null,
    val command: String = "getvar:all",
    val imageUri: Uri? = null,
    val imageName: String? = null,
    val output: String = "",
    val pendingCommand: PendingFastbootCommand? = null,
    val running: Boolean = false,
    val status: OperationStatus = OperationStatus.Idle,
) {
    val selectedDevice: FastbootUsbDevice?
        get() = devices.firstOrNull { it.id == selectedDeviceId }
}

class FastbootViewModel(
    private val fastbootRepository: FastbootRepository = AppServices.fastbootRepository,
    private val fileManager: LocalFileManager = AppServices.localFileManager,
) : ViewModel() {
    private val state = MutableStateFlow(FastbootUiState())
    val uiState: StateFlow<FastbootUiState> = state.asStateFlow()

    fun refreshDevices() {
        val devices = fastbootRepository.listDevices()
        state.value = state.value.copy(
            devices = devices,
            selectedDeviceId = state.value.selectedDeviceId?.takeIf { id -> devices.any { it.id == id } }
                ?: devices.firstOrNull()?.id,
            status = if (devices.isEmpty()) {
                OperationStatus.Failed("未发现 Fastboot 设备", "请让目标设备进入 Fastboot 模式，并通过 OTG 连接。")
            } else {
                OperationStatus.Idle
            },
        )
    }

    fun selectDevice(deviceId: String) {
        state.value = state.value.copy(selectedDeviceId = deviceId, status = OperationStatus.Idle)
    }

    fun requestPermission() {
        val deviceId = state.value.selectedDeviceId ?: return
        state.value = state.value.copy(running = true, status = OperationStatus.Running("正在请求 USB 授权"))
        viewModelScope.launch {
            when (val result = fastbootRepository.requestPermission(deviceId)) {
                is FastbootOperationResult.Success -> {
                    refreshDevices()
                    state.value = state.value.copy(running = false, status = OperationStatus.Success("USB 授权完成"))
                }
                is FastbootOperationResult.Failure -> fail(result)
            }
        }
    }

    fun connect() {
        val deviceId = state.value.selectedDeviceId ?: return
        state.value = state.value.copy(running = true, status = OperationStatus.Running("正在连接 Fastboot"))
        viewModelScope.launch {
            when (val result = fastbootRepository.connect(deviceId)) {
                is FastbootOperationResult.Success -> {
                    state.value = state.value.copy(
                        connectedDevice = result.data,
                        running = false,
                        status = OperationStatus.Success("Fastboot 已连接"),
                    )
                }
                is FastbootOperationResult.Failure -> fail(result)
            }
        }
    }

    fun disconnect() {
        fastbootRepository.disconnect()
        state.value = state.value.copy(
            connectedDevice = null,
            running = false,
            status = OperationStatus.Success("Fastboot 已断开"),
        )
    }

    fun onCommandChanged(value: String) {
        state.value = state.value.copy(command = value, status = OperationStatus.Idle)
    }

    fun onImageSelected(uri: Uri?) {
        state.value = state.value.copy(
            imageUri = uri,
            imageName = uri?.let(fileManager::displayName),
            status = OperationStatus.Idle,
        )
    }

    fun clearImage() {
        state.value = state.value.copy(imageUri = null, imageName = null)
    }

    fun execute() {
        val command = state.value.command.trim()
        val prepared = FastbootCommandPolicy.prepare(command)
        if (prepared.normalizedCommand.isBlank()) {
            state.value = state.value.copy(
                status = OperationStatus.Failed("无法执行 Fastboot 命令", "请先输入命令。"),
            )
            return
        }
        if (prepared.requiresConfirmation) {
            state.value = state.value.copy(
                pendingCommand = PendingFastbootCommand(
                    command = prepared.normalizedCommand,
                    imageUri = state.value.imageUri,
                    imageName = state.value.imageName,
                ),
            )
            return
        }
        executeConfirmed(prepared.normalizedCommand, state.value.imageUri)
    }

    fun confirmPendingCommand() {
        val pending = state.value.pendingCommand ?: return
        state.value = state.value.copy(pendingCommand = null)
        executeConfirmed(pending.command, pending.imageUri)
    }

    fun cancelPendingCommand() {
        state.value = state.value.copy(pendingCommand = null)
    }

    private fun executeConfirmed(command: String, imageUri: Uri?) {
        state.value = state.value.copy(
            running = true,
            output = "",
            status = OperationStatus.Running("正在执行 Fastboot 命令"),
        )
        viewModelScope.launch(Dispatchers.IO) {
            var imageFile: File? = null
            try {
                imageFile = imageUri?.let { fileManager.copyToCache(it) }
                when (val result = fastbootRepository.execute(command, imageFile)) {
                    is FastbootOperationResult.Success -> {
                        state.value = state.value.copy(
                            output = result.data.output.ifBlank { "OKAY" },
                            running = false,
                            status = OperationStatus.Success("Fastboot 命令执行完成"),
                        )
                    }
                    is FastbootOperationResult.Failure -> fail(result)
                }
            } catch (error: Throwable) {
                state.value = state.value.copy(
                    running = false,
                    status = OperationStatus.Failed(
                        text = "Fastboot 命令执行失败",
                        suggestion = error.message ?: "请确认镜像文件可读取，并保持 USB 连接。",
                    ),
                )
            } finally {
                imageFile?.delete()
            }
        }
    }

    private fun fail(result: FastbootOperationResult.Failure) {
        state.value = state.value.copy(
            running = false,
            status = OperationStatus.Failed(result.message, result.suggestion),
        )
    }

    override fun onCleared() {
        fastbootRepository.disconnect()
        super.onCleared()
    }
}
