package com.sky22333.skyadb.qr

import com.sky22333.skyadb.model.AdbEndpoint
import java.net.URLDecoder
import java.net.URLEncoder

sealed interface QrPayload {
    data class WirelessPairing(val serviceName: String, val pairingCode: String) : QrPayload

    data class DirectConnect(val endpoint: AdbEndpoint) : QrPayload
}

object QrPayloadParser {
    private const val ConnectPrefix = "skyadb://connect?endpoint="

    fun parse(raw: String): QrPayload? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        parseWirelessPairing(value)?.let { return it }
        parseDirectConnect(value)?.let { return it }
        return null
    }

    private fun parseWirelessPairing(value: String): QrPayload.WirelessPairing? {
        if (!value.startsWith("WIFI:", ignoreCase = true)) return null
        val fields = splitEscaped(value.substringAfter(':'), ';')
            .mapNotNull { token ->
                val separator = indexOfUnescaped(token, ':')
                if (separator <= 0) null
                else {
                    val key = unescape(token.substring(0, separator)).uppercase()
                    key to unescape(token.substring(separator + 1))
                }
            }
            .toMap()
        if (!fields["T"].equals("ADB", ignoreCase = true)) return null
        val serviceName = fields["S"]?.trim().orEmpty()
        val pairingCode = fields["P"]?.trim().orEmpty()
        if (serviceName.isEmpty() || pairingCode.isEmpty()) return null
        return QrPayload.WirelessPairing(serviceName, pairingCode)
    }

    private fun parseDirectConnect(value: String): QrPayload.DirectConnect? {
        val encodedEndpoint =
            when {
                value.startsWith(ConnectPrefix, ignoreCase = true) -> value.substring(ConnectPrefix.length)
                else -> null
            }
        if (encodedEndpoint != null) {
            val decoded = runCatching { URLDecoder.decode(encodedEndpoint, Charsets.UTF_8.name()) }
                .getOrNull()
                ?: return null
            return AdbEndpoint.parseAuthority(decoded)?.let(QrPayload::DirectConnect)
        }

        val adbConnectText = value
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("adb connect ", ignoreCase = true) }
            ?.substringAfter("adb connect ", "")
            ?.trim()
        val authority = adbConnectText ?: value.substringBefore('\n').trim()
        return AdbEndpoint.parseAuthority(authority)?.let(QrPayload::DirectConnect)
    }

    private fun splitEscaped(value: String, separator: Char): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> {
                    current.append('\\').append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == separator -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        result += current.toString()
        return result
    }

    private fun indexOfUnescaped(value: String, target: Char): Int {
        var escaped = false
        value.forEachIndexed { index, character ->
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == target -> return index
            }
        }
        return -1
    }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var escaped = false
        value.forEach { character ->
            if (escaped) {
                result.append(character)
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else {
                result.append(character)
            }
        }
        if (escaped) result.append('\\')
        return result.toString()
    }
}

object ConnectionQrPayload {
    fun encode(endpoint: AdbEndpoint): String {
        val encoded = URLEncoder.encode(endpoint.authority, Charsets.UTF_8.name())
        return "skyadb://connect?endpoint=$encoded"
    }

    fun encode(authority: String): String? =
        AdbEndpoint.parseAuthority(authority)?.let(::encode)
}
