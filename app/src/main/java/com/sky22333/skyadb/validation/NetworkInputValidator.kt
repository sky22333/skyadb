package com.sky22333.skyadb.validation

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText

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
        val host = input.substringBefore(':').trim()
        val hasPort = ':' in input
        val portText = if (hasPort) input.substringAfter(':').trim() else defaultPort.toString()
        val portError = if (hasPort && portText.isEmpty()) {
            AppText.Res(R.string.error_port_not_number, AppText.Res(R.string.unit_port))
        } else portError(portText)
        return Endpoint(host, portText.toIntOrNull(), ipv4Error(host), portError)
    }

    fun ipv4Error(value: String): Int? {
        return when {
            value.isBlank() -> null
            !isValidIpv4(value) -> R.string.error_ipv4_invalid
            else -> null
        }
    }

    fun portError(value: String, @StringRes labelRes: Int = R.string.unit_port): AppText.Res? {
        val portNumber = value.toIntOrNull()
        return when {
            value.isBlank() -> null
            portNumber == null -> AppText.Res(R.string.error_port_not_number, AppText.Res(labelRes))
            portNumber !in 1..65535 -> AppText.Res(R.string.error_port_out_of_range, AppText.Res(labelRes))
            else -> null
        }
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split(".")
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                part.all { it.isDigit() } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
