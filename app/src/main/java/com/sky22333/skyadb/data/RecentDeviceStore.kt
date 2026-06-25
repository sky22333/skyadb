package com.sky22333.skyadb.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.recentDeviceDataStore by preferencesDataStore(name = "recent_devices")

class RecentDeviceStore(context: Context) {
    private val dataStore = context.applicationContext.recentDeviceDataStore

    val devices: Flow<List<AdbDevice>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            preferences[Keys.Devices]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decodeDevice)
                .toList()
        }

    suspend fun saveDevices(devices: List<AdbDevice>) {
        dataStore.edit { preferences ->
            preferences[Keys.Devices] = devices
                .take(MaxRecentDevices)
                .joinToString(separator = "\n", transform = ::encodeDevice)
        }
    }

    suspend fun upsert(device: AdbDevice) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.Devices]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decodeDevice)
                .toList()
            val next = listOf(device) + current.filterNot { it.id == device.id }
            preferences[Keys.Devices] = next
                .take(MaxRecentDevices)
                .joinToString(separator = "\n", transform = ::encodeDevice)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.Devices)
        }
    }

    private fun encodeDevice(device: AdbDevice): String {
        return listOf(
            escape(device.id),
            escape(device.name),
            escape(device.host),
            device.port.toString(),
            device.type.name,
            device.connectionState.name,
            escape(device.lastConnectedText),
            device.linkKind.name,
        ).joinToString("|")
    }

    private fun decodeDevice(value: String): AdbDevice? {
        val parts = value.split("|")
        if (parts.size !in 7..8) return null
        val port = parts[3].toIntOrNull() ?: return null
        return AdbDevice(
            id = unescape(parts[0]),
            name = unescape(parts[1]),
            host = unescape(parts[2]),
            port = port,
            type = DeviceType.entries.firstOrNull { it.name == parts[4] } ?: DeviceType.Unknown,
            connectionState = ConnectionState.Disconnected,
            lastConnectedText = unescape(parts[6]),
            linkKind = parts.getOrNull(7)?.let { name ->
                AdbLinkKind.entries.firstOrNull { it.name == name }
            } ?: AdbLinkKind.Wifi,
        )
    }

    private fun escape(value: String): String {
        return value
            .replace("%", "%25")
            .replace("|", "%7C")
            .replace("\n", "%0A")
    }

    private fun unescape(value: String): String {
        return value
            .replace("%0A", "\n")
            .replace("%7C", "|")
            .replace("%25", "%")
    }

    private object Keys {
        val Devices = stringPreferencesKey("devices")
    }

    private companion object {
        const val MaxRecentDevices = 8
    }
}
