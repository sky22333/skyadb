package com.qwadb.app.ui.gallery

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.OperationStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaType { Photo, Video }

/** 媒体库中的一个条目，直接引用应用私有目录里的真实文件。 */
data class MediaItem(
    val file: File,
    val type: MediaType,
) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val lastModified: Long get() = file.lastModified()
}

enum class GalleryFilter(@param:StringRes val labelRes: Int) {
    All(R.string.gallery_filter_all),
    Photo(R.string.gallery_filter_photo),
    Video(R.string.gallery_filter_video),
}

data class MediaGalleryUiState(
    val loading: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val filter: GalleryFilter = GalleryFilter.All,
    val operationStatus: OperationStatus = OperationStatus.Idle,
)

class MediaGalleryViewModel : ViewModel() {
    private val state = MutableStateFlow(MediaGalleryUiState())
    val uiState: StateFlow<MediaGalleryUiState> = state.asStateFlow()

    private var allItems: List<MediaItem> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        state.value = state.value.copy(loading = true, operationStatus = OperationStatus.Idle)
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { scanMediaFiles() }
            allItems = items
            state.value = state.value.copy(
                loading = false,
                items = applyFilter(items, state.value.filter),
            )
        }
    }

    fun setFilter(filter: GalleryFilter) {
        state.value = state.value.copy(filter = filter, items = applyFilter(allItems, filter))
    }

    /** 删除指定媒体文件，成功后刷新列表。 */
    fun delete(item: MediaItem) {
        state.value = state.value.copy(operationStatus = OperationStatus.Running(appString(R.string.gallery_deleting)))
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { item.file.delete() }
            if (deleted) {
                refresh()
            } else {
                state.value = state.value.copy(
                    operationStatus = OperationStatus.Failed(
                        text = appString(R.string.gallery_delete_failed),
                        suggestion = appString(R.string.gallery_confirm_permission),
                    ),
                )
            }
        }
    }

    /** 将指定媒体文件保存到用户选择的系统位置。 */
    fun saveToUri(context: Context, item: MediaItem, uri: Uri?) {
        if (uri == null) {
            state.value = state.value.copy(
                operationStatus = OperationStatus.Failed(
                    text = appString(R.string.gallery_save_failed),
                    suggestion = appString(R.string.gallery_confirm_writable),
                ),
            )
            return
        }
        state.value = state.value.copy(operationStatus = OperationStatus.Running(appString(R.string.gallery_saving)))
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri).use { output ->
                        requireNotNull(output) { appString(R.string.gallery_cannot_open_location) }
                        item.file.inputStream().use { input -> input.copyTo(output) }
                    }
                }
            }.fold(
                onSuccess = {
                    state.value = state.value.copy(operationStatus = OperationStatus.Success(appString(R.string.gallery_saved)))
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        operationStatus = OperationStatus.Failed(
                            text = appString(R.string.gallery_save_failed),
                            suggestion = error.message ?: appString(R.string.gallery_confirm_writable),
                        ),
                    )
                },
            )
        }
    }

    private fun applyFilter(items: List<MediaItem>, filter: GalleryFilter): List<MediaItem> = when (filter) {
        GalleryFilter.All -> items
        GalleryFilter.Photo -> items.filter { it.type == MediaType.Photo }
        GalleryFilter.Video -> items.filter { it.type == MediaType.Video }
    }

    /** 扫描相机拍摄目录（Pictures/Movies），按修改时间倒序返回。 */
    private fun scanMediaFiles(): List<MediaItem> {
        val context = AppServices.context
        val dirs = listOf(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
        ).distinct()
        return dirs.flatMap { dir ->
            dir.listFiles()?.mapNotNull { file ->
                when {
                    file.isFile && file.extension in PhotoExtensions ->
                        MediaItem(file, MediaType.Photo)
                    file.isFile && file.extension in VideoExtensions ->
                        MediaItem(file, MediaType.Video)
                    else -> null
                }
            } ?: emptyList()
        }.sortedByDescending { it.lastModified }
    }

    private companion object {
        val PhotoExtensions = setOf("png", "jpg", "jpeg", "webp")
        val VideoExtensions = setOf("mp4", "webm", "3gp", "mkv")
    }
}
