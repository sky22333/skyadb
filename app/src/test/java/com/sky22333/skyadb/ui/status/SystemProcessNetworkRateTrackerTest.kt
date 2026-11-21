package com.sky22333.skyadb.ui.status

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemProcessNetworkRateTrackerTest {
    @Test
    fun holdsRateWhileNetstatsCountersAreBetweenUpdates() {
        val tracker = SystemProcessNetworkRateTracker(holdMillis = 2_500L)
        val uid = 10123

        assertEquals(0.0, tracker.update(mapOf(uid to (1000L to 500L)), 0)[uid]!!.rxBytesPerSecond, 0.001)
        assertEquals(0.0, tracker.update(mapOf(uid to (1000L to 500L)), 1_000)[uid]!!.rxBytesPerSecond, 0.001)

        val updated = tracker.update(mapOf(uid to (3000L to 1500L)), 2_000)[uid]!!
        assertEquals(1_000.0, updated.rxBytesPerSecond, 0.001)
        assertEquals(500.0, updated.txBytesPerSecond, 0.001)

        val held = tracker.update(mapOf(uid to (3000L to 1500L)), 3_000)[uid]!!
        assertEquals(1_000.0, held.rxBytesPerSecond, 0.001)
        assertEquals(500.0, held.txBytesPerSecond, 0.001)

        tracker.update(mapOf(uid to (3000L to 1500L)), 4_500)
        tracker.update(mapOf(uid to (3000L to 1500L)), 6_000)
        val expired = tracker.update(mapOf(uid to (3000L to 1500L)), 8_000)[uid]!!
        assertEquals(0.0, expired.rxBytesPerSecond, 0.001)
        assertEquals(0.0, expired.txBytesPerSecond, 0.001)
    }

    @Test
    fun holdsRateAcrossSlowProcessSamples() {
        val tracker = SystemProcessNetworkRateTracker(holdMillis = 2_500L)
        val uid = 10123

        tracker.update(mapOf(uid to (1000L to 500L)), 0)
        val changed = tracker.update(mapOf(uid to (3000L to 1500L)), 1_000)[uid]!!
        assertEquals(1_000.0, changed.rxBytesPerSecond, 0.001)

        val held = tracker.update(mapOf(uid to (3000L to 1500L)), 7_000)[uid]!!
        assertEquals(1_000.0, held.rxBytesPerSecond, 0.001)
        assertEquals(500.0, held.txBytesPerSecond, 0.001)

        val expired = tracker.update(mapOf(uid to (3000L to 1500L)), 8_000)[uid]!!
        assertEquals(0.0, expired.rxBytesPerSecond, 0.001)
        assertEquals(0.0, expired.txBytesPerSecond, 0.001)
    }
}
