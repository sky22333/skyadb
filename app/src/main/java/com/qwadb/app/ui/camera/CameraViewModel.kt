package com.qwadb.app.ui.camera

import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
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
import com.qwadb.app.scrcpy.CameraFace
import com.qwadb.app.scrcpy.CameraQuality
import com.qwadb.app.scrcpy.CameraRepository
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CameraUiState(
    /** 当前查看的摄像头朝向。 */
    val face: CameraFace = CameraFace.Back,
    /** 是否已进入摄像头显示阶段。 */
    val started: Boolean = false,
    /** 摄像头视频流尺寸。 */
    val videoSize: Pair<Int, Int>? = null,
    val status: OperationStatus = OperationStatus.Idle,
    /** 后置手电筒是否开启。 */
    val torchOn: Boolean = false,
)

class CameraViewModel(
    private val repository: CameraRepository = AppServices.cameraRepository,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = state.asStateFlow()

    private val startMutex = Mutex()
    @Volatile private var surface: Surface? = null
    /** 预览 Surface 的像素尺寸，用于拍照时按相同尺寸分配目标 Bitmap。 */
    @Volatile private var surfaceSize: Pair<Int, Int>? = null
    private val stopped = AtomicBoolean(false)
    @Volatile private var launchRequested = false
    @Volatile private var photoBusy = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var quality: CameraQuality = CameraQuality()

    /** 进入摄像头显示阶段：按选择启动对应的单摄会话。 */
    fun start(face: CameraFace) {
        if (state.value.started) return
        state.value = state.value.copy(
            face = face,
            started = true,
            status = OperationStatus.Running(appString(R.string.camera_starting)),
        )
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            quality = CameraQuality(
                resolution = settings.cameraResolution,
                maxFps = settings.cameraFps,
                videoBitRate = settings.cameraBitrateMbps * BitrateMbpsToBps,
            )
            // 仅当 Surface 已就绪时才标记并启动；否则等待 onSurfaceCreated。
            val readySurface = surface
            if (readySurface != null && !launchRequested) {
                launchRequested = true
                launchFace(readySurface)
            }
        }
    }

    fun onSurfaceCreated(newSurface: Surface) {
        surface = newSurface
        if (!state.value.started) return
        if (!launchRequested) {
            launchRequested = true
            launchFace(newSurface)
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) surfaceSize = width to height
    }

    fun onSurfaceDestroyed() {
        surface = null
        surfaceSize = null
    }

    /** 手电筒（后置闪光灯）开关。 */
    fun toggleTorch() {
        val next = !state.value.torchOn
        val ok = runCatching { repository.setTorch(next) }.isSuccess
        if (ok) {
            state.value = state.value.copy(torchOn = next)
        } else {
            Toast.makeText(
                AppServices.context,
                appString(R.string.camera_torch_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 拍照：通过 PixelCopy 抓取预览 Surface 的当前画面，保存为 PNG 到应用外部图片目录。 */
    fun capturePhoto() {
        if (photoBusy || !state.value.started) return
        // PixelCopy 自 API 26 引入；低版本设备直接提示失败，避免崩溃。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(
                AppServices.context,
                appString(R.string.camera_photo_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val targetSurface = surface
        val size = surfaceSize
        if (targetSurface == null || !targetSurface.isValid || size == null) {
            Toast.makeText(
                AppServices.context,
                appString(R.string.camera_photo_view_required),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        photoBusy = true
        Toast.makeText(
            AppServices.context,
            appString(R.string.camera_photo_capturing),
            Toast.LENGTH_SHORT,
        ).show()
        val fileName = "camera-${System.currentTimeMillis()}.png"
        val saveDir = AppServices.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: AppServices.context.filesDir
        val saveFile = File(saveDir, fileName)
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        val requested = runCatching {
            PixelCopy.request(
                targetSurface,
                null,
                bitmap,
                { result ->
                    photoBusy = false
                    if (result == PixelCopy.SUCCESS) {
                        savePhoto(bitmap, saveFile)
                    } else {
                        bitmap.recycle()
                        Toast.makeText(
                            AppServices.context,
                            appString(R.string.camera_photo_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                mainHandler,
            )
        }.isSuccess
        if (!requested) {
            photoBusy = false
            bitmap.recycle()
            Toast.makeText(
                AppServices.context,
                appString(R.string.camera_photo_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun savePhoto(bitmap: Bitmap, saveFile: File) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    saveFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }.isSuccess
            }
            Toast.makeText(
                AppServices.context,
                if (saved) {
                    appString(R.string.camera_photo_saved) + "\n" + saveFile.absolutePath
                } else {
                    appString(R.string.camera_photo_failed)
                },
                if (saved) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        viewModelScope.launch { repository.stopAll() }
        state.value = CameraUiState()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun launchFace(targetSurface: Surface) {
        viewModelScope.launch {
            startMutex.withLock {
                if (stopped.get()) return@withLock
                when (
                    val result = repository.start(
                        face = state.value.face,
                        surface = targetSurface,
                        quality = quality,
                        onVideoSize = { _, width, height ->
                            state.value = state.value.copy(videoSize = width to height)
                        },
                        onStreamError = { _, error ->
                            handleFailure(error)
                        },
                    )
                ) {
                    is AdbOperationResult.Success -> {
                        state.value = state.value.copy(
                            status = OperationStatus.Success(appString(R.string.camera_active)),
                        )
                    }
                    is AdbOperationResult.Failure -> handleFailure(result.cause)
                }
            }
        }
    }

    private fun handleFailure(error: Throwable?) {
        state.value = state.value.copy(
            status = OperationStatus.Failed(
                text = appString(R.string.camera_main_failed),
                suggestion = error?.message ?: appString(R.string.camera_start_failed_suggestion),
            ),
        )
    }
}
