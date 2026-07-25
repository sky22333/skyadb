package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.KeyEvent
import android.view.Surface
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.adb.MirrorConnections
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
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
                        message = "屏幕镜像视频流异常",
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
                    message = "屏幕镜像启动失败",
                    suggestion = mirrorDiagnosticSuggestion(qualityPreset, optionsText),
                    cause = error,
                )
                AdbOperationResult.Failure(
                    message = "屏幕镜像启动失败",
                    suggestion = error.message ?: "请查看设置里的诊断日志。",
                    cause = error,
                )
            },
        )
    }

    fun sendTouch(event: MirrorTouchEvent) {
        runCatching {
            session?.controlClient?.sendTouch(event)
        }.onFailure { error ->
            recordControlFailure("发送触摸", "远程触摸发送失败", error)
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
            recordControlFailure("发送按键", "远程按键发送失败", error)
        }
    }

    fun sendText(text: String) {
        runCatching { session?.controlClient?.sendText(text) }
            .onFailure { error ->
                recordControlFailure("发送文本", "远程文本发送失败", error)
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
                        message = "释放屏幕镜像资源失败",
                        suggestion = "如果再次启动异常，请重新连接设备。",
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
            suggestion = "镜像连接可能已断开，请重新进入屏幕镜像。",
            cause = error,
        )
    }

    private fun mirrorDiagnosticSuggestion(
        qualityPreset: MirrorQualityPreset,
        optionsText: String,
        serverLog: String = "",
    ): String {
        val base = "当前画质：${qualityPreset.label}。启动参数：$optionsText。请重新进入屏幕镜像；如果持续失败，请切换到流畅画质。"
        return if (serverLog.isBlank()) {
            base
        } else {
            "$base\nscrcpy server 日志：\n${serverLog.take(ServerLogDiagnosticMaxChars)}"
        }
    }

    private companion object {
        const val ServerLogDiagnosticMaxChars = 300
        const val MinAudioSdkInt = 30
    }
}
