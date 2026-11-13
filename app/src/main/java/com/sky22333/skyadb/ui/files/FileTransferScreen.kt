package com.sky22333.skyadb.ui.files

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.More
import android.content.Intent
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.window.WindowDialog
import com.sky22333.skyadb.ui.components.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.model.RemoteFileEntry
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import java.util.Locale
private val FileMotion = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
private val FileMotionDp = tween<androidx.compose.ui.unit.Dp>(durationMillis = 180, easing = FastOutSlowInEasing)
private val RowHeight = 44.dp
private val IconSize = 22.dp

@Composable
fun FileTransferScreen(
    bottomPadding: Dp = 0.dp,
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
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onStoragePermissionResult()
    }

    LaunchedEffect(Unit) {
        viewModel.onStoragePermissionResult()
        viewModel.refreshAll()
    }

    FileManagerContent(
        uiState = uiState,
        bottomPadding = bottomPadding,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshAll,
        onGoUpPane = viewModel::goUp,
        onSyncClick = viewModel::syncPathFromOther,
        onTransferClick = viewModel::transferSelected,
        onCancelTransfer = viewModel::cancelTransfer,
        onNewFolderClick = viewModel::showNewFolderDialog,
        onRenameEntry = viewModel::renameEntry,
        onTransferEntry = viewModel::transferEntry,
        onDeleteSelected = viewModel::requestDeleteSelected,
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                permissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } else {
                legacyPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                )
            }
        },
    )
}

