package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.KeyEvent
import android.view.Surface
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.operationCatching
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScrcpyRepository(
    private val context: Context,
    private val kadbManager: KadbManager,
) {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val generation = AtomicLong()
    @Volatile private var session: ScrcpySession? = null

    fun requestStop() {
        val token = generation.incrementAndGet()
        cleanupScope.launch {
            lifecycleMutex.withLock {
                // A delayed cleanup must not tear down a newer start request.
                if (generation.get() == token) stopLocked()
            }
        }
    }

    suspend fun start(
        surface: Surface,
        qualityPreset: MirrorQualityPreset = MirrorQualityPreset.Balanced,
        onVideoSize: (Int, Int) -> Unit,
        onStreamError: (Throwable) -> Unit = {},
        onAudioError: (Throwable) -> Unit = {},
    ): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val token = generation.incrementAndGet()
            lifecycleMutex.withLock {
                stopLocked()
                currentCoroutineContext().ensureActive()
                val options = qualityPreset.options
                val optionsText = options.diagnosticText()
                val audioEnabled = (kadbManager.currentDeviceSdkInt() ?: 0) >= MinAudioSdkInt
                val connections =
                    when (val acquired = kadbManager.mirrorClient()) {
                        is AdbOperationResult.Failure -> return@withContext acquired
                        is AdbOperationResult.Success -> acquired.data
                    }
                try {
                    operationCatching {
                        val created =
                            ScrcpySession.start(
                                context,
                                connections,
                                surface,
                                options,
                                audioEnabled,
                                onVideoSize,
                                onAudioError = { error ->
                                    DiagnosticLogger.record(
                                        module = DiagnosticModule.Mirror,
                                        operation = "音频流",
                                        target = kadbManager.currentEndpoint(),
                                        message = appString(R.string.scrcpy_audio_stream_error),
                                        suggestion = appString(R.string.scrcpy_start_failed_fallback_suggestion),
                                        cause = error,
                                    )
                                    onAudioError(error)
                                },
                                onError = { error, serverLog ->
                                    DiagnosticLogger.record(
                                        module = DiagnosticModule.Mirror,
                                        operation = "视频流",
                                        target = kadbManager.currentEndpoint(),
                                        message = appString(R.string.scrcpy_video_stream_error),
                                        suggestion =
                                            mirrorDiagnosticSuggestion(
                                                qualityPreset,
                                                optionsText,
                                                serverLog,
                                            ),
                                        cause = error,
                                    )
                                    requestStop()
                                    onStreamError(error)
                                },
                            )
                        session = created
                        currentCoroutineContext().ensureActive()
                        check(generation.get() == token) { "Mirror start cancelled" }
                        created.start()
                        AdbOperationResult.Success(Unit)
                    }
                        .getOrElse { error ->
                            stopLocked()
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
                                suggestion =
                                    error.message
                                        ?: appString(
                                            R.string.scrcpy_start_failed_fallback_suggestion
                                        ),
                                cause = error,
                            )
                        }
                } finally {
                    if (!currentCoroutineContext().isActive || generation.get() != token) {
                        withContext(NonCancellable) { stopLocked() }
                    }
                }
            }
        }

    fun sendTouch(event: MirrorTouchEvent) {
        runCatching {
            session?.controlClient?.sendTouch(event)
        }
            .onFailure { error ->
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
        }
            .onFailure { error ->
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

    suspend fun stop() =
        withContext(Dispatchers.IO + NonCancellable) {
            lifecycleMutex.withLock { stopLocked() }
        }

    private suspend fun stopLocked() {
        val current = session
        session = null
        current?.stop()
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
        val base =
            appString(
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
