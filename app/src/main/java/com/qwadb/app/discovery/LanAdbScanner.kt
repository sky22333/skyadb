package com.qwadb.app.discovery

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LanAdbScanner(
    private val probe: AdbProtocolProbe = AdbProtocolProbe(),
) {
    suspend fun scan(
        hosts: List<String>,
        ports: List<Int>,
        concurrency: Int = DefaultConcurrency,
        timeoutMillis: Int = DefaultTimeoutMillis,
        onProgress: suspend (AdbScanProgress) -> Unit,
        onResult: suspend (AdbScanResult) -> Unit,
    ) = coroutineScope {
        val targets = hosts.flatMap { host -> ports.distinct().map { port -> host to port } }
        val total = targets.size
        val scanned = AtomicInteger(0)
        val lastProgressAt = AtomicLong(0L)
        val semaphore = Semaphore(concurrency.coerceIn(1, MaxConcurrency))

        onProgress(AdbScanProgress(scanned = 0, total = total))

        targets.map { (host, port) ->
            async {
                ensureActive()
                semaphore.withPermit {
                    ensureActive()
                    val result = probe.probe(host = host, port = port, timeoutMillis = timeoutMillis)
                    val current = scanned.incrementAndGet()
                    val now = System.currentTimeMillis()
                    val previous = lastProgressAt.get()
                    if (
                        current == total ||
                        current % ProgressUpdateStep == 0 ||
                        now - previous >= ProgressUpdateIntervalMillis && lastProgressAt.compareAndSet(previous, now)
                    ) {
                        onProgress(AdbScanProgress(scanned = current, total = total))
                    }
                    if (result.state.visible) {
                        onResult(result)
                    }
                }
            }
        }.awaitAll()
    }

    private companion object {
        const val DefaultConcurrency = 48
        const val MaxConcurrency = 64
        const val DefaultTimeoutMillis = 600
        const val ProgressUpdateStep = 8
        const val ProgressUpdateIntervalMillis = 120L
    }
}
