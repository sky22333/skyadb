package com.sky22333.skyadb.ui.mirror

import android.view.MotionEvent
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.scrcpy.MirrorTouchEvent
import com.sky22333.skyadb.scrcpy.ScrcpyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MirrorUiState(
    val status: OperationStatus = OperationStatus.Idle,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audioError: String? = null,
)

class MirrorViewModel(
    private val repository: ScrcpyRepository = AppServices.scrcpyRepository,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(MirrorUiState())
    val uiState: StateFlow<MirrorUiState> = state.asStateFlow()

    /** 控制指令串行发送；与 stop 解耦，避免 onCleared 取消正在释放的会话。 */
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controlCommands = Channel<ControlCommand>(Channel.UNLIMITED)
    private val controlLock = Any()
    private var pendingMoves: ControlCommand.Moves? = null
    @Volatile private var started = false
    private var startJob: Job? = null
    private var latestSurface: Surface? = null

    init {
        controlScope.launch { processControlCommands() }
    }

    fun onSurfaceCreated(surface: Surface) {
        latestSurface = surface
        if (repository.isRunning()) {
            repository.setSurface(surface)
            return
        }
        if (started) return
        start(surface)
    }

    fun onSurfaceDestroyed() {
        latestSurface = null
        repository.clearSurface()
    }

    private fun start(surface: Surface) {
        if (started) return
        started = true
        state.update { snapshot ->
            snapshot.copy(status = OperationStatus.Running(appString(R.string.mirror_starting)))
        }
        startJob = viewModelScope.launch {
            val qualityPreset = settingsStore.settings.first().mirrorQualityPreset
            if (!started) return@launch
            val launchSurface = latestSurface?.takeIf { it.isValid } ?: surface
            if (!launchSurface.isValid) {
                started = false
                state.update { snapshot ->
                    snapshot.copy(
                        status =
                            OperationStatus.Failed(
                                appString(R.string.scrcpy_start_failed),
                                appString(R.string.mirror_invalid_surface),
                            )
                    )
                }
                return@launch
            }
            when (
                val result =
                    repository.start(
                        surface = launchSurface,
                        qualityPreset = qualityPreset,
                        onVideoSize = { width, height ->
                            state.update { snapshot ->
                                snapshot.copy(videoWidth = width, videoHeight = height)
                            }
                        },
                        onStreamError = { error ->
                            started = false
                            state.update { snapshot ->
                                snapshot.copy(
                                    status =
                                        OperationStatus.Failed(
                                            text = appString(R.string.mirror_disconnected),
                                            suggestion =
                                                error.message
                                                    ?: appString(R.string.mirror_reenter_hint),
                                        )
                                )
                            }
                        },
                        onAudioError = { state.update { it.copy(audioError = appString(R.string.scrcpy_audio_stream_error)) } },
                    )
            ) {
                is AdbOperationResult.Success -> {
                    latestSurface?.takeIf { it.isValid }?.let { repository.setSurface(it) }
                    state.update { snapshot ->
                        snapshot.copy(
                            status = OperationStatus.Success(appString(R.string.mirror_active))
                        )
                    }
                }
                is AdbOperationResult.Failure -> {
                    started = false
                    state.update { snapshot ->
                        snapshot.copy(
                            status = OperationStatus.Failed(result.message, result.suggestion)
                        )
                    }
                }
            }
        }
    }

    fun sendTouch(event: MotionEvent, width: Int, height: Int) {
        if (
            event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            for (index in 0 until event.pointerCount) {
                MirrorTouchEvent.from(event, width, height, index)?.let { enqueueTouch(it) }
            }
        } else {
            val touch = MirrorTouchEvent.from(event, width, height) ?: return
            enqueueTouch(touch)
        }
    }

    fun sendKey(keyCode: Int) {
        enqueue(ControlCommand.Key(keyCode))
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        enqueue(ControlCommand.Text(text))
    }

    fun stop() {
        startJob?.cancel()
        started = false
        latestSurface = null
        state.value = MirrorUiState()
        repository.requestStop()
    }

    override fun onCleared() {
        startJob?.cancel()
        started = false
        latestSurface = null
        controlCommands.close()
        controlScope.cancel()
        repository.requestStop()
        super.onCleared()
    }

    private suspend fun processControlCommands() {
        for (command in controlCommands) {
            dispatchControl(command)
        }
    }

    private fun enqueueTouch(touch: MirrorTouchEvent) =
        synchronized(controlLock) {
            if (touch.isMove) {
                val batch =
                    pendingMoves
                        ?: ControlCommand.Moves(linkedMapOf()).also {
                            pendingMoves = it
                            controlCommands.trySend(it)
                        }
                batch.events[touch.pointerId] = touch
            } else {
                pendingMoves = null
                controlCommands.trySend(ControlCommand.Touch(touch))
            }
        }

    private fun enqueue(command: ControlCommand) =
        synchronized(controlLock) {
            pendingMoves = null
            controlCommands.trySend(command)
        }

    private fun dispatchControl(command: ControlCommand) {
        when (command) {
            is ControlCommand.Moves -> {
                val events =
                    synchronized(controlLock) {
                        if (pendingMoves === command) pendingMoves = null
                        command.events.values.toList()
                    }
                events.forEach(repository::sendTouch)
            }
            is ControlCommand.Touch -> repository.sendTouch(command.event)
            is ControlCommand.Key -> repository.sendKey(command.keyCode)
            is ControlCommand.Text -> repository.sendText(command.text)
        }
    }

    private sealed interface ControlCommand {
        data class Moves(val events: MutableMap<Int, MirrorTouchEvent>) : ControlCommand

        data class Touch(val event: MirrorTouchEvent) : ControlCommand

        data class Key(val keyCode: Int) : ControlCommand

        data class Text(val text: String) : ControlCommand
    }
}