@Composable
internal fun FileManagerContent(
    uiState: FileTransferUiState,
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onGoUpPane: (FilePaneId) -> Unit,
    onSyncClick: () -> Unit,
    onTransferClick: () -> Unit,
    onCancelTransfer: () -> Unit,
    onNewFolderClick: () -> Unit,
    onRenameEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onTransferEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onDeleteSelected: () -> Unit,
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
    val context = LocalContext.current
    var menuEntry by remember { mutableStateOf<Pair<FilePaneId, RemoteFileEntry>?>(null) }
    var detailsEntry by remember { mutableStateOf<RemoteFileEntry?>(null) }
    val transferring = uiState.operationStatus is OperationStatus.Running
    val active = uiState.active
    val sideLabel = stringResource(
        if (uiState.activePane == FilePaneId.Local) R.string.files_side_local else R.string.files_side_remote,
    )
    val selectedCount = uiState.selectedPaths.size


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(bottom = bottomPadding),
    ) {
        Box {
            SmallTopAppBar(
                title = shortPath(active.path),
                subtitle = if (selectedCount > 0) {
                    stringResource(R.string.files_title_selected_format, sideLabel, selectedCount)
                } else {
                    stringResource(R.string.files_title_count_format, sideLabel, active.entries.size)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (transferring) {
                        IconButton(onClick = onCancelTransfer) {
                            Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.files_cancel_transfer_desc))
                        }
                    } else {
                        IconButton(onClick = onRefreshClick) {
                            Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                        val newFolder = stringResource(R.string.files_new_folder_desc)
                        val syncPath = stringResource(R.string.files_sync_path_desc)
                        val transferSelected = stringResource(R.string.files_transfer_selected)
                        val deleteSelected = stringResource(R.string.files_delete_selected)
                        val actions = DropdownEntry(buildList {
                            add(DropdownItem(text = newFolder, onClick = onNewFolderClick))
                            add(DropdownItem(text = syncPath, onClick = onSyncClick))
                            if (uiState.selectedFiles.isNotEmpty()) {
                                add(DropdownItem(text = transferSelected, onClick = onTransferClick))
                            }
                            if (selectedCount > 0) {
                                add(DropdownItem(text = deleteSelected, onClick = onDeleteSelected))
                            }
                        })
                        OverlayIconDropdownMenu(entry = actions) {
                            Icon(MiuixIcons.More, contentDescription = stringResource(R.string.files_more_actions))
                        }
                    }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 64.dp, end = 112.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(AppDimens.ControlRadius))
                    .clickable(onClickLabel = stringResource(R.string.files_path_label), onClick = onPathClick),
            )
        }

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
                selectedPaths = uiState.selectedPaths.takeIf { uiState.activePane == FilePaneId.Local }
                    .orEmpty(),
                enabled = !transferring,
                onActivate = onActivatePane,
                onOpenEntry = onOpenEntry,
                onSelectEntry = onSelectEntry,
                onLongPressEntry = { pane, entry ->
                    onActivatePane(pane)
                    menuEntry = pane to entry
                },
                onNavigateUp = { onGoUpPane(FilePaneId.Local) },
            )
            FilePane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                paneId = FilePaneId.Remote,
                pane = uiState.remote,
                selectedPaths = uiState.selectedPaths.takeIf { uiState.activePane == FilePaneId.Remote }
                    .orEmpty(),
                enabled = !transferring,
                onActivate = onActivatePane,
                onOpenEntry = onOpenEntry,
                onSelectEntry = onSelectEntry,
                onLongPressEntry = { pane, entry ->
                    onActivatePane(pane)
                    menuEntry = pane to entry
                },
                onNavigateUp = { onGoUpPane(FilePaneId.Remote) },
            )
        }

        ActivePaneIndicator(activePane = uiState.activePane)

        StatusFooter(status = uiState.operationStatus)
    }

    menuEntry?.let { (pane, entry) ->
        FileActionDialog(
            entry = entry,
            onDismiss = { menuEntry = null },
            onTransfer = { menuEntry = null; onTransferEntry(pane, entry) },
            onRename = { menuEntry = null; onRenameEntry(pane, entry) },
            onDelete = { menuEntry = null; onDeleteEntry(pane, entry) },
            onCopyPath = {
                copyFileText(context, entry.name, entry.path)
                menuEntry = null
            },
            onCopyName = {
                copyFileText(context, entry.name, entry.name)
                menuEntry = null
            },
            onDetails = { menuEntry = null; detailsEntry = entry },
        )
    }
    detailsEntry?.let { entry ->
        WindowDialog(
            show = true,
            onDismissRequest = { detailsEntry = null },
            title = entry.name,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.files_path_label) + ": " + entry.path)
                Text(if (entry.isDirectory) stringResource(R.string.files_dir_meta) else formatBytes(entry.sizeBytes))
                Button(onClick = { detailsEntry = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
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

private fun copyFileText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.files_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TransferProgress(status: OperationStatus) {
    if (status !is OperationStatus.Running) return
    if (status.progress == null) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        )
    } else {
        LinearProgressIndicator(
            progress = status.progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
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
            is OperationStatus.Failed -> stringResource(R.string.device_status_error_format, status.text, status.suggestion)
            is OperationStatus.Success -> status.text
            OperationStatus.Idle -> ""
        }
        val color = when (status) {
            is OperationStatus.Failed -> MiuixTheme.colorScheme.error
            is OperationStatus.Success -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        }
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MiuixTheme.textStyles.footnote2,
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
            .background(MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.files_permission_required),
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onErrorContainer,
        )
        Button(onClick = onRequestStoragePermission) {
            Text(stringResource(R.string.action_authorize))
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
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(maxWidth / 2)
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun FilePane(
    modifier: Modifier = Modifier,
    paneId: FilePaneId,
    pane: FilePaneState,
    selectedPaths: Set<String>,
    enabled: Boolean,
    onActivate: (FilePaneId) -> Unit,
    onOpenEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onSelectEntry: (FilePaneId, RemoteFileEntry) -> Unit,
    onLongPressEntry: (FilePaneId, RemoteFileEntry) -> Unit,
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
                                text = pane.error ?: stringResource(R.string.files_empty_dir),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = enabled,
                                        indication = null,
                                        interactionSource = null,
                                        onClick = { onActivate(paneId) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = AppDimens.CompactGap),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                                    onLongPressEntry(paneId, entry)
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
            .clip(RoundedCornerShape(AppDimens.ControlRadius))
            .combinedClickable(
                enabled = enabled,
                indication = LocalIndication.current,
                interactionSource = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Folder,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = AppDimens.CompactGap)
                .size(IconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = "..",
            style = MiuixTheme.textStyles.body2,
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
    val folderLabel = stringResource(R.string.files_dir_meta)
    val meta = remember(entry.isDirectory, entry.sizeBytes, folderLabel) {
        if (entry.isDirectory) folderLabel else formatBytes(entry.sizeBytes)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(AppDimens.ControlRadius))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.20f)
                else Color.Transparent,
            )
            .combinedClickable(
                enabled = enabled,
                indication = LocalIndication.current,
                interactionSource = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) MiuixIcons.Folder else MiuixIcons.File,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = AppDimens.CompactGap)
                .size(IconSize),
            tint = if (entry.isDirectory) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
        ) {
            Text(
                text = entry.name,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileActionDialog(
    entry: RemoteFileEntry,
    onDismiss: () -> Unit,
    onTransfer: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyName: () -> Unit,
    onDetails: () -> Unit,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = entry.name,
    ) {
        Column {
            if (!entry.isDirectory) FileAction(stringResource(R.string.files_transfer_to_other_desc), onTransfer)
            FileAction(stringResource(R.string.action_rename), onRename)
            FileAction(stringResource(R.string.files_copy_name), onCopyName)
            FileAction(stringResource(R.string.files_copy_path), onCopyPath)
            FileAction(stringResource(R.string.files_details), onDetails)
            FileAction(stringResource(R.string.action_delete), onDelete, destructive = true)
        }
    }
}

@Composable
private fun FileAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AppDimens.ControlRadius)),
        insideMargin = PaddingValues(vertical = 12.dp),
        onClick = onClick,
    ) {
        Text(label, style = MiuixTheme.textStyles.main, color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DialogActions(onDismiss: () -> Unit, onConfirm: () -> Unit, confirmLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text(confirmLabel) }
    }
}

