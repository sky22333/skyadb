package com.sky22333.skyadb.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.FlatButton
import com.sky22333.skyadb.ui.theme.AppDimens
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun QrScanScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    onConnected: () -> Unit,
    viewModel: QrScanViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    QrScanContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        permissionGranted = permissionGranted,
        onBackClick = onBackClick,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onQrScanned = viewModel::onQrScanned,
        onRetry = viewModel::reset,
        onConnected = onConnected,
    )
}

@Composable
private fun QrScanContent(
    bottomPadding: Dp,
    uiState: QrScanUiState,
    permissionGranted: Boolean,
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onQrScanned: (String) -> Unit,
    onRetry: () -> Unit,
    onConnected: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(bottom = bottomPadding),
    ) {
        SmallTopAppBar(
            title = stringResource(R.string.scan_qr_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(AppDimens.ControlRadius))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (permissionGranted) {
                    QrCameraPreview(
                        enabled = !uiState.scanLocked,
                        onQrScanned = onQrScanned,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ScanFrame()
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.scan_camera_permission_required),
                            color = Color.White,
                        )
                        Button(onClick = onRequestPermission) {
                            Text(stringResource(R.string.scan_grant_camera))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.scan_qr_instruction),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
            ScanStatus(
                status = uiState.status,
                busy = uiState.busy,
                connected = uiState.connectedEndpoint != null,
                onRetry = onRetry,
                onConnected = onConnected,
            )
        }
    }
}

@Composable
private fun ScanFrame() {
    Box(
        modifier = Modifier
            .size(230.dp)
            .border(2.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(24.dp))
    )
}

@Composable
private fun ScanStatus(
    status: OperationStatus,
    busy: Boolean,
    connected: Boolean,
    onRetry: () -> Unit,
    onConnected: () -> Unit,
) {
    when {
        busy -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            (status as? OperationStatus.Running)?.let {
                Text(
                    text = it.text,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
        connected -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (status as? OperationStatus.Success)?.let {
                Text(it.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            FlatButton(onClick = onConnected, primary = true, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
        }
        status is OperationStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_scan_again))
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    enabled: Boolean,
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )

    LaunchedEffect(previewView, enabled, lifecycleOwner) {
        if (!enabled) return@LaunchedEffect
        val provider = runCatching { context.awaitCameraProvider() }.getOrElse { return@LaunchedEffect }
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = QrImageAnalyzer(onQrScanned)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            awaitCancellation()
        } catch (_: Throwable) {
            // The camera may be unavailable or occupied; the page remains usable for retry/back.
        } finally {
            provider.unbindAll()
            executor.shutdownNow()
        }
    }
}

private class QrImageAnalyzer(
    private val onQrScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) return
            val luminance = image.luminanceSource() ?: return
            decodeAnyRotation(luminance)?.let(onQrScanned)
        } finally {
            image.close()
        }
    }

    private fun decodeAnyRotation(source: LuminanceSource): String? {
        var current = source
        repeat(4) {
            try {
                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(current)))
                reader.reset()
                return result.text
            } catch (_: Throwable) {
                reader.reset()
            }
            current = runCatching { current.rotateCounterClockwise() }.getOrNull() ?: return null
        }
        return null
    }

    private fun ImageProxy.luminanceSource(): LuminanceSource? {
        val plane = planes.firstOrNull() ?: return null
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return null
        val buffer = plane.buffer.duplicate()
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bytes = ByteArray(width * height)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val index = row * rowStride + column * pixelStride
                if (index >= buffer.limit()) return null
                bytes[row * width + column] = buffer.get(index)
            }
        }
        return PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
    }
}

private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(this),
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }
