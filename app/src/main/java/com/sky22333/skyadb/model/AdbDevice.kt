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
    val connectionState: ConnectionState,
    val linkKind: AdbLinkKind = AdbLinkKind.Wifi,
)

enum class ConnectionState(@param:StringRes val labelRes: Int) {
    Disconnected(R.string.connection_state_disconnected),
    Connected(R.string.connection_state_connected),
    Offline(R.string.connection_state_offline),
}
