package com.sky22333.skyadb.ui.screenshot

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val uiState by viewModel.uiState.collectAsState()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotContent(
    bottomPadding: Dp = 0.dp,
    uiState: ScreenshotUiState,
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    val preview by produceState<ImageBitmap?>(initialValue = null, key1 = uiState.latestLocalPath) {
        value = withContext(Dispatchers.IO) {
            decodePreviewImage(uiState.latestLocalPath)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.screenshot_title))
                    Text(
                        stringResource(R.string.screenshot_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimens.CardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.screenshot_task_title),
                        description = stringResource(R.string.screenshot_task_desc),
                    )
                    Text(
                        text = uiState.latestFileName ?: stringResource(R.string.screenshot_no_screenshot_yet),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (uiState.latestLocalPath != null) {
                        ScreenshotPreview(preview = preview)
                    }
                    ScreenshotStatus(status = uiState.operationStatus)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onCaptureClick, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.nav_screenshot))
                        }
                        OutlinedButton(
                            onClick = onSaveClick,
                            enabled = uiState.saveEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(imageVector = Icons.Outlined.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                    OutlinedButton(
                        onClick = onClearClick,
                        enabled = uiState.latestLocalPath != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_clear_preview))
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
            Text(status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is OperationStatus.Success -> Text(status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(name = "设备截图", showBackground = true, widthDp = 390)
@Composable
private fun ScreenshotContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
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
private fun ScreenshotPreview(preview: ImageBitmap?) {
    if (preview == null) {
        Text(
            text = stringResource(R.string.screenshot_preview_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Image(
            bitmap = preview,
            contentDescription = stringResource(R.string.screenshot_preview_desc),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
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
