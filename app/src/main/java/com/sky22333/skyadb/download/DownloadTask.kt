package com.sky22333.skyadb.download

import androidx.annotation.StringRes
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString

data class DownloadTask(
    val url: String,
    val fileName: String,
    val targetPath: String,
    val localPath: String? = null,
    val progress: Float,
    val state: DownloadState,
    val message: String = appString(state.labelRes),
)

enum class DownloadState(@param:StringRes val labelRes: Int) {
    Waiting(R.string.download_state_waiting),
    Downloading(R.string.download_state_downloading),
    Pushing(R.string.download_state_pushing),
    Installing(R.string.download_state_installing),
    Success(R.string.download_state_success),
    Failed(R.string.download_state_failed),
    Canceled(R.string.download_state_canceled),
}

sealed interface DownloadResult {
    data class Success(
        val fileName: String,
        val localPath: String,
    ) : DownloadResult

    data class Failure(
        val message: String,
        val suggestion: String,
        val cause: Throwable? = null,
    ) : DownloadResult

    data object Canceled : DownloadResult
}
