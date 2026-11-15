package io.github.rhythmcache.fastboot

/**
 * Parses a size variable such as `max-download-size` the way AOSP fastboot does: whitespace is trimmed,
 * a `0x`/`0X` prefix means hexadecimal, anything else is decimal. Returns null for empty, unparsable or
 * non-positive values (the caller then falls back, as AOSP's get_uint_var returns 0).
 *
 * Note an unprefixed value is decimal on purpose: "268435456" must not be read as hex.
 */
internal fun parseDeviceSize(raw: String): Long? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val v = if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toLongOrNull(16) else s.toLongOrNull(10)
    return v?.takeIf { it > 0 }
}
