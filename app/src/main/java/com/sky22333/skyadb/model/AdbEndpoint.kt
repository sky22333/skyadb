package com.sky22333.skyadb.model

import java.net.IDN
import java.util.Locale

/** Formats and compares ADB network endpoints without losing IPv6 information. */
data class AdbEndpoint(
    val host: String,
    val port: Int,
) {
    val authority: String = AdbEndpoint.format(host, port)

    override fun toString(): String = authority

    companion object {
        /** Removes one pair of IPv6 brackets before handing the host to a socket. */
        fun hostForSocket(host: String): String {
            val trimmed = host.trim()
            return if (trimmed.length >= 2 && trimmed.first() == '[' && trimmed.last() == ']') {
                trimmed.substring(1, trimmed.length - 1)
            } else {
                trimmed
            }
        }

        /** IPv4 is host:port; IPv6 is always bracketed as [host]:port. */
        fun format(host: String, port: Int): String {
            val socketHost = hostForSocket(host)
            return if (socketHost.contains(':')) "[$socketHost]:$port" else "$socketHost:$port"
        }

        /** Accepts an IPv4/IPv6 literal or DNS host name and returns the socket host. */
        fun normalizeHost(value: String): String? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.first() == '[' || trimmed.last() == ']') {
                if (trimmed.length < 2 || trimmed.first() != '[' || trimmed.last() != ']') return null
                val inner = trimmed.substring(1, trimmed.length - 1)
                if (inner.isEmpty() || '[' in inner || ']' in inner || ':' !in inner) return null
                return inner.takeIf { isValidIp(it) }
            }
            if ('[' in trimmed || ']' in trimmed || ':' in trimmed) {
                return trimmed.takeIf { isValidIp(it) }
            }
            if (isValidIp(trimmed)) return trimmed
            if (resemblesIpv4Literal(trimmed)) return null
            return normalizeDnsHost(trimmed)
        }

        fun isValidIp(value: String): Boolean = parseComparableHost(value) != null

        fun isValidHost(value: String): Boolean = normalizeHost(value) != null

        /** Compares hosts while normalizing IP and DNS representations. */
        fun sameHost(left: String, right: String): Boolean {
            val leftHost = parseComparableHost(left)
            val rightHost = parseComparableHost(right)
            if (leftHost != null || rightHost != null) {
                if (leftHost == null || rightHost == null) return false
                if (leftHost.addressKey != rightHost.addressKey) return false
                if (leftHost.zone != null && rightHost.zone != null) {
                    return leftHost.zone.equals(rightHost.zone, ignoreCase = true)
                }
                return true
            }
            val normalizedLeft = normalizeHost(left)
            val normalizedRight = normalizeHost(right)
            if (normalizedLeft != null && normalizedRight != null) return normalizedLeft == normalizedRight
            return hostForSocket(left).equals(hostForSocket(right), ignoreCase = true)
        }

        /** Compares already formatted authorities such as 192.168.1.2:5555 or [::1]:5555. */
        fun sameAuthority(left: String, right: String): Boolean {
            val leftEndpoint = parseAuthority(left)
            val rightEndpoint = parseAuthority(right)
            if (leftEndpoint == null || rightEndpoint == null) return left.trim() == right.trim()
            return leftEndpoint.port == rightEndpoint.port && sameHost(leftEndpoint.host, rightEndpoint.host)
        }

        private data class ComparableHost(
            val addressKey: String,
            val zone: String?,
        )

        private fun normalizeDnsHost(value: String): String? {
            if (value.any { it == '/' || it == '\\' || it.isWhitespace() }) return null
            val withoutTrailingDot = if (value.endsWith('.')) value.dropLast(1) else value
            if (withoutTrailingDot.isEmpty()) return null
            val ascii = try {
                IDN.toASCII(withoutTrailingDot, IDN.USE_STD3_ASCII_RULES)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (ascii.isEmpty() || ascii.length > MAX_DNS_HOST_LENGTH) return null
            val labels = ascii.split('.')
            if (labels.any { !isValidDnsLabel(it) }) return null
            return ascii.lowercase(Locale.ROOT)
        }

        private fun isValidDnsLabel(label: String): Boolean =
            label.length in 1..MAX_DNS_LABEL_LENGTH && label.first() != '-' && label.last() != '-' &&
                label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }

        private fun resemblesIpv4Literal(value: String): Boolean =
            value.isNotEmpty() && value.all { it in '0'..'9' || it == '.' }

        private fun parseComparableHost(value: String): ComparableHost? {
            val socketHost = hostForSocket(value)
            if (socketHost.isEmpty()) return null
            val zoneSeparator = socketHost.indexOf('%')
            val address = if (zoneSeparator < 0) socketHost else socketHost.substring(0, zoneSeparator)
            val zone = if (zoneSeparator < 0) {
                null
            } else {
                if (socketHost.indexOf('%', zoneSeparator + 1) >= 0) return null
                socketHost.substring(zoneSeparator + 1).takeIf { isValidZone(it) } ?: return null
            }
            if (address.isEmpty()) return null
            parseIpv4(address)?.let { groups ->
                if (zone != null) return null
                return ComparableHost("4:${groups.joinToString(".")}", null)
            }
            parseIpv6(address)?.let { groups ->
                return ComparableHost("6:${groups.joinToString(":")}", zone)
            }
            return null
        }

        private fun parseAuthority(value: String): AdbEndpoint? {
            val input = value.trim()
            if (input.isEmpty()) return null
            if (input.startsWith('[')) {
                val close = input.indexOf(']')
                if (close <= 1) return null
                val host = normalizeHost(input.substring(0, close + 1)) ?: return null
                val suffix = input.substring(close + 1)
                if (!suffix.startsWith(':')) return null
                val port = suffix.substring(1).toIntOrNull() ?: return null
                if (port !in 1..65535) return null
                return AdbEndpoint(host, port)
            }
            val separator = input.indexOf(':')
            if (separator <= 0 || separator != input.lastIndexOf(':')) return null
            val host = normalizeHost(input.substring(0, separator)) ?: return null
            val port = input.substring(separator + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return AdbEndpoint(host, port)
        }

        private fun isValidZone(zone: String): Boolean = zone.isNotEmpty() && zone.all {
            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' || it == '.'
        }

        private fun parseIpv4(value: String): IntArray? {
            val parts = value.split('.')
            if (parts.size != 4) return null
            val groups = IntArray(4)
            for (index in parts.indices) {
                val part = parts[index]
                if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
                groups[index] = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            }
            return groups
        }

        private fun parseIpv6(value: String): IntArray? {
            if (value.isEmpty()) return null
            val compression = value.indexOf("::")
            if (compression >= 0 && compression != value.lastIndexOf("::")) return null

            fun parseSection(section: String): List<Int>? {
                if (section.isEmpty()) return emptyList()
                val parts = section.split(':')
                if (parts.any { it.isEmpty() }) return null
                val groups = ArrayList<Int>(parts.size)
                parts.forEachIndexed { index, part ->
                    if (part.contains('.')) {
                        if (index != parts.lastIndex) return null
                        val ipv4 = parseIpv4(part) ?: return null
                        groups.add((ipv4[0] shl 8) or ipv4[1])
                        groups.add((ipv4[2] shl 8) or ipv4[3])
                    } else {
                        if (part.length !in 1..4 || part.any { !it.isHexDigit() }) return null
                        groups.add(part.toInt(16))
                    }
                }
                return groups
            }

            if (compression < 0) {
                val groups = parseSection(value) ?: return null
                return groups.takeIf { it.size == 8 }?.toIntArray()
            }

            val left = parseSection(value.substring(0, compression)) ?: return null
            val right = parseSection(value.substring(compression + 2)) ?: return null
            if (left.size + right.size >= 8) return null
            return IntArray(8).also { result ->
                left.forEachIndexed { index, group -> result[index] = group }
                val rightOffset = 8 - right.size
                right.forEachIndexed { index, group -> result[rightOffset + index] = group }
            }
        }

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private const val MAX_DNS_HOST_LENGTH = 253
        private const val MAX_DNS_LABEL_LENGTH = 63
    }
}
