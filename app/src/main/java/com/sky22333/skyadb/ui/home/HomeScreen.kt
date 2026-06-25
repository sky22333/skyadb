package com.sky22333.skyadb.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceType
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppStatusBadge
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AppDimens
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
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
        val port = discoveredPort.toIntOrNull()
        if (discoveredHost.isNotBlank() && port != null) {
            viewModel.onDiscoveredEndpointSelected(discoveredHost, port)
            onDiscoveredEndpointConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(text = "设备连接")
                    Text(
                        text = uiState.connectionStateText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                SectionHeader(
                    title = "最近设备",
                    description = "成功连接过的设备会保存在这里，方便下次快速重连",
                    trailing = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新最近设备",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }

            if (uiState.recentDevices.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有历史设备",
                        message = "先通过手动连接或无线调试配对添加一台设备。",
                    )
                }
            } else {
                items(uiState.recentDevices, key = { it.id }) { device ->
                    RecentDeviceCard(
                        device = device,
                        onClick = { viewModel.onRecentDeviceSelected(device) },
                    )
                }
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
                description = "通过 OTG 线连接另一台 Android 设备（ADB 或 Fastboot）",
                trailing = {
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新 USB 设备",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )

            if (attachments.isEmpty()) {
                EmptyState(
                    title = "未检测到 USB 设备",
                    message = "请用 OTG 线连接目标设备，并确认已开启 USB 调试或处于 Fastboot 模式。",
                )
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
                    text = "VID ${attachment.vendorId.toString(16)} · PID ${attachment.productId.toString(16)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$modeLabel · ${if (attachment.hasPermission) "已授权" else "待授权"}",
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
                Text(text = if (connecting) "连接中…" else "连接")
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
            SectionHeader(
                title = "手动连接",
                description = "输入目标设备的 ADB 地址和端口",
            )

            OutlinedTextField(
                value = ip,
                onValueChange = onIpChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP 地址") },
                singleLine = true,
                placeholder = { Text("例如 192.168.1.86") },
                isError = ipError != null,
                supportingText = {
                    Text(ipError ?: "请确认目标设备与本机处于同一局域网")
                },
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError != null,
                supportingText = {
                    Text(portError ?: "默认 WiFi ADB 端口通常为 5555")
                },
            )

            OperationStatusMessage(status = operationStatus)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onConnectClick,
                    enabled = connectEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.AddLink, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "连接设备")
                }
                OutlinedButton(
                    onClick = onPairingClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "无线配对")
                }
            }
            OutlinedButton(
                onClick = onDiscoveryClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "发现局域网设备")
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
private fun RecentDeviceCard(
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
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(
                                when (device.linkKind) {
                                    AdbLinkKind.Wifi -> "${device.host}:${device.port}"
                                    AdbLinkKind.UsbOtg -> device.linkKind.label
                                },
                            )
                            append(" · ${device.type.label}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AppStatusBadge(state = device.connectionState)
            }

            Text(
                text = device.lastConnectedText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
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

@Preview(name = "最近设备卡片", showBackground = true, widthDp = 390)
@Composable
private fun RecentDeviceCardPreview() {
    AdbManagerTheme(dynamicColor = false) {
        RecentDeviceCard(
            device = AdbDevice(
                id = "preview-tv",
                name = "客厅电视",
                host = "192.168.1.86",
                port = 5555,
                type = DeviceType.Tv,
                connectionState = ConnectionState.Connected,
                lastConnectedText = "刚刚连接",
            ),
            onClick = {},
        )
    }
}
