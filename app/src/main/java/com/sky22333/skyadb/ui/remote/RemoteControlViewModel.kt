package com.sky22333.skyadb.ui.remote

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteControlUiState(
    val status: OperationStatus = OperationStatus.Idle,
)

enum class RemoteKey(@param:StringRes val labelRes: Int, val keyCode: String) {
    Power(R.string.remote_key_power, "KEYCODE_POWER"),
    Wakeup(R.string.remote_key_wakeup, "KEYCODE_WAKEUP"),
    Sleep(R.string.remote_key_sleep, "KEYCODE_SLEEP"),
    Home(R.string.remote_key_home, "KEYCODE_HOME"),
    Back(R.string.remote_key_back, "KEYCODE_BACK"),
    Menu(R.string.remote_key_menu, "KEYCODE_MENU"),
    Up(R.string.remote_key_up, "KEYCODE_DPAD_UP"),
    Down(R.string.remote_key_down, "KEYCODE_DPAD_DOWN"),
    Left(R.string.remote_key_left, "KEYCODE_DPAD_LEFT"),
    Right(R.string.remote_key_right, "KEYCODE_DPAD_RIGHT"),
    Center(R.string.remote_key_center, "KEYCODE_DPAD_CENTER"),
    VolumeUp(R.string.remote_key_volume_up, "KEYCODE_VOLUME_UP"),
    VolumeDown(R.string.remote_key_volume_down, "KEYCODE_VOLUME_DOWN"),
    Mute(R.string.remote_key_mute, "KEYCODE_VOLUME_MUTE"),
    PlayPause(R.string.remote_key_play_pause, "KEYCODE_MEDIA_PLAY_PAUSE"),
    Previous(R.string.remote_key_previous, "KEYCODE_MEDIA_PREVIOUS"),
    Next(R.string.remote_key_next, "KEYCODE_MEDIA_NEXT"),
}

class RemoteControlViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = state.asStateFlow()

    fun sendKey(key: RemoteKey) {
        val label = appString(key.labelRes)
        state.value = state.value.copy(status = OperationStatus.Running(appString(R.string.remote_sending, label)))
        viewModelScope.launch {
            when (val result = adbRepository.runShell("input keyevent ${key.keyCode}")) {
                is AdbOperationResult.Success -> {
                    state.value = if (result.data.exitCode == 0) {
                        state.value.copy(status = OperationStatus.Success(appString(R.string.remote_sent, label)))
                    } else {
                        state.value.copy(
                            status = OperationStatus.Failed(
                                text = appString(R.string.remote_send_failed),
                                suggestion = result.data.errorOutput
                                    .toRemoteInputSuggestion()
                                    .resolve(AppServices.context),
                            ),
                        )
                    }
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        status = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }
}

internal fun String.toRemoteInputSuggestion(): AppText {
    return when {
        contains("INJECT_EVENTS", ignoreCase = true) ||
            contains("Injecting input events", ignoreCase = true) ->
            AppText.Res(R.string.remote_key_control_blocked)
        else -> {
            val firstLine = lineSequence().firstOrNull { it.isNotBlank() }
            if (firstLine != null) {
                AppText.Plain(firstLine)
            } else {
                AppText.Res(R.string.remote_confirm_connected)
            }
        }
    }
}
