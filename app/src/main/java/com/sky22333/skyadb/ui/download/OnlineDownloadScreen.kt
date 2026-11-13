package com.sky22333.skyadb.ui.download

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sky22333.skyadb.ui.components.FlatButton
import com.sky22333.skyadb.ui.components.FlatTabRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.download.DownloadState
import com.sky22333.skyadb.download.DownloadTask
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun OnlineDownloadScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: OnlineDownloadViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnlineDownloadContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onModeChanged = viewModel::onModeChanged,
        onUrlChanged = viewModel::onUrlChanged,
        onTargetPathChanged = viewModel::onTargetPathChanged,
        onStartClick = viewModel::onStartClick,
        onCancelClick = viewModel::onCancelClick,
    )
}

@Composable
internal fun OnlineDownloadContent(
    bottomPadding: Dp = 0.dp,
    uiState: OnlineDownloadUiState,
    onBackClick: () -> Unit,
    onModeChanged: (OnlineDownloadMode) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTargetPathChanged: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = stringResource(R.string.online_download_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            DownloadFormSection(
                uiState = uiState,
                onModeChanged = onModeChanged,
                onUrlChanged = onUrlChanged,
                onTargetPathChanged = onTargetPathChanged,
                onStartClick = onStartClick,
                onCancelClick = onCancelClick,
            )
            DownloadTaskSection(task = uiState.task)
            DownloadStatusMessage(status = uiState.operationStatus)
        }
    }
}

@Composable
private fun DownloadFormSection(
    uiState: OnlineDownloadUiState,
    onModeChanged: (OnlineDownloadMode) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTargetPathChanged: (String) -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlatTabRow(
            tabs = OnlineDownloadMode.entries.map { stringResource(it.labelRes) },
            selectedTabIndex = OnlineDownloadMode.entries.indexOf(uiState.mode),
            onTabSelected = { onModeChanged(OnlineDownloadMode.entries[it]) },
            minWidth = 120.dp,
            maxWidth = 200.dp,
        )
        TextField(
            value = uiState.url,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.online_download_url_label),
            singleLine = true,
        )
        uiState.urlError?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }
        if (uiState.mode == OnlineDownloadMode.PushFile) {
            TextField(
                value = uiState.targetPath,
                onValueChange = onTargetPathChanged,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.online_download_target_path_label),
                singleLine = true,
            )
            if (uiState.targetPath.isBlank() && uiState.targetPathError == null) Text("/sdcard/Download/", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            uiState.targetPathError?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlatButton(
                onClick = onStartClick,
                primary = true,
                enabled = uiState.actionEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = MiuixIcons.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_start))
            }
            IconButton(
                onClick = onCancelClick,
                enabled = uiState.operationStatus is OperationStatus.Running,
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = if (uiState.operationStatus is OperationStatus.Running) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskSection(task: DownloadTask?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.online_download_task_status_title))
        if (task == null) {
            Text(
                text = stringResource(R.string.online_download_no_task),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        } else {
            Text(text = task.fileName, style = MiuixTheme.textStyles.subtitle)
            Text(
                text = task.message,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
            LinearProgressIndicator(
                progress = task.progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.online_download_status_format,
                    stringResource(task.state.labelRes),
                ),
                color = if (task.state == DownloadState.Failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                style = MiuixTheme.textStyles.footnote1,
            )
            if (task.targetPath.isNotBlank()) {
                Text(
                    text = stringResource(R.string.online_download_target_format, task.targetPath),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

@Composable
private fun DownloadStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(text = status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Success -> Text(text = status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Preview(name = "在线下载 - APK", showBackground = true, widthDp = 390)
@Composable
private fun OnlineDownloadApkPreview() {
    AdbManagerTheme() {
        OnlineDownloadContent(
            uiState = OnlineDownloadUiState(
                mode = OnlineDownloadMode.InstallApk,
                url = "https://example.com/app.apk",
                actionEnabled = true,
            ),
            onBackClick = {},
            onModeChanged = {},
            onUrlChanged = {},
            onTargetPathChanged = {},
            onStartClick = {},
            onCancelClick = {},
        )
    }
}

@Preview(name = "在线下载 - 文件推送", showBackground = true, widthDp = 390)
@Composable
private fun OnlineDownloadPushPreview() {
    AdbManagerTheme() {
        OnlineDownloadContent(
            uiState = OnlineDownloadUiState(
                mode = OnlineDownloadMode.PushFile,
                url = "https://example.com/config.json",
                targetPath = "/sdcard/Download/",
                actionEnabled = true,
                task = DownloadTask(
                    url = "https://example.com/config.json",
                    fileName = "config.json",
                    targetPath = "/sdcard/Download/config.json",
                    progress = 0.56f,
                    state = DownloadState.Downloading,
                    message = "已下载 56%",
                ),
            ),
            onBackClick = {},
            onModeChanged = {},
            onUrlChanged = {},
            onTargetPathChanged = {},
            onStartClick = {},
            onCancelClick = {},
        )
    }
}
