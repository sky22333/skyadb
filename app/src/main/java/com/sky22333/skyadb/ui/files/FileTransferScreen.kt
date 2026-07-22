package com.sky22333.skyadb.ui.files

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.ui.components.AppTopBar
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import java.util.Locale

private val FileMotion = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
private val FileMotionDp = tween<androidx.compose.ui.unit.Dp>(durationMillis = 180, easing = FastOutSlowInEasing)
private val RowHeight = 44.dp
private val BottomBarHeight = 48.dp
private val IconSize = 22.dp

@Composable
fun FileTransferScreen(
    @Suppress("UNUSED_PARAMETER") bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: FileTransferViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onStoragePermissionResult()
    }

    LaunchedEffect(Unit) {
        viewModel.onStoragePermissionResult()
        viewModel.refreshAll()
    }

    FileManagerContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshAll,
        onGoUpClick = { viewModel.goUp() },
        onGoUpPane = viewModel::goUp,
        onSyncClick = viewModel::syncPathFromOther,
        onTransferClick = viewModel::transferSelected,
        onCancelTransfer = viewModel::cancelTransfer,
        onNewFolderClick = viewModel::showNewFolderDialog,
        onRenameClick = viewModel::showRenameDialog,
        onPathClick = viewModel::showJumpDialog,
        onActivatePane = viewModel::setActivePane,
        onOpenEntry = viewModel::openEntry,
        onSelectEntry = viewModel::selectEntry,
        onDeleteEntry = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissNewFolder = viewModel::dismissNewFolderDialog,
        onCreateFolder = viewModel::createFolder,
        onDismissRename = viewModel::dismissRenameDialog,
        onRenameInputChanged = viewModel::onRenameInputChanged,
        onConfirmRename = viewModel::confirmRename,
        onDismissJump = viewModel::dismissJumpDialog,
        onJumpInputChanged = viewModel::onJumpInputChanged,
        onConfirmJump = viewModel::confirmJump,
        onRequestStoragePermission = {
            permissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
    )
}

