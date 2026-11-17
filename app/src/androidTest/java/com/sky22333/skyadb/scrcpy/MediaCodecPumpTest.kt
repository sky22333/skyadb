package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaCodecList
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaCodecPumpTest {
    /** The final frame must leave the decoder without a subsequent network packet. */
    @Test
    fun singleFrameIsOutputWhileProducerIsIdle() = runBlocking {
        val (format, frame) = encodeFrame()
        val rendered = CompletableDeferred<Unit>()
        val decoder =
            MediaCodecPump(
                onOutput = { codec, index, info ->
                    codec.releaseOutputBuffer(index, false)
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)
                        rendered.complete(Unit)
                },
                onError = { rendered.completeExceptionally(it) },
            )
        try {
            decoder.configure(format)
            decoder.queue(frame, 1_000L)
            withTimeout(3_000L) { rendered.await() }
        } finally {
            decoder.close()
        }
    }

    @Test
    fun tryQueueAcceptsFrameWhenInputIsAvailable() = runBlocking {
        val (format, frame) = encodeFrame()
        val rendered = CompletableDeferred<Unit>()
        val decoder =
            MediaCodecPump(
                onOutput = { codec, index, info ->
                    codec.releaseOutputBuffer(index, false)
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)
                        rendered.complete(Unit)
                },
                onError = { rendered.completeExceptionally(it) },
            )
        try {
            decoder.configure(format)
            val accepted =
                withTimeout(3_000L) {
                    while (!decoder.tryQueue(frame, 1_000L)) {
                        delay(10)
                    }
                    true
                }
            assertTrue(accepted)
            withTimeout(3_000L) { rendered.await() }
        } finally {
            decoder.close()
        }
    }

    private fun encodeFrame(): Pair<MediaFormat, ByteArray> {
        val softwareEncoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.first {
            it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AVC) &&
                (it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google."))
        }
        val encoder = MediaCodec.createByCodecName(softwareEncoder.name)
        try {
            val format =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 128, 128).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val input = encoder.dequeueInputBuffer(1_000_000)
            check(input >= 0)
            encoder.getInputBuffer(input)!!.put(ByteArray(128 * 128 * 3 / 2) { 128.toByte() })
            encoder.queueInputBuffer(
                input,
                0,
                128 * 128 * 3 / 2,
                1_000L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                val output = encoder.dequeueOutputBuffer(info, 100_000)
                if (output < 0) continue
                try {
                    if (info.size == 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0)
                        continue
                    val source = encoder.getOutputBuffer(output)!!
                    source.position(info.offset)
                    source.limit(info.offset + info.size)
                    val frame = ByteArray(info.size).also { source.get(it) }
                    val decodedFormat =
                        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 128, 128)
                    for (key in listOf("csd-0", "csd-1")) {
                        encoder.outputFormat.getByteBuffer(key)?.let { csd ->
                            val bytes = ByteArray(csd.remaining()).also { csd.duplicate().get(it) }
                            decodedFormat.setByteBuffer(key, ByteBuffer.wrap(bytes))
                        }
                    }
                    return decodedFormat to frame
                } finally {
                    encoder.releaseOutputBuffer(output, false)
                }
            }
            error("Encoder did not produce a test frame")
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
        }
    }
}
