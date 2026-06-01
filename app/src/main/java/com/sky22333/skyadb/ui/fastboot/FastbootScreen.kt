package com.sky22333.skyadb.ui.fastboot

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AssistChip
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
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.fastboot.FastbootUsbDevice
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun FastbootScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: FastbootViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImageSelected(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDevices()
    }

    FastbootContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshDevices,
        onDeviceSelected = viewModel::selectDevice,
        onPermissionClick = viewModel::requestPermission,
        onConnectClick = viewModel::connect,
        onDisconnectClick = viewModel::disconnect,
        onCommandChanged = viewModel::onCommandChanged,
        onPickImageClick = { imagePicker.launch(arrayOf("*/*")) },
        onClearImageClick = viewModel::clearImage,
        onExecuteClick = viewModel::execute,
        onConfirmCommand = viewModel::confirmPendingCommand,
        onCancelCommand = viewModel::cancelPendingCommand,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastbootContent(
    bottomPadding: Dp = 0.dp,
    uiState: FastbootUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onPermissionClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onCommandChanged: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onClearImageClick: () -> Unit,
    onExecuteClick: () -> Unit,
    onConfirmCommand: () -> Unit,
    onCancelCommand: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Fastboot") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick, enabled = !uiState.running) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新 Fastboot 设备")
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
                DeviceCard(
                    uiState = uiState,
                    onDeviceSelected = onDeviceSelected,
                    onPermissionClick = onPermissionClick,
                    onConnectClick = onConnectClick,
                    onDisconnectClick = onDisconnectClick,
                )
            }
            item {
                CommandCard(
                    uiState = uiState,
                    onCommandChanged = onCommandChanged,
                    onPickImageClick = onPickImageClick,
                    onClearImageClick = onClearImageClick,
                    onExecuteClick = onExecuteClick,
                )
            }
            item {
                PendingCommandCard(
                    pendingCommand = uiState.pendingCommand,
                    onConfirmCommand = onConfirmCommand,
                    onCancelCommand = onCancelCommand,
                )
            }
            item { FastbootStatus(status = uiState.status, running = uiState.running) }
            if (uiState.output.isNotBlank()) {
                item { OutputCard(output = uiState.output) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCard(
    uiState: FastbootUiState,
    onDeviceSelected: (String) -> Unit,
    onPermissionClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("USB Fastboot 设备", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "进入 bootloader 后授权并连接",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                uiState.connectedDevice?.let { AssistChip(onClick = {}, label = { Text("已连接") }) }
            }

            DeviceSelector(
                devices = uiState.devices,
                selectedDevice = uiState.selectedDevice,
                onDeviceSelected = onDeviceSelected,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPermissionClick,
                    enabled = uiState.selectedDevice != null && uiState.selectedDevice?.hasPermission == false && !uiState.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("授权")
                }
                Button(
                    onClick = onConnectClick,
                    enabled = uiState.selectedDevice?.hasPermission == true && !uiState.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("连接")
                }
                OutlinedButton(
                    onClick = onDisconnectClick,
                    enabled = uiState.connectedDevice != null && !uiState.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("断开")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelector(
    devices: List<FastbootUsbDevice>,
    selectedDevice: FastbootUsbDevice?,
    onDeviceSelected: (String) -> Unit,
) {
    if (devices.isEmpty()) {
        Text(
            text = "未发现 Fastboot 设备",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            OutlinedButton(
                onClick = { onDeviceSelected(device.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${if (device.id == selectedDevice?.id) "已选择 " else ""}${device.name} (${device.vendorId.toString(16)}:${device.productId.toString(16)})",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CommandCard(
    uiState: FastbootUiState,
    onCommandChanged: (String) -> Unit,
    onPickImageClick: () -> Unit,
    onClearImageClick: () -> Unit,
    onExecuteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.command,
                onValueChange = onCommandChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fastboot 命令") },
                placeholder = { Text("getvar:all / flash:boot / erase:userdata") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickImageClick, enabled = !uiState.running, modifier = Modifier.weight(1f)) {
                    Text(if (uiState.imageName == null) "选择镜像" else "更换镜像")
                }
                OutlinedButton(
                    onClick = onClearImageClick,
                    enabled = uiState.imageName != null && !uiState.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清除")
                }
            }
            uiState.imageName?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onExecuteClick,
                enabled = uiState.connectedDevice != null && !uiState.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("执行")
            }
        }
    }
}

@Composable
private fun PendingCommandCard(
    pendingCommand: PendingFastbootCommand?,
    onConfirmCommand: () -> Unit,
    onCancelCommand: () -> Unit,
) {
    if (pendingCommand == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("确认执行高风险命令", color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text = pendingCommand.command,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            pendingCommand.imageName?.let {
                Text("镜像：$it", color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancelCommand) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("取消")
                }
                Button(onClick = onConfirmCommand) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("确认执行")
                }
            }
        }
    }
}

@Composable
private fun FastbootStatus(status: OperationStatus, running: Boolean) {
    if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is OperationStatus.Success -> Text(status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = "${status.text}，${status.suggestion}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OutputCard(output: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            text = output,
            modifier = Modifier.padding(AppDimens.CardPadding),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(name = "Fastboot", showBackground = true, widthDp = 390)
@Composable
private fun FastbootContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        FastbootContent(
            uiState = FastbootUiState(
                devices = listOf(
                    FastbootUsbDevice("/dev/bus/usb/001/002", "Pixel Fastboot", 0x18d1, 0x4ee0, true),
                ),
                selectedDeviceId = "/dev/bus/usb/001/002",
                output = "product: raven\nunlocked: yes",
                status = OperationStatus.Success("Fastboot 命令执行完成"),
            ),
            onBackClick = {},
            onRefreshClick = {},
            onDeviceSelected = {},
            onPermissionClick = {},
            onConnectClick = {},
            onDisconnectClick = {},
            onCommandChanged = {},
            onPickImageClick = {},
            onClearImageClick = {},
            onExecuteClick = {},
            onConfirmCommand = {},
            onCancelCommand = {},
        )
    }
}
