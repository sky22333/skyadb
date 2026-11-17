package com.sky22333.skyadb.scrcpy

import android.content.Context
import android.view.Surface
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.MirrorClient
import com.sky22333.skyadb.i18n.appString
import java.io.EOFException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScrcpySession
private constructor(
    private val serverStream: AdbStream,
    private val controlStream: AdbStream,
    private val pendingAudioStream: AdbStream?,
    val controlClient: ScrcpyControlClient,
    private val decoder: ScrcpyVideoDecoder,
    private val scope: CoroutineScope,
    private val logLines: ArrayDeque<String>,
    private val statsTracker: MirrorStatsTracker,
    private val mirrorClient: MirrorClient,
    private val onError: (Throwable, String) -> Unit,
    private val onAudioError: (Throwable) -> Unit,
    private val onStats: (MirrorStats) -> Unit,
) {
    @Volatile private var audioDecoder: ScrcpyAudioDecoder? = null

    @Volatile private var stopping = false
    @Volatile private var finalStatsEmitted = false

    fun start() {
        scope.launch { readServerLogs() }
        scope.launch {
            while (isActive) {
                delay(StatsIntervalMillis)
                emitStats()
            }
        }
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
            }
                .onFailure { error ->
                    runCatching { audioStream.close() }
                    if (!stopping && error !is CancellationException) onAudioError(error)
                }
        }
    }

    fun setSurface(surface: Surface) {
        decoder.setSurface(surface)
        // 释放旧 Surface 的解码器，并从服务端请求新的关键帧。
        scope.launch {
            decoder.releaseCurrentDecoder()
            controlClient.resetVideo()
        }
    }

    fun clearSurface() {
        decoder.clearSurface()
        scope.launch { decoder.releaseCurrentDecoder() }
    }

    suspend fun stop() =
        withContext(NonCancellable) {
            stopping = true
            decoder.stop()
            val audio = audioDecoder
            if (audio != null) {
                audio.stop()
            } else {
                runCatching { pendingAudioStream?.close() }
            }
            runCatching { controlStream.close() }
            runCatching { serverStream.close() }
            runCatching { mirrorClient.close() }
            scope.cancel()
            scope.coroutineContext[Job]?.join()
            if (!finalStatsEmitted) {
                finalStatsEmitted = true
                emitStats()
            }
        }

    private fun serverLogTail(): String {
        return synchronized(logLines) {
            logLines.takeLast(ServerLogTailLines).joinToString("\n")
        }
    }

    private fun emitStats() {
        runCatching { onStats(statsTracker.snapshot()) }
    }

    private suspend fun readServerLogs() =
        withContext(Dispatchers.IO) {
            while (isActive) {
                val line =
                    try {
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
        private const val StatsIntervalMillis = 1_000L

        suspend fun start(
            context: Context,
            mirrorClient: MirrorClient,
            surface: Surface,
            options: ScrcpyOptions = ScrcpyOptions(),
            audioEnabled: Boolean,
            onVideoSize: (Int, Int) -> Unit,
            onStats: (MirrorStats) -> Unit = {},
            onError: (Throwable, String) -> Unit,
            onAudioError: (Throwable) -> Unit,
        ): ScrcpySession =
            withContext(Dispatchers.IO) {
                val kadb: Kadb = mirrorClient.kadb
                val serverManager = ScrcpyServerManager(context)
                val logs = ArrayDeque<String>()
                val opened = mutableListOf<AdbStream>()
                var transferred = false
                try {
                    serverManager.pushServer(kadb)
                    val scid = generateScid()
                    val socketName = "scrcpy_${ScrcpyConstants.formatScid(scid)}"
                    val serverStream =
                        kadb
                            .open(
                                "shell:${serverManager.buildStartCommand(scid, options, audioEnabled)} 2>&1"
                            )
                            .also { opened += it }

                    // 官方顺序：video → audio → control；dummy byte 仅第一路。
                    val videoStream =
                        openLocalAbstractWithRetry(kadb, socketName, expectDummyByte = true).also {
                            opened += it
                        }
                    val audioStream =
                        if (audioEnabled) {
                            openLocalAbstractWithRetry(
                                    kadb,
                                    socketName,
                                    expectDummyByte = false,
                                )
                                .also { opened += it }
                        } else {
                            null
                        }
                    val controlStream =
                        openLocalAbstractWithRetry(kadb, socketName, expectDummyByte = false).also {
                            opened += it
                        }

                    // 协议握手：必须消费设备名与 codec id，再进入 demux。
                    skipDeviceName(videoStream)
                    val videoCodecId = videoStream.source.readInt()
                    val controlClient = ScrcpyControlClient(controlStream)
                    val statsTracker =
                        MirrorStatsTracker(
                            transport = mirrorClient.transport,
                            connectionMode = mirrorClient.mode,
                        )
                    val videoDecoder =
                        ScrcpyVideoDecoder(
                            stream = videoStream,
                            codecId = videoCodecId,
                            surface = surface,
                            statsTracker = statsTracker,
                            onRequestKeyFrame = controlClient::resetVideo,
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
                            statsTracker = statsTracker,
                            mirrorClient = mirrorClient,
                            onError = onError,
                            onAudioError = onAudioError,
                            onStats = onStats,
                        )
                        .also { transferred = true }
                } finally {
                    if (!transferred) {
                        opened.asReversed().forEach { stream -> runCatching { stream.close() } }
                    }
                }
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
                var pending: AdbStream? = null
                try {
                    val stream = kadb.open("localabstract:$socketName")
                    pending = stream
                    if (expectDummyByte) {
                        val dummy = stream.source.readByte().toInt()
                        if (dummy < 0) throw EOFException("scrcpy dummy byte missing")
                    }
                    return stream
                } catch (error: Throwable) {
                    runCatching { pending?.close() }
                    if (error is CancellationException) throw error
                    lastError = error
                    delay(ScrcpyConstants.ConnectRetryDelayMillis)
                }
            }
            throw IllegalStateException(
                appString(R.string.scrcpy_socket_connect_failed, socketName),
                lastError,
            )
        }

        private fun skipDeviceName(stream: AdbStream) {
            stream.source.skip(ScrcpyProtocol.DeviceNameLength.toLong())
        }
    }
}
