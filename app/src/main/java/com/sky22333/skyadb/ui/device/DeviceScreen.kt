package com.sky22333.skyadb.ui.device

import com.sky22333.skyadb.ui.components.AppIcons

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RebootMode
import com.sky22333.skyadb.ui.components.PopupAnchor
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.Button
import top.yukonga.miuix.kmp.window.WindowDialog
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.components.ToolActionEntry
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.ui.theme.AdbManagerTheme

@Composable
fun DeviceScreen(
    bottomPadding: Dp = 0.dp,
    onAppsClick: () -> Unit = {},
    onLocalAppsClick: () -> Unit = {},
    onInstallClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onFilesClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    onShellClick: () -> Unit = {},
    onRemoteClick: () -> Unit = {},
    onMirrorClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    viewModel: DeviceViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var fastbootPanel by rememberSaveable { mutableStateOf<FastbootPanel?>(null) }

    DeviceContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onAppsClick = onAppsClick,
        onLocalAppsClick = onLocalAppsClick,
        onInstallClick = onInstallClick,
        onDownloadClick = onDownloadClick,
        onFilesClick = onFilesClick,
        onScreenshotClick = onScreenshotClick,
        onShellClick = onShellClick,
        onRemoteClick = onRemoteClick,
        onMirrorClick = onMirrorClick,
        onLogsClick = onLogsClick,
        onRefreshClick = viewModel::refreshDeviceInfo,
        onReboot = viewModel::reboot,
        onFastbootAction = { fastbootPanel = it },
    )
    FastbootActions(panel = fastbootPanel, currentSlot = uiState.fastbootSummary?.values?.get("current-slot"), onClosed = { fastbootPanel = null }, onRefresh = viewModel::refreshDeviceInfo)
}

@Composable
internal fun DeviceContent(
    bottomPadding: Dp = 0.dp,
    uiState: DeviceUiState,
    onAppsClick: () -> Unit,
    onLocalAppsClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFilesClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onShellClick: () -> Unit,
    onRemoteClick: () -> Unit,
    onMirrorClick: () -> Unit,
    onLogsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onReboot: (RebootMode) -> Unit,
    onFastbootAction: (FastbootPanel) -> Unit = {},
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState != ConnectionState.Connected) showInfo = false
    }
    val infoLabel = stringResource(R.string.section_system_info)
    val rebootLabel = stringResource(R.string.fastboot_reboot_to)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.device_details_title),
            color = MiuixTheme.colorScheme.background,
            actions = {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !uiState.refreshing && uiState.connectionState == ConnectionState.Connected,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (uiState.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.device_refresh_info_desc),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f).then(
                            if (uiState.connectionState == ConnectionState.Connected) Modifier.clickable(role = Role.Button, onClickLabel = infoLabel) {
                                showInfo = true
                                onRefreshClick()
                            } else Modifier
                        )) {
                            Text(
                                text = uiState.deviceName,
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.device_connect_hint),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                        if (uiState.connectionState == ConnectionState.Connected && !uiState.rebooting) {
                            val entries = DropdownEntry(RebootMode.entries.map { mode ->
                                DropdownItem(text = stringResource(mode.label), onClick = { onReboot(mode) })
                            })
                            PopupAnchor {
                                OverlayIconDropdownMenu(entry = entries, modifier = Modifier.semantics { contentDescription = rebootLabel }) {
                                    AppStatusBadge(state = uiState.connectionState)
                                }
                            }
                        } else {
                            AppStatusBadge(state = uiState.connectionState)
                        }
                    }
                }
            }

            if (uiState.rebootStatus != OperationStatus.Idle) {
                item { DeviceRefreshStatus(status = uiState.rebootStatus) }
            }
            if (uiState.sessionKind == AdbSessionKind.UsbFastboot) {
                item { SectionHeader(title = stringResource(R.string.fb_images)) }
                item {
                    ActionGrid(listOf(
                        QuickActionSpec(stringResource(R.string.fb_flash), AppIcons.Install) { onFastbootAction(FastbootPanel.Flash) },
                        QuickActionSpec(stringResource(R.string.fb_boot), AppIcons.Power) { onFastbootAction(FastbootPanel.Boot) },
                        QuickActionSpec(stringResource(R.string.fb_archive), MiuixIcons.Folder) { onFastbootAction(FastbootPanel.Archive) },
                    ), columns = 3)
                }
                item { SectionHeader(title = stringResource(R.string.fb_device)) }
                item {
                    ActionGrid(listOf(
                        QuickActionSpec(stringResource(R.string.fb_slot), MiuixIcons.GridView) { onFastbootAction(FastbootPanel.Slot); onRefreshClick() },
                        QuickActionSpec(stringResource(R.string.fastboot_console_title), AppIcons.Terminal, onShellClick),
                    ))
                }
            } else {
                item { SectionHeader(title = stringResource(R.string.section_quick_actions)) }
                item {
                    QuickActionGrid(
                        onAppsClick = onAppsClick,
                        onLocalAppsClick = onLocalAppsClick,
                        onInstallClick = onInstallClick,
                        onDownloadClick = onDownloadClick,
                        onFilesClick = onFilesClick,
                        onScreenshotClick = onScreenshotClick,
                        onShellClick = onShellClick,
                        onRemoteClick = onRemoteClick,
                        onMirrorClick = onMirrorClick,
                        onLogsClick = onLogsClick,
                    )
                }
            }

            if (!showInfo && uiState.refreshStatus is OperationStatus.Failed) {
                item { DeviceRefreshStatus(status = uiState.refreshStatus) }
            }
        }
    }
    DeviceInfoDialog(
        visible = showInfo && uiState.connectionState == ConnectionState.Connected,
        uiState = uiState,
        onDismiss = { showInfo = false },
    )
}

