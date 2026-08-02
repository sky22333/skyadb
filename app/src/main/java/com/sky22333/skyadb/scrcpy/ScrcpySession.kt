package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.Surface
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.MirrorConnections
import com.sky22333.skyadb.i18n.appString
import java.io.EOFException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
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
    private val pendingAudioStream: AdbStream?,
    val controlClient: ScrcpyControlClient,
    private val decoder: ScrcpyVideoDecoder,
    private val scope: CoroutineScope,
    private val logLines: ArrayDeque<String>,
    private val onError: (Throwable, String) -> Unit,
) {
    @Volatile
    private var audioDecoder: ScrcpyAudioDecoder? = null

    @Volatile
    private var stopping = false

    fun start() {
        scope.launch { readServerLogs() }
        scope.launch {
            runCatching { decoder.start() }
                .onFailure { error ->
                    if (!stopping && error !is CancellationException) {
                        onError(error, serverLogTail())
                    }
                }
        }
        val audioStream = pendingAudioStream ?: return
        scope.launch {
            runCatching {
                val audioCodecId = audioStream.source.readInt()
                ScrcpyAudioDecoder(audioStream, audioCodecId).also {
                    audioDecoder = it
                    it.start()
                }
            }.onFailure {
                runCatching { audioStream.close() }
            }
        }
    }

    fun setSurface(surface: Surface) {
        decoder.setSurface(surface)
        // RESET_VIDEO 走 ADB socket，必须在 IO 线程。
        scope.launch { controlClient.resetVideo() }
    }

    fun clearSurface() {
        decoder.clearSurface()
    }

    fun stop() {
        stopping = true
        scope.cancel()
        decoder.stop()
        val audio = audioDecoder
        if (audio != null) {
            audio.stop()
        } else {
            runCatching { pendingAudioStream?.close() }
        }
        runCatching { controlStream.close() }
        runCatching { serverStream.close() }
    }

    private fun serverLogTail(): String {
        return synchronized(logLines) {
            logLines.takeLast(ServerLogTailLines).joinToString("\n")
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
        private const val ServerLogTailLines = 20

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
            require(!audioEnabled || audioKadb != null) { appString(R.string.scrcpy_audio_requires_connection) }

            val serverManager = ScrcpyServerManager(context)
            val logs = ArrayDeque<String>()
            serverManager.pushServer(controlKadb)
            val scid = generateScid()
            val socketName = "scrcpy_${ScrcpyConstants.formatScid(scid)}"
            val serverStream = controlKadb.open(
                "shell:${serverManager.buildStartCommand(scid, options, audioEnabled)} 2>&1",
            )

            // 官方顺序：video → audio → control；dummy byte 仅第一路。
            val videoStream = openLocalAbstractWithRetry(videoKadb, socketName, expectDummyByte = true)
            val audioStream = if (audioEnabled && audioKadb != null) {
                openLocalAbstractWithRetry(audioKadb, socketName, expectDummyByte = false)
            } else {
                null
            }
            val controlStream = openLocalAbstractWithRetry(controlKadb, socketName, expectDummyByte = false)

            // 协议握手：必须消费设备名与 codec id，再进入 demux。
            skipDeviceName(videoStream)
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

            ScrcpySession(
                serverStream = serverStream,
                controlStream = controlStream,
                pendingAudioStream = audioStream,
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
            throw IllegalStateException(appString(R.string.scrcpy_socket_connect_failed, socketName), lastError)
        }

        private fun skipDeviceName(stream: AdbStream) {
            stream.source.skip(ScrcpyProtocol.DeviceNameLength.toLong())
        }
    }
}
