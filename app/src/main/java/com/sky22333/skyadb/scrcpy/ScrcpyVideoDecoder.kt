package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScrcpyVideoDecoder(
    private val stream: AdbStream,
    private val codecId: Int,
    private var surface: Surface,
    private val onVideoSize: (Int, Int) -> Unit,
) {
    @Volatile
    private var running = false

    @Volatile
    private var surfaceValid = surface.isValid

    private var codec: MediaCodec? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var configPacket: ByteArray? = null
    private var awaitingKeyFrame = true

    suspend fun start() = withContext(Dispatchers.IO) {
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
                if (!isConfig && awaitingKeyFrame) {
                    if (!isKeyFrame) continue
                    awaitingKeyFrame = false
                }
                queuePacket(decoder, data, pts, isConfig)
                drain(decoder)
            }
        } finally {
            release()
        }
    }

    fun setSurface(next: Surface) {
        surface = next
        surfaceValid = next.isValid
        val current = codec
        if (current != null) {
            try {
                current.setOutputSurface(next)
            } catch (_: IllegalArgumentException) {
                release()
            } catch (_: IllegalStateException) {
                release()
            }
        }
        awaitingKeyFrame = true
    }

    fun clearSurface() {
        surfaceValid = false
    }

    fun stop() {
        running = false
        runCatching { stream.close() }
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
