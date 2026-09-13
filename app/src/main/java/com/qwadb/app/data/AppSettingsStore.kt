package com.qwadb.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.annotation.StringRes
import com.qwadb.app.R
import com.qwadb.app.scrcpy.MirrorDefaultMaxSize
import com.qwadb.app.scrcpy.MirrorQualityPreset
import com.qwadb.app.scrcpy.parseResolutionSize
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/** 码率以 Mbps 存储，内部使用时转换为 bps（* 1_000_000）。 */
const val BitrateMbpsToBps = 1_000_000

data class AppSettings(
    val defaultPort: Int = 5555,
    val connectionTimeoutSeconds: Int = 10,
    val commandTimeoutSeconds: Int = 30,
    val scanRanges: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    val mirrorQualityPreset: MirrorQualityPreset = MirrorQualityPreset.Balanced,
    val mirrorCustomResolution: String = "1920x1080",
    val mirrorCustomFps: Int = 30,
    val mirrorCustomBitrateMbps: Int = 4,
    val cameraResolution: String = "1280x720",
    val cameraFps: Int = 30,
    val cameraBitrateMbps: Int = 4,
    val statusRefreshIntervalMs: Int = 5000,
) {
    val mirrorCustomMaxSize: Int
        get() = parseResolutionSize(mirrorCustomResolution, MirrorDefaultMaxSize)
}

enum class ThemeMode(@param:StringRes val labelRes: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

class AppSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.appSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            AppSettings(
                defaultPort = preferences[Keys.DefaultPort] ?: 5555,
                connectionTimeoutSeconds = preferences[Keys.ConnectionTimeoutSeconds] ?: 10,
                commandTimeoutSeconds = preferences[Keys.CommandTimeoutSeconds] ?: 30,
                scanRanges = preferences[Keys.ScanRanges].orEmpty(),
                themeMode = ThemeMode.entries.firstOrNull {
                    it.name == preferences[Keys.ThemeMode]
                } ?: ThemeMode.System,
                mirrorQualityPreset = MirrorQualityPreset.fromName(preferences[Keys.MirrorQualityPreset]),
                mirrorCustomResolution = preferences[Keys.MirrorCustomResolution] ?: "1920x1080",
                mirrorCustomFps = preferences[Keys.MirrorCustomFps] ?: 30,
                mirrorCustomBitrateMbps = preferences[Keys.MirrorCustomBitrateMbps] ?: 4,
                cameraResolution = preferences[Keys.CameraResolution] ?: "1280x720",
                cameraFps = preferences[Keys.CameraFps] ?: 30,
                cameraBitrateMbps = preferences[Keys.CameraBitrateMbps] ?: 4,
                statusRefreshIntervalMs = preferences[Keys.StatusRefreshIntervalMs] ?: 5000,
            )
        }

    suspend fun updateDefaultPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.DefaultPort] = port
        }
    }

    suspend fun updateConnectionTimeoutSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.ConnectionTimeoutSeconds] = seconds
        }
    }

    suspend fun updateCommandTimeoutSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CommandTimeoutSeconds] = seconds
        }
    }

    suspend fun updateScanRanges(value: String) {
        dataStore.edit { preferences ->
            preferences[Keys.ScanRanges] = value
        }
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.ThemeMode] = themeMode.name
        }
    }

    suspend fun updateMirrorQualityPreset(preset: MirrorQualityPreset) {
        dataStore.edit { preferences ->
            preferences[Keys.MirrorQualityPreset] = preset.name
        }
    }

    suspend fun updateMirrorCustomResolution(value: String) {
        dataStore.edit { preferences ->
            preferences[Keys.MirrorCustomResolution] = value
        }
    }

    suspend fun updateMirrorCustomFps(fps: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.MirrorCustomFps] = fps
        }
    }

    suspend fun updateMirrorCustomBitrateMbps(mbps: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.MirrorCustomBitrateMbps] = mbps
        }
    }

    suspend fun updateCameraResolution(value: String) {
        dataStore.edit { preferences ->
            preferences[Keys.CameraResolution] = value
        }
    }

    suspend fun updateCameraFps(fps: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CameraFps] = fps
        }
    }

    suspend fun updateCameraBitrateMbps(mbps: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CameraBitrateMbps] = mbps
        }
    }

    suspend fun updateStatusRefreshIntervalMs(intervalMs: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.StatusRefreshIntervalMs] = intervalMs
        }
    }

    private object Keys {
        val DefaultPort = intPreferencesKey("default_port")
        val ConnectionTimeoutSeconds = intPreferencesKey("connection_timeout_seconds")
        val CommandTimeoutSeconds = intPreferencesKey("command_timeout_seconds")
        val ScanRanges = stringPreferencesKey("scan_ranges")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val MirrorQualityPreset = stringPreferencesKey("mirror_quality_preset")
        val MirrorCustomResolution = stringPreferencesKey("mirror_custom_resolution")
        val MirrorCustomFps = intPreferencesKey("mirror_custom_fps")
        val MirrorCustomBitrateMbps = intPreferencesKey("mirror_custom_bitrate_mbps")
        val CameraResolution = stringPreferencesKey("camera_resolution")
        val CameraFps = intPreferencesKey("camera_fps")
        val CameraBitrateMbps = intPreferencesKey("camera_bitrate_mbps")
        val StatusRefreshIntervalMs = intPreferencesKey("status_refresh_interval_ms")
    }
}
