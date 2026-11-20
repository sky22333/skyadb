package com.sky22333.skyadb.ui.camera

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.scrcpy.ScrcpyCameraFacing
import com.sky22333.skyadb.ui.components.AppIcons
import com.sky22333.skyadb.ui.components.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CameraPreviewScreen(
    onBackClick: () -> Unit,
    viewModel: CameraPreviewViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = cameraSurface(
                    containerWidth = maxWidth.value,
                    containerHeight = maxHeight.value,
                    videoWidth = uiState.videoWidth,
                    videoHeight = uiState.videoHeight,
                ),
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(
                            object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    viewModel.onSurfaceCreated(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    viewModel.onSurfaceDestroyed()
                                }
                            }
                        )
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .wrapContentWidth(Alignment.End)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::switchFacing) {
                Icon(
                    AppIcons.SwitchCamera,
                    contentDescription = stringResource(R.string.camera_preview_switch),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onBackClick) {
                Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back), tint = Color.White)
            }
        }

        CameraStatusCard(
            status = uiState.status,
            facing = uiState.facing,
            onRetry = viewModel::retry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        )
    }
}

private fun cameraSurface(
    containerWidth: Float,
    containerHeight: Float,
    videoWidth: Int,
    videoHeight: Int,
): Modifier {
    if (containerWidth <= 0f || containerHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) {
        return Modifier.fillMaxSize()
    }
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val containerRatio = containerWidth / containerHeight
    return if (containerRatio > videoRatio) {
        Modifier.fillMaxHeight().aspectRatio(videoRatio)
    } else {
        Modifier.fillMaxWidth().aspectRatio(videoRatio)
    }
}

@Composable
private fun CameraStatusCard(
    status: OperationStatus,
    facing: ScrcpyCameraFacing,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val facingLabel = stringResource(
        if (facing == ScrcpyCameraFacing.Front) R.string.camera_preview_front
        else R.string.camera_preview_back
    )
    Card(
        modifier = modifier,
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_preview_current, facingLabel),
                color = Color.White,
                style = MiuixTheme.textStyles.footnote1,
            )
            when (status) {
                OperationStatus.Idle -> Unit
                is OperationStatus.Running -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(status.text, color = Color.White.copy(alpha = 0.78f), style = MiuixTheme.textStyles.footnote1)
                }
                is OperationStatus.Success -> Unit
                is OperationStatus.Failed -> {
                    Text(
                        text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
                        color = Color(0xFFFFB4AB),
                        style = MiuixTheme.textStyles.footnote1,
                    )
                    Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}
