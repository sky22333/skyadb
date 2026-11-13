package com.sky22333.skyadb.ai

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Inspect a selected ZIP without extracting files to disk or expanding unbounded data. */
internal suspend fun inspectArchive(input: InputStream, target: String? = null): String {
    val context = currentCoroutineContext()
    val result = StringBuilder()
    var total = 0L
    var count = 0
    val limit = 4L * 1024 * 1024
    val buffer = ByteArray(8192)
    val buffered = input.buffered()
    buffered.mark(4)
    val signature = ByteArray(4)
    var signatureLength = 0
    while (signatureLength < signature.size) {
        val n = buffered.read(signature, signatureLength, signature.size - signatureLength)
        if (n < 0) break
        signatureLength += n
    }
    require(signatureLength == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
        ((signature[2] == 3.toByte() && signature[3] == 4.toByte()) || (signature[2] == 5.toByte() && signature[3] == 6.toByte()))) { "Not a supported ZIP archive" }
    buffered.reset()
    return ZipInputStream(buffered).use { zip ->
        while (count < 200 && total < limit) {
            context.ensureActive()
            val entry = zip.nextEntry ?: return@use if (target == null) result.toString().ifEmpty { "Archive is empty" } else "Entry not found"
            count++
            val name = entry.name.take(1000)
            if (target == null) result.append(if (entry.isDirectory) "dir " else "file ").append(name.replace('\n', ' ')).append('\n')
            val selected = target != null && entry.name == target && !entry.isDirectory
            val output = if (selected) ByteArrayOutputStream() else null
            while (true) {
                context.ensureActive()
                val n = zip.read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
                if (n < 0) break
                total += n
                if (output != null) {
                    val keep = minOf(n, 16_001 - output.size())
                    if (keep > 0) output.write(buffer, 0, keep)
                    if (output.size() > 16_000) break
                }
                if (total >= limit) break
            }
            if (selected) {
                val bytes = requireNotNull(output).toByteArray()
                val truncated = bytes.size > 16_000 || total >= limit
                val text = decodeText(bytes, minOf(bytes.size, 16_000), truncated)
                return@use text + if (truncated) "\n[truncated]" else ""
            }
        }
        if (target == null) result.append("[archive listing truncated]").toString() else "Entry not found within scan limit"
    }
}

internal fun decodeText(bytes: ByteArray, length: Int, truncated: Boolean = false): String {
    require((0 until length).none { bytes[it] == 0.toByte() }) { "Binary content is not supported as text" }
    var end = length
    while (true) {
        try {
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, end)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            if (!truncated || length - end >= 3 || end == 0) throw e
            end--
        }
    }
}
