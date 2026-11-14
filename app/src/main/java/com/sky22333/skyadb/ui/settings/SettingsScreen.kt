package com.sky22333.skyadb.ui.settings

import com.sky22333.skyadb.ui.components.PopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.data.ThemeMode
import com.sky22333.skyadb.i18n.AppLanguage
import com.sky22333.skyadb.scrcpy.MirrorQualityPreset
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.R
import com.sky22333.skyadb.discovery.ScanRangeParser
import com.sky22333.skyadb.validation.NetworkInputValidator
import com.sky22333.skyadb.ui.components.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.layout.Row

@Composable
fun SettingsScreen(
    bottomPadding: Dp = 0.dp,
    onDiagnosticsClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    var editing by rememberSaveable { mutableStateOf<SettingField?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = stringResource(R.string.settings_title), color = MiuixTheme.colorScheme.background)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
        ) {
            items(SettingField.entries.size) { index ->
                val field = SettingField.entries[index]
                ArrowPreference(
                    modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                    title = stringResource(field.titleRes),
                    startAction = { PreferenceIcon(field.icon) },
                    endActions = {
                        Text(
                            text = field.displayValue(uiState),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    insideMargin = PaddingValues(0.dp),
                    onClick = { editing = field },
                )
            }

            item {
                PopupAnchor {
                    OverlayDropdownPreference(
                        modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                        title = stringResource(R.string.settings_mirror_quality_title),
                        items = MirrorQualityPreset.entries.map { stringResource(it.labelRes) },
                        selectedIndex = MirrorQualityPreset.entries.indexOf(uiState.mirrorQualityPreset),
                        onSelectedIndexChange = { onMirrorQualityPresetSelected(MirrorQualityPreset.entries[it]) },
                        startAction = { PreferenceIcon(MiuixIcons.Tune) },
                        insideMargin = PaddingValues(0.dp),
                    )
                }
            }

            item {
                PopupAnchor {
                    OverlayDropdownPreference(
                        modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                        title = stringResource(R.string.settings_theme_mode_title),
                        items = ThemeMode.entries.map { stringResource(it.labelRes) },
                        selectedIndex = ThemeMode.entries.indexOf(uiState.themeMode),
                        onSelectedIndexChange = { onThemeModeSelected(ThemeMode.entries[it]) },
                        startAction = { PreferenceIcon(MiuixIcons.Theme) },
                        insideMargin = PaddingValues(0.dp),
                    )
                }
            }

            item {
                PopupAnchor {
                    OverlayDropdownPreference(
                        modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                        title = stringResource(R.string.settings_language),
                        items = AppLanguage.entries.map { stringResource(it.labelRes) },
                        selectedIndex = AppLanguage.entries.indexOf(uiState.language),
                        onSelectedIndexChange = { onLanguageSelected(AppLanguage.entries[it]) },
                        startAction = { PreferenceIcon(MiuixIcons.Translate) },
                        insideMargin = PaddingValues(0.dp),
                    )
                }
            }

            item {
                ArrowPreference(
                    modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                    title = stringResource(R.string.settings_diagnostics_title),
                    startAction = { PreferenceIcon(MiuixIcons.Report) },
                    insideMargin = PaddingValues(0.dp),
                    onClick = onDiagnosticsClick,
                )
                ArrowPreference(
                    modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                    title = stringResource(R.string.settings_clear_recent_devices_title),
                    startAction = { PreferenceIcon(MiuixIcons.Clear) },
                    insideMargin = PaddingValues(0.dp),
                    onClick = onClearRecentDevicesClicked,
                )
            }

            item {
                ArrowPreference(
                    modifier = Modifier.clip(RoundedCornerShape(AppDimens.ControlRadius)),
                    title = stringResource(R.string.settings_project_url_title),
                    startAction = { PreferenceIcon(MiuixIcons.Forward) },
                    insideMargin = PaddingValues(0.dp),
                    onClick = { uriHandler.openUri(ProjectUrl) },
                )
            }
        }
    }
    editing?.let { field ->
        SettingEditor(
            field = field,
            initialValue = field.value(uiState),
            onDismiss = { editing = null },
            onSave = { value ->
                when (field) {
                    SettingField.Port -> onDefaultPortChanged(value)
                    SettingField.ConnectionTimeout -> onConnectionTimeoutChanged(value)
                    SettingField.CommandTimeout -> onCommandTimeoutChanged(value)
                    SettingField.ScanRanges -> onScanRangesChanged(value)
                }
                editing = null
            },
        )
    }
}

private enum class SettingField(val titleRes: Int, val icon: ImageVector) {
    Port(R.string.settings_default_port_title, MiuixIcons.Link),
    ConnectionTimeout(R.string.settings_connection_timeout_title, MiuixIcons.Timer),
    CommandTimeout(R.string.settings_command_timeout_title, MiuixIcons.Timer),
    ScanRanges(R.string.settings_scan_ranges_title, MiuixIcons.Search);

    fun value(state: SettingsUiState): String = when (this) {
        Port -> state.defaultPort
        ConnectionTimeout -> state.connectionTimeoutSeconds
        CommandTimeout -> state.commandTimeoutSeconds
        ScanRanges -> state.scanRanges
    }

    @Composable
    fun displayValue(state: SettingsUiState): String = when (this) {
        ConnectionTimeout, CommandTimeout -> "${value(state)} ${stringResource(R.string.unit_seconds)}"
        else -> value(state).replace('\n', ' ')
    }
}

@Composable
private fun SettingEditor(field: SettingField, initialValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var draft by rememberSaveable(field) { mutableStateOf(initialValue) }
    val error = when (field) {
        SettingField.Port -> NetworkInputValidator.portError(draft)?.resolve(context)
        SettingField.ConnectionTimeout, SettingField.CommandTimeout -> when {
            draft.isBlank() -> stringResource(R.string.settings_timeout_required)
            draft.toIntOrNull() !in 1..300 -> stringResource(R.string.settings_timeout_range)
            else -> null
        }
        SettingField.ScanRanges -> ScanRangeParser.validationError(draft)?.resolve(context)
    }
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(field.titleRes)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft,
                onValueChange = { value ->
                    draft = when (field) {
                        SettingField.Port -> value.filter(Char::isDigit).take(5)
                        SettingField.ConnectionTimeout, SettingField.CommandTimeout -> value.filter(Char::isDigit).take(3)
                        SettingField.ScanRanges -> value.lineSequence().joinToString("\n") { it.trim().take(32) }
                    }
                },
                label = if (field == SettingField.ScanRanges) stringResource(R.string.settings_scan_ranges_placeholder) else "",
                useLabelAsPlaceholder = true,
                singleLine = field != SettingField.ScanRanges,
                minLines = 1,
                maxLines = if (field == SettingField.ScanRanges) 4 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (field == SettingField.ScanRanges) KeyboardType.Text else KeyboardType.Number),
            )
            error?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(draft) }, enabled = error == null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    }
}

@Composable
private fun PreferenceIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

private const val ProjectUrl = "https://github.com/sky22333/skyadb"

@Preview(name = "设置页", showBackground = true, widthDp = 390)
@Composable
private fun SettingsContentPreview() {
    AdbManagerTheme() {
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
