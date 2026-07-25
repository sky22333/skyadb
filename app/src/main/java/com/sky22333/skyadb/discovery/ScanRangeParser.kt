package com.sky22333.skyadb.discovery

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText

object ScanRangeParser {
    fun parseConfiguredRanges(value: String): List<LocalNetwork> {
        return value
            .split("\n", ",", "，", ";", "；")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseEntry(it, sourceLabelRes = R.string.discovery_source_manual_config) }
            .distinctBy { it.subnetLabel }
            .take(MaxConfiguredRanges)
    }

    fun validationError(value: String): AppText.Res? {
        val entries = value
            .split("\n", ",", "，", ";", "；")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (entries.size > MaxConfiguredRanges) {
            return AppText.Res(R.string.scan_range_error_too_many, MaxConfiguredRanges)
        }
        val invalid = entries.firstOrNull { parseEntry(it, sourceLabelRes = R.string.discovery_source_manual_config) == null }
        return if (invalid == null) null else AppText.Res(R.string.scan_range_error_invalid, invalid)
    }

    fun subnetForHost(host: String, @StringRes sourceLabelRes: Int): LocalNetwork? {
        return if (host.isPrivateIpv4()) {
            createSubnet(address = host, prefixLength = 24, sourceLabelRes = sourceLabelRes, excludedHost = null)
        } else {
            null
        }
    }

    fun subnetForLocalAddress(address: String, @StringRes sourceLabelRes: Int): LocalNetwork? {
        if (!address.isPrivateIpv4()) return null
        return createSubnet(address = address, prefixLength = DefaultPrefixLength, sourceLabelRes = sourceLabelRes)
    }

    private fun parseEntry(entry: String, @StringRes sourceLabelRes: Int): LocalNetwork? {
        val parts = entry.split("/")
        val address = parts.firstOrNull()?.trim().orEmpty()
        if (!address.isPrivateIpv4()) return null
        val prefix = when (parts.size) {
            1 -> DefaultPrefixLength
            2 -> parts[1].toIntOrNull() ?: return null
            else -> return null
        }
        if (prefix !in 24..32) return null
        return createSubnet(address = address, prefixLength = prefix, sourceLabelRes = sourceLabelRes, excludedHost = null)
    }

    private fun createSubnet(
        address: String,
        prefixLength: Int,
        @StringRes sourceLabelRes: Int,
        excludedHost: String? = address,
    ): LocalNetwork? {
        val addressInt = address.toIpv4IntOrNull() ?: return null
        val mask = prefixLength.toMask()
        val network = addressInt and mask
        val broadcast = network or mask.inv()
        val rawCount = when (prefixLength) {
            32 -> 1
            31 -> 2
            else -> (broadcast - network - 1).coerceAtLeast(0)
        }
        val excludedInRange = excludedHost != null && when (prefixLength) {
            32 -> excludedHost == address
            31 -> {
                val excludedInt = excludedHost.toIpv4IntOrNull()
                excludedInt == network || excludedInt == broadcast
            }
            else -> {
                val excludedInt = excludedHost.toIpv4IntOrNull()
                excludedInt != null && excludedInt in (network + 1) until broadcast
            }
        }
        val hostCount = if (excludedInRange) (rawCount - 1).coerceAtLeast(0) else rawCount

        return LocalNetwork(
            deviceIp = address,
            subnetLabel = "${network.toIpv4String()}/$prefixLength",
            sourceLabelRes = sourceLabelRes,
            hostCount = hostCount,
            networkInt = network,
            broadcastInt = broadcast,
            prefixLength = prefixLength,
            excludedHost = excludedHost,
        )
    }

    private fun String.isPrivateIpv4(): Boolean {
        val parts = split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            parts[0] == 192 && parts[1] == 168 ||
            parts[0] == 172 && parts[1] in 16..31
    }

    private fun String.toIpv4IntOrNull(): Int? {
        val parts = split('.')
        if (parts.size != 4) return null
        var value = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            value = (value shl 8) or octet
        }
        return value
    }

    private fun Int.toMask(): Int {
        return if (this == 0) 0 else -1 shl (32 - this)
    }

    private const val MaxConfiguredRanges = 6
    private const val DefaultPrefixLength = 24
}
