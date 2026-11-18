package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.flyfishxu.kadb.stream.AdbStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScrcpyVideoDecoder(
    private val stream: AdbStream,
    private val codecId: Int,
    surface: Surface,
    private val onVideoSize: (Int, Int) -> Unit,
    private val statsTracker: MirrorStatsTracker,
    private val onRequestKeyFrame: () -> Unit,
) {
    @Volatile private var running = true
    private val surfaceState = AtomicReference(SurfaceState(surface, surface.isValid, 0L))
    @Volatile private var pump: MediaCodecPump? = null
    @Volatile private var decoderError: Throwable? = null
    private var width = 0
    private var height = 0
    private var config: ByteArray? = null
    @Volatile private var awaitingKeyFrame = true
    private val lastKeyFrameRequestNanos = AtomicLong()
    private val decoderMutex = Mutex()
    @Volatile private var decoderEpoch = -1L

    suspend fun start() =
        withContext(Dispatchers.IO) {
            val header = ByteArray(ScrcpyProtocol.PacketHeaderLength)
            val fields = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            try {
                while (running) {
                    currentCoroutineContext().ensureActive()
                    stream.source.readFully(header)
                    fields.rewind()
                    val ptsAndFlags = fields.long
                    val size = fields.int
                    if ((ptsAndFlags and ScrcpyProtocol.PacketFlagSession) != 0L) {
                        statsTracker.resetLatencyBaseline()
                        fields.position(4)
                        val nextWidth = fields.int
                        val nextHeight = fields.int
                        require(nextWidth in 1..32768 && nextHeight in 1..32768) {
                            "Invalid video size"
                        }
                        if (nextWidth != width || nextHeight != height) {
                            release()
                            width = nextWidth
                            height = nextHeight
                            config = null
                            awaitingKeyFrame = true
                            onVideoSize(width, height)
                        }
                        continue
                    }
                    require(size in 1..10_000_000) { "Invalid video packet length: $size" }
                    val data = stream.source.readByteArray(size.toLong())
                    val isConfig = (ptsAndFlags and ScrcpyProtocol.PacketFlagConfig) != 0L
                    val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PacketFlagKeyFrame) != 0L
                    val pts = ptsAndFlags and ScrcpyProtocol.PacketPtsMask
                    if (!isConfig) statsTracker.onPacketReceived(size, pts)
                    if (isConfig) config = data
                    val surfaceSnapshot = surfaceState.get()
                    if (decoderEpoch != surfaceSnapshot.epoch) {
                        release()
                        awaitingKeyFrame = true
                    }
                    if (
                        width == 0 ||
                            height == 0 ||
                            !surfaceSnapshot.valid ||
                            !surfaceSnapshot.surface.isValid
                    ) {
                        if (!isConfig) statsTracker.onFrameDropped()
                        continue
                    }
                    if (!isConfig && awaitingKeyFrame && !isKeyFrame) {
                        statsTracker.onFrameDropped()
                        requestKeyFrame()
                        continue
                    }
                    try {
                        val existing = pump
                        val decoder = existing ?: configure(surfaceSnapshot)
                        if (decoder == null) {
                            if (!isConfig) statsTracker.onFrameDropped()
                            awaitingKeyFrame = true
                            continue
                        }
                        // configure() already submitted cached CSD once.
                        if (isConfig) {
                            if (existing != null)
                                decoder.queue(data, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        } else if (awaitingKeyFrame) {
                            decoder.queue(data, pts)
                            awaitingKeyFrame = false
                        } else if (!decoder.tryQueue(data, pts)) {
                            if (isKeyFrame) {
                                decoder.queue(data, pts)
                            } else {
                                statsTracker.onFrameDropped()
                                awaitingKeyFrame = true
                                requestKeyFrame()
                                continue
                            }
                        }
                    } catch (error: Exception) {
                        // Some hardware decoders report an abandoned Surface asynchronously.
                        // That codec belongs to the previous Surface, not the current session.
                        val current = surfaceState.get()
                        if (surfaceSnapshot.epoch == current.epoch && current.valid) throw error
                        release()
                        awaitingKeyFrame = true
                        if (!isConfig) statsTracker.onFrameDropped()
                    }
                }
            } catch (error: Exception) {
                throw decoderError ?: error
            } finally {
                withContext(NonCancellable) { release() }
                runCatching { stream.close() }
            }
        }

    fun setSurface(next: Surface): Long {
        awaitingKeyFrame = true
        while (true) {
            val current = surfaceState.get()
            val updated = SurfaceState(next, next.isValid, current.epoch + 1)
            if (surfaceState.compareAndSet(current, updated)) return current.epoch
        }
    }

    fun clearSurface(): Long {
        awaitingKeyFrame = true
        return surfaceState.getAndUpdate { it.copy(valid = false, epoch = it.epoch + 1) }.epoch
    }

    suspend fun releaseCurrentDecoder(epoch: Long) {
        release(epoch, awaitKeyFrame = true)
    }

    fun stop() {
        running = false
        runCatching { stream.close() } // Wake a blocked network read before cancelling the job.
    }

    private suspend fun configure(requested: SurfaceState): MediaCodecPump? = decoderMutex.withLock {
        val current = surfaceState.get()
        if (current.epoch != requested.epoch || !current.valid) return@withLock null
        val mime =
            when (codecId) {
                ScrcpyProtocol.CodecH264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                ScrcpyProtocol.CodecH265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
                ScrcpyProtocol.CodecAv1 -> "video/av01"
                ScrcpyProtocol.CodecVp8 -> MediaFormat.MIMETYPE_VIDEO_VP8
                ScrcpyProtocol.CodecVp9 -> MediaFormat.MIMETYPE_VIDEO_VP9
                else -> error("Unsupported video codec: $codecId")
            }
        val decoder =
            MediaCodecPump(
                onCodecName = statsTracker::onDecoderSelected,
                onFrameSubmitted = statsTracker::onFrameSubmitted,
                onOutput = { codec, index, info ->
                    if (
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
                    ) {
                        statsTracker.onFrameDecoded()
                    }
                    val latest = surfaceState.get()
                    codec.releaseOutputBuffer(
                        index,
                        latest.valid &&
                            current.epoch == latest.epoch &&
                            latest.surface.isValid &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0,
                    )
                },
                onError = { error ->
                    while (true) {
                        val latest = surfaceState.get()
                        if (current.epoch != latest.epoch || !latest.valid) break
                        if (surfaceState.compareAndSet(latest, latest.copy(valid = false))) {
                            decoderError = error
                            stop()
                            break
                        }
                    }
                },
            )
        try {
            decoder.configure(MediaFormat.createVideoFormat(mime, width, height), current.surface)
            val latest = surfaceState.get()
            if (latest.epoch != current.epoch || !latest.valid) {
                withContext(NonCancellable) { runCatching { decoder.close() } }
                return@withLock null
            }
            config?.let { decoder.queue(it, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG) }
            pump = decoder
            decoderEpoch = current.epoch
            decoder
        } catch (error: Exception) {
            withContext(NonCancellable) { runCatching { decoder.close() } }
            throw error
        }
    }

    private fun requestKeyFrame() {
        val now = System.nanoTime()
        while (true) {
            val previous = lastKeyFrameRequestNanos.get()
            if (previous != 0L && now - previous < KeyFrameRequestIntervalNanos) return
            if (lastKeyFrameRequestNanos.compareAndSet(previous, now)) break
        }
        statsTracker.onKeyFrameRequested()
        runCatching { onRequestKeyFrame() }
    }

    private suspend fun release(expectedEpoch: Long? = null, awaitKeyFrame: Boolean = false) = decoderMutex.withLock {
        if (expectedEpoch != null && decoderEpoch != expectedEpoch) return@withLock
        if (awaitKeyFrame) awaitingKeyFrame = true
        val decoder = pump ?: return@withLock
        pump = null
        decoderEpoch = -1L
        withContext(NonCancellable) {
            try {
                decoder.close()
            } finally {
                statsTracker.onDecoderReset()
            }
        }
    }

    private data class SurfaceState(
        val surface: Surface,
        val valid: Boolean,
        val epoch: Long,
    )

    private companion object {
        const val KeyFrameRequestIntervalNanos = 1_000_000_000L
    }
}
