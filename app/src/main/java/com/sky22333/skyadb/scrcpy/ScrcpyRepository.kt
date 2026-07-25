package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.KeyEvent
import android.view.Surface
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.adb.MirrorConnections
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScrcpyRepository(
    private val context: Context,
    private val kadbManager: KadbManager,
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = AtomicBoolean(false)
    private var session: ScrcpySession? = null
    private var mirrorConnections: MirrorConnections? = null

    fun requestStop() {
        cleanupScope.launch { stop() }
    }

    suspend fun start(
        surface: Surface,
        qualityPreset: MirrorQualityPreset = MirrorQualityPreset.Balanced,
        onVideoSize: (Int, Int) -> Unit,
        onStreamError: (Throwable) -> Unit = {},
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        stop()
        val options = qualityPreset.options
        val optionsText = options.diagnosticText()
        val audioEnabled = (kadbManager.currentDeviceSdkInt() ?: 0) >= MinAudioSdkInt
        val connections = when (val acquired = kadbManager.beginMirrorSession(audioEnabled)) {
            is AdbOperationResult.Failure -> return@withContext acquired
            is AdbOperationResult.Success -> acquired.data
        }
        mirrorConnections = connections

        runCatching {
            ScrcpySession.start(
                context = context,
                connections = connections,
                surface = surface,
                options = options,
                audioEnabled = audioEnabled,
                onVideoSize = onVideoSize,
                onError = { error, serverLog ->
                    DiagnosticLogger.record(
                        module = DiagnosticModule.Mirror,
                        operation = "视频流",
                        target = kadbManager.currentEndpoint(),
                        message = appString(R.string.scrcpy_video_stream_error),
                        suggestion = mirrorDiagnosticSuggestion(qualityPreset, optionsText, serverLog),
                        cause = error,
                    )
                    cleanupScope.launch { stop() }
                    onStreamError(error)
                },
            ).also { session = it }
        }.fold(
            onSuccess = { AdbOperationResult.Success(Unit) },
            onFailure = { error ->
                stop()
                DiagnosticLogger.record(
                    module = DiagnosticModule.Mirror,
                    operation = "启动镜像",
                    target = kadbManager.currentEndpoint(),
                    message = appString(R.string.scrcpy_start_failed),
                    suggestion = mirrorDiagnosticSuggestion(qualityPreset, optionsText),
                    cause = error,
                )
                AdbOperationResult.Failure(
                    message = appString(R.string.scrcpy_start_failed),
                    suggestion = error.message ?: appString(R.string.scrcpy_start_failed_fallback_suggestion),
                    cause = error,
                )
            },
        )
    }

    fun sendTouch(event: MirrorTouchEvent) {
        runCatching {
            session?.controlClient?.sendTouch(event)
        }.onFailure { error ->
            recordControlFailure("发送触摸", appString(R.string.scrcpy_send_touch_failed), error)
        }
    }

    fun sendKey(keyCode: Int) {
        runCatching {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                session?.controlClient?.sendBackOrScreenOn()
            } else {
                session?.controlClient?.sendKey(keyCode)
            }
        }.onFailure { error ->
            recordControlFailure("发送按键", appString(R.string.scrcpy_send_key_failed), error)
        }
    }

    fun sendText(text: String) {
        runCatching { session?.controlClient?.sendText(text) }
            .onFailure { error ->
                recordControlFailure("发送文本", appString(R.string.scrcpy_send_text_failed), error)
            }
    }

    fun setSurface(surface: Surface) {
        session?.setSurface(surface)
    }

    fun clearSurface() {
        session?.clearSurface()
    }

    fun isRunning(): Boolean = session != null

    suspend fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        try {
            val currentSession = session
            session = null
            runCatching { currentSession?.stop() }
                .onFailure { error ->
                    DiagnosticLogger.record(
                        module = DiagnosticModule.Mirror,
                        operation = "停止镜像",
                        message = appString(R.string.scrcpy_stop_release_failed),
                        suggestion = appString(R.string.scrcpy_stop_release_failed_suggestion),
                        cause = error,
                    )
                }
            val connections = mirrorConnections
            mirrorConnections = null
            kadbManager.endMirrorSession(connections)
        } finally {
            stopping.set(false)
        }
    }

    private fun recordControlFailure(operation: String, message: String, error: Throwable) {
        DiagnosticLogger.record(
            module = DiagnosticModule.Mirror,
            operation = operation,
            message = message,
            suggestion = appString(R.string.scrcpy_control_disconnected_suggestion),
            cause = error,
        )
    }

    private fun mirrorDiagnosticSuggestion(
        qualityPreset: MirrorQualityPreset,
        optionsText: String,
        serverLog: String = "",
    ): String {
        val base = appString(
            R.string.scrcpy_mirror_diagnostic_template,
            appString(qualityPreset.labelRes),
            optionsText,
        )
        return if (serverLog.isBlank()) {
            base
        } else {
            appString(
                R.string.scrcpy_mirror_diagnostic_with_log,
                base,
                serverLog.take(ServerLogDiagnosticMaxChars),
            )
        }
    }

    private companion object {
        const val ServerLogDiagnosticMaxChars = 300
        const val MinAudioSdkInt = 30
    }
}
