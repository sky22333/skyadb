package com.qwadb.app.ui.gallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.ui.components.AppTopBar as TopAppBar
import com.qwadb.app.ui.components.EmptyState
import com.qwadb.app.ui.theme.AdbManagerTheme
import com.qwadb.app.ui.theme.AppDimens
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaGalleryScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: MediaGalleryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    var deleteCandidate by remember { mutableStateOf<MediaItem?>(null) }

    val photoSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> previewItem?.let { viewModel.saveToUri(context, it, uri) } }
    val videoSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri -> previewItem?.let { viewModel.saveToUri(context, it, uri) } }

    val onSaveClick: (MediaItem) -> Unit = { item ->
        previewItem = item
        if (item.type == MediaType.Photo) {
            photoSaveLauncher.launch(item.name)
        } else {
            videoSaveLauncher.launch(item.name)
        }
    }
    val onDeleteClick: (MediaItem) -> Unit = { item ->
        previewItem = null
        deleteCandidate = item
    }

    MediaGalleryContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refresh,
        onFilterClick = viewModel::setFilter,
        onItemClick = { previewItem = it },
        onSaveClick = onSaveClick,
        onDeleteClick = onDeleteClick,
    )

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.gallery_delete_confirm_title)) },
            text = { Text(stringResource(R.string.gallery_delete_confirm_message, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    viewModel.delete(item)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    previewItem?.let { item ->
        MediaPreviewDialog(
            item = item,
            onDismiss = { previewItem = null },
            onSave = { onSaveClick(item) },
            onDelete = { onDeleteClick(item) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaGalleryContent(
    bottomPadding: Dp,
    uiState: MediaGalleryUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onFilterClick: (GalleryFilter) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onSaveClick: (MediaItem) -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.gallery_title))
                    Text(
                        text = stringResource(R.string.gallery_subtitle),
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
            actions = {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !uiState.loading,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (uiState.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.gallery_refresh_desc),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        FilterRow(
            selected = uiState.filter,
            onFilterClick = onFilterClick,
        )

        when {
            uiState.loading && uiState.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.items.isEmpty() -> EmptyState(
                title = stringResource(R.string.gallery_empty_title),
                message = stringResource(R.string.gallery_empty_message),
                modifier = Modifier.padding(AppDimens.ScreenPadding),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.items, key = { it.file.absolutePath }) { item ->
                    MediaGridCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onSaveClick = { onSaveClick(item) },
                        onDeleteClick = { onDeleteClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: GalleryFilter,
    onFilterClick: (GalleryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilterClick(filter) },
                label = { Text(stringResource(filter.labelRes)) },
            )
        }
    }
}

@Composable
private fun MediaGridCard(
    item: MediaItem,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                MediaThumbnail(item = item)
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatSize(item.sizeBytes)} \u00b7 ${formatDate(item.lastModified)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = onSaveClick) {
                    Icon(imageVector = Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = onDeleteClick) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = item.file) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(item) }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!,
                contentDescription = stringResource(R.string.gallery_preview_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
        }
        if (item.type == MediaType.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.type) {
                    MediaType.Photo -> PhotoPreview(item = item)
                    MediaType.Video -> VideoPreview(item = item)
                }
                Text(
                    text = "${formatSize(item.sizeBytes)} \u00b7 ${formatDate(item.lastModified)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Icon(imageVector = Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_delete))
            }
        },
    )
}

@Composable
private fun PhotoPreview(item: MediaItem) {
    val preview by produceState<ImageBitmap?>(initialValue = null, key1 = item.file) {
        value = withContext(Dispatchers.IO) { decodePreviewImage(item.file.absolutePath) }
    }
    if (preview == null) {
        Text(
            text = stringResource(R.string.gallery_preview_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Image(
            bitmap = preview!!,
            contentDescription = stringResource(R.string.gallery_preview_desc),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VideoPreview(item: MediaItem) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setVideoPath(item.file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    )
}

private fun decodeThumbnail(item: MediaItem): ImageBitmap? = try {
    when (item.type) {
        MediaType.Photo -> decodePreviewImage(item.file.absolutePath, ThumbMaxSize)
        MediaType.Video -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(item.file.absolutePath)
                retriever.getFrameAtTime(0)?.asImageBitmap()
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
} catch (_: Exception) {
    null
}

private fun decodePreviewImage(path: String, maxSize: Int = PreviewMaxSize): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private const val ThumbMaxSize = 400
private const val PreviewMaxSize = 1600

@Preview(name = "媒体库 - 空", showBackground = true, widthDp = 390)
@Composable
private fun MediaGalleryEmptyPreview() {
    AdbManagerTheme(dynamicColor = false) {
        MediaGalleryContent(
            bottomPadding = 0.dp,
            uiState = MediaGalleryUiState(),
            onBackClick = {},
            onRefreshClick = {},
            onFilterClick = {},
            onItemClick = {},
            onSaveClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(name = "媒体库 - 有内容", showBackground = true, widthDp = 390)
@Composable
private fun MediaGalleryContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        MediaGalleryContent(
            bottomPadding = 0.dp,
            uiState = MediaGalleryUiState(
                items = listOf(
                    MediaItem(File("/tmp/camera-1.png"), MediaType.Photo),
                    MediaItem(File("/tmp/camera-2.mp4"), MediaType.Video),
                    MediaItem(File("/tmp/camera-3.png"), MediaType.Photo),
                ),
            ),
            onBackClick = {},
            onRefreshClick = {},
            onFilterClick = {},
            onItemClick = {},
            onSaveClick = {},
            onDeleteClick = {},
        )
    }
}
