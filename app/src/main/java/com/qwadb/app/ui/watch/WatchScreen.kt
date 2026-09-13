package com.qwadb.app.ui.watch

import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.qwadb.app.ui.ImmersiveFullscreen
import com.qwadb.app.ui.shared.screenStreamSurface

/** 观看屏幕：复用屏幕镜像技术展示画面，点击屏幕仅提示不支持操作。 */
@Composable
fun WatchScreen(
    onBackClick: () -> Unit,
    viewModel: WatchViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var surface by remember { mutableStateOf<Surface?>(null) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    BackHandler { onBackClick() }

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
            val surfaceModifier = Modifier.screenStreamSurface(
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
                        // 观看模式不发送任何控制指令，仅提示该操作不支持。
                        setOnTouchListener { _, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                viewModel.onInteraction()
                            }
                            true
                        }
                    }
                },
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
            IconButton(
                onClick = {
                    viewModel.stop()
                    onBackClick()
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.watch_close_desc),
                    tint = Color.White,
                )
            }
        }

        if (uiState.status is OperationStatus.Running || uiState.status is OperationStatus.Failed) {
            WatchStatus(
                modifier = Modifier.align(Alignment.Center),
                status = uiState.status,
            )
        }
    }
}

@Composable
private fun WatchStatus(
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
