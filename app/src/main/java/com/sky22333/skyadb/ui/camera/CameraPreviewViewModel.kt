package com.sky22333.skyadb.ui.camera

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.scrcpy.MirrorQualityPreset
import com.sky22333.skyadb.scrcpy.MirrorStats
import com.sky22333.skyadb.scrcpy.MirrorVideoCodec
import com.sky22333.skyadb.scrcpy.ScrcpyCameraFacing
import com.sky22333.skyadb.scrcpy.ScrcpyRepository
import com.sky22333.skyadb.scrcpy.ScrcpyVideoSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraPreviewUiState(
    val status: OperationStatus = OperationStatus.Idle,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val facing: ScrcpyCameraFacing = ScrcpyCameraFacing.Back,
    val stats: MirrorStats = MirrorStats(),
)

class CameraPreviewViewModel(
    private val repository: ScrcpyRepository = AppServices.scrcpyRepository,
) : ViewModel() {
    private val state = MutableStateFlow(CameraPreviewUiState())
    val uiState: StateFlow<CameraPreviewUiState> = state.asStateFlow()

    private var latestSurface: Surface? = null
    private var startJob: Job? = null
    private var generation = 0L
    private var started = false

    fun onSurfaceCreated(surface: Surface) {
        latestSurface = surface
        if (started) repository.setSurface(surface) else start(surface, state.value.facing)
    }

    fun onSurfaceDestroyed() {
        latestSurface = null
        repository.clearSurface()
    }

    fun switchFacing() {
        val next = if (state.value.facing == ScrcpyCameraFacing.Back) {
            ScrcpyCameraFacing.Front
        } else {
            ScrcpyCameraFacing.Back
        }
        state.update { it.copy(facing = next) }
        val surface = latestSurface?.takeIf { it.isValid } ?: return
        generation++
        started = false
        start(surface, next)
    }

    fun retry() {
        val surface = latestSurface?.takeIf { it.isValid } ?: return
        generation++
        started = false
        start(surface, state.value.facing)
    }

    fun stop() {
        generation++
        startJob?.cancel()
        started = false
        latestSurface = null
        state.value = CameraPreviewUiState()
        repository.requestStop()
    }

    override fun onCleared() {
        generation++
        startJob?.cancel()
        started = false
        latestSurface = null
        repository.requestStop()
        super.onCleared()
    }

    private fun start(surface: Surface, facing: ScrcpyCameraFacing) {
        if (started) return
        started = true
        val requestGeneration = ++generation
        state.update {
            it.copy(
                status = OperationStatus.Running(appString(R.string.camera_preview_starting)),
                videoWidth = 0,
                videoHeight = 0,
            )
        }
        startJob = viewModelScope.launch {
            when (
                val result = repository.start(
                    surface = surface,
                    qualityPreset = MirrorQualityPreset.Balanced,
                    videoCodec = MirrorVideoCodec.H264,
                    videoSource = ScrcpyVideoSource.Camera,
                    cameraFacing = facing,
                    onVideoSize = { width, height ->
                        if (generation == requestGeneration) {
                            state.update { it.copy(videoWidth = width, videoHeight = height) }
                        }
                    },
                    onStats = { stats ->
                        if (generation == requestGeneration) state.update { it.copy(stats = stats) }
                    },
                    onStreamError = { error ->
                        if (generation == requestGeneration) {
                            started = false
                            state.update {
                                it.copy(
                                    status = OperationStatus.Failed(
                                        appString(R.string.camera_preview_disconnected),
                                        error.message ?: appString(R.string.camera_preview_retry_hint),
                                    )
                                )
                            }
                        }
                    },
                )
            ) {
                is AdbOperationResult.Success -> if (generation == requestGeneration) {
                    latestSurface?.takeIf { it.isValid }?.let(repository::setSurface)
                    state.update { it.copy(status = OperationStatus.Success(appString(R.string.camera_preview_active))) }
                }
                is AdbOperationResult.Failure -> if (generation == requestGeneration) {
                    started = false
                    state.update { it.copy(status = OperationStatus.Failed(result.message, result.suggestion)) }
                }
            }
        }
    }
}
