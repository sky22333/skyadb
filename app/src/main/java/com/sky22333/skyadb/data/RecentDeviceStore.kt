package com.sky22333.skyadb.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.map

private val Context.recentDeviceDataStore by preferencesDataStore(name = "recent_devices")

class RecentDeviceStore(context: Context) {
    private val dataStore = context.applicationContext.recentDeviceDataStore

    val devices: Flow<List<AdbDevice>> = dataStore.data
        .retryWhen { throwable, _ -> StorageReadErrors.awaitRetry("recent_devices", throwable) }
        .onEach { StorageReadErrors.recovered("recent_devices") }
        .map { preferences ->
            preferences[Keys.Devices]
                .orEmpty()
                .lineSequence()
                .mapNotNull(RecentDeviceCodec::decode)
                .toList()
        }

    suspend fun upsert(device: AdbDevice) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.Devices]
                .orEmpty()
                .lineSequence()
                .mapNotNull(RecentDeviceCodec::decode)
                .toList()
            val next = listOf(device) + current.filterNot { it.id == device.id }
            preferences[Keys.Devices] = next
                .take(MaxRecentDevices)
                .joinToString(separator = "\n", transform = RecentDeviceCodec::encode)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.Devices)
        }
    }

    suspend fun remove(deviceId: String) {
        dataStore.edit { preferences ->
            val next = preferences[Keys.Devices]
                .orEmpty()
                .lineSequence()
                .mapNotNull(RecentDeviceCodec::decode)
                .filterNot { it.id == deviceId }
                .joinToString(separator = "\n", transform = RecentDeviceCodec::encode)
            if (next.isEmpty()) preferences.remove(Keys.Devices) else preferences[Keys.Devices] = next
        }
    }

    private object Keys {
        val Devices = stringPreferencesKey("devices")
    }

    private companion object {
        const val MaxRecentDevices = 8
    }
}

internal object RecentDeviceCodec {
    fun encode(device: AdbDevice): String {
        return listOf(
            escape(device.id),
            escape(device.name),
            escape(device.host),
            device.port.toString(),
        ).joinToString("|")
    }

    fun decode(value: String): AdbDevice? {
        val parts = value.split("|")
        if (parts.size != 4) return null
        val port = parts[3].toIntOrNull() ?: return null
        return AdbDevice(
            id = unescape(parts[0]),
            name = unescape(parts[1]),
            host = unescape(parts[2]),
            port = port,
            connectionState = ConnectionState.Disconnected,
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

}
