package com.qwadb.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.data.AppSettingsStore
import com.qwadb.app.data.RecentDeviceStore
import com.qwadb.app.data.ThemeMode
import com.qwadb.app.discovery.NetworkInfoProvider
import com.qwadb.app.discovery.ScanRangeParser
import com.qwadb.app.i18n.AppLanguage
import com.qwadb.app.i18n.appString
import com.qwadb.app.scrcpy.MirrorQualityPreset
import com.qwadb.app.validation.NetworkInputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultPort: String = "5555",
    val connectionTimeoutSeconds: String = "10",
    val commandTimeoutSeconds: String = "30",
    val scanRanges: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    val mirrorQualityPreset: MirrorQualityPreset = MirrorQualityPreset.Balanced,
    val mirrorCustomResolution: String = "1920x1080",
    val mirrorCustomFps: String = "30",
    val mirrorCustomBitrateMbps: String = "4",
    val cameraResolution: String = "1280x720",
    val cameraFps: String = "30",
    val cameraBitrateMbps: String = "4",
    val statusRefreshIntervalMs: String = "5000",
    val language: AppLanguage = AppLanguage.FollowSystem,
    val defaultPortError: String? = null,
    val connectionTimeoutError: String? = null,
    val commandTimeoutError: String? = null,
    val scanRangesError: String? = null,
    val mirrorCustomResolutionError: String? = null,
    val mirrorCustomFpsError: String? = null,
    val mirrorCustomBitrateError: String? = null,
    val cameraResolutionError: String? = null,
    val cameraFpsError: String? = null,
    val cameraBitrateError: String? = null,
    val statusRefreshIntervalError: String? = null,
)

