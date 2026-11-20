package com.sky22333.skyadb.scrcpy

import com.sky22333.skyadb.model.MirrorConnectionMode
import com.sky22333.skyadb.model.MirrorTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorStatsTrackerTest {
    @Test
    fun snapshotReportsCountersRatesAndThroughput() {
        val tracker = MirrorStatsTracker(MirrorTransport.Wifi, MirrorConnectionMode.Dedicated)
        val start = 1_000_000_000L

        tracker.snapshot(nowNanos = start)
        tracker.onPacketReceived(sizeBytes = 1_000, ptsUs = 0L, nowNanos = start)
        tracker.onFrameSubmitted()
        tracker.onDecoderSelected("c2.android.avc.decoder")
        tracker.onFrameDecoded()
        tracker.onFrameDropped()
        tracker.onKeyFrameRequested()
        tracker.onPacketReceived(
            sizeBytes = 1_000,
            ptsUs = 1_000_000L,
            nowNanos = start + 1_000_000_000L,
        )
        tracker.onFrameSubmitted()
        tracker.onFrameDecoded()

        val stats = tracker.snapshot(nowNanos = start + 1_000_000_000L)

        assertEquals(2L, stats.receivedFrames)
        assertEquals(2L, stats.decodedFrames)
        assertEquals(1L, stats.droppedFrames)
        assertEquals(2.0, stats.receiveFps, 0.001)
        assertEquals(2.0, stats.decodeFps, 0.001)
        assertEquals(2_000.0, stats.bytesPerSecond, 0.001)
        assertEquals(0, stats.queueDepth)
        assertEquals(0L, stats.latencyGrowthMs)
        assertEquals(1L, stats.keyFrameRequests)
        assertEquals(MirrorTransport.Wifi, stats.transport)
        assertEquals(MirrorConnectionMode.Dedicated, stats.connectionMode)
        assertEquals("c2.android.avc.decoder", stats.decoderName)
    }

    @Test
    fun resetLatencyBaseline_restartsPipelineMeasurement() {
        val tracker = MirrorStatsTracker()
        val start = 1_000_000_000L

        tracker.onPacketReceived(sizeBytes = 1, ptsUs = 10_000_000L, nowNanos = start)
        tracker.resetLatencyBaseline()
        tracker.onPacketReceived(
            sizeBytes = 1,
            ptsUs = 11_000_000L,
            nowNanos = start + 1_000_000_000L,
        )

        assertEquals(0L, tracker.snapshot(nowNanos = start + 1_000_000_000L).latencyGrowthMs)
    }

    @Test
    fun latencyGrowthTracksArrivalBehindMediaTimeline() {
        val tracker = MirrorStatsTracker()
        val start = 1_000_000_000L

        tracker.onPacketReceived(sizeBytes = 1, ptsUs = 0L, nowNanos = start)
        tracker.onPacketReceived(
            sizeBytes = 1,
            ptsUs = 1_000_000L,
            nowNanos = start + 1_500_000_000L,
        )

        assertEquals(500L, tracker.snapshot(nowNanos = start + 1_500_000_000L).latencyGrowthMs)
    }

    @Test
    fun queueDepthTracksSubmittedFramesNotYetDecoded() {
        val tracker = MirrorStatsTracker()

        tracker.onFrameSubmitted()
        tracker.onFrameSubmitted()
        assertEquals(2, tracker.snapshot().queueDepth)

        tracker.onFrameDecoded()
        assertEquals(1, tracker.snapshot().queueDepth)

        tracker.onDecoderReset()
        assertEquals(0, tracker.snapshot().queueDepth)
    }
}
