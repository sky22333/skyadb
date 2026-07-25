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
import kotlinx.coroutines.withContext

/**
 * 按官方 demux 协议播放 scrcpy 音频流；失败时由调用方忽略，不影响视频。
 */
class ScrcpyAudioDecoder(
    private val stream: AdbStream,
    private val codecId: Int,
) {
    @Volatile
    private var running = false

    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var configPacket: ByteArray? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        val header = ByteArray(ScrcpyProtocol.PacketHeaderLength)
        try {
            if (codecId == ScrcpyProtocol.CodecRaw) {
                ensureTrack(DefaultSampleRate, 2)
            }
            while (running) {
                stream.source.readFully(header)
                val packetHeader = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                val ptsAndFlags = packetHeader.long
                val size = packetHeader.int
                if (size !in 1..1_000_000) continue

                val data = stream.source.readByteArray(size.toLong())
                val isConfig = (ptsAndFlags and ScrcpyProtocol.PacketFlagConfig) != 0L
                val pts = ptsAndFlags and ScrcpyProtocol.PacketPtsMask
                if (isConfig) {
                    configPacket = data
                    continue
                }

                if (codecId == ScrcpyProtocol.CodecRaw) {
                    audioTrack?.write(data, 0, data.size)
                    continue
                }

                val decoder = codec ?: configureCodecOrSkip() ?: continue
                queuePacket(decoder, data, pts)
                drain(decoder)
            }
        } finally {
            release()
        }
    }

    fun stop() {
        running = false
        runCatching { stream.close() }
    }

    private fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    private fun configureCodecOrSkip(): MediaCodec? {
        val mime = when (codecId) {
            ScrcpyProtocol.CodecOpus -> MediaFormat.MIMETYPE_AUDIO_OPUS
            ScrcpyProtocol.CodecAac -> MediaFormat.MIMETYPE_AUDIO_AAC
            ScrcpyProtocol.CodecFlac -> MediaFormat.MIMETYPE_AUDIO_FLAC
            else -> return null
        }
        val format = MediaFormat.createAudioFormat(mime, DefaultSampleRate, 2)
        configPacket?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
        codec = decoder
        ensureTrack(DefaultSampleRate, 2)
        configPacket?.let { csd ->
            val index = decoder.dequeueInputBuffer(5_000)
            if (index >= 0) {
                decoder.getInputBuffer(index)?.apply {
                    clear()
                    put(csd)
                }
                decoder.queueInputBuffer(index, 0, csd.size, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
            }
        }
        return decoder
    }

    private fun ensureTrack(sampleRate: Int, channelCount: Int) {
        if (audioTrack != null) return
        val track = buildTrack(sampleRate, channelCount)
        audioTrack = track
        track.play()
    }

    private fun buildTrack(sampleRate: Int, channelCount: Int): AudioTrack {
        val channelMask = if (channelCount >= 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate / 10)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun queuePacket(decoder: MediaCodec, data: ByteArray, pts: Long) {
        val index = decoder.dequeueInputBuffer(5_000)
        if (index < 0) return
        val inputBuffer = decoder.getInputBuffer(index) ?: return
        inputBuffer.clear()
        inputBuffer.put(data)
        decoder.queueInputBuffer(index, 0, data.size, pts, 0)
    }

    private fun drain(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = decoder.dequeueOutputBuffer(info, 0)
            when {
                index >= 0 -> {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        val output = decoder.getOutputBuffer(index)
                        val track = audioTrack
                        if (output != null && track != null) {
                            val pcm = ByteArray(info.size)
                            output.position(info.offset)
                            output.get(pcm)
                            track.write(pcm, 0, pcm.size)
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    runCatching { audioTrack?.release() }
                    audioTrack = null
                    ensureTrack(sampleRate, channelCount)
                }
                else -> return
            }
        }
    }

    private companion object {
        const val DefaultSampleRate = 48_000
    }
}
