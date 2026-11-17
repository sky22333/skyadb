package com.sky22333.skyadb.ui.mirror


import com.sky22333.skyadb.ui.components.AppIcons
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.MirrorConnectionMode
import com.sky22333.skyadb.model.MirrorTransport
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.scrcpy.MirrorStats

@Composable
fun MirrorScreen(
    onBackClick: () -> Unit,
    viewModel: MirrorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val hideKeyboard: () -> Unit = {
        keyboardVisible = false
        focus.clearFocus()
        keyboard?.hide()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceModifier =
                Modifier.mirrorSurface(
                    containerWidth = maxWidth.value,
                    containerHeight = maxHeight.value,
                    videoWidth = uiState.videoWidth,
                    videoHeight = uiState.videoHeight,
                )
            AndroidView(
                modifier = surfaceModifier,
                factory = { context ->
                    object : SurfaceView(context) {
                            override fun performClick(): Boolean {
                                super.performClick()
                                return true
                            }
                        }
                        .apply {
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
                            setOnTouchListener { view, event ->
                                if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                                if (holder.surface.isValid) {
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
            onToggleControls = {
                controlsVisible = !controlsVisible
                if (!controlsVisible) hideKeyboard()
            },
            onToggleKeyboard = {
                if (keyboardVisible) hideKeyboard()
                else {
                    controlsVisible = true
                    keyboardVisible = true
                }
            },
            onClose = onBackClick,
        )

        uiState.audioError?.let { error ->
            Text(error, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp), color = Color.White)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MirrorStatsBar(
                    stats = uiState.stats,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
                MirrorControls(
                    modifier = Modifier,
                    keyboardVisible = keyboardVisible,
                    onHideKeyboard = hideKeyboard,
                    inputText = inputText,
                    onInputTextChange = { inputText = it.take(2000) },
                    onSendText = {
                        viewModel.sendText(inputText)
                        inputText = ""
                    },
                    onKey = viewModel::sendKey,
                )
            }
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
    onToggleKeyboard: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .wrapContentWidth(Alignment.End)
                .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MirrorIconButton(
            icon = AppIcons.Keyboard,
            contentDescription = stringResource(R.string.mirror_toggle_keyboard),
            onClick = onToggleKeyboard,
        )
        MirrorIconButton(
            icon = MiuixIcons.ChevronBackward,
            rotation = if (controlsVisible) 270f else 90f,
            contentDescription =
                stringResource(
                    if (controlsVisible) R.string.mirror_hide_controls_desc
                    else R.string.mirror_show_controls_desc
                ),
            onClick = onToggleControls,
        )
        MirrorIconButton(
            icon = MiuixIcons.Close,
            contentDescription = stringResource(R.string.mirror_close_desc),
            onClick = onClose,
        )
    }
}

@Composable
private fun MirrorStatsBar(
    stats: MirrorStats,
    modifier: Modifier = Modifier,
) {
    val transportLabel =
        stringResource(
            if (stats.transport == MirrorTransport.Wifi) R.string.mirror_transport_wifi
            else R.string.mirror_transport_usb
        )
    val connectionLabel =
        stringResource(
            if (stats.connectionMode == MirrorConnectionMode.Dedicated)
                R.string.mirror_connection_dedicated
            else R.string.mirror_connection_shared
        )
    val decoderLabel =
        if (stats.decoderName.isBlank()) stringResource(R.string.mirror_decoder_unknown)
        else stats.decoderName
    Card(
        modifier = modifier,
        cornerRadius = 10.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Color.Black.copy(alpha = 0.50f), contentColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.mirror_stats_frames,
                    stats.receivedFrames,
                    stats.decodedFrames,
                    stats.droppedFrames,
                ),
                color = Color.White.copy(alpha = 0.88f),
                style = MiuixTheme.textStyles.footnote1,
            )
            Text(
                text = stringResource(
                    R.string.mirror_stats_rates,
                    stats.receiveFps,
                    stats.decodeFps,
                ),
                color = Color.White.copy(alpha = 0.72f),
                style = MiuixTheme.textStyles.footnote1,
            )
            Text(
                text = stringResource(
                    R.string.mirror_stats_connection,
                    transportLabel,
                    connectionLabel,
                    decoderLabel,
                ),
                color = Color.White.copy(alpha = 0.72f),
                style = MiuixTheme.textStyles.footnote1,
            )
            Text(
                text = stringResource(
                    R.string.mirror_stats_pipeline,
                    stats.bitrateMbps,
                    stats.queueDepth,
                    stats.latencyGrowthMs,
                    stats.keyFrameRequests,
                ),
                color = Color.White.copy(alpha = 0.72f),
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

@Composable
private fun MirrorControls(
    modifier: Modifier,
    keyboardVisible: Boolean,
    onHideKeyboard: () -> Unit,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onKey: (Int) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Color.Black.copy(alpha = 0.50f), contentColor = Color.White),
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
                MirrorIconButton(
                    MiuixIcons.Back,
                    stringResource(R.string.action_back),
                ) {
                    onKey(KeyEvent.KEYCODE_BACK)
                }
                MirrorIconButton(MiuixIcons.Home, stringResource(R.string.remote_key_home)) { onKey(KeyEvent.KEYCODE_HOME) }
                MirrorIconButton(
                    AppIcons.RecentTasks,
                    stringResource(R.string.mirror_recent_tasks_desc),
                ) {
                    onKey(KeyEvent.KEYCODE_APP_SWITCH)
                }
                MirrorIconButton(
                    AppIcons.Power,
                    stringResource(R.string.remote_key_power),
                ) {
                    onKey(KeyEvent.KEYCODE_POWER)
                }
                MirrorIconButton(
                    AppIcons.VolumeDown,
                    stringResource(R.string.mirror_volume_down_desc),
                ) {
                    onKey(KeyEvent.KEYCODE_VOLUME_DOWN)
                }
                MirrorIconButton(
                    MiuixIcons.VolumeUp,
                    stringResource(R.string.mirror_volume_up_desc),
                ) {
                    onKey(KeyEvent.KEYCODE_VOLUME_UP)
                }
            }
            if (keyboardVisible)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = inputText,
                        onValueChange = onInputTextChange,
                        singleLine = true,
                        label = stringResource(R.string.mirror_send_text),
                        useLabelAsPlaceholder = true,
                        textStyle = MiuixTheme.textStyles.main.copy(color = Color.White),
                        colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Black.copy(alpha = 0.45f), labelColor = Color.White.copy(alpha = 0.72f)),
                        cursorBrush = SolidColor(Color.White),
                    )
                    MirrorIconButton(
                        icon = MiuixIcons.Send,
                        contentDescription = stringResource(R.string.mirror_send_text),
                        enabled = inputText.isNotBlank(),
                        onClick = onSendText,
                    )
                    MirrorIconButton(
                        MiuixIcons.Close,
                        stringResource(R.string.mirror_hide_keyboard),
                        onClick = onHideKeyboard,
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
    rotation: Float = 0f,
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
            modifier = Modifier.rotate(rotation),
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
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                is OperationStatus.Running -> Text(status.text)
                is OperationStatus.Failed -> {
                    Text(status.text, color = MiuixTheme.colorScheme.error)
                    Text(
                        status.suggestion,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                else -> Unit
            }
        }
    }
}
