package com.sky22333.skyadb.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import com.sky22333.skyadb.validation.CommandInputParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShellUiState(
    val command: String = "",
    val output: String = "",
    val history: List<String> = emptyList(),
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val executeEnabled: Boolean = false,
    val fastboot: Boolean = false,
    val connected: Boolean = false,
    val summary: FastbootDeviceSummary? = null,
    val summaryLoading: Boolean = false,
    val summaryError: String? = null,
)

class ShellViewModel(private val adbRepository: AdbRepository = AppServices.adbRepository) :
    ViewModel() {
    private val state =
        MutableStateFlow(
            ShellUiState(
                fastboot = adbRepository.sessionKind() == AdbSessionKind.UsbFastboot,
                connected = adbRepository.sessionKind() != AdbSessionKind.None,
            )
        )
    val uiState: StateFlow<ShellUiState> = state.asStateFlow()
    private var deviceId: String? = null
    private var commandOwnership = 0L

    init {
        viewModelScope.launch {
            var previousId: String? = null
            adbRepository.activeDeviceId.collect { connectedId ->
                val kind = adbRepository.sessionKind()
                val deviceChanged = previousId != null && connectedId != previousId
                if (connectedId != previousId) commandOwnership++
                deviceId = connectedId
                state.update { snapshot ->
                    val nextStatus = if (deviceChanged) OperationStatus.Idle else snapshot.operationStatus
                    snapshot.copy(
                        fastboot =
                            if (kind == AdbSessionKind.None) snapshot.fastboot
                            else kind == AdbSessionKind.UsbFastboot,
                        connected = kind != AdbSessionKind.None,
                        output = if (deviceChanged) "" else snapshot.output,
                        operationStatus = nextStatus,
                        executeEnabled =
                            snapshot.command.isNotBlank() &&
                                kind != AdbSessionKind.None &&
                                nextStatus !is OperationStatus.Running,
                        summary = if (connectedId != previousId) null else snapshot.summary,
                    )
                }
                if (kind == AdbSessionKind.UsbFastboot && connectedId != previousId)
                    refreshSummary()
                previousId = connectedId
            }
        }
    }

    fun refreshSummary() {
        if (
            !state.value.fastboot ||
                !state.value.connected ||
                state.value.summaryLoading ||
                state.value.operationStatus is OperationStatus.Running
        )
            return
        state.update { it.copy(summaryLoading = true, summaryError = null) }
        val requestedId = deviceId
        viewModelScope.launch {
            try {
                val result = adbRepository.fastbootSummary()
                if (deviceId != requestedId) return@launch
                when (result) {
                    is AdbOperationResult.Success -> state.update { it.copy(summary = result.data) }
                    is AdbOperationResult.Failure ->
                        state.update {
                            it.copy(summaryError = "${result.message}：${result.suggestion}")
                        }
                }
            } finally {
                state.update { it.copy(summaryLoading = false) }
                if (deviceId != requestedId && state.value.connected) refreshSummary()
            }
        }
    }

    fun onQuickCommand(command: String) {
        if (
            !state.value.fastboot ||
                !state.value.connected ||
                state.value.summaryLoading ||
                state.value.operationStatus is OperationStatus.Running
        )
            return
        onCommandChanged(command)
        onExecuteClick()
    }

    fun onCommandChanged(value: String) {
        if (state.value.operationStatus is OperationStatus.Running) return
        state.value =
            state.value.copy(
                command = value,
                executeEnabled = value.isNotBlank() && state.value.connected,
                operationStatus = OperationStatus.Idle,
            )
    }

    fun onHistoryCommandClick(command: String) {
        onCommandChanged(command)
    }

    fun onExecuteClick() {
        if (state.value.operationStatus is OperationStatus.Running || state.value.summaryLoading)
            return
        val fastboot = adbRepository.sessionKind() == AdbSessionKind.UsbFastboot
        val parsed = CommandInputParser.parse(state.value.command, fastboot)
        if (parsed is CommandInputParser.Result.Rejected) {
            state.value =
                state.value.copy(
                    fastboot = fastboot,
                    output = "",
                    operationStatus =
                        OperationStatus.Failed(
                            text = appString(R.string.shell_cannot_execute),
                            suggestion =
                                appString(
                                    when (parsed.error) {
                                        CommandInputParser.Error.Empty ->
                                            R.string.shell_enter_command_hint
                                        CommandInputParser.Error.WrongMode ->
                                            R.string.command_wrong_mode
                                        CommandInputParser.Error.Devices ->
                                            R.string.command_devices_hint
                                        CommandInputParser.Error.HostCommand ->
                                            R.string.command_host_only_hint
                                        CommandInputParser.Error.ClientOptions ->
                                            R.string.command_client_options_hint
                                        CommandInputParser.Error.FastbootSyntax ->
                                            R.string.command_fastboot_syntax_hint
                                        CommandInputParser.Error.FileTransfer ->
                                            R.string.command_fastboot_file_hint
                                    }
                                ),
                        ),
                )
            return
        }
        val command = (parsed as CommandInputParser.Result.Ready).command
        val targetDeviceId = deviceId
        val targetSessionToken = adbRepository.sessionToken()
        val targetCommandOwnership = ++commandOwnership

        fun isCurrentTarget() =
            deviceId == targetDeviceId && commandOwnership == targetCommandOwnership

        state.value =
            state.value.copy(
                executeEnabled = false,
                fastboot = fastboot,
                command = command,
                output = "",
                operationStatus =
                    OperationStatus.Running(appString(R.string.shell_executing, command)),
            )

        viewModelScope.launch {
            if (fastboot) {
                when (val result = adbRepository.runFastbootCommand(command)) {
                    is AdbOperationResult.Success -> if (isCurrentTarget()) {
                        applySuccess(command, result.data, exitCode = 0)
                    }
                    is AdbOperationResult.Failure -> if (isCurrentTarget()) applyFailure(result)
                }
                return@launch
            }

            when (
                val result =
                    adbRepository.runShell(
                        command,
                        timeoutMillis = null,
                        expectedSessionToken = targetSessionToken,
                    )
            ) {
                is AdbOperationResult.Success -> {
                    if (!isCurrentTarget()) return@launch
                    val commandResult = result.data
                    val combinedOutput = buildString {
                        if (commandResult.output.isNotBlank()) append(commandResult.output.trim())
                        if (commandResult.errorOutput.isNotBlank()) {
                            if (isNotEmpty()) appendLine()
                            append(commandResult.errorOutput.trim())
                        }
                        if (isEmpty()) append(appString(R.string.shell_command_no_output))
                    }
                    applySuccess(command, combinedOutput, commandResult.exitCode)
                }
                is AdbOperationResult.Failure -> if (isCurrentTarget()) applyFailure(result)
            }
        }
    }

    private fun applySuccess(command: String, output: String, exitCode: Int) {
        state.value =
            state.value.copy(
                output = output,
                history = (listOf(command) + state.value.history).distinct().take(20),
                connected = adbRepository.sessionKind() != AdbSessionKind.None,
                executeEnabled = adbRepository.sessionKind() != AdbSessionKind.None,
                operationStatus =
                    if (state.value.fastboot && exitCode == 0) {
                        OperationStatus.Success(
                            appString(
                                if (
                                    command == "reboot" ||
                                        command.startsWith("reboot-") ||
                                        command == "continue"
                                )
                                    R.string.fastboot_reboot_sent
                                else R.string.fastboot_command_done
                            )
                        )
                    } else if (exitCode == 0) {
                        OperationStatus.Success(appString(R.string.shell_execute_success, exitCode))
                    } else {
                        OperationStatus.Failed(
                            appString(R.string.kadb_command_failed),
                            "Exit $exitCode: ${output.takeLast(500)}",
                        )
                    },
            )
    }

    private fun applyFailure(result: AdbOperationResult.Failure) {
        state.value =
            state.value.copy(
                connected = adbRepository.sessionKind() != AdbSessionKind.None,
                executeEnabled = adbRepository.sessionKind() != AdbSessionKind.None,
                output = result.output ?: "",
                operationStatus = OperationStatus.Failed(result.message, result.suggestion),
            )
    }
}
