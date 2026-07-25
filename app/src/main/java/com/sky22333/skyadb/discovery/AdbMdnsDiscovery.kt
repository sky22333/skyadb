package com.sky22333.skyadb.discovery

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import kotlinx.coroutines.flow.StateFlow

enum class AdbMdnsServiceType(
    val nsdType: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val actionLabelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    Pairing(
        nsdType = "_adb-tls-pairing._tcp.",
        labelRes = R.string.mdns_service_pairing_label,
        actionLabelRes = R.string.mdns_service_pairing_action,
        descriptionRes = R.string.mdns_service_pairing_desc,
    ),
    Connect(
        nsdType = "_adb-tls-connect._tcp.",
        labelRes = R.string.mdns_service_connect_label,
        actionLabelRes = R.string.mdns_service_connect_action,
        descriptionRes = R.string.mdns_service_connect_desc,
    ),
    Legacy(
        nsdType = "_adb._tcp.",
        labelRes = R.string.mdns_service_legacy_label,
        actionLabelRes = R.string.mdns_service_connect_action,
        descriptionRes = R.string.mdns_service_legacy_desc,
    ),
}

data class AdbMdnsEndpoint(
    val name: String,
    val host: String,
    val port: Int,
    val type: AdbMdnsServiceType,
) {
    val id: String = "${type.name}:$host:$port"
    val endpoint: String = "$host:$port"
}

data class AdbMdnsDiscoveryState(
    val running: Boolean = false,
    val endpoints: List<AdbMdnsEndpoint> = emptyList(),
    val error: String? = null,
)

interface AdbMdnsDiscovery {
    val state: StateFlow<AdbMdnsDiscoveryState>

    fun start(types: Set<AdbMdnsServiceType> = AdbMdnsServiceType.entries.toSet())

    fun stop()

    /** 短时只扫连接服务；超时或取消后必定 stop，避免常驻耗电。 */
    suspend fun findConnectPort(
        host: String,
        timeoutMs: Long = DefaultConnectLookupTimeoutMs,
    ): Int?

    companion object {
        const val DefaultConnectLookupTimeoutMs = 2_500L
    }
}

fun List<AdbMdnsEndpoint>.connectPortForHost(host: String): Int? {
    val target = host.trim()
    if (target.isEmpty()) return null
    return firstOrNull { endpoint ->
        endpoint.type == AdbMdnsServiceType.Connect && endpoint.host == target
    }?.port
}
