package com.sky22333.skyadb.discovery

data class LocalNetwork(
    val deviceIp: String,
    val subnetLabel: String,
    val sourceLabel: String = "当前网络",
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
    val label: String,
    val description: String,
    val visible: Boolean,
) {
    PortClosed("未开放", "目标端口未响应", false),
    NotAdb("非 ADB", "端口开放，但未响应 ADB 握手", false),
    PortOpen("端口开放", "端口可连接，可尝试使用 ADB 连接确认", true),
    AdbUnauthorized("需要授权", "发现 ADB 服务，需要在目标设备确认授权", true),
    AdbAvailable("可连接", "发现可响应的 ADB 服务", true),
    AdbSecure("无线调试", "发现 Android 11+ 无线调试安全握手端口", true),
    Failed("探测失败", "探测过程异常，可重试扫描", false),
}

data class AdbScanProgress(
    val scanned: Int,
    val total: Int,
)
