package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.KeyEvent
import android.view.Surface
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.adb.MirrorClient
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
        videoCodec: MirrorVideoCodec = MirrorVideoCodec.H264,
        onVideoSize: (Int, Int) -> Unit,
        onStats: (MirrorStats) -> Unit = {},
        onStreamError: (Throwable) -> Unit = {},
        onAudioError: (Throwable) -> Unit = {},
    ): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val token = generation.incrementAndGet()
            lifecycleMutex.withLock {
                stopLocked()
                currentCoroutineContext().ensureActive()
                val requestedCodec =
                    if (videoCodec == MirrorVideoCodec.H265 && !localDecoderSupports(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                        DiagnosticLogger.record(
                            module = DiagnosticModule.Mirror,
                            operation = "H.265 不可用",
                            target = kadbManager.currentEndpoint(),
                            message = "本机没有可用的 HEVC 解码器，已改用 H.264",
                        )
                        MirrorVideoCodec.H264
                    } else {
                        videoCodec
                    }
                var options = qualityPreset.options.copy(videoCodec = requestedCodec)
                var optionsText = options.diagnosticText()
                val audioEnabled = (kadbManager.currentDeviceSdkInt() ?: 0) >= MinAudioSdkInt
                val mirrorClient =
                    when (val acquired = kadbManager.mirrorClient()) {
                        is AdbOperationResult.Failure -> return@withContext acquired
                        is AdbOperationResult.Success -> acquired.data
                    }
                try {
                    operationCatching {
                        val firstAttempt =
                            operationCatching {
                                startSession(
                                    context = context,
                                    mirrorClient = mirrorClient,
                                    surface = surface,
                                    options = options,
                                    audioEnabled = audioEnabled,
                                    onVideoSize = onVideoSize,
                                    onStats = onStats,
                                    onStreamError = onStreamError,
                                    onAudioError = onAudioError,
                                    qualityPreset = qualityPreset,
                                    optionsText = optionsText,
                                )
                            }
                        val created =
                            if (firstAttempt.isFailure && requestedCodec == MirrorVideoCodec.H265) {
                                options = qualityPreset.options.copy(videoCodec = MirrorVideoCodec.H264)
                                optionsText = options.diagnosticText()
                                DiagnosticLogger.record(
                                    module = DiagnosticModule.Mirror,
                                    operation = "H.265 回退",
                                    target = kadbManager.currentEndpoint(),
                                    message = "H.265 镜像启动失败，已回退 H.264",
                                    suggestion = appString(R.string.scrcpy_mirror_codec_fallback_suggestion),
                                    cause = firstAttempt.exceptionOrNull(),
                                )
                                startSession(
                                    context = context,
                                    mirrorClient = mirrorClient,
                                    surface = surface,
                                    options = options,
                                    audioEnabled = audioEnabled,
                                    onVideoSize = onVideoSize,
                                    onStats = onStats,
                                    onStreamError = onStreamError,
                                    onAudioError = onAudioError,
                                    qualityPreset = qualityPreset,
                                    optionsText = optionsText,
                                )
                            } else {
                                firstAttempt.getOrElse { error -> throw error }
                            }
                        session = created
                        currentCoroutineContext().ensureActive()
                        check(generation.get() == token) { "Mirror start cancelled" }
                        created.start()
                        AdbOperationResult.Success(Unit)
                    }
                        .getOrElse { error ->
                            stopLocked()
                            if (session == null) runCatching { mirrorClient.close() }
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
                        withContext(NonCancellable) {
                            if (session == null) mirrorClient.close() else stopLocked()
                        }
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

    private suspend fun startSession(
        context: Context,
        mirrorClient: MirrorClient,
        surface: Surface,
        options: ScrcpyOptions,
        audioEnabled: Boolean,
        onVideoSize: (Int, Int) -> Unit,
        onStats: (MirrorStats) -> Unit,
        onStreamError: (Throwable) -> Unit,
        onAudioError: (Throwable) -> Unit,
        qualityPreset: MirrorQualityPreset,
        optionsText: String,
    ): ScrcpySession =
        ScrcpySession.start(
            context = context,
            mirrorClient = mirrorClient,
            surface = surface,
            options = options,
            audioEnabled = audioEnabled,
            onVideoSize = onVideoSize,
            onStats = onStats,
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

    private fun localDecoderSupports(mime: String): Boolean =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        }.getOrDefault(false)

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
