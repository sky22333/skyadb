package com.sky22333.skyadb.ai

internal fun readTextPage(input: java.io.InputStream, offset: Long): String {
    require(offset >= 0) { "Invalid offset" }
    var remaining = offset
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) remaining -= skipped
        else if (input.read() < 0) return "End of file"
        else remaining--
    }
    val bytes = ByteArray(16001)
    var count = 0
    while (count < bytes.size) {
        val n = input.read(bytes, count, bytes.size - count)
        if (n < 0) break
        count += n
    }
    val more = count > 16000
    val text = decodeText(bytes, minOf(count, 16000), more)
    return text + if (more) "\n[next_offset=${offset + text.toByteArray(Charsets.UTF_8).size}]" else "\n[end of file]"
}
