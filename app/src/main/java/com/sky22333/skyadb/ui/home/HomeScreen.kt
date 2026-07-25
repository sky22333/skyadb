package com.sky22333.skyadb.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceType
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.usb.UsbOtgAttachment
import com.sky22333.skyadb.usb.UsbOtgMode

@OptIn(ExperimentalMaterial3Api::class)
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
    val uiState by viewModel.uiState.collectAsState()

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
            title = { Text(text = stringResource(R.string.home_title)) },
            actions = {
                if (uiState.canDisconnect) {
                    IconButton(onClick = viewModel::onDisconnectClicked) {
                        Icon(
                            imageVector = Icons.Outlined.LinkOff,
                            contentDescription = stringResource(R.string.action_disconnect),
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
                ManualConnectCard(
                    ip = uiState.ip,
                    port = uiState.port,
                    ipError = uiState.ipError,
                    portError = uiState.portError,
                    connectEnabled = uiState.connectEnabled,
                    operationStatus = uiState.operationStatus,
                    onIpChanged = viewModel::onIpChanged,
                    onPortChanged = viewModel::onPortChanged,
                    onConnectClick = viewModel::onConnectClicked,
                    onPairingClick = onPairingClick,
                    onDiscoveryClick = onDiscoveryClick,
                )
            }

            item {
                UsbOtgConnectCard(
                    attachments = uiState.usbAttachments,
                    connectingDeviceName = uiState.connectingUsbDeviceName,
                    onRefreshClick = viewModel::refreshUsbDevices,
                    onConnectClick = viewModel::onUsbConnectClicked,
                )
            }

            item {
                RecentDevicesCard(
                    devices = uiState.recentDevices,
                    onDeviceClick = viewModel::onRecentDeviceSelected,
                )
            }
        }
    }
}

@Composable
private fun UsbOtgConnectCard(
    attachments: List<UsbOtgAttachment>,
    connectingDeviceName: String?,
    onRefreshClick: () -> Unit,
    onConnectClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
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
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.home_refresh_usb_desc),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            if (attachments.isEmpty()) {
                EmptyState(title = stringResource(R.string.home_no_usb_devices))
            } else {
                attachments.forEach { attachment ->
                    UsbOtgDeviceRow(
                        attachment = attachment,
                        connecting = attachment.deviceName == connectingDeviceName,
                        onConnectClick = { onConnectClick(attachment.deviceName) },
                    )
                }
            }
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
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (attachment.hasPermission) R.string.device_authorized else R.string.device_unauthorized,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onConnectClick,
                enabled = !connecting,
            ) {
                Icon(imageVector = Icons.Outlined.Cable, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(if (connecting) R.string.action_connecting else R.string.action_connect))
            }
        }
    }
}

@Composable
private fun ManualConnectCard(
    ip: String,
    port: String,
    ipError: String?,
    portError: String?,
    connectEnabled: Boolean,
    operationStatus: OperationStatus,
    onIpChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
    onPairingClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.home_manual_connect_title))

            OutlinedTextField(
                value = ip,
                onValueChange = onIpChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_ip_label)) },
                singleLine = true,
                placeholder = { Text("192.168.1.86") },
                isError = ipError != null,
                supportingText = ipError?.let { { Text(it) } },
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.unit_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
            )

            OperationStatusMessage(status = operationStatus)

            Button(
                onClick = onConnectClick,
                enabled = connectEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.AddLink, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.action_connect))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = onPairingClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_wireless_pairing),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
                TextButton(
                    onClick = onDiscoveryClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_discover_devices),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Failed -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.CardRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = status.text,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = status.suggestion,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RecentDevicesCard(
    devices: List<AdbDevice>,
    onDeviceClick: (AdbDevice) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
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
}

@Composable
private fun RecentDeviceRow(
    device: AdbDevice,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (device.linkKind) {
                        AdbLinkKind.Wifi -> "${device.host}:${device.port}"
                        AdbLinkKind.UsbOtg -> device.linkKind.label
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            AppStatusBadge(state = device.connectionState)
        }
    }
}

@Preview(name = "手动连接 - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun ManualConnectCardEmptyPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ManualConnectCard(
            ip = "",
            port = "5555",
            ipError = null,
            portError = null,
            connectEnabled = false,
            operationStatus = OperationStatus.Idle,
            onIpChanged = {},
            onPortChanged = {},
            onConnectClick = {},
            onPairingClick = {},
            onDiscoveryClick = {},
        )
    }
}

@Preview(name = "手动连接 - 错误态", showBackground = true, widthDp = 390)
@Composable
private fun ManualConnectCardErrorPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ManualConnectCard(
            ip = "192.168.1.999",
            port = "70000",
            ipError = "请输入正确的 IPv4 地址",
            portError = "端口范围应为 1-65535",
            connectEnabled = false,
            operationStatus = OperationStatus.Failed(
                text = "无法发起连接",
                suggestion = "请先检查 IP 地址和端口是否正确。",
            ),
            onIpChanged = {},
            onPortChanged = {},
            onConnectClick = {},
            onPairingClick = {},
            onDiscoveryClick = {},
        )
    }
}

@Preview(name = "最近设备", showBackground = true, widthDp = 390)
@Composable
private fun RecentDevicesCardPreview() {
    AdbManagerTheme(dynamicColor = false) {
        RecentDevicesCard(
            devices = listOf(
                AdbDevice(
                    id = "preview-tv",
                    name = "客厅电视",
                    host = "192.168.1.86",
                    port = 5555,
                    type = DeviceType.Tv,
                    connectionState = ConnectionState.Connected,
                    lastConnectedText = "刚刚连接",
                ),
            ),
            onDeviceClick = {},
        )
    }
}
