package com.qwadb.app.ui.camera

import android.view.Surface
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.scrcpy.CameraFace
import com.qwadb.app.ui.ImmersiveFullscreen

@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    if (!uiState.started) {
        CameraSelectUi(
            onStart = viewModel::start,
            onBackClick = onBackClick,
        )
    } else {
        CameraDisplayUi(
            uiState = uiState,
            viewModel = viewModel,
            onBackClick = {
                viewModel.stop()
                onBackClick()
            },
        )
    }
}

/** 摄像头选择阶段：前置 / 后置。 */
@Composable
private fun CameraSelectUi(
    onStart: (CameraFace) -> Unit,
    onBackClick: () -> Unit,
) {
    var mode by remember { mutableStateOf(0) }
    val selection = when (mode) {
        1 -> CameraFace.Front
        else -> CameraFace.Back
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.camera_select_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.camera_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(stringResource(R.string.camera_face_back)) },
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(stringResource(R.string.camera_face_front)) },
                    )
                    Button(
                        onClick = { onStart(selection) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.camera_start_view))
                    }
                }
            }
        }
    }
}

/** 摄像头显示阶段：单摄全屏 + 底部拍照/录像/手电筒控制栏。 */
@Composable
private fun CameraDisplayUi(
    uiState: CameraUiState,
    viewModel: CameraViewModel,
    onBackClick: () -> Unit,
) {
    ImmersiveFullscreen()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val containerWidth = maxWidth.value
            val containerHeight = maxHeight.value
            val videoSize = uiState.videoSize
            val mainModifier = Modifier.cameraSurfaceModifier(
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                videoWidth = videoSize?.first ?: 0,
                videoHeight = videoSize?.second ?: 0,
            )
            CameraSurfaceView(
                modifier = mainModifier,
                onSurfaceCreated = viewModel::onSurfaceCreated,
                onSurfaceSizeChanged = viewModel::onSurfaceSizeChanged,
                onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.camera_close_desc),
                    tint = Color.White,
                )
            }
        }

        CameraControlBar(
            uiState = uiState,
            onPhotoClick = viewModel::capturePhoto,
            onTorchClick = viewModel::toggleTorch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )

        if (uiState.status is OperationStatus.Running || uiState.status is OperationStatus.Failed) {
            CameraStatus(
                modifier = Modifier.align(Alignment.Center),
                status = uiState.status,
            )
        }
    }
}

/** 底部控制栏：手电筒（仅后置）/ 拍照。 */
@Composable
private fun CameraControlBar(
    uiState: CameraUiState,
    onPhotoClick: () -> Unit,
    onTorchClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.face == CameraFace.Back) {
            CircleActionButton(
                onClick = onTorchClick,
                contentDescription = stringResource(
                    if (uiState.torchOn) R.string.camera_torch_off_desc else R.string.camera_torch_on_desc,
                ),
            ) {
                Icon(
                    imageVector = if (uiState.torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = null,
                    tint = if (uiState.torchOn) Color(0xFFFFD54F) else Color.White,
                )
            }
        }

        CircleActionButton(
            onClick = onPhotoClick,
            contentDescription = stringResource(R.string.camera_take_photo_desc),
            emphasized = true,
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 带半透明圆形底色的操作按钮。 */
@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    val diameter = if (emphasized) 64.dp else 52.dp
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(diameter),
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .background(
                    color = if (emphasized) {
                        Color(0xFF1E88E5)
                    } else {
                        Color.Black.copy(alpha = 0.45f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun CameraSurfaceView(
    modifier: Modifier,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceSizeChanged: (Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    val currentOnCreated by rememberUpdatedState(onSurfaceCreated)
    val currentOnSizeChanged by rememberUpdatedState(onSurfaceSizeChanged)
    val currentOnDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        currentOnCreated(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        currentOnSizeChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentOnDestroyed()
                    }
                })
            }
        },
    )
}

private fun Modifier.cameraSurfaceModifier(
    containerWidth: Float,
    containerHeight: Float,
    videoWidth: Int,
    videoHeight: Int,
): Modifier {
    if (containerWidth <= 0f || containerHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) {
        return fillMaxSize()
    }
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val containerRatio = containerWidth / containerHeight
    return if (containerRatio > videoRatio) {
        fillMaxHeight().aspectRatio(videoRatio)
    } else {
        fillMaxWidth().aspectRatio(videoRatio)
    }
}

@Composable
private fun CameraStatus(
    modifier: Modifier,
    status: OperationStatus,
) {
    Card(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                is OperationStatus.Running -> Text(status.text)
                is OperationStatus.Failed -> {
                    Text(status.text, color = MaterialTheme.colorScheme.error)
                    Text(
                        status.suggestion,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Unit
            }
        }
    }
}
