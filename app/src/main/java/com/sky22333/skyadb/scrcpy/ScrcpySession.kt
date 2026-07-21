package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.Surface
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.adb.MirrorConnections
import java.io.EOFException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScrcpySession private constructor(
    private val serverStream: AdbStream,
    private val controlStream: AdbStream,
    private val audioDecoder: ScrcpyAudioDecoder?,
    val deviceInfo: ScrcpyDeviceInfo,
    val controlClient: ScrcpyControlClient,
    private val decoder: ScrcpyVideoDecoder,
    private val scope: CoroutineScope,
    private val logLines: ArrayDeque<String>,
    private val onError: (Throwable, String) -> Unit,
) {
    fun start() {
        scope.launch { readServerLogs() }
        scope.launch {
            runCatching { decoder.start() }
                .onFailure { error -> onError(error, serverLogTail(maxLines = 20)) }
        }
        val audio = audioDecoder ?: return
        scope.launch {
            runCatching { audio.start() }
            // 音频失败不影响画面（对齐官方 soft-fail）。
        }
    }

    fun stop() {
        scope.cancel()
        decoder.stop()
        audioDecoder?.stop()
        runCatching { controlStream.close() }
        runCatching { serverStream.close() }
    }

    fun serverLogTail(maxLines: Int = 80): String {
        return synchronized(logLines) {
            logLines.takeLast(maxLines.coerceAtLeast(1)).joinToString("\n")
        }
    }

    private suspend fun readServerLogs() = withContext(Dispatchers.IO) {
        while (isActive) {
            val line = try {
                serverStream.source.readUtf8Line() ?: break
            } catch (_: EOFException) {
                break
            } catch (_: Throwable) {
                break
            }
            synchronized(logLines) {
                if (logLines.size >= 120) logLines.removeFirst()
                logLines.addLast(line)
            }
        }
    }

    companion object {
        suspend fun start(
            context: Context,
            connections: MirrorConnections,
            surface: Surface,
            options: ScrcpyOptions = ScrcpyOptions(),
            audioEnabled: Boolean,
            onVideoSize: (Int, Int) -> Unit,
            onError: (Throwable, String) -> Unit,
        ): ScrcpySession = withContext(Dispatchers.IO) {
            val controlKadb = connections.control
            val videoKadb = connections.video
            val audioKadb = connections.audio
            require(!audioEnabled || audioKadb != null) { "启用音频时需要 audio 连接" }

            val serverManager = ScrcpyServerManager(context)
            val logs = ArrayDeque<String>()
            serverManager.pushServer(controlKadb)
            val scid = generateScid()
            val socketName = "scrcpy_${scid.toString(16).padStart(8, '0')}"
            val serverStream = controlKadb.open(
                "shell:${serverManager.buildStartCommand(scid, options, audioEnabled)} 2>&1",
            )

            delay(200)
            // 官方顺序：video → audio → control；dummy byte 仅第一路。
            val videoStream = openLocalAbstractWithRetry(videoKadb, socketName, expectDummyByte = true)
            val audioStream = if (audioEnabled && audioKadb != null) {
                openLocalAbstractWithRetry(audioKadb, socketName, expectDummyByte = false)
            } else {
                null
            }
            val controlStream = openLocalAbstractWithRetry(controlKadb, socketName, expectDummyByte = false)

            val name = readDeviceName(videoStream)
            val videoCodecId = videoStream.source.readInt()
            val controlClient = ScrcpyControlClient(controlStream)
            val videoDecoder = ScrcpyVideoDecoder(
                stream = videoStream,
                codecId = videoCodecId,
                surface = surface,
                onVideoSize = { width, height ->
                    controlClient.updateVideoSize(width, height)
                    onVideoSize(width, height)
                },
            )

            val audioDecoder = audioStream?.let { stream ->
                runCatching {
                    val audioCodecId = stream.source.readInt()
                    ScrcpyAudioDecoder(stream, audioCodecId)
                }.getOrElse {
                    runCatching { stream.close() }
                    null
                }
            }

            ScrcpySession(
                serverStream = serverStream,
                controlStream = controlStream,
                audioDecoder = audioDecoder,
                deviceInfo = ScrcpyDeviceInfo(name = name, codecId = videoCodecId),
                controlClient = controlClient,
                decoder = videoDecoder,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                logLines = logs,
                onError = onError,
            ).also { it.start() }
        }

        private fun generateScid(): UInt {
            return (Random.nextInt() and 0x7fffffff).toUInt()
        }

        private suspend fun openLocalAbstractWithRetry(
            kadb: Kadb,
            socketName: String,
            expectDummyByte: Boolean,
        ): AdbStream {
            var lastError: Throwable? = null
            repeat(ScrcpyConstants.ConnectRetryCount) {
                try {
                    val stream = kadb.open("localabstract:$socketName")
                    if (expectDummyByte) {
                        val dummy = stream.source.readByte().toInt()
                        if (dummy < 0) throw EOFException("scrcpy dummy byte missing")
                    }
                    return stream
                } catch (error: Throwable) {
                    lastError = error
                    delay(ScrcpyConstants.ConnectRetryDelayMillis)
                }
            }
            throw IllegalStateException("无法连接 scrcpy socket：$socketName", lastError)
        }

        private fun readDeviceName(stream: AdbStream): String {
            val bytes = stream.source.readByteArray(ScrcpyProtocol.DeviceNameLength.toLong())
            val length = bytes.indexOf(0).takeIf { it >= 0 } ?: bytes.size
            return bytes.copyOf(length).toString(Charsets.UTF_8).ifBlank { "Android 设备" }
        }
    }
}
