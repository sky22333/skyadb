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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
            title = { Text("设备详情") },
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
                            contentDescription = "刷新设备信息",
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = uiState.deviceName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "连接设备后可在下方查看系统信息",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            AppStatusBadge(state = uiState.connectionState)
                        }
                    }
                }
            }

            item { SectionHeader(title = "快捷操作") }
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
                    title = "系统信息",
                    description = if (uiState.infoExpanded) "基础信息缺失时会显示为未知" else "点击图标展开查看",
                    trailing = {
                        IconButton(
                            onClick = onToggleInfoClick,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = if (uiState.infoExpanded) "收起系统信息" else "查看系统信息",
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
                            "品牌" to uiState.info.brand,
                            "型号" to uiState.info.model,
                            "Android 版本" to uiState.info.androidVersion,
                            "SDK" to uiState.info.sdk,
                            "ABI" to uiState.info.abi,
                            "分辨率" to uiState.info.resolution,
                            "电池" to uiState.info.battery,
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
            text = "${status.text}：${status.suggestion}",
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
    val actions = when (sessionKind) {
        AdbSessionKind.UsbFastboot -> listOf(
            QuickActionSpec("Shell", Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
        )
        AdbSessionKind.None, AdbSessionKind.Wifi, AdbSessionKind.UsbAdb -> listOf(
            QuickActionSpec("应用管理", Icons.Outlined.Apps, onAppsClick, SharedToolKeys.Apps),
            QuickActionSpec("本机应用", Icons.Outlined.Apps, onLocalAppsClick, SharedToolKeys.LocalApps),
            QuickActionSpec("安装 APK", Icons.Outlined.Android, onInstallClick, SharedToolKeys.Install),
            QuickActionSpec("在线下载", Icons.Outlined.Download, onDownloadClick, SharedToolKeys.Download),
            QuickActionSpec("文件管理", Icons.Outlined.FolderOpen, onFilesClick, SharedToolKeys.Files),
            QuickActionSpec("Shell", Icons.Outlined.Code, onShellClick, SharedToolKeys.Shell),
            QuickActionSpec("屏幕镜像", Icons.Outlined.Android, onMirrorClick),
            QuickActionSpec("遥控器", Icons.Outlined.Android, onRemoteClick, SharedToolKeys.Remote),
            QuickActionSpec("系统日志", Icons.Outlined.Code, onLogsClick, SharedToolKeys.Logs),
            QuickActionSpec("截图", Icons.Outlined.PhotoCamera, onScreenshotClick, SharedToolKeys.Screenshot),
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
