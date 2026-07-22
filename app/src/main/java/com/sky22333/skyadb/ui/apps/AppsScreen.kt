package com.sky22333.skyadb.ui.apps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.localapps.LocalAppIcons
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun AppsScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: AppsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
        onResult = viewModel::exportPendingApp,
    )

    LaunchedEffect(Unit) {
        // 先让进页转场画完一帧，再触发 loading 重组，避免 Skipped frames
        withFrameNanos { }
        viewModel.loadApps()
    }

    LaunchedEffect(uiState.pendingExportPackage) {
        uiState.pendingExportPackage?.let { packageName ->
            exportLauncher.launch("$packageName.apk")
        }
    }

    AppsContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onQueryChanged = viewModel::onQueryChanged,
        onFilterChanged = viewModel::onFilterChanged,
        onRefreshClick = { viewModel.loadApps(force = true) },
        onLaunchClick = viewModel::launchApp,
        onStopClick = viewModel::forceStopApp,
        onSetEnabledClick = viewModel::setAppEnabled,
        onExportClick = viewModel::requestExport,
        onUninstallClick = viewModel::uninstallApp,
        onCancelPendingAction = viewModel::cancelPendingAction,
        onConfirmPendingAction = viewModel::confirmPendingAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsContent(
    bottomPadding: Dp = 0.dp,
    uiState: AppsUiState,
    onBackClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (AppFilter) -> Unit,
    onRefreshClick: () -> Unit,
    onLaunchClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    onSetEnabledClick: (AppInfo, Boolean) -> Unit,
    onExportClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onCancelPendingAction: () -> Unit,
    onConfirmPendingAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(text = "应用管理")
                    Text(
                        text = "查看、启动、停止或卸载应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "刷新应用列表")
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
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索应用") },
                    singleLine = true,
                    placeholder = { Text("输入包名或应用名") },
                )
            }
            item {
                AppFilterRow(
                    selected = uiState.filter,
                    apps = uiState.apps,
                    onFilterChanged = onFilterChanged,
                )
            }
            item { AppsStatusMessage(status = uiState.operationStatus) }
            item {
                SectionHeader(
                    title = "应用列表",
                    description = "共 ${uiState.filteredApps.size} 个，分类和搜索在本地即时过滤",
                )
            }
            if (uiState.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (!uiState.loading && uiState.filteredApps.isEmpty()) {
                item { EmptyState(title = "暂无应用", message = "连接设备并刷新后，应用会显示在这里。") }
            } else {
                items(
                    items = uiState.filteredApps,
                    key = { it.packageName },
                ) { app ->
                    AppItemCard(
                        app = app,
                        onLaunchClick = onLaunchClick,
                        onStopClick = onStopClick,
                        onSetEnabledClick = onSetEnabledClick,
                        onExportClick = onExportClick,
                        onUninstallClick = onUninstallClick,
                    )
                }
            }
        }
    }

    PendingActionDialog(
        pendingAction = uiState.pendingAction,
        onDismiss = onCancelPendingAction,
        onConfirm = onConfirmPendingAction,
    )
}

@Composable
private fun AppFilterRow(
    selected: AppFilter,
    apps: List<AppInfo>,
    onFilterChanged: (AppFilter) -> Unit,
) {
    val userCount = apps.count { !it.isSystem }
    val systemCount = apps.count { it.isSystem }
    val counts = mapOf(
        AppFilter.All to apps.size,
        AppFilter.User to userCount,
        AppFilter.System to systemCount,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilterChanged(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") },
            )
        }
    }
}

@Composable
private fun PendingActionDialog(
    pendingAction: AppPendingAction?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (pendingAction == null) return

    val title = when (pendingAction) {
        is AppPendingAction.Uninstall -> "确认卸载应用？"
        is AppPendingAction.SetEnabled -> if (pendingAction.enabled) "确认启用应用？" else "确认冻结应用？"
    }
    val message = when (pendingAction) {
        is AppPendingAction.Uninstall -> "将从目标设备卸载 ${pendingAction.packageName}。"
        is AppPendingAction.SetEnabled -> when {
            pendingAction.enabled -> "将恢复 ${pendingAction.packageName} 的可用状态。"
            pendingAction.isSystem -> "冻结系统应用可能影响桌面、设置或播放功能。"
            else -> "冻结后 ${pendingAction.packageName} 将无法启动。"
        }
    }
    val confirmLabel = when (pendingAction) {
        is AppPendingAction.Uninstall -> "卸载"
        is AppPendingAction.SetEnabled -> if (pendingAction.enabled) "启用" else "冻结"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AppItemCard(
    app: AppInfo,
    onLaunchClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    onSetEnabledClick: (AppInfo, Boolean) -> Unit,
    onExportClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceAppIcon(app = app)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(app.statusLabel) },
            )
            AppActionMenu(
                app = app,
                onLaunchClick = onLaunchClick,
                onStopClick = onStopClick,
                onSetEnabledClick = onSetEnabledClick,
                onExportClick = onExportClick,
                onUninstallClick = onUninstallClick,
            )
        }
    }
}

@Composable
private fun DeviceAppIcon(app: AppInfo) {
    val context = LocalContext.current
    val bitmap by produceState(
        initialValue = LocalAppIcons.peek(app.packageName)?.asImageBitmap(),
        key1 = app.packageName,
    ) {
        if (value != null) return@produceState
        value = LocalAppIcons.load(context, app.packageName)?.asImageBitmap()
    }
    Card(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSystem) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        val icon = bitmap
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (app.isSystem) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
    }
}

@Composable
private fun AppActionMenu(
    app: AppInfo,
    onLaunchClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    onSetEnabledClick: (AppInfo, Boolean) -> Unit,
    onExportClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "应用操作",
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("启动") },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                enabled = app.enabled,
                onClick = {
                    expanded = false
                    onLaunchClick(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text("停止") },
                leadingIcon = { Icon(Icons.Outlined.StopCircle, contentDescription = null) },
                enabled = app.enabled,
                onClick = {
                    expanded = false
                    onStopClick(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text(if (app.enabled) "冻结" else "启用") },
                leadingIcon = {
                    Icon(
                        imageVector = if (app.enabled) Icons.Outlined.Close else Icons.Outlined.Check,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onSetEnabledClick(app, !app.enabled)
                },
            )
            DropdownMenuItem(
                text = { Text("导出") },
                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                onClick = {
                    expanded = false
                    onExportClick(app.packageName)
                },
            )
            DropdownMenuItem(
                text = { Text("卸载") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onUninstallClick(app.packageName)
                },
            )
        }
    }
}

private val AppInfo.statusLabel: String
    get() = when {
        !enabled && isSystem -> "系统冻结"
        !enabled -> "已冻结"
        isSystem -> "系统"
        else -> "用户"
    }

@Composable
private fun AppsStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is OperationStatus.Success -> Text(text = status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = "${status.text}：${status.suggestion}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(name = "应用管理 - 列表", showBackground = true, widthDp = 390)
@Composable
private fun AppsContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        AppsContent(
            uiState = AppsUiState(
                apps = listOf(
                    AppInfo("com.android.tv.settings", "settings", true),
                    AppInfo("com.example.player", "player", false),
                ),
                operationStatus = OperationStatus.Success("已读取 2 个应用"),
            ),
            onBackClick = {},
            onQueryChanged = {},
            onFilterChanged = {},
            onRefreshClick = {},
            onLaunchClick = {},
            onStopClick = {},
            onSetEnabledClick = { _, _ -> },
            onExportClick = {},
            onUninstallClick = {},
            onCancelPendingAction = {},
            onConfirmPendingAction = {},
        )
    }
}
