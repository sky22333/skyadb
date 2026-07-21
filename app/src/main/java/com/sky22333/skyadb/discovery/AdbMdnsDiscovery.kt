package com.sky22333.skyadb.discovery

import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.flow.StateFlow

enum class AdbMdnsServiceType(
    val label: String,
    val actionLabel: String,
    val description: String,
) {
    Pairing(
        label = "无线调试配对",
        actionLabel = "配对",
        description = "输入目标设备显示的 6 位配对码后完成配对",
    ),
    Connect(
        label = "无线调试连接",
        actionLabel = "连接",
        description = "已配对设备可使用此端口连接",
    ),
    Legacy(
        label = "传统 WiFi ADB",
        actionLabel = "连接",
        description = "适用于已开启 adb tcpip 的设备",
    );

    companion object {
        fun from(type: MdnsServiceType): AdbMdnsServiceType {
            return when (type) {
                MdnsServiceType.TLS_PAIRING -> Pairing
                MdnsServiceType.TLS_CONNECT -> Connect
                MdnsServiceType.ADB -> Legacy
            }
        }
    }
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
    fun start()
    fun stop()
}