@Composable
private fun FileManagerContent(
    uiState: FileTransferUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onGoUpClick: () -> Unit,
    onGoUpPane: (FilePaneId) -> Unit,
    onSyncClick: () -> Unit,
    onTransferClick: () -> Unit,
    onCancelTransfer: () -> Unit,
    onNewFolderClick: () -> Unit,
    onRenameClick: () -> Unit,
    onPathClick: () -> Unit,
    onActivatePane: (FilePaneId) -> Unit,
    onOpenEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onSelectEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onDeleteEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissNewFolder: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismissRename: () -> Unit,
    onRenameInputChanged: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissJump: () -> Unit,
    onJumpInputChanged: (String) -> Unit,
    onConfirmJump: () -> Unit,
    onRequestStoragePermission: () -> Unit,
) {
    val transferring = uiState.operationStatus is OperationStatus.Running
    val active = uiState.active
    val sideLabel = if (uiState.activePane == FilePaneId.Local) "本机" else "设备"
    val selectedCount = uiState.selectedPaths.size
    val titleKey = remember(uiState.activePane, active.path, active.entries.size, selectedCount) {
        listOf(sideLabel, active.path, active.entries.size.toString(), selectedCount.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        AppTopBar(
            title = {
                AnimatedContent(
                    targetState = titleKey,
                    transitionSpec = {
                        fadeIn(FileMotion) togetherWith fadeOut(tween(100))
                    },
                    label = "file-title",
                ) { key ->
                    val side = key[0]
                    val path = key[1]
                    val count = key[2]
                    val selected = key[3].toInt()
                    Column(
                        modifier = Modifier.combinedClickable(
                            indication = ripple(bounded = true),
                            interactionSource = null,
                            onClick = onPathClick,
                        ),
                    ) {
                        Text(
                            text = if (selected > 0) "$side · 已选 $selected" else "$side · $count 项",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = shortPath(path),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (transferring) {
                    IconButton(onClick = onCancelTransfer) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消传输")
                    }
                } else {
                    IconButton(
                        onClick = onRenameClick,
                        enabled = selectedCount == 1,
                    ) {
                        Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "重命名")
                    }
                    IconButton(onClick = onRefreshClick) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            },
        )

        AnimatedVisibility(
            visible = uiState.needsStoragePermission,
            enter = fadeIn(FileMotion),
            exit = fadeOut(tween(100)),
        ) {
            PermissionBanner(onRequestStoragePermission = onRequestStoragePermission)
        }

        AnimatedVisibility(
            visible = uiState.operationStatus is OperationStatus.Running,
            enter = fadeIn(FileMotion),
            exit = fadeOut(tween(100)),
        ) {
            TransferProgress(status = uiState.operationStatus)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            FilePane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                paneId = FilePaneId.Local,
                pane = uiState.local,
                isActive = uiState.activePane == FilePaneId.Local,
                selectedPaths = uiState.selectedPaths.takeIf { uiState.activePane == FilePaneId.Local }
                    .orEmpty(),
                enabled = !transferring,
                onActivate = onActivatePane,
                onOpenEntry = onOpenEntry,
                onSelectEntry = onSelectEntry,
                onDeleteEntry = onDeleteEntry,
                onNavigateUp = { onGoUpPane(FilePaneId.Local) },
            )
            FilePane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                paneId = FilePaneId.Remote,
                pane = uiState.remote,
                isActive = uiState.activePane == FilePaneId.Remote,
                selectedPaths = uiState.selectedPaths.takeIf { uiState.activePane == FilePaneId.Remote }
                    .orEmpty(),
                enabled = !transferring,
                onActivate = onActivatePane,
                onOpenEntry = onOpenEntry,
                onSelectEntry = onSelectEntry,
                onDeleteEntry = onDeleteEntry,
                onNavigateUp = { onGoUpPane(FilePaneId.Remote) },
            )
        }

        ActivePaneIndicator(activePane = uiState.activePane)

        FileBottomBar(
            canGoUp = active.canGoUp,
            enabled = !transferring,
            onGoUpClick = onGoUpClick,
            onSyncClick = onSyncClick,
            onTransferClick = onTransferClick,
            onNewFolderClick = onNewFolderClick,
        )

        StatusFooter(status = uiState.operationStatus)
    }

    DeleteConfirmDialog(
        label = uiState.pendingDeleteLabel,
        onDismiss = onCancelDelete,
        onConfirm = onConfirmDelete,
    )
    NewFolderDialog(
        visible = uiState.newFolderDialogVisible,
        onDismiss = onDismissNewFolder,
        onCreate = onCreateFolder,
    )
    RenameDialog(
        visible = uiState.renameDialogVisible,
        value = uiState.renameInput,
        error = uiState.renameError,
        onDismiss = onDismissRename,
        onValueChange = onRenameInputChanged,
        onConfirm = onConfirmRename,
    )
    JumpPathDialog(
        visible = uiState.jumpDialogVisible,
        value = uiState.jumpInput,
        error = uiState.jumpError,
        isRemote = uiState.activePane == FilePaneId.Remote,
        onDismiss = onDismissJump,
        onValueChange = onJumpInputChanged,
        onConfirm = onConfirmJump,
    )
}

@Composable
private fun TransferProgress(status: OperationStatus) {
    if (status !is OperationStatus.Running) return
    if (status.progress == null) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            trackColor = Color.Transparent,
        )
    } else {
        LinearProgressIndicator(
            progress = { status.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            trackColor = Color.Transparent,
        )
    }
}

