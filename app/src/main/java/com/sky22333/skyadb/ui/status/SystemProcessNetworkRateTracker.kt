package com.sky22333.skyadb.ui.status

internal class SystemProcessNetworkRateTracker(
    private val holdMillis: Long = 2_500L,
    private val maxHoldMillis: Long = 30_000L,
) {
    data class Rate(
        val rxBytesPerSecond: Double,
        val txBytesPerSecond: Double,
    )

    private data class Baseline(
        val rxBytes: Long,
        val txBytes: Long,
        val sampledAtMillis: Long,
        val rate: Rate,
    )

    private val baselines = HashMap<Int, Baseline>()
    private var previousUpdateMillis: Long? = null
    private var observedSampleIntervalMillis: Long = 0L

    fun update(current: Map<Int, Pair<Long, Long>>, nowMillis: Long): Map<Int, Rate> {
        previousUpdateMillis?.let { previous ->
            observedSampleIntervalMillis = (nowMillis - previous).coerceAtLeast(0L)
        }
        previousUpdateMillis = nowMillis
        val effectiveHoldMillis = maxOf(
            holdMillis,
            (observedSampleIntervalMillis * 2 + 1_000L).coerceAtMost(maxHoldMillis),
        )
        val result = HashMap<Int, Rate>(current.size)
        current.forEach { (uid, bytes) ->
            val rxBytes = bytes.first.coerceAtLeast(0)
            val txBytes = bytes.second.coerceAtLeast(0)
            val previous = baselines[uid]
            if (
                previous == null ||
                rxBytes < previous.rxBytes ||
                txBytes < previous.txBytes
            ) {
                val rate = if (
                    previous != null &&
                    nowMillis - previous.sampledAtMillis <= effectiveHoldMillis
                ) {
                    previous.rate
                } else {
                    Rate(0.0, 0.0)
                }
                baselines[uid] = Baseline(rxBytes, txBytes, nowMillis, rate)
                result[uid] = rate
                return@forEach
            }
            if (rxBytes != previous.rxBytes || txBytes != previous.txBytes) {
                val elapsedSeconds = (nowMillis - previous.sampledAtMillis).coerceAtLeast(1L) / 1000.0
                val rate = Rate(
                    rxBytesPerSecond = (rxBytes - previous.rxBytes) / elapsedSeconds,
                    txBytesPerSecond = (txBytes - previous.txBytes) / elapsedSeconds,
                )
                baselines[uid] = Baseline(rxBytes, txBytes, nowMillis, rate)
                result[uid] = rate
                return@forEach
            }
            if (nowMillis - previous.sampledAtMillis <= effectiveHoldMillis) {
                result[uid] = previous.rate
            } else {
                val rate = Rate(0.0, 0.0)
                baselines[uid] = Baseline(rxBytes, txBytes, nowMillis, rate)
                result[uid] = rate
            }
        }
        baselines.forEach { (uid, baseline) ->
            if (uid !in current && nowMillis - baseline.sampledAtMillis <= effectiveHoldMillis) {
                result[uid] = baseline.rate
            }
        }
        val retainedUids = HashSet<Int>(current.keys)
        retainedUids.addAll(result.keys)
        baselines.keys.retainAll(retainedUids)
        return result
    }

    fun reset() {
        baselines.clear()
        previousUpdateMillis = null
        observedSampleIntervalMillis = 0L
    }
}
