package com.sky22333.skyadb.scrcpy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.flyfishxu.kadb.stream.AdbStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Independent codec callbacks and direct PCM buffers; no per-frame PCM array copy. */
class ScrcpyAudioDecoder(private val stream: AdbStream, private val codecId: Int) {
    @Volatile private var running = true
    private var pump: MediaCodecPump? = null
    private var track: AudioTrack? = null
    private var sampleRate = 0
    private var channelCount = 0

    suspend fun start() =
        withContext(Dispatchers.IO) {
            val header = ByteArray(ScrcpyProtocol.PacketHeaderLength)
            val fields = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            try {
                // 0/1 are scrcpy's disabled/failed audio handshake sentinels, not codecs.
                if (codecId == 0) return@withContext
                check(codecId != 1) { "Device audio capture failed" }
                while (running) {
                    currentCoroutineContext().ensureActive()
                    stream.source.readFully(header)
                    fields.rewind()
                    val ptsAndFlags = fields.long
                    val size = fields.int
                    require(size in 1..1_000_000) { "Invalid audio packet length: $size" }
                    val data = stream.source.readByteArray(size.toLong())
                    if (codecId == ScrcpyProtocol.CodecRaw) {
                        ensureTrack(DefaultSampleRate, 2)
                        val written = track!!.write(data, 0, data.size)
                        check(written == data.size) { "Audio write failed: $written" }
                        continue
                    }
                    val isConfig = (ptsAndFlags and ScrcpyProtocol.PacketFlagConfig) != 0L
                    if (pump == null) configure(if (isConfig) data else null)
                    if (!isConfig) pump!!.queue(data, ptsAndFlags and ScrcpyProtocol.PacketPtsMask)
                }
            } finally {
                withContext(NonCancellable) { pump?.close() }
                pump = null
                runCatching { track?.stop() }
                runCatching { track?.release() }
                track = null
                runCatching { stream.close() }
            }
        }

    fun stop() {
        running = false
        runCatching { stream.close() }
    }

    private suspend fun configure(config: ByteArray?) {
        val mime =
            when (codecId) {
                ScrcpyProtocol.CodecAac -> MediaFormat.MIMETYPE_AUDIO_AAC
                ScrcpyProtocol.CodecOpus -> MediaFormat.MIMETYPE_AUDIO_OPUS
                ScrcpyProtocol.CodecFlac -> MediaFormat.MIMETYPE_AUDIO_FLAC
                else -> error("Unsupported audio codec: $codecId")
            }
        val format = MediaFormat.createAudioFormat(mime, DefaultSampleRate, 2)
        config?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        val decoder =
            MediaCodecPump(
                onFormat = { output ->
                    ensureTrack(
                        output.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        output.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    )
                },
                onOutput = { codec, index, info ->
                    try {
                        if (
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                info.size > 0
                        ) {
                            val pcm = requireNotNull(codec.getOutputBuffer(index))
                            pcm.position(info.offset)
                            pcm.limit(info.offset + info.size)
                            while (running && pcm.hasRemaining()) {
                                val written =
                                    track!!.write(pcm, pcm.remaining(), AudioTrack.WRITE_BLOCKING)
                                check(written > 0) { "Audio write failed: $written" }
                            }
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                },
                onError = { stop() },
            )
        pump = decoder
        decoder.configure(format)
    }

    private fun ensureTrack(rate: Int, channels: Int) {
        if (track != null && sampleRate == rate && channelCount == channels) return
        runCatching { track?.stop() }
        runCatching { track?.release() }
        val mask =
            if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val bufferSize = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        require(bufferSize > 0) { "Unsupported audio output format" }
        track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()
                .also { it.play() }
        sampleRate = rate
        channelCount = channels
    }

    private companion object {
        const val DefaultSampleRate = 48_000
    }
}
