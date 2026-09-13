package com.qwadb.app.scrcpy

import android.content.Context
import android.view.Surface
import com.qwadb.app.R
import com.qwadb.app.adb.KadbManager
import com.qwadb.app.adb.MirrorConnections
import com.qwadb.app.diagnostics.DiagnosticLogger
import com.qwadb.app.diagnostics.DiagnosticModule
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 摄像头查看仓库：按朝向（前置/后置）启动一条 scrcpy camera 会话。
 * 同一时刻仅查看一个摄像头（前置或后置二选一）。
 */
class CameraRepository(
    private val context: Context,
    private val kadbManager: KadbManager,
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private val sessions = ConcurrentHashMap<CameraFace, Entry>()

    private class Entry(
        val session: ScrcpySession,
        val connections: MirrorConnections,
    )

    fun isRunning(face: CameraFace): Boolean = sessions[face] != null

    fun anyRunning(): Boolean = sessions.isNotEmpty()

    suspend fun start(
        face: CameraFace,
        surface: Surface,
        quality: CameraQuality,
        onVideoSize: (CameraFace, Int, Int) -> Unit = { _, _, _ -> },
        onStreamError: (CameraFace, Throwable) -> Unit = { _, _ -> },
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        stop(face)
        val captureOptions = CameraCaptureOptions(face = face, quality = quality)
        val optionsText = captureOptions.diagnosticText()
        val connections = when (val acquired = kadbManager.beginMirrorSession(audioEnabled = false)) {
            is AdbOperationResult.Failure -> return@withContext acquired
            is AdbOperationResult.Success -> acquired.data
        }
        runCatching {
            ScrcpySession.start(
                context = context,
                connections = connections,
                surface = surface,
                options = ScrcpyOptions(
                    maxSize = quality.width,
                    maxFps = quality.maxFps,
                    videoBitRate = quality.videoBitRate,
                ),
                audioEnabled = false,
                cameraOptions = captureOptions,
                onVideoSize = { width, height -> onVideoSize(face, width, height) },
                onError = { error, serverLog ->
                    DiagnosticLogger.record(
                        module = DiagnosticModule.Camera,
                        operation = "摄像头视频流",
                        target = kadbManager.currentEndpoint(),
                        message = appString(R.string.camera_stream_error),
                        suggestion = appString(
                            R.string.camera_diagnostic_template,
                            appString(face.defaultLabelRes),
                            optionsText,
                            serverLog.take(ServerLogDiagnosticMaxChars),
                        ),
                        cause = error,
                    )
                    cleanupScope.launch { stop(face) }
                    onStreamError(face, error)
                },
            ).also { sessions[face] = Entry(it, connections) }
        }.fold(
            onSuccess = { AdbOperationResult.Success(Unit) },
            onFailure = { error ->
                stop(face)
                DiagnosticLogger.record(
                    module = DiagnosticModule.Camera,
                    operation = "启动摄像头",
                    target = kadbManager.currentEndpoint(),
                    message = appString(R.string.camera_start_failed),
                    suggestion = appString(R.string.scrcpy_start_failed_fallback_suggestion),
                    cause = error,
                )
                AdbOperationResult.Failure(
                    message = appString(R.string.camera_start_failed),
                    suggestion = error.message ?: appString(R.string.camera_start_failed_suggestion),
                    cause = error,
                )
            },
        )
    }

    fun setSurface(face: CameraFace, surface: Surface) {
        sessions[face]?.session?.setSurface(surface)
    }

    fun clearSurface(face: CameraFace) {
        sessions[face]?.session?.clearSurface()
    }

    /** 手电筒（后置闪光灯）开关，仅对当前会话生效。 */
    fun setTorch(enabled: Boolean) {
        sessions.values.firstOrNull()?.session?.setTorch(enabled)
    }

    suspend fun stop(face: CameraFace) {
        val entry = sessions.remove(face) ?: return
        runCatching { entry.session.stop() }
        kadbManager.endMirrorSession(entry.connections)
    }

    suspend fun stopAll() {
        if (!stopping.compareAndSet(false, true)) return
        try {
            sessions.keys.toList().forEach { face -> runCatching { stop(face) } }
        } finally {
            stopping.set(false)
        }
    }

    private companion object {
        const val ServerLogDiagnosticMaxChars = 300
    }
}
