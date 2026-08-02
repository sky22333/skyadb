package com.sky22333.skyadb.ui.mirror

import android.view.KeyEvent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus

@Composable
fun MirrorScreen(
    onBackClick: () -> Unit,
    viewModel: MirrorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var surface by remember { mutableStateOf<Surface?>(null) }
    var inputText by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceModifier = Modifier.mirrorSurface(
                containerWidth = maxWidth.value,
                containerHeight = maxHeight.value,
                videoWidth = uiState.videoWidth,
                videoHeight = uiState.videoHeight,
            )
            AndroidView(
                modifier = surfaceModifier,
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                surface = holder.surface
                                viewModel.onSurfaceCreated(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surface = null
                                viewModel.onSurfaceDestroyed()
                            }
                        })
                        setOnTouchListener { view, event ->
                            if (surface != null) {
                                viewModel.sendTouch(event, view.width, view.height)
                            }
                            true
                        }
                    }
                },
            )
        }

        MirrorTopActions(
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible },
            onClose = {
                viewModel.stop()
                onBackClick()
            },
        )

        if (controlsVisible) {
            MirrorControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                inputText = inputText,
                onInputTextChange = { inputText = it.take(2000) },
                onSendText = {
                    viewModel.sendText(inputText)
                    inputText = ""
                },
                onKey = viewModel::sendKey,
            )
        }

        if (uiState.status is OperationStatus.Running || uiState.status is OperationStatus.Failed) {
            MirrorStatus(
                modifier = Modifier.align(Alignment.Center),
                status = uiState.status,
            )
        }
    }
}

private fun Modifier.mirrorSurface(
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
private fun MirrorTopActions(
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MirrorIconButton(
            icon = Icons.Outlined.MoreVert,
            contentDescription = stringResource(
                if (controlsVisible) R.string.mirror_hide_controls_desc else R.string.mirror_show_controls_desc,
            ),
            onClick = onToggleControls,
        )
        MirrorIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.mirror_close_desc),
            onClick = onClose,
        )
    }
}

@Composable
private fun MirrorControls(
    modifier: Modifier,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onKey: (Int) -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.50f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MirrorIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) { onKey(KeyEvent.KEYCODE_BACK) }
                MirrorIconButton(Icons.Outlined.Home, "Home") { onKey(KeyEvent.KEYCODE_HOME) }
                MirrorIconButton(Icons.Outlined.Apps, stringResource(R.string.mirror_recent_tasks_desc)) { onKey(KeyEvent.KEYCODE_APP_SWITCH) }
                MirrorIconButton(Icons.Outlined.PowerSettingsNew, stringResource(R.string.remote_power_title)) { onKey(KeyEvent.KEYCODE_POWER) }
                MirrorIconButton(Icons.AutoMirrored.Outlined.VolumeDown, stringResource(R.string.mirror_volume_down_desc)) { onKey(KeyEvent.KEYCODE_VOLUME_DOWN) }
                MirrorIconButton(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.mirror_volume_up_desc)) { onKey(KeyEvent.KEYCODE_VOLUME_UP) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = inputText,
                    onValueChange = onInputTextChange,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.mirror_send_text)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.72f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.38f),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.72f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.58f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.58f),
                    ),
                )
                MirrorIconButton(
                    icon = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.mirror_send_text),
                    enabled = inputText.isNotBlank(),
                    onClick = onSendText,
                )
            }
        }
    }
}

@Composable
private fun MirrorIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun MirrorStatus(
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
