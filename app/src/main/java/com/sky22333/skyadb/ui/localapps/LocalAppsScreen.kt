package com.sky22333.skyadb.ui.localapps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.localapps.LocalAppIcons
import com.sky22333.skyadb.localapps.LocalInstalledApp
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
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
    val uiState by viewModel.uiState.collectAsState()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalAppsContent(
    bottomPadding: Dp = 0.dp,
    uiState: LocalAppsUiState,
    onBackClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onInstallClick: (LocalInstalledApp) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.local_apps_title))
                    Text(
                        stringResource(R.string.local_apps_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick, enabled = !uiState.loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.local_apps_refresh_desc))
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
                    label = { Text(stringResource(R.string.local_apps_search_label)) },
                    placeholder = { Text(stringResource(R.string.local_apps_search_placeholder)) },
                    singleLine = true,
                )
            }
            item { LocalAppsStatus(status = uiState.operationStatus) }
            item {
                SectionHeader(
                    title = stringResource(R.string.local_apps_user_apps_title),
                    description = stringResource(R.string.local_apps_list_desc_format, uiState.filteredApps.size),
                )
            }
            if (uiState.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (!uiState.loading && uiState.filteredApps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.local_apps_empty_title),
                        message = stringResource(R.string.local_apps_empty_message),
                    )
                }
            } else {
                items(uiState.filteredApps, key = { it.packageName }) { app ->
                    LocalAppCard(
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
private fun LocalAppCard(
    app: LocalInstalledApp,
    installing: Boolean,
    onInstallClick: (LocalInstalledApp) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                LocalAppIcon(packageName = app.packageName)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (app.versionName.isBlank()) formatSize(app.apkSizeBytes) else "${app.versionName} · ${formatSize(app.apkSizeBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = { onInstallClick(app) },
                enabled = app.installable && !installing,
                modifier = Modifier.width(ActionWidth),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(if (app.installable) R.string.nav_install else R.string.local_apps_install_unsupported),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ActionWidth = 96.dp

@Composable
private fun LocalAppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState(
        initialValue = LocalAppIcons.peek(packageName)?.asImageBitmap(),
        key1 = packageName,
    ) {
        if (value != null) return@produceState
        value = LocalAppIcons.load(context, packageName)?.asImageBitmap()
    }
    val icon = bitmap
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                Text(status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is OperationStatus.Success -> Text(status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
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
    AdbManagerTheme(dynamicColor = false) {
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
