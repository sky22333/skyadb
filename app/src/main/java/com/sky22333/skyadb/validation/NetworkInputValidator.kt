package com.sky22333.skyadb.validation

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import com.sky22333.skyadb.model.AdbEndpoint

object NetworkInputValidator {
    data class Endpoint(
        val host: String,
        val port: Int?,
        val ipError: Int?,
        val portError: AppText.Res?,
    ) {
        val isValid: Boolean get() = host.isNotBlank() && port != null && ipError == null && portError == null
    }

    fun endpoint(value: String, defaultPort: Int): Endpoint {
        val input = value.trim()
        if (input.isEmpty()) return Endpoint("", defaultPort, null, null)

        var hostText: String? = null
        var portText = defaultPort.toString()
        var explicitPort = false
        var malformed = false

        when {
            input.startsWith('[') -> {
                val close = input.indexOf(']')
                if (close <= 1) {
                    malformed = true
                } else {
                    val bracketedHost = input.substring(1, close)
                    hostText = bracketedHost
                    if (':' !in bracketedHost) malformed = true
                    val suffix = input.substring(close + 1)
                    when {
                        suffix.isEmpty() -> Unit
                        suffix.startsWith(':') -> {
                            explicitPort = true
                            portText = suffix.substring(1).trim()
                            if ('[' in portText || ']' in portText) malformed = true
                        }
                        else -> malformed = true
                    }
                }
            }
            '[' in input || ']' in input -> malformed = true
            input.count { it == ':' } == 0 -> hostText = input
            input.count { it == ':' } == 1 -> {
                hostText = input.substringBefore(':')
                portText = input.substringAfter(':').trim()
                explicitPort = true
            }
            else -> hostText = input
        }

        val normalizedHost = if (malformed) null else hostText?.let { AdbEndpoint.normalizeHost(it) }
        val ipError = if (malformed || normalizedHost == null) R.string.error_ipv4_invalid else null
        val portValidationError = if (explicitPort && portText.isEmpty()) {
            AppText.Res(R.string.error_port_not_number, AppText.Res(R.string.unit_port))
        } else {
            portError(portText)
        }
        return Endpoint(
            host = normalizedHost.orEmpty(),
            port = portText.toIntOrNull(),
            ipError = ipError,
            portError = portValidationError,
        )
    }

    fun ipError(value: String): Int? {
        if (value.isBlank()) return null
        return if (AdbEndpoint.normalizeHost(value) == null) R.string.error_ipv4_invalid else null
    }

    fun normalizeHost(value: String): String? = AdbEndpoint.normalizeHost(value)

    fun portError(value: String, @StringRes labelRes: Int = R.string.unit_port): AppText.Res? {
        val input = value.trim()
        val portNumber = input.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }?.toIntOrNull()
        return when {
            input.isBlank() -> null
            portNumber == null -> AppText.Res(R.string.error_port_not_number, AppText.Res(labelRes))
            portNumber !in 1..65535 -> AppText.Res(R.string.error_port_out_of_range, AppText.Res(labelRes))
            else -> null
        }
    }
}
