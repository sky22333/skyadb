package com.sky22333.skyadb.model

import androidx.annotation.StringRes
import com.sky22333.skyadb.R

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

enum class DeviceType(@param:StringRes val labelRes: Int) {
    Phone(R.string.device_type_phone),
    Tablet(R.string.device_type_tablet),
    Tv(R.string.device_type_tv),
    Box(R.string.device_type_box),
    Unknown(R.string.device_type_unknown),
}

enum class ConnectionState(@param:StringRes val labelRes: Int) {
    Disconnected(R.string.connection_state_disconnected),
    Connecting(R.string.connection_state_connecting),
    Connected(R.string.connection_state_connected),
    Failed(R.string.connection_state_failed),
    Offline(R.string.connection_state_offline),
}
