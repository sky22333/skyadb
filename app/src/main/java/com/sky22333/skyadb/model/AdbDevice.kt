package com.sky22333.skyadb.model

enum class AdbLinkKind(val label: String) {
    Wifi("Wi-Fi"),
    UsbOtg("USB OTG"),
}

data class AdbDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val type: DeviceType,
    val connectionState: ConnectionState,
    val lastConnectedText: String,
    val linkKind: AdbLinkKind = AdbLinkKind.Wifi,
)

enum class DeviceType(val label: String) {
    Phone("手机"),
    Tablet("平板"),
    Tv("电视"),
    Box("盒子"),
    Unknown("未知设备"),
}

enum class ConnectionState(val label: String) {
    Disconnected("未连接"),
    Connecting("连接中"),
    Connected("已连接"),
    Failed("连接失败"),
    Offline("离线"),
}
