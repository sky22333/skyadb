package com.sky22333.skyadb.ui.install

import com.sky22333.skyadb.ui.components.AppIcons

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sky22333.skyadb.ui.components.FlatButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.OperationProgressIndicator
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun InstallApkScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: InstallApkViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onApkSelected(uri)
    }

    InstallApkContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onPickClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
        onInstallClick = viewModel::onInstallClick,
    )
}

@Composable
internal fun InstallApkContent(
    bottomPadding: Dp = 0.dp,
    uiState: InstallApkUiState,
    onBackClick: () -> Unit,
    onPickClick: () -> Unit,
    onInstallClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = stringResource(R.string.install_apk_title),
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionHeader(
                    title = stringResource(R.string.install_apk_local_title),
                )
                Text(
                    text = uiState.selectedName ?: stringResource(R.string.install_apk_no_file_selected),
                    style = MiuixTheme.textStyles.subtitle,
                )
                if (uiState.selectedUriText != null) {
                    Text(
                        text = uiState.selectedUriText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                InstallStatusMessage(status = uiState.operationStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlatButton(onClick = onPickClick, modifier = Modifier.weight(1f)) {
                        Icon(imageVector = MiuixIcons.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pick_apk))
                    }
                    FlatButton(
                        onClick = onInstallClick,
                        primary = true,
                        enabled = uiState.installEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = AppIcons.Install, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OperationProgressIndicator(progress = status.progress)
                Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        is OperationStatus.Success -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Preview(name = "安装 APK", showBackground = true, widthDp = 390)
@Composable
private fun InstallApkContentPreview() {
    AdbManagerTheme() {
        InstallApkContent(
            uiState = InstallApkUiState(
                selectedName = "demo.apk",
                selectedUriText = "content://downloads/demo.apk",
                installEnabled = true,
            ),
            onBackClick = {},
            onPickClick = {},
            onInstallClick = {},
        )
    }
}
