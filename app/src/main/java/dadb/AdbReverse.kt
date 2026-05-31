package dadb

import java.io.IOException

data class AdbReverseRule(
    val device: String,
    val host: String,
)

internal enum class AdbServiceStatus {
    OKAY,
    FAIL,
    UNKNOWN,
}

internal data class AdbServiceResponse(
    val status: AdbServiceStatus,
    val payload: String,
)

internal fun buildReverseForwardDestination(
    device: String,
    host: String,
    noRebind: Boolean = false,
): String {
    require(device.isNotBlank()) { "device must not be blank" }
    require(host.isNotBlank()) { "host must not be blank" }
    val prefix = if (noRebind) "reverse:forward:norebind:" else "reverse:forward:"
    return "$prefix$device;$host"
}

internal fun buildReverseKillDestination(device: String): String {
    require(device.isNotBlank()) { "device must not be blank" }
    return "reverse:killforward:$device"
}

internal fun buildReverseKillAllDestination(): String = "reverse:killforward-all"

internal fun buildReverseListDestination(): String = "reverse:list-forward"

internal fun parseAdbServiceResponse(raw: String): AdbServiceResponse {
    return when {
        raw.startsWith("OKAY") -> {
            AdbServiceResponse(
                status = AdbServiceStatus.OKAY,
                payload = decodeProtocolStringOrRaw(raw.removePrefix("OKAY")),
            )
        }

        raw.startsWith("FAIL") -> {
            AdbServiceResponse(
                status = AdbServiceStatus.FAIL,
                payload = decodeProtocolStringOrRaw(raw.removePrefix("FAIL")),
            )
        }

        else -> {
            AdbServiceResponse(
                status = AdbServiceStatus.UNKNOWN,
                payload = decodeProtocolStringOrRaw(raw),
            )
        }
    }
}

internal fun parseReverseListOutput(output: String): List<AdbReverseRule> {
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val fields = line.split(Regex("\\s+"))
            when {
                fields.size >= 3 -> AdbReverseRule(
                    device = fields[fields.lastIndex - 1],
                    host = fields.last(),
                )

                fields.size == 2 -> AdbReverseRule(
                    device = fields[0],
                    host = fields[1],
                )

                else -> null
            }
        }
        .toList()
}

internal fun Dadb.executeAdbService(destination: String): String {
    return open(destination).use { stream ->
        val raw = stream.source.readUtf8()
        val response = parseAdbServiceResponse(raw)
        if (response.status == AdbServiceStatus.FAIL) {
            throw IOException("ADB service failed for '$destination': ${response.payload}")
        }
        response.payload
    }
}

internal data class ReverseTcpTarget(
    val host: String,
    val port: Int,
)

internal fun parseReverseTcpTarget(destination: String): ReverseTcpTarget? {
    if (!destination.startsWith("tcp:")) {
        return null
    }

    val raw = destination.removePrefix("tcp:")
    if (raw.isBlank()) {
        return null
    }

    val segments = raw.split(':')
    return when (segments.size) {
        1 -> {
            val port = segments[0].toIntOrNull() ?: return null
            if (port !in 1..65535) {
                return null
            }
            ReverseTcpTarget(host = "127.0.0.1", port = port)
        }

        2 -> {
            val host = segments[0].ifBlank { return null }
            val port = segments[1].toIntOrNull() ?: return null
            if (port !in 1..65535) {
                return null
            }
            ReverseTcpTarget(host = host, port = port)
        }

        else -> null
    }
}

internal fun requireSupportedReverseHostDestination(destination: String) {
    require(parseReverseTcpTarget(destination) != null) {
        "Direct dadb reverse currently supports only tcp:<port> or tcp:<host>:<port> host destinations"
    }
}

private fun decodeProtocolStringOrRaw(content: String): String {
    if (content.length < 4) {
        return content
    }

    val header = content.substring(0, 4)
    val length = header.toIntOrNull(16) ?: return content
    val payloadEnd = 4 + length
    if (content.length < payloadEnd) {
        return content
    }

    return content.substring(4, payloadEnd)
}
