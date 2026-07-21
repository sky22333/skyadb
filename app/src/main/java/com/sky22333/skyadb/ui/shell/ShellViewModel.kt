package com.sky22333.skyadb.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShellUiState(
    val command: String = "",
    val output: String = "",
    val history: List<String> = emptyList(),
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val executeEnabled: Boolean = false,
)

class ShellViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = state.asStateFlow()

    fun onCommandChanged(value: String) {
        state.value = state.value.copy(
            command = value,
            executeEnabled = value.isNotBlank(),
            operationStatus = OperationStatus.Idle,
        )
    }

    fun onHistoryCommandClick(command: String) {
        onCommandChanged(command)
    }

    fun onExecuteClick() {
        val command = state.value.command.trim()
        if (command.isBlank()) {
            state.value = state.value.copy(
                executeEnabled = false,
                operationStatus = OperationStatus.Failed(
                    text = "无法执行命令",
                    suggestion = "请先输入 Shell 命令。",
                ),
            )
            return
        }

        state.value = state.value.copy(
            executeEnabled = false,
            operationStatus = OperationStatus.Running("正在执行：$command"),
        )

        viewModelScope.launch {
            if (adbRepository.sessionKind() == AdbSessionKind.UsbFastboot) {
                when (val result = adbRepository.runFastbootCommand(command)) {
                    is AdbOperationResult.Success -> applySuccess(command, result.data, exitCode = 0)
                    is AdbOperationResult.Failure -> applyFailure(result)
                }
                return@launch
            }

            when (val result = adbRepository.runShell(command)) {
                is AdbOperationResult.Success -> {
                    val commandResult = result.data
                    val combinedOutput = buildString {
                        if (commandResult.output.isNotBlank()) append(commandResult.output.trim())
                        if (commandResult.errorOutput.isNotBlank()) {
                            if (isNotEmpty()) appendLine()
                            append(commandResult.errorOutput.trim())
                        }
                        if (isEmpty()) append("命令执行完成，无输出。")
                    }
                    applySuccess(command, combinedOutput, commandResult.exitCode)
                }
                is AdbOperationResult.Failure -> applyFailure(result)
            }
        }
    }

    private fun applySuccess(command: String, output: String, exitCode: Int) {
        state.value = state.value.copy(
            output = output,
            history = (listOf(command) + state.value.history).distinct().take(20),
            executeEnabled = true,
            operationStatus = OperationStatus.Success("命令执行完成，退出码 $exitCode"),
        )
    }

    private fun applyFailure(result: AdbOperationResult.Failure) {
        state.value = state.value.copy(
            executeEnabled = true,
            operationStatus = OperationStatus.Failed(result.message, result.suggestion),
        )
    }
}
