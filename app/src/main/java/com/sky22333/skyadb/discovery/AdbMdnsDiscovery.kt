package com.sky22333.skyadb.discovery

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AdbEndpoint
import kotlinx.coroutines.flow.StateFlow

enum class AdbMdnsServiceType(
    @param:StringRes val labelRes: Int,
    @param:StringRes val actionLabelRes: Int,
) {
    Pairing(
        labelRes = R.string.mdns_service_pairing_label,
        actionLabelRes = R.string.mdns_service_pairing_action,
    ),
    Connect(
        labelRes = R.string.mdns_service_connect_label,
        actionLabelRes = R.string.mdns_service_connect_action,
    ),
    Legacy(
        labelRes = R.string.mdns_service_legacy_label,
        actionLabelRes = R.string.mdns_service_connect_action,
    ),
}

data class AdbMdnsEndpoint(
    val name: String,
    val host: String,
    val port: Int,
    val type: AdbMdnsServiceType,
) {
    val id: String = "${type.name}:${AdbEndpoint.format(host, port)}"
    val endpoint: String = AdbEndpoint.format(host, port)
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

    /** 扫码配对二维码只包含服务名，需要短时发现对应的配对端口。 */
    suspend fun findPairingEndpoint(
        serviceName: String,
        timeoutMs: Long = DefaultPairingLookupTimeoutMs,
    ): AdbMdnsEndpoint?

    companion object {
        const val DefaultConnectLookupTimeoutMs = 2_500L
        const val DefaultPairingLookupTimeoutMs = 8_000L
    }
}
