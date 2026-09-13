package com.qwadb.app.ui.watch

import android.view.Surface
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.data.AppSettingsStore
import com.qwadb.app.data.BitrateMbpsToBps
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.scrcpy.MirrorQualityPreset
import com.qwadb.app.scrcpy.ScrcpyOptions
import com.qwadb.app.scrcpy.ScrcpyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WatchUiState(
    val status: OperationStatus = OperationStatus.Idle,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/** 观看屏幕：复用屏幕镜像（ScrcpyRepository）技术与画质配置，但仅展示画面，不发送任何控制指令。 */
class WatchViewModel(
    private val repository: ScrcpyRepository = AppServices.scrcpyRepository,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = state.asStateFlow()

    private var started = false
    private var latestSurface: Surface? = null

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

    /** 观看模式下点击屏幕：仅提示不支持操作，不发送触摸指令。 */
    fun onInteraction() {
        Toast.makeText(
            AppServices.context,
            appString(R.string.watch_touch_unsupported),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun start(surface: Surface) {
        if (started) return
        started = true
        state.value = state.value.copy(status = OperationStatus.Running(appString(R.string.watch_starting)))
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val qualityPreset = settings.mirrorQualityPreset
            val customOptions = if (qualityPreset == MirrorQualityPreset.Custom) {
                ScrcpyOptions(
                    maxSize = settings.mirrorCustomMaxSize,
                    maxFps = settings.mirrorCustomFps,
                    videoBitRate = settings.mirrorCustomBitrateMbps * BitrateMbpsToBps,
                )
            } else {
                null
            }
            if (!started) return@launch
            val launchSurface = latestSurface?.takeIf { it.isValid } ?: surface
            if (!launchSurface.isValid) {
                started = false
                state.value = state.value.copy(
                    status = OperationStatus.Failed(
                        appString(R.string.scrcpy_start_failed),
                        appString(R.string.watch_invalid_surface),
                    ),
                )
                return@launch
            }
            when (
                val result = repository.start(
                    surface = launchSurface,
                    qualityPreset = qualityPreset,
                    customOptions = customOptions,
                    onVideoSize = { width, height ->
                        state.value = state.value.copy(videoWidth = width, videoHeight = height)
                    },
                    onStreamError = { error ->
                        started = false
                        state.value = state.value.copy(
                            status = OperationStatus.Failed(
                                text = appString(R.string.watch_disconnected),
                                suggestion = error.message ?: appString(R.string.watch_reenter_hint),
                            ),
                        )
                    },
                )
            ) {
                is AdbOperationResult.Success -> {
                    latestSurface?.takeIf { it.isValid }?.let { repository.setSurface(it) }
                    state.value = state.value.copy(status = OperationStatus.Success(appString(R.string.watch_active)))
                }
                is AdbOperationResult.Failure -> {
                    started = false
                    state.value = state.value.copy(
                        status = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun stop() {
        started = false
        latestSurface = null
        state.value = WatchUiState()
        repository.requestStop()
    }

    override fun onCleared() {
        started = false
        latestSurface = null
        repository.requestStop()
        super.onCleared()
    }
}
