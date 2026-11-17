package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** One codec owner thread. Output is delivered even while the network reader is blocked. */
internal class MediaCodecPump(
    private val onCodecName: (String) -> Unit = {},
    private val onFormat: (MediaFormat) -> Unit = {},
    private val onOutput: (MediaCodec, Int, MediaCodec.BufferInfo) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val thread =
        HandlerThread("skyadb-codec", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private var codec: MediaCodec? = null
    private val inputIndices = ArrayDeque<Int>()
    private var pending: Pending? = null
    private var failure: Throwable? = null
    @Volatile private var closed = false
    private val released = CompletableDeferred<Unit>()

    suspend fun configure(format: MediaFormat, surface: Surface? = null) = execute {
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        val decoder = MediaCodec.createDecoderByType(mime)
        codec = decoder // Ownership starts before configure, including its failure path.
        runCatching { onCodecName(decoder.name) }
        if (
            surface != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                decoder.codecInfo
                    .getCapabilitiesForType(mime)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
        ) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        decoder.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    if (closed || failure != null) return
                    inputIndices.addLast(index)
                    feed()
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    if (closed || failure != null) return
                    try {
                        onOutput(codec, index, info)
                    } catch (error: Exception) {
                        fail(error)
                    }
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (!closed)
                        try {
                            onFormat(format)
                        } catch (error: Exception) {
                            fail(error)
                        }
                }

                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) =
                    fail(error)
            },
            handler,
        )
        decoder.configure(format, surface, null, 0)
        decoder.start()
    }

    /** Await submission: at most one encoded packet is retained, with natural backpressure. */
    suspend fun queue(data: ByteArray, pts: Long, flags: Int = 0) {
        while (true) {
            val submitted = CompletableDeferred<Unit>()
            val accepted = CompletableDeferred<Boolean>()
            check(!closed) { "Decoder closed" }
            check(
                handler.post {
                    val error = failure
                    when {
                        closed -> {
                            accepted.completeExceptionally(IllegalStateException("Decoder closed"))
                        }
                        error != null -> {
                            accepted.completeExceptionally(error)
                        }
                        pending != null -> accepted.complete(false)
                        else -> {
                            pending = Pending(data, pts, flags, submitted)
                            accepted.complete(true)
                            feed()
                        }
                    }
                }
            ) {
                "Decoder thread stopped"
            }
            if (accepted.await()) {
                submitted.await()
                return
            }
            // A configuration/key packet must not be dropped when the single slot is occupied.
            delay(1)
        }
    }

    /**
     * Submit only when an input buffer is available when the codec owner thread handles the call.
     * Unlike [queue], this never waits for a codec buffer and lets the caller drop a frame instead.
     */
    suspend fun tryQueue(data: ByteArray, pts: Long, flags: Int = 0): Boolean {
        val accepted = CompletableDeferred<Boolean>()
        check(!closed) { "Decoder closed" }
        check(
            handler.post {
                val error = failure
                when {
                    closed ->
                        accepted.completeExceptionally(IllegalStateException("Decoder closed"))
                    error != null -> accepted.completeExceptionally(error)
                    pending != null || inputIndices.isEmpty() -> accepted.complete(false)
                    else -> {
                        try {
                            submitImmediately(data, pts, flags)
                            accepted.complete(true)
                        } catch (error: Exception) {
                            fail(error)
                            accepted.completeExceptionally(error)
                        }
                    }
                }
            }
        ) {
            "Decoder thread stopped"
        }
        return accepted.await()
    }

    suspend fun close() {
        if (!closed) {
            closed = true
            val posted =
                handler.post {
                    pending?.submitted?.completeExceptionally(IllegalStateException("Decoder closed"))
                    pending = null
                    inputIndices.clear()
                    runCatching { codec?.stop() }
                    runCatching { codec?.release() }
                    codec = null
                    released.complete(Unit)
                    thread.quitSafely()
                }
            if (!posted) {
                runCatching { codec?.release() }
                codec = null
                released.complete(Unit)
            }
        }
        released.await()
    }

    private fun feed() {
        val packet = pending ?: return
        if (inputIndices.isEmpty()) return
        try {
            submitImmediately(packet.data, packet.pts, packet.flags)
            pending = null
            packet.submitted.complete(Unit)
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun submitImmediately(data: ByteArray, pts: Long, flags: Int) {
        val decoder = requireNotNull(codec)
        val index = inputIndices.removeFirst()
        val buffer = requireNotNull(decoder.getInputBuffer(index))
        check(buffer.capacity() >= data.size) {
            "Encoded packet exceeds decoder input capacity"
        }
        buffer.clear()
        buffer.put(data)
        decoder.queueInputBuffer(index, 0, data.size, pts, flags)
    }

    private fun fail(error: Throwable) {
        if (closed || failure != null) return
        failure = error
        pending?.submitted?.completeExceptionally(error)
        pending = null
        onError(error)
    }

    private suspend fun execute(block: () -> Unit) {
        val result = CompletableDeferred<Unit>()
        check(
            handler.post {
                try {
                    block()
                    result.complete(Unit)
                } catch (error: Exception) {
                    result.completeExceptionally(error)
                }
            }
        )
        result.await()
    }

    private data class Pending(
        val data: ByteArray,
        val pts: Long,
        val flags: Int,
        val submitted: CompletableDeferred<Unit>,
    )
}
