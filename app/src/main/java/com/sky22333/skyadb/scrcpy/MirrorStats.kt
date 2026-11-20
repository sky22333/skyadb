package com.sky22333.skyadb.scrcpy

import com.sky22333.skyadb.model.MirrorConnectionMode
import com.sky22333.skyadb.model.MirrorTransport

data class MirrorStats(
    val transport: MirrorTransport = MirrorTransport.Wifi,
    val connectionMode: MirrorConnectionMode = MirrorConnectionMode.Shared,
    val decoderName: String = "",
    val receivedFrames: Long = 0,
    val decodedFrames: Long = 0,
    val droppedFrames: Long = 0,
    val receiveFps: Double = 0.0,
    val decodeFps: Double = 0.0,
    val bytesPerSecond: Double = 0.0,
    val queueDepth: Int = 0,
    val latencyGrowthMs: Long = 0,
    val keyFrameRequests: Long = 0,
)

/** Thread-safe counters shared by the ADB reader and MediaCodec callback thread. */
class MirrorStatsTracker(
    private val transport: MirrorTransport = MirrorTransport.Wifi,
    private val connectionMode: MirrorConnectionMode = MirrorConnectionMode.Shared,
) {
    private val lock = Any()
    private var decoderName = ""
    private var receivedFrames = 0L
    private var submittedFrames = 0L
    private var decodedFrames = 0L
    private var droppedFrames = 0L
    private var receivedBytes = 0L
    private var keyFrameRequests = 0L

    private var sampleNanos = System.nanoTime()
    private var sampleReceivedFrames = 0L
    private var sampleDecodedFrames = 0L
    private var sampleReceivedBytes = 0L

    private var latencyBaselinePtsUs: Long? = null
    private var latencyBaselineNanos = 0L
    private var latencyGrowthMs = 0L

    fun onPacketReceived(
        sizeBytes: Int,
        ptsUs: Long,
        nowNanos: Long = System.nanoTime(),
    ) {
        synchronized(lock) {
            receivedFrames++
            receivedBytes += sizeBytes
            val baselinePts = latencyBaselinePtsUs
            if (baselinePts == null) {
                latencyBaselinePtsUs = ptsUs
                latencyBaselineNanos = nowNanos
                latencyGrowthMs = 0L
            } else {
                val mediaElapsedUs = ptsUs - baselinePts
                val wallElapsedUs = (nowNanos - latencyBaselineNanos) / 1_000L
                latencyGrowthMs = ((wallElapsedUs - mediaElapsedUs) / 1_000L).coerceAtLeast(0L)
            }
        }
    }

    fun onFrameDecoded() {
        synchronized(lock) { decodedFrames++ }
    }

    fun onFrameSubmitted() {
        synchronized(lock) { submittedFrames++ }
    }

    fun onDecoderSelected(name: String) {
        synchronized(lock) {
            if (name.isNotBlank()) decoderName = name
        }
    }

    fun onDecoderReset() {
        synchronized(lock) {
            submittedFrames = decodedFrames
        }
    }

    fun onFrameDropped() {
        synchronized(lock) { droppedFrames++ }
    }

    fun onKeyFrameRequested() {
        synchronized(lock) { keyFrameRequests++ }
    }

    fun resetLatencyBaseline() {
        synchronized(lock) {
            latencyBaselinePtsUs = null
            latencyBaselineNanos = 0L
            latencyGrowthMs = 0L
        }
    }

    fun snapshot(nowNanos: Long = System.nanoTime()): MirrorStats {
        synchronized(lock) {
            val elapsedSeconds = ((nowNanos - sampleNanos) / 1_000_000_000.0).coerceAtLeast(0.0)
            val receiveFps =
                if (elapsedSeconds > 0.0) {
                    (receivedFrames - sampleReceivedFrames) / elapsedSeconds
                } else {
                    0.0
                }
            val decodeFps =
                if (elapsedSeconds > 0.0) {
                    (decodedFrames - sampleDecodedFrames) / elapsedSeconds
                } else {
                    0.0
                }
            val bytesPerSecond =
                if (elapsedSeconds > 0.0) {
                    (receivedBytes - sampleReceivedBytes) / elapsedSeconds
                } else {
                    0.0
                }

            sampleNanos = nowNanos
            sampleReceivedFrames = receivedFrames
            sampleDecodedFrames = decodedFrames
            sampleReceivedBytes = receivedBytes

            return MirrorStats(
                transport = transport,
                connectionMode = connectionMode,
                decoderName = decoderName,
                receivedFrames = receivedFrames,
                decodedFrames = decodedFrames,
                droppedFrames = droppedFrames,
                receiveFps = receiveFps,
                decodeFps = decodeFps,
                bytesPerSecond = bytesPerSecond,
                queueDepth = (submittedFrames - decodedFrames).coerceAtLeast(0L).toInt(),
                latencyGrowthMs = latencyGrowthMs,
                keyFrameRequests = keyFrameRequests,
            )
        }
    }
}
