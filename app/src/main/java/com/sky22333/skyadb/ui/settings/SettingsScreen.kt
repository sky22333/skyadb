package com.sky22333.skyadb.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.data.ThemeMode
import com.sky22333.skyadb.i18n.AppLanguage
import com.sky22333.skyadb.scrcpy.MirrorQualityPreset
import com.sky22333.skyadb.ui.components.SettingBlock
import com.sky22333.skyadb.ui.components.SettingGroupCard
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    bottomPadding: Dp = 0.dp,
    onDiagnosticsClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onDefaultPortChanged = viewModel::onDefaultPortChanged,
        onConnectionTimeoutChanged = viewModel::onConnectionTimeoutChanged,
        onCommandTimeoutChanged = viewModel::onCommandTimeoutChanged,
        onScanRangesChanged = viewModel::onScanRangesChanged,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onMirrorQualityPresetSelected = viewModel::onMirrorQualityPresetSelected,
        onLanguageSelected = viewModel::onLanguageSelected,
        onClearRecentDevicesClicked = viewModel::onClearRecentDevicesClicked,
        onDiagnosticsClick = onDiagnosticsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    bottomPadding: Dp = 0.dp,
    uiState: SettingsUiState,
    onDefaultPortChanged: (String) -> Unit,
    onConnectionTimeoutChanged: (String) -> Unit,
    onCommandTimeoutChanged: (String) -> Unit,
    onScanRangesChanged: (String) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onMirrorQualityPresetSelected: (MirrorQualityPreset) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onClearRecentDevicesClicked: () -> Unit,
    onDiagnosticsClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = 14.dp,
                end = AppDimens.ScreenPadding,
                bottom = 14.dp + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.Outlined.SettingsEthernet,
                        title = stringResource(R.string.settings_default_port_title),
                        description = stringResource(R.string.settings_default_port_desc),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.defaultPort,
                            onValueChange = onDefaultPortChanged,
                            singleLine = true,
                            suffix = { Text(stringResource(R.string.unit_port)) },
                            isError = uiState.defaultPortError != null,
                            supportingText = uiState.defaultPortError?.let { { Text(it) } },
                        )
                    }
                    SettingBlock(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.settings_connection_timeout_title),
                        description = stringResource(R.string.settings_connection_timeout_desc),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.connectionTimeoutSeconds,
                            onValueChange = onConnectionTimeoutChanged,
                            singleLine = true,
                            suffix = { Text(stringResource(R.string.unit_seconds)) },
                            isError = uiState.connectionTimeoutError != null,
                            supportingText = uiState.connectionTimeoutError?.let { { Text(it) } },
                        )
                    }
                    SettingBlock(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.settings_command_timeout_title),
                        description = stringResource(R.string.settings_command_timeout_desc),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.commandTimeoutSeconds,
                            onValueChange = onCommandTimeoutChanged,
                            singleLine = true,
                            suffix = { Text(stringResource(R.string.unit_seconds)) },
                            isError = uiState.commandTimeoutError != null,
                            supportingText = uiState.commandTimeoutError?.let { { Text(it) } },
                        )
                    }
                    SettingBlock(
                        icon = Icons.Outlined.SettingsEthernet,
                        title = stringResource(R.string.settings_scan_ranges_title),
                        description = stringResource(R.string.settings_scan_ranges_desc),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.scanRanges,
                            onValueChange = onScanRangesChanged,
                            placeholder = { Text(stringResource(R.string.settings_scan_ranges_placeholder)) },
                            minLines = 1,
                            maxLines = 4,
                            isError = uiState.scanRangesError != null,
                            supportingText = uiState.scanRangesError?.let { { Text(it) } },
                        )
                    }
                }
            }

            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.settings_mirror_quality_title),
                        description = stringResource(R.string.settings_mirror_quality_desc),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MirrorQualityPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = uiState.mirrorQualityPreset == preset,
                                    onClick = { onMirrorQualityPresetSelected(preset) },
                                    label = { Text(stringResource(preset.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.Outlined.DarkMode,
                        title = stringResource(R.string.settings_theme_mode_title),
                        description = stringResource(R.string.settings_theme_mode_desc),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.themeMode == mode,
                                    onClick = { onThemeModeSelected(mode) },
                                    label = { Text(stringResource(mode.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_language),
                        description = stringResource(R.string.settings_language_desc),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppLanguage.entries.forEach { language ->
                                FilterChip(
                                    selected = uiState.language == language,
                                    onClick = { onLanguageSelected(language) },
                                    label = { Text(stringResource(language.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.settings_diagnostics_title),
                        description = stringResource(R.string.settings_diagnostics_desc),
                        onClick = onDiagnosticsClick,
                    )
                    SettingBlock(
                        icon = Icons.Outlined.CleaningServices,
                        title = stringResource(R.string.settings_clear_recent_devices_title),
                        description = stringResource(R.string.settings_clear_recent_devices_desc),
                        onClick = onClearRecentDevicesClicked,
                    )
                }
            }

            item {
                SettingGroupCard {
                    SettingBlock(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = stringResource(R.string.settings_project_url_title),
                        description = "sky22333/skyadb",
                        onClick = { uriHandler.openUri(ProjectUrl) },
                    )
                }
            }
        }
    }
}

private const val ProjectUrl = "https://github.com/sky22333/skyadb"

@Preview(name = "设置页", showBackground = true, widthDp = 390)
@Composable
private fun SettingsContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        SettingsContent(
            uiState = SettingsUiState(),
            onDefaultPortChanged = {},
            onConnectionTimeoutChanged = {},
            onCommandTimeoutChanged = {},
            onScanRangesChanged = {},
            onThemeModeSelected = {},
            onMirrorQualityPresetSelected = {},
            onLanguageSelected = {},
            onClearRecentDevicesClicked = {},
            onDiagnosticsClick = {},
        )
    }
}
