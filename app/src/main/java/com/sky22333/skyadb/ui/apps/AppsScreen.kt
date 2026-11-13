package com.sky22333.skyadb.ui.apps


import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.FlatTabRow
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.window.WindowDialog
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.PackageAppIcon
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun AppsScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: AppsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
        onResult = viewModel::exportPendingApp,
    )

    LaunchedEffect(Unit) {
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
    val filteredApps = remember(uiState.apps, uiState.filter, uiState.query) { uiState.filteredApps }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.apps_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick) {
                    Icon(imageVector = MiuixIcons.Refresh, contentDescription = stringResource(R.string.apps_refresh_list_desc))
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
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap)) {
                    TextField(
                        value = uiState.query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.apps_search_label),
                        singleLine = true,
                    )
                    AppFilterRow(
                        selected = uiState.filter,
                        apps = uiState.apps,
                        onFilterChanged = onFilterChanged,
                    )
                    if (uiState.operationStatus !is OperationStatus.Idle) {
                        AppsStatusMessage(status = uiState.operationStatus)
                    }
                    SectionHeader(
                        title = stringResource(R.string.apps_list_title),
                        description = stringResource(R.string.apps_list_desc_format, filteredApps.size),
                    )
                }
            }
            if (!uiState.loading && filteredApps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.apps_empty_title),
                        message = stringResource(R.string.apps_empty_message),
                    )
                }
            } else {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                ) { app ->
                    AppItemRow(
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
internal fun AppFilterRow(
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

    FlatTabRow(
        tabs = AppFilter.entries.map { "${stringResource(it.labelRes)} ${counts[it] ?: 0}" },
        selectedTabIndex = AppFilter.entries.indexOf(selected),
        onTabSelected = { onFilterChanged(AppFilter.entries[it]) },
        maxWidth = 120.dp,
    )
}

@Composable
private fun PendingActionDialog(
    pendingAction: AppPendingAction?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (pendingAction == null) return

    val title = stringResource(
        when (pendingAction) {
            is AppPendingAction.Uninstall -> R.string.apps_confirm_uninstall_title
            is AppPendingAction.SetEnabled -> {
                if (pendingAction.enabled) R.string.apps_confirm_enable_title else R.string.apps_confirm_freeze_title
            }
        },
    )
    val message = when (pendingAction) {
        is AppPendingAction.Uninstall ->
            stringResource(R.string.apps_uninstall_message_format, pendingAction.packageName)
        is AppPendingAction.SetEnabled -> when {
            pendingAction.enabled ->
                stringResource(R.string.apps_enable_message_format, pendingAction.packageName)
            pendingAction.isSystem -> stringResource(R.string.apps_freeze_system_warning)
            else -> stringResource(R.string.apps_freeze_message_format, pendingAction.packageName)
        }
    }
    val confirmLabel = stringResource(
        when (pendingAction) {
            is AppPendingAction.Uninstall -> R.string.action_uninstall
            is AppPendingAction.SetEnabled -> if (pendingAction.enabled) R.string.action_enable else R.string.action_freeze
        },
    )

    WindowDialog(show = true, onDismissRequest = onDismiss, title = title, summary = message) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text(confirmLabel) }
        }
    }
}

@Composable
private fun AppItemRow(
    app: AppInfo,
    onLaunchClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    onSetEnabledClick: (AppInfo, Boolean) -> Unit,
    onExportClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageAppIcon(packageName = app.packageName, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MiuixTheme.textStyles.subtitle,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
            )
        }
        Text(stringResource(app.statusLabelRes), style = MiuixTheme.textStyles.footnote1)
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

@Composable
internal fun AppActionMenu(
    app: AppInfo,
    onLaunchClick: (String) -> Unit,
    onStopClick: (String) -> Unit,
    onSetEnabledClick: (AppInfo, Boolean) -> Unit,
    onExportClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
) {
    val entries = DropdownEntry(
        listOf(
            DropdownItem(text = stringResource(R.string.action_launch), enabled = app.enabled, onClick = { onLaunchClick(app.packageName) }),
            DropdownItem(text = stringResource(R.string.action_stop), enabled = app.enabled, onClick = { onStopClick(app.packageName) }),
            DropdownItem(text = stringResource(if (app.enabled) R.string.action_freeze else R.string.action_enable), onClick = { onSetEnabledClick(app, !app.enabled) }),
            DropdownItem(text = stringResource(R.string.action_export), onClick = { onExportClick(app.packageName) }),
            DropdownItem(text = stringResource(R.string.action_uninstall), onClick = { onUninstallClick(app.packageName) }),
        ),
    )
    OverlayIconDropdownMenu(entry = entries, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = MiuixIcons.More,
            contentDescription = stringResource(R.string.apps_action_menu_desc),
            modifier = Modifier.size(18.dp),
        )
    }
}

private val AppInfo.statusLabelRes: Int
    get() = when {
        !enabled && isSystem -> R.string.app_status_system_frozen
        !enabled -> R.string.app_status_frozen
        isSystem -> R.string.app_status_system
        else -> R.string.app_status_user
    }

@Composable
private fun AppsStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is OperationStatus.Success -> Text(text = status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Preview(name = "应用管理 - 列表", showBackground = true, widthDp = 390)
@Composable
private fun AppsContentPreview() {
    AdbManagerTheme() {
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
