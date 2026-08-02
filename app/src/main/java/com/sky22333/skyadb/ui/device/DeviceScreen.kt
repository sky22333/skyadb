package com.sky22333.skyadb.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.components.ToolActionCard
import com.sky22333.skyadb.ui.shared.SharedToolKeys
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.ui.theme.AdbManagerTheme

@OptIn(ExperimentalMaterial3Api::class)
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
    val uiState by viewModel.uiState.collectAsState()

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
        onToggleInfoClick = viewModel::toggleInfoExpanded,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceContent(
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
    onToggleInfoClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.device_details_title)) },
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
                            imageVector = Icons.Outlined.Refresh,
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.CardRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.deviceName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.device_connect_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            AppStatusBadge(state = uiState.connectionState)
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.section_quick_actions)) }
            item {
                QuickActionGrid(
                    sessionKind = uiState.sessionKind,
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

            item {
                SectionHeader(
                    title = stringResource(R.string.section_system_info),
                    description = stringResource(
                        if (uiState.infoExpanded) R.string.device_info_expanded_desc else R.string.device_info_collapsed_desc,
                    ),
                    trailing = {
                        IconButton(
                            onClick = onToggleInfoClick,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(
                                    if (uiState.infoExpanded) R.string.device_info_collapse_desc else R.string.device_info_expand_desc,
                                ),
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.infoExpanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                )
            }
            if (uiState.infoExpanded) {
                item { DeviceRefreshStatus(status = uiState.refreshStatus) }
                item {
                    InfoGrid(
                        items = listOf(
                            stringResource(R.string.device_info_brand) to uiState.info.brand,
                            stringResource(R.string.device_info_model) to uiState.info.model,
                            stringResource(R.string.device_info_android_version) to uiState.info.androidVersion,
                            "SDK" to uiState.info.sdk,
                            "ABI" to uiState.info.abi,
                            stringResource(R.string.device_info_resolution) to uiState.info.resolution,
                            stringResource(R.string.device_info_battery) to uiState.info.battery,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRefreshStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimens.CardRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.CardPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    sessionKind: AdbSessionKind,
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
    val actions = when (sessionKind) {
        AdbSessionKind.UsbFastboot -> listOf(
            QuickActionSpec(shellLabel, Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
        )
        AdbSessionKind.None, AdbSessionKind.Wifi, AdbSessionKind.UsbAdb -> listOf(
            QuickActionSpec(stringResource(R.string.action_apps), Icons.Outlined.Apps, onAppsClick, SharedToolKeys.Apps),
            QuickActionSpec(stringResource(R.string.nav_local_apps), Icons.Outlined.Apps, onLocalAppsClick, SharedToolKeys.LocalApps),
            QuickActionSpec(stringResource(R.string.action_install_apk), Icons.Outlined.Android, onInstallClick, SharedToolKeys.Install),
            QuickActionSpec(stringResource(R.string.action_download_online), Icons.Outlined.Download, onDownloadClick, SharedToolKeys.Download),
            QuickActionSpec(stringResource(R.string.action_file_manage), Icons.Outlined.FolderOpen, onFilesClick, SharedToolKeys.Files),
            QuickActionSpec(shellLabel, Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
            QuickActionSpec(stringResource(R.string.action_mirror), Icons.Outlined.Android, onMirrorClick),
            QuickActionSpec(stringResource(R.string.action_remote), Icons.Outlined.Android, onRemoteClick, SharedToolKeys.Remote),
            QuickActionSpec(stringResource(R.string.action_system_log), Icons.Outlined.Code, onLogsClick, SharedToolKeys.Logs),
            QuickActionSpec(stringResource(R.string.nav_screenshot), Icons.Outlined.PhotoCamera, onScreenshotClick, SharedToolKeys.Screenshot),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowActions.forEach { action ->
                    ToolActionCard(
                        title = action.label,
                        icon = action.icon,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        sharedContentKey = action.sharedContentKey,
                    )
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val sharedContentKey: String? = null,
)

@Preview(name = "设备详情 - 未连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentDisconnectedPreview() {
    AdbManagerTheme(dynamicColor = false) {
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
            onToggleInfoClick = {},
        )
    }
}

@Preview(name = "设备详情 - 已连接", showBackground = true, widthDp = 390)
@Composable
private fun DeviceContentConnectedPreview() {
    AdbManagerTheme(dynamicColor = false) {
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
            onToggleInfoClick = {},
        )
    }
}