class SettingsViewModel(
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
    private val recentDeviceStore: RecentDeviceStore = AppServices.recentDeviceStore,
    private val networkInfoProvider: NetworkInfoProvider = AppServices.networkInfoProvider,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState(language = AppLanguage.current()))
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()
    private var defaultScanRangeSaved = false

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                val scanRanges = settings.scanRanges.ifBlank { currentDefaultScanRange() }
                state.value = state.value.copy(
                    defaultPort = settings.defaultPort.toString(),
                    connectionTimeoutSeconds = settings.connectionTimeoutSeconds.toString(),
                    commandTimeoutSeconds = settings.commandTimeoutSeconds.toString(),
                    scanRanges = scanRanges,
                    themeMode = settings.themeMode,
                    mirrorQualityPreset = settings.mirrorQualityPreset,
                    mirrorCustomResolution = settings.mirrorCustomResolution,
                    mirrorCustomFps = settings.mirrorCustomFps.toString(),
                    mirrorCustomBitrateMbps = settings.mirrorCustomBitrateMbps.toString(),
                    cameraResolution = settings.cameraResolution,
                    cameraFps = settings.cameraFps.toString(),
                    cameraBitrateMbps = settings.cameraBitrateMbps.toString(),
                    statusRefreshIntervalMs = settings.statusRefreshIntervalMs.toString(),
                    defaultPortError = null,
                    connectionTimeoutError = null,
                    commandTimeoutError = null,
                    scanRangesError = null,
                )
                if (!defaultScanRangeSaved && settings.scanRanges.isBlank() && scanRanges.isNotBlank()) {
                    defaultScanRangeSaved = true
                    settingsStore.updateScanRanges(scanRanges)
                }
            }
        }
    }

    fun onDefaultPortChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(5)
        val error = NetworkInputValidator.portError(filtered)?.resolve(AppServices.context)
        state.value = state.value.copy(defaultPort = filtered, defaultPortError = error)
        val port = filtered.toIntOrNull()
        if (port != null && error == null) {
            viewModelScope.launch {
                settingsStore.updateDefaultPort(port)
            }
        }
    }

    fun onConnectionTimeoutChanged(value: String) {
        updateTimeout(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    connectionTimeoutSeconds = text,
                    connectionTimeoutError = error,
                )
            },
            onSave = settingsStore::updateConnectionTimeoutSeconds,
        )
    }

    fun onCommandTimeoutChanged(value: String) {
        updateTimeout(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    commandTimeoutSeconds = text,
                    commandTimeoutError = error,
                )
            },
            onSave = settingsStore::updateCommandTimeoutSeconds,
        )
    }

    fun onScanRangesChanged(value: String) {
        val normalized = value
            .lineSequence()
            .joinToString("\n") { line -> line.trim().take(32) }
        val error = ScanRangeParser.validationError(normalized)?.resolve(AppServices.context)
        state.value = state.value.copy(scanRanges = normalized, scanRangesError = error)
        if (error == null) {
            viewModelScope.launch {
                settingsStore.updateScanRanges(normalized)
            }
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
        viewModelScope.launch {
            settingsStore.updateThemeMode(themeMode)
        }
    }

    fun onMirrorQualityPresetSelected(preset: MirrorQualityPreset) {
        state.value = state.value.copy(mirrorQualityPreset = preset)
        viewModelScope.launch {
            settingsStore.updateMirrorQualityPreset(preset)
        }
    }

    fun onMirrorCustomResolutionChanged(value: String) {
        updateResolution(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    mirrorCustomResolution = text,
                    mirrorCustomResolutionError = error,
                )
            },
            onSave = settingsStore::updateMirrorCustomResolution,
        )
    }

    fun onMirrorCustomFpsChanged(value: String) {
        updateFps(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    mirrorCustomFps = text,
                    mirrorCustomFpsError = error,
                )
            },
            onSave = settingsStore::updateMirrorCustomFps,
        )
    }

    fun onMirrorCustomBitrateMbpsChanged(value: String) {
        updateBitrate(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    mirrorCustomBitrateMbps = text,
                    mirrorCustomBitrateError = error,
                )
            },
            onSave = settingsStore::updateMirrorCustomBitrateMbps,
        )
    }

    fun onCameraResolutionChanged(value: String) {
        updateResolution(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    cameraResolution = text,
                    cameraResolutionError = error,
                )
            },
            onSave = settingsStore::updateCameraResolution,
        )
    }

    fun onCameraFpsChanged(value: String) {
        updateFps(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    cameraFps = text,
                    cameraFpsError = error,
                )
            },
            onSave = settingsStore::updateCameraFps,
        )
    }

    fun onCameraBitrateMbpsChanged(value: String) {
        updateBitrate(
            value = value,
            onState = { text, error ->
                state.value = state.value.copy(
                    cameraBitrateMbps = text,
                    cameraBitrateError = error,
                )
            },
            onSave = settingsStore::updateCameraBitrateMbps,
        )
    }

    fun onStatusRefreshIntervalChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(5)
        val intervalMs = filtered.toIntOrNull()
        val error = when {
            filtered.isBlank() -> appString(R.string.settings_status_refresh_required)
            intervalMs == null ||
                intervalMs !in StatusRefreshIntervalMinMs..StatusRefreshIntervalMaxMs -> {
                appString(R.string.settings_status_refresh_range)
            }
            else -> null
        }
        state.value = state.value.copy(
            statusRefreshIntervalMs = filtered,
            statusRefreshIntervalError = error,
        )
        if (intervalMs != null && error == null) {
            viewModelScope.launch {
                settingsStore.updateStatusRefreshIntervalMs(intervalMs)
            }
        }
    }

    fun onLanguageSelected(language: AppLanguage) {
        state.value = state.value.copy(language = language)
        AppLanguage.apply(language)
    }

    fun onClearRecentDevicesClicked() {
        viewModelScope.launch {
            recentDeviceStore.clear()
        }
    }

    private fun updateTimeout(
        value: String,
        onState: (String, String?) -> Unit,
        onSave: suspend (Int) -> Unit,
    ) {
        val filtered = value.filter { it.isDigit() }.take(3)
        val seconds = filtered.toIntOrNull()
        val error = when {
            filtered.isBlank() -> appString(R.string.settings_timeout_required)
            seconds == null || seconds !in 1..300 -> appString(R.string.settings_timeout_range)
            else -> null
        }
        onState(filtered, error)
        if (seconds != null && error == null) {
            viewModelScope.launch {
                onSave(seconds)
            }
        }
    }

    /** 分辨率输入：允许 1920x1080 形式，校验通过后保存。 */
    private fun updateResolution(
        value: String,
        onState: (String, String?) -> Unit,
        onSave: suspend (String) -> Unit,
    ) {
        val trimmed = value.trim().filter { it.isLetterOrDigit() || it == 'x' || it == 'X' || it == '×' }
        val error = if (com.qwadb.app.scrcpy.parseResolutionSize(trimmed, 0) > 0) {
            null
        } else {
            appString(R.string.settings_resolution_invalid)
        }
        onState(trimmed, error)
        if (error == null && trimmed.isNotBlank()) {
            viewModelScope.launch {
                onSave(trimmed)
            }
        }
    }

    /** 帧率输入：1..120。 */
    private fun updateFps(
        value: String,
        onState: (String, String?) -> Unit,
        onSave: suspend (Int) -> Unit,
    ) {
        val filtered = value.filter { it.isDigit() }.take(3)
        val fps = filtered.toIntOrNull()
        val error = when {
            filtered.isBlank() -> appString(R.string.settings_fps_range)
            fps == null || fps !in 1..120 -> appString(R.string.settings_fps_range)
            else -> null
        }
        onState(filtered, error)
        if (fps != null && error == null) {
            viewModelScope.launch {
                onSave(fps)
            }
        }
    }

    /** 码率输入（Mbps）：1..100。 */
    private fun updateBitrate(
        value: String,
        onState: (String, String?) -> Unit,
        onSave: suspend (Int) -> Unit,
    ) {
        val filtered = value.filter { it.isDigit() }.take(3)
        val mbps = filtered.toIntOrNull()
        val error = when {
            filtered.isBlank() -> appString(R.string.settings_bitrate_range)
            mbps == null || mbps !in 1..100 -> appString(R.string.settings_bitrate_range)
            else -> null
        }
        onState(filtered, error)
        if (mbps != null && error == null) {
            viewModelScope.launch {
                onSave(mbps)
            }
        }
    }

    private fun currentDefaultScanRange(): String {
        return networkInfoProvider.currentLocalNetworks()
            .firstOrNull()
            ?.subnetLabel
            .orEmpty()
    }
}

/** 系统状态刷新间隔范围（毫秒）。 */
const val StatusRefreshIntervalMinMs = 500
const val StatusRefreshIntervalMaxMs = 10_000
