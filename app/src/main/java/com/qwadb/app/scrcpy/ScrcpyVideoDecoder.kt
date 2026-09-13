package com.qwadb.app.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.flyfishxu.kadb.stream.AdbStream
import com.qwadb.app.R
import com.qwadb.app.i18n.appString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class ScrcpyVideoDecoder(
    private val stream: AdbStream,
    private val codecId: Int,
    @Volatile private var surface: Surface,
    private val onVideoSize: (Int, Int) -> Unit,
) {
    @Volatile
    private var running = false

    @Volatile
    private var surfaceValid = surface.isValid

    /**
     * MediaCodec 要求 configure/start/setOutputSurface/stop 等操作在同一个线程执行。
     * 解码循环独占该专用单线程；切换 Surface 时也只标记 pendingSurface，
     * 由解码循环在自身线程上完成 setOutputSurface 重绑定，避免跨线程操作导致
     * 画面冻结、主副切换失效或 IllegalStateException 崩溃。
     */
    private val decoderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "scrcpy-video-decoder-$codecId").apply { isDaemon = true }
    }
    private val decoderDispatcher = decoderExecutor.asCoroutineDispatcher()

    @Volatile
    private var codec: MediaCodec? = null

    /** 等待解码线程应用的新 Surface（双摄切换时由 setSurface 设置）。 */
    @Volatile
    private var pendingSurface: Surface? = null

    private var videoWidth = 0
    private var videoHeight = 0
    private var configPacket: ByteArray? = null
    private var awaitingKeyFrame = true

    suspend fun start() = withContext(decoderDispatcher) {
        running = true
        val header = ByteArray(ScrcpyProtocol.PacketHeaderLength)
        try {
            while (running) {
                stream.source.readFully(header)
                if (isSessionPacket(header)) {
                    readSessionPacket(header)
                    continue
                }

                val packetHeader = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                val ptsAndFlags = packetHeader.long
                val size = packetHeader.int
                if (size !in 1..10_000_000) continue

                val data = stream.source.readByteArray(size.toLong())
                val isConfig = (ptsAndFlags and ScrcpyProtocol.PacketFlagConfig) != 0L
                val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PacketFlagKeyFrame) != 0L
                val pts = ptsAndFlags and ScrcpyProtocol.PacketPtsMask
                if (isConfig) configPacket = data

                val decoder = codec ?: configureCodecOrSkip() ?: continue
                applyPendingSurface(decoder)
                if (!isConfig && awaitingKeyFrame) {
                    if (!isKeyFrame) continue
                    awaitingKeyFrame = false
                }
                queuePacket(decoder, data, pts, isConfig)
                drain(decoder)
            }
        } finally {
            release()
            decoderExecutor.shutdown()
        }
    }

    fun setSurface(next: Surface) {
        surface = next
        surfaceValid = next.isValid
        // 只记录待切换的 Surface，由解码线程在下一帧到达时执行重绑定。
        pendingSurface = next
        awaitingKeyFrame = true
    }

    /** 在解码线程上应用挂起的 Surface 切换。 */
    private fun applyPendingSurface(decoder: MediaCodec) {
        val pending = pendingSurface ?: return
        if (!pending.isValid) {
            pendingSurface = null
            return
        }
        pendingSurface = null
        try {
            decoder.setOutputSurface(pending)
        } catch (_: Exception) {
            release()
        }
    }

    fun clearSurface() {
        surfaceValid = false
    }

    fun stop() {
        running = false
        runCatching { stream.close() }
        decoderExecutor.shutdown()
    }

    private fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    private fun isSessionPacket(header: ByteArray): Boolean {
        val ptsAndFlags = ByteBuffer.wrap(header, 0, 8).order(ByteOrder.BIG_ENDIAN).long
        return (ptsAndFlags and ScrcpyProtocol.PacketFlagSession) != 0L
    }

    private fun readSessionPacket(header: ByteArray) {
        val width = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(header, 8, 4).order(ByteOrder.BIG_ENDIAN).int
        if (width <= 0 || height <= 0) return
        if (width == videoWidth && height == videoHeight) return

        videoWidth = width
        videoHeight = height
        onVideoSize(width, height)
        release()
        awaitingKeyFrame = true
    }

    private fun configureCodecOrSkip(): MediaCodec? {
        val width = videoWidth
        val height = videoHeight
        if (width <= 0 || height <= 0 || !surfaceValid || !surface.isValid) return null

        val mime = when (codecId) {
            ScrcpyProtocol.CodecH264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            ScrcpyProtocol.CodecH265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            ScrcpyProtocol.CodecAv1 -> "video/av01"
            ScrcpyProtocol.CodecVp8 -> MediaFormat.MIMETYPE_VIDEO_VP8
            ScrcpyProtocol.CodecVp9 -> MediaFormat.MIMETYPE_VIDEO_VP9
            else -> error(appString(R.string.scrcpy_unsupported_codec, codecId.toString(16)))
        }
        return MediaCodec.createDecoderByType(mime).also { decoder ->
            decoder.configure(MediaFormat.createVideoFormat(mime, width, height), surface, null, 0)
            decoder.start()
            codec = decoder
            awaitingKeyFrame = true
            configPacket?.let { queuePacket(decoder, it, 0L, isConfig = true) }
        }
    }

    private fun queuePacket(decoder: MediaCodec, data: ByteArray, pts: Long, isConfig: Boolean) {
        val index = decoder.dequeueInputBuffer(5_000)
        if (index < 0) return
        val inputBuffer = decoder.getInputBuffer(index) ?: return
        inputBuffer.clear()
        inputBuffer.put(data)
        decoder.queueInputBuffer(
            index,
            0,
            data.size,
            pts,
            if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0,
        )
    }

    private fun drain(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = decoder.dequeueOutputBuffer(info, 0)
            when {
                index >= 0 -> {
                    val render = surfaceValid &&
                        surface.isValid &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    decoder.releaseOutputBuffer(index, render)
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> return
            }
        }
    }
}