@Composable
private fun DeleteConfirmDialog(label: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (label == null) return
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(R.string.files_delete_title_format, label), summary = stringResource(R.string.files_delete_confirm_message)) {
        DialogActions(onDismiss, onConfirm, stringResource(R.string.action_delete))
    }
}

@Composable
private fun RenameDialog(visible: Boolean, value: String, error: String?, onDismiss: () -> Unit, onValueChange: (String) -> Unit, onConfirm: () -> Unit) {
    if (!visible) return
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(R.string.action_rename)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = value, onValueChange = onValueChange, label = stringResource(R.string.files_new_name_label), singleLine = true)
            error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            DialogActions(onDismiss, onConfirm, stringResource(R.string.action_confirm))
        }
    }
}

@Composable
private fun NewFolderDialog(visible: Boolean, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    if (!visible) return
    var name by remember { mutableStateOf("") }
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(R.string.files_new_folder_desc)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.files_folder_name_label), singleLine = true)
            DialogActions(onDismiss, { onCreate(name) }, stringResource(R.string.action_create))
        }
    }
}

@Composable
private fun JumpPathDialog(visible: Boolean, value: String, error: String?, isRemote: Boolean, onDismiss: () -> Unit, onValueChange: (String) -> Unit, onConfirm: () -> Unit) {
    if (!visible) return
    WindowDialog(show = true, onDismissRequest = onDismiss, title = stringResource(if (isRemote) R.string.files_jump_remote_title else R.string.files_jump_local_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = value, onValueChange = onValueChange, label = stringResource(R.string.files_path_label), singleLine = true)
            error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            DialogActions(onDismiss, onConfirm, stringResource(R.string.action_confirm))
        }
    }
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
    AdbManagerTheme() {
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
            onGoUpPane = {},
            onSyncClick = {},
            onTransferClick = {},
            onCancelTransfer = {},
            onNewFolderClick = {},
            onRenameEntry = { _, _ -> },
            onTransferEntry = { _, _ -> },
            onDeleteSelected = {},
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
