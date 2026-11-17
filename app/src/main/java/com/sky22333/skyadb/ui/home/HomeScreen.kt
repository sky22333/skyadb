package com.sky22333.skyadb.ui.home

import androidx.compose.foundation.clickable
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbEndpoint
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.AppIcons
import com.sky22333.skyadb.ui.components.FlatButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.usb.UsbOtgAttachment
import com.sky22333.skyadb.usb.UsbOtgMode

@Composable
fun HomeScreen(
    bottomPadding: Dp = 0.dp,
    onPairingClick: () -> Unit = {},
    onDiscoveryClick: () -> Unit = {},
    discoveredHost: String = "",
    discoveredPort: String = "",
    onDiscoveredEndpointConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(discoveredHost, discoveredPort) {
        if (discoveredHost.isBlank()) return@LaunchedEffect
        val port = discoveredPort.toIntOrNull()
        if (port != null) {
            viewModel.onDiscoveredEndpointSelected(discoveredHost, port)
        } else {
            viewModel.onPairedHostPrepared(discoveredHost)
        }
        onDiscoveredEndpointConsumed()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.home_title),
            color = MiuixTheme.colorScheme.background,
            actions = {
                IconButton(onClick = onPairingClick) {
                    Icon(AppIcons.Wifi, contentDescription = stringResource(R.string.action_wireless_pairing))
                }
                IconButton(onClick = onDiscoveryClick) {
                    Icon(MiuixIcons.Search, contentDescription = stringResource(R.string.action_discover_devices))
                }
                if (uiState.canDisconnect) {
                    IconButton(onClick = viewModel::onDisconnectClicked) {
                        Icon(MiuixIcons.Remove, contentDescription = stringResource(R.string.action_disconnect))
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
                ManualConnectSection(
                    endpoint = uiState.endpoint,
                    endpointError = uiState.endpointError,
                    connectEnabled = uiState.connectEnabled,
                    operationStatus = uiState.operationStatus,
                    onEndpointChanged = viewModel::onEndpointChanged,
                    onConnectClick = viewModel::onConnectClicked,
                )
            }

            item {
                UsbOtgConnectSection(
                    attachments = uiState.usbAttachments,
                    connectingDeviceName = uiState.connectingUsbDeviceName,
                    onRefreshClick = viewModel::refreshUsbDevices,
                    onConnectClick = viewModel::onUsbConnectClicked,
                )
            }

            item {
                RecentDevicesSection(
                    devices = uiState.recentDevices,
                    onDeviceClick = viewModel::onRecentDeviceSelected,
                )
            }
        }
    }
}

@Composable
private fun UsbOtgConnectSection(
    attachments: List<UsbOtgAttachment>,
    connectingDeviceName: String?,
    onRefreshClick: () -> Unit,
    onConnectClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(
            title = "USB OTG",
            trailing = {
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.home_refresh_usb_desc),
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        attachments.forEach { attachment ->
            UsbOtgDeviceRow(
                attachment = attachment,
                connecting = attachment.deviceName == connectingDeviceName,
                onConnectClick = { onConnectClick(attachment.deviceName) },
            )
        }
    }
}

@Composable
private fun UsbOtgDeviceRow(
    attachment: UsbOtgAttachment,
    connecting: Boolean,
    onConnectClick: () -> Unit,
) {
    val modeLabel = when (attachment.mode) {
        UsbOtgMode.Adb -> "ADB"
        UsbOtgMode.Fastboot -> "Fastboot"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Link,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modeLabel,
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    if (attachment.hasPermission) R.string.device_authorized else R.string.device_unauthorized,
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        FlatButton(onClick = onConnectClick, enabled = !connecting) {
            Text(stringResource(if (connecting) R.string.action_connecting else R.string.action_connect))
        }
    }
}

@Composable
private fun ManualConnectSection(
    endpoint: String,
    endpointError: String?,
    connectEnabled: Boolean,
    operationStatus: OperationStatus,
    onEndpointChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.home_manual_connect_title),
            trailing = {
                FlatButton(onClick = onConnectClick, enabled = connectEnabled, primary = true) {
                    Text(stringResource(R.string.action_connect))
                }
            },
        )
        TextField(
            value = endpoint,
            onValueChange = onEndpointChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.home_endpoint_label),
            singleLine = true,
        )
        endpointError?.let { Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error) }
        OperationStatusMessage(status = operationStatus)
    }
}

@Composable
private fun OperationStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = status.text,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
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
private fun RecentDevicesSection(
    devices: List<AdbDevice>,
    onDeviceClick: (AdbDevice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(title = stringResource(R.string.home_recent_devices_title))

        if (devices.isEmpty()) {
            EmptyState(title = stringResource(R.string.home_no_recent_devices))
        } else {
            devices.forEach { device ->
                RecentDeviceRow(
                    device = device,
                    onClick = { onDeviceClick(device) },
                )
            }
        }
    }
}

@Composable
private fun RecentDeviceRow(
    device: AdbDevice,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.ControlRadius))
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (device.linkKind) {
                    AdbLinkKind.Wifi -> AdbEndpoint.format(device.host, device.port)
                    AdbLinkKind.UsbOtg -> device.linkKind.label
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        AppStatusBadge(state = device.connectionState)
    }
}

@Preview(name = "手动连接 - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun ManualConnectSectionEmptyPreview() {
    AdbManagerTheme() {
        ManualConnectSection(
            endpoint = "",
            endpointError = null,
            connectEnabled = false,
            operationStatus = OperationStatus.Idle,
            onEndpointChanged = {},
            onConnectClick = {},
        )
    }
}

@Preview(name = "手动连接 - 错误态", showBackground = true, widthDp = 390)
@Composable
private fun ManualConnectSectionErrorPreview() {
    AdbManagerTheme() {
        ManualConnectSection(
            endpoint = "192.168.1.999:70000",
            endpointError = "请输入正确的 IP 地址或域名",
            connectEnabled = false,
            operationStatus = OperationStatus.Failed(
                text = "无法发起连接",
                suggestion = "请先检查 IP 地址和端口是否正确。",
            ),
            onEndpointChanged = {},
            onConnectClick = {},
        )
    }
}

@Preview(name = "最近设备", showBackground = true, widthDp = 390)
@Composable
private fun RecentDevicesSectionPreview() {
    AdbManagerTheme() {
        RecentDevicesSection(
            devices = listOf(
                AdbDevice(
                    id = "preview-tv",
                    name = "客厅电视",
                    host = "192.168.1.86",
                    port = 5555,
                    connectionState = ConnectionState.Connected,
                ),
            ),
            onDeviceClick = {},
        )
    }
}
