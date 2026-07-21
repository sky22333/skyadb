package com.sky22333.skyadb.discovery

import kotlinx.coroutines.flow.StateFlow

enum class AdbMdnsServiceType(
    val nsdType: String,
    val label: String,
    val actionLabel: String,
    val description: String,
) {
    Pairing(
        nsdType = "_adb-tls-pairing._tcp.",
        label = "无线调试配对",
        actionLabel = "配对",
        description = "输入目标设备显示的 6 位配对码后完成配对",
    ),
    Connect(
        nsdType = "_adb-tls-connect._tcp.",
        label = "无线调试连接",
        actionLabel = "连接",
        description = "已配对设备可使用此端口连接",
    ),
    Legacy(
        nsdType = "_adb._tcp.",
        label = "传统 WiFi ADB",
        actionLabel = "连接",
        description = "适用于已开启 adb tcpip 的设备",
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
    fun start()
    fun stop()
}
