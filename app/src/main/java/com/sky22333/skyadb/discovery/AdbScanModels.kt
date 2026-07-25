package com.sky22333.skyadb.discovery

import androidx.annotation.StringRes
import com.sky22333.skyadb.R

data class LocalNetwork(
    val deviceIp: String,
    val subnetLabel: String,
    @param:StringRes val sourceLabelRes: Int,
    val hostCount: Int,
    private val networkInt: Int,
    private val broadcastInt: Int,
    private val prefixLength: Int,
    private val excludedHost: String?,
) {
    fun expandHosts(): List<String> {
        val hosts = when (prefixLength) {
            32 -> listOf(deviceIp)
            31 -> listOf(networkInt.toIpv4String(), broadcastInt.toIpv4String())
            else -> ((networkInt + 1)..(broadcastInt - 1)).map { it.toIpv4String() }
        }
        return if (excludedHost == null) hosts else hosts.filterNot { it == excludedHost }
    }
}

internal fun Int.toIpv4String(): String {
    return listOf(
        this ushr 24 and 0xff,
        this ushr 16 and 0xff,
        this ushr 8 and 0xff,
        this and 0xff,
    ).joinToString(".")
}

data class AdbScanResult(
    val host: String,
    val port: Int,
    val state: AdbProbeState,
    val latencyMs: Long,
) {
    val endpoint: String = "$host:$port"
}

enum class AdbProbeState(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val visible: Boolean,
) {
    PortClosed(R.string.adb_probe_port_closed_label, R.string.adb_probe_port_closed_desc, false),
    NotAdb(R.string.adb_probe_not_adb_label, R.string.adb_probe_not_adb_desc, false),
    PortOpen(R.string.adb_probe_port_open_label, R.string.adb_probe_port_open_desc, true),
    AdbUnauthorized(R.string.adb_probe_unauthorized_label, R.string.adb_probe_unauthorized_desc, true),
    AdbAvailable(R.string.adb_probe_available_label, R.string.adb_probe_available_desc, true),
    AdbSecure(R.string.adb_probe_secure_label, R.string.adb_probe_secure_desc, true),
    Failed(R.string.adb_probe_failed_label, R.string.adb_probe_failed_desc, false),
}

data class AdbScanProgress(
    val scanned: Int,
    val total: Int,
)