@Composable
private fun StatusFooter(status: OperationStatus) {
    val visible = status is OperationStatus.Running ||
        status is OperationStatus.Failed ||
        status is OperationStatus.Success
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(FileMotion),
        exit = fadeOut(tween(120)),
    ) {
        val text = when (status) {
            is OperationStatus.Running -> status.text
            is OperationStatus.Failed -> "${status.text}：${status.suggestion}"
            is OperationStatus.Success -> status.text
            OperationStatus.Idle -> ""
        }
        val color = when (status) {
            is OperationStatus.Failed -> MaterialTheme.colorScheme.error
            is OperationStatus.Success -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionBanner(onRequestStoragePermission: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "需要「所有文件访问」权限",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        TextButton(onClick = onRequestStoragePermission) {
            Text("授权")
        }
    }
}

@Composable
private fun ActivePaneIndicator(activePane: FilePaneId) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val targetX = if (activePane == FilePaneId.Local) 0.dp else maxWidth / 2
        val x by animateDpAsState(targetValue = targetX, animationSpec = FileMotionDp, label = "pane-indicator")
        Box(
            modifier = Modifier
                .offset(x = x)
                .width(maxWidth / 2)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun FileBottomBar(
    canGoUp: Boolean,
    enabled: Boolean,
    onGoUpClick: () -> Unit,
    onSyncClick: () -> Unit,
    onTransferClick: () -> Unit,
    onNewFolderClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarIcon(Icons.Outlined.ArrowUpward, "上级", enabled && canGoUp, onGoUpClick)
        BottomBarIcon(Icons.Outlined.Sync, "同步对面路径", enabled, onSyncClick)
        BottomBarIcon(Icons.Outlined.SwapHoriz, "传到对面", enabled, onTransferClick)
        BottomBarIcon(Icons.Outlined.CreateNewFolder, "新建文件夹", enabled, onNewFolderClick)
    }
}

@Composable
private fun RowScope.BottomBarIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .combinedClickable(
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

@Composable
private fun FilePane(
    modifier: Modifier = Modifier,
    paneId: FilePaneId,
    pane: FilePaneState,
    @Suppress("UNUSED_PARAMETER") isActive: Boolean,
    selectedPaths: Set<String>,
    enabled: Boolean,
    onActivate: (FilePaneId) -> Unit,
    onOpenEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onSelectEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onDeleteEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Box(modifier = modifier) {
        when {
            pane.loading && pane.entries.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(26.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    if (pane.canGoUp) {
                        item(key = "$paneId-up", contentType = "up") {
                            NavigateUpRow(enabled = enabled, onClick = onNavigateUp)
                        }
                    }
                    if (pane.entries.isEmpty()) {
                        item(key = "$paneId-empty", contentType = "empty") {
                            Text(
                                text = pane.error ?: "空目录",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = enabled,
                                        indication = null,
                                        interactionSource = null,
                                        onClick = { onActivate(paneId) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = AppDimens.CompactGap),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = pane.entries,
                            key = { it.path },
                            contentType = { if (it.isDirectory) "dir" else "file" },
                        ) { entry ->
                            FileRow(
                                entry = entry,
                                selected = entry.path in selectedPaths,
                                enabled = enabled,
                                onClick = {
                                    onActivate(paneId)
                                    if (entry.isDirectory) onOpenEntry(paneId, entry)
                                    else onSelectEntry(paneId, entry)
                                },
                                onLongClick = {
                                    onActivate(paneId)
                                    onDeleteEntry(paneId, entry)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (pane.loading && pane.entries.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun NavigateUpRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .combinedClickable(
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = AppDimens.CompactGap)
                .size(IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FileRow(
    entry: RemoteFileEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val meta = remember(entry.isDirectory, entry.sizeBytes) {
        if (entry.isDirectory) "文件夹" else formatBytes(entry.sizeBytes)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                else Color.Transparent,
            )
            .combinedClickable(
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = AppDimens.CompactGap)
                .size(IconSize),
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    label: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (label == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 $label？") },
        text = { Text("确认删除选中项？此操作可能不可恢复。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameDialog(
    visible: Boolean,
    value: String,
    error: String?,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("新名称") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NewFolderDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    if (!visible) return
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文件夹名称") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun JumpPathDialog(
    visible: Boolean,
    value: String,
    error: String?,
    isRemote: Boolean,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRemote) "跳转设备路径" else "跳转本机路径") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("路径") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun shortPath(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    if (trimmed.length <= 32) return trimmed.ifBlank { "/" }
    return "…" + trimmed.takeLast(31)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

@Preview(name = "文件管理沉浸双栏", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun FileManagerContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        FileManagerContent(
            uiState = FileTransferUiState(
                local = FilePaneState(
                    path = "/sdcard/Download",
                    entries = listOf(
                        RemoteFileEntry("DCIM", "/sdcard/Download/DCIM", true, 0L),
                        RemoteFileEntry("notes.txt", "/sdcard/Download/notes.txt", false, 2048L),
                    ),
                ),
                remote = FilePaneState(
                    path = "/sdcard/Download",
                    entries = listOf(
                        RemoteFileEntry("demo.apk", "/sdcard/Download/demo.apk", false, 12_400_000L),
                        RemoteFileEntry("Movies", "/sdcard/Download/Movies", true, 0L),
                    ),
                ),
                activePane = FilePaneId.Local,
                selectedPaths = setOf("/sdcard/Download/notes.txt"),
            ),
            onBackClick = {},
            onRefreshClick = {},
            onGoUpClick = {},
            onGoUpPane = {},
            onSyncClick = {},
            onTransferClick = {},
            onCancelTransfer = {},
            onNewFolderClick = {},
            onRenameClick = {},
            onPathClick = {},
            onActivatePane = {},
            onOpenEntry = { _, _ -> },
            onSelectEntry = { _, _ -> },
            onDeleteEntry = { _, _ -> },
            onCancelDelete = {},
            onConfirmDelete = {},
            onDismissNewFolder = {},
            onCreateFolder = {},
            onDismissRename = {},
            onRenameInputChanged = {},
            onConfirmRename = {},
            onDismissJump = {},
            onJumpInputChanged = {},
            onConfirmJump = {},
            onRequestStoragePermission = {},
        )
    }
}
