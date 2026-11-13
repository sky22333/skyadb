package com.sky22333.skyadb.ui.screenshot

import top.yukonga.miuix.kmp.icon.extended.ScreenCapture

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenshotScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: ScreenshotViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        viewModel.saveToUri(context, uri)
    }

    ScreenshotContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onCaptureClick = { viewModel.capture(context) },
        onSaveClick = { saveLauncher.launch(uiState.latestFileName ?: "screenshot.png") },
        onClearClick = viewModel::clearPreview,
    )
}

@Composable
internal fun ScreenshotContent(
    bottomPadding: Dp = 0.dp,
    uiState: ScreenshotUiState,
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    val preview = key(uiState.latestLocalPath) {
        produceState<PreviewState>(initialValue = PreviewState.Loading) {
            val image = withContext(Dispatchers.IO) { decodePreviewImage(uiState.latestLocalPath) }
            value = if (image == null) PreviewState.Failed else PreviewState.Ready(image)
        }.value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = stringResource(R.string.screenshot_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onClearClick, enabled = uiState.latestLocalPath != null) {
                    Icon(
                        MiuixIcons.Clear,
                        contentDescription = stringResource(R.string.action_clear_preview),
                        tint = if (uiState.latestLocalPath != null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
                    )
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
                    title = stringResource(R.string.screenshot_task_title),
                )
                Text(
                    text = uiState.latestFileName ?: stringResource(R.string.screenshot_no_screenshot_yet),
                    style = MiuixTheme.textStyles.subtitle,
                )
                if (uiState.latestLocalPath != null) {
                    ScreenshotPreview(preview = preview)
                }
                ScreenshotStatus(status = uiState.operationStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlatButton(onClick = onCaptureClick, modifier = Modifier.weight(1f), primary = true) {
                        Icon(imageVector = MiuixIcons.ScreenCapture, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_screenshot))
                    }
                    FlatButton(
                        onClick = onSaveClick,
                        enabled = uiState.saveEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = MiuixIcons.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        is OperationStatus.Success -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Preview(name = "设备截图", showBackground = true, widthDp = 390)
@Composable
private fun ScreenshotContentPreview() {
    AdbManagerTheme() {
        ScreenshotContent(
            uiState = ScreenshotUiState(
                latestFileName = "screenshot.png",
                latestLocalPath = "/cache/screenshots/screenshot.png",
                saveEnabled = true,
                operationStatus = OperationStatus.Success("截图已生成，请选择保存位置"),
            ),
            onBackClick = {},
            onCaptureClick = {},
            onSaveClick = {},
            onClearClick = {},
        )
    }
}

@Composable
private fun ScreenshotPreview(preview: PreviewState) {
    when (preview) {
        PreviewState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        PreviewState.Failed -> Text(
            text = stringResource(R.string.screenshot_preview_failed),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
        is PreviewState.Ready -> Image(
            bitmap = preview.image,
            contentDescription = stringResource(R.string.screenshot_preview_desc),
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data object Failed : PreviewState
    data class Ready(val image: ImageBitmap) : PreviewState
}

private fun decodePreviewImage(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > PreviewMaxSize || bounds.outHeight / sampleSize > PreviewMaxSize) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

private const val PreviewMaxSize = 1600
