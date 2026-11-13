package com.sky22333.skyadb.ui.localapps


import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import com.sky22333.skyadb.ui.components.FlatButton
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.localapps.LocalInstalledApp
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.PackageAppIcon
import com.sky22333.skyadb.ui.components.OperationProgressIndicator
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import java.util.Locale
@Composable
fun LocalAppsScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: LocalAppsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    LocalAppsContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onQueryChanged = viewModel::onQueryChanged,
        onRefreshClick = { viewModel.loadApps(force = true) },
        onInstallClick = viewModel::installToDevice,
    )
}

@Composable
private fun LocalAppsContent(
    bottomPadding: Dp = 0.dp,
    uiState: LocalAppsUiState,
    onBackClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onInstallClick: (LocalInstalledApp) -> Unit,
) {
    val filteredApps = remember(uiState.apps, uiState.query) { uiState.filteredApps }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.local_apps_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick, enabled = !uiState.loading) {
                    Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.local_apps_refresh_desc))
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
                        label = stringResource(R.string.local_apps_search_label),
                        singleLine = true,
                    )
                    if (uiState.operationStatus !is OperationStatus.Idle) {
                        LocalAppsStatus(status = uiState.operationStatus)
                    }
                    SectionHeader(
                        title = stringResource(R.string.local_apps_user_apps_title),
                        description = stringResource(R.string.local_apps_list_desc_format, filteredApps.size),
                    )
                }
            }
            if (!uiState.loading && filteredApps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.local_apps_empty_title),
                        message = stringResource(R.string.local_apps_empty_message),
                    )
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    LocalAppRow(
                        app = app,
                        installing = uiState.operationStatus is OperationStatus.Running,
                        onInstallClick = onInstallClick,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LocalAppRow(
    app: LocalInstalledApp,
    installing: Boolean,
    onInstallClick: (LocalInstalledApp) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageAppIcon(packageName = app.packageName, modifier = Modifier.size(38.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = app.label,
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (app.versionName.isBlank()) formatSize(app.apkSizeBytes) else "${app.versionName} · ${formatSize(app.apkSizeBytes)}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (app.installable) {
            FlatButton(onClick = { onInstallClick(app) }, enabled = !installing) {
                Text(stringResource(R.string.nav_install))
            }
        } else {
            Text(
                text = stringResource(R.string.local_apps_install_unsupported),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalAppsStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OperationProgressIndicator(progress = status.progress)
                Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
        }
        is OperationStatus.Success -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return appString(R.string.local_apps_unknown_size)
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${bytes / 1024} KB"
    }
}

@Preview(name = "本机应用", showBackground = true, widthDp = 390)
@Composable
private fun LocalAppsContentPreview() {
    AdbManagerTheme() {
        LocalAppsContent(
            uiState = LocalAppsUiState(
                apps = listOf(
                    LocalInstalledApp(
                        packageName = "com.example.player",
                        label = "视频播放器",
                        sourcePath = "/data/app/player/base.apk",
                        versionName = "1.2.0",
                        isSingleApk = true,
                        apkSizeBytes = 18_500_000,
                    ),
                    LocalInstalledApp(
                        packageName = "com.example.split",
                        label = "拆分应用",
                        sourcePath = "/data/app/split/base.apk",
                        versionName = "2.0.0",
                        isSingleApk = false,
                        apkSizeBytes = 0,
                    ),
                ),
            ),
            onBackClick = {},
            onQueryChanged = {},
            onRefreshClick = {},
            onInstallClick = {},
        )
    }
}