@Composable
private fun DeviceRefreshStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(
            text = status.text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun DeviceInfoDialog(visible: Boolean, uiState: DeviceUiState, onDismiss: () -> Unit) {
    WindowDialog(
        show = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.section_system_info),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.sessionKind == AdbSessionKind.UsbFastboot) {
                DeviceRefreshStatus(uiState.refreshStatus)
                val labels = listOf(R.string.fastboot_product, R.string.fastboot_serial, R.string.fastboot_bootloader_version, R.string.fastboot_slot, R.string.fastboot_mode)
                val unavailable = stringResource(R.string.fastboot_not_provided)
                com.sky22333.skyadb.model.FastbootDeviceSummary.Variables.zip(labels).forEach { (key, label) ->
                    val raw = uiState.fastbootSummary?.values?.get(key)
                    val value = if (key == "is-userspace") when (raw) { "yes" -> "fastbootd"; "no" -> "Bootloader"; else -> unavailable } else raw ?: unavailable
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(label), modifier = Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MiuixTheme.textStyles.body2)
                    }
                }
            } else {
                DeviceRefreshStatus(uiState.refreshStatus)
                val rows = listOf(
                    stringResource(R.string.device_info_brand) to uiState.info.brand,
                    stringResource(R.string.device_info_manufacturer) to uiState.info.manufacturer,
                    stringResource(R.string.device_info_model) to uiState.info.model,
                    stringResource(R.string.device_info_device) to uiState.info.device,
                    stringResource(R.string.device_info_android_version) to uiState.info.androidVersion,
                    "SDK" to uiState.info.sdk,
                    "ABI" to uiState.info.abi,
                    stringResource(R.string.device_info_build) to uiState.info.buildId,
                    stringResource(R.string.device_info_security_patch) to uiState.info.securityPatch,
                    stringResource(R.string.device_info_resolution) to uiState.info.resolution,
                    stringResource(R.string.device_info_battery) to uiState.info.battery,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rows.forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label, modifier = Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                            Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    onAppsClick: () -> Unit,
    onLocalAppsClick: () -> Unit,
    onInstallClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFilesClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onShellClick: () -> Unit,
    onRemoteClick: () -> Unit,
    onMirrorClick: () -> Unit,
    onLogsClick: () -> Unit,
) {
    val shellLabel = stringResource(R.string.nav_shell)
    val actions = listOf(
            QuickActionSpec(stringResource(R.string.action_apps), MiuixIcons.GridView, onAppsClick),
            QuickActionSpec(stringResource(R.string.nav_local_apps), AppIcons.DeviceApps, onLocalAppsClick),
            QuickActionSpec(stringResource(R.string.action_install_apk), AppIcons.Install, onInstallClick),
            QuickActionSpec(stringResource(R.string.action_download_online), MiuixIcons.Download, onDownloadClick),
            QuickActionSpec(stringResource(R.string.action_file_manage), MiuixIcons.Folder, onFilesClick),
            QuickActionSpec(shellLabel, AppIcons.Terminal, onShellClick),
            QuickActionSpec(stringResource(R.string.action_mirror), MiuixIcons.ScreenMirroring, onMirrorClick),
            QuickActionSpec(stringResource(R.string.action_remote), AppIcons.Remote, onRemoteClick),
            QuickActionSpec(stringResource(R.string.action_system_log), MiuixIcons.Notes, onLogsClick),
            QuickActionSpec(stringResource(R.string.nav_screenshot), MiuixIcons.ScreenCapture, onScreenshotClick),
        )

    ActionGrid(actions)
}

@Composable
private fun ActionGrid(actions: List<QuickActionSpec>, columns: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(columns).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowActions.forEach { action ->
                    ToolActionEntry(
                        title = action.label,
                        icon = action.icon,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowActions.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Preview(name = "设备详情 - 未连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentDisconnectedPreview() {
    AdbManagerTheme() {
        DeviceContent(
            uiState = DeviceUiState(),
            onAppsClick = {},
            onLocalAppsClick = {},
            onInstallClick = {},
            onDownloadClick = {},
            onFilesClick = {},
            onScreenshotClick = {},
            onShellClick = {},
            onRemoteClick = {},
            onMirrorClick = {},
            onLogsClick = {},
            onRefreshClick = {},
            onReboot = {},
        )
    }
}

@Preview(name = "设备详情 - 已连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentConnectedPreview() {
    AdbManagerTheme() {
        DeviceContent(
            uiState = DeviceUiState(
                deviceName = "客厅电视",
                connectionState = ConnectionState.Connected,
                sessionKind = AdbSessionKind.Wifi,
                info = DeviceInfo(
                    brand = "Google",
                    model = "Android TV",
                    androidVersion = "14",
                    sdk = "34",
                    abi = "arm64-v8a",
                    resolution = "3840 x 2160",
                    battery = "未知",
                ),
            ),
            onAppsClick = {},
            onLocalAppsClick = {},
            onInstallClick = {},
            onDownloadClick = {},
            onFilesClick = {},
            onScreenshotClick = {},
            onShellClick = {},
            onRemoteClick = {},
            onMirrorClick = {},
            onLogsClick = {},
            onRefreshClick = {},
            onReboot = {},
        )
    }
}
