package com.sky22333.skyadb.adb

import com.sky22333.skyadb.model.OperationStatus
import okio.Buffer
import okio.Source
import okio.Timeout

/**
 * 统计已读字节并节流回调，供 Kadb push/install 的 Source 重载使用。
 */
class CountingSource(
    private val delegate: Source,
    private val totalBytes: Long,
    private val onProgress: (transferred: Long, total: Long) -> Unit,
) : Source {
    private var transferred = 0L
    private var lastProgressAt = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = delegate.read(sink, byteCount)
        if (read > 0L) {
            transferred += read
            val now = System.currentTimeMillis()
            val finished = totalBytes > 0L && transferred >= totalBytes
            if (finished || now - lastProgressAt >= ProgressUpdateIntervalMillis) {
                lastProgressAt = now
                onProgress(transferred.coerceAtMost(totalBytes.coerceAtLeast(transferred)), totalBytes)
            }
        }
        return read
    }

    override fun close() = delegate.close()

    override fun timeout(): Timeout = delegate.timeout()

    private companion object {
        const val ProgressUpdateIntervalMillis = 150L
    }
}

internal fun adbFailureSuggestion(error: Throwable, fallback: String): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?: return fallback
    return raw
        .removePrefix("Install failed: ")
        .removePrefix("Sync failed: ")
        .trim()
        .ifBlank { fallback }
}

fun adbTransferRunning(
    transferringLabel: String,
    finishingLabel: String,
    transferred: Long,
    total: Long,
): OperationStatus.Running {
    if (total <= 0L || transferred >= total) {
        return OperationStatus.Running(finishingLabel)
    }
    val progress = (transferred.toFloat() / total).coerceIn(0f, 1f)
    return OperationStatus.Running(
        text = "$transferringLabel ${(progress * 100).toInt()}%",
        progress = progress,
    )
}
