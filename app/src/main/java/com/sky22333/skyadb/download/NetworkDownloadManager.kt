package com.sky22333.skyadb.download

import android.content.Context
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.operationCatching
import com.sky22333.skyadb.validation.DownloadInputValidator
import com.sky22333.skyadb.validation.FileNamePolicy
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class NetworkDownloadManager(
    private val context: Context? = null,
    private val persistentDirectory: File? = null,
    private val client: OkHttpClient =
        OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build(),
) {
    private val activeCall = AtomicReference<Call?>(null)

    fun validateUrl(url: String): Boolean {
        return DownloadInputValidator.isHttpUrl(url)
    }

    fun cancelCurrentDownload() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun download(
        url: String,
        preferredFileName: String? = null,
        onProgress: (DownloadTask) -> Unit,
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            if (!validateUrl(url)) {
                return@withContext DownloadResult.Failure(
                    message = appString(R.string.download_invalid_url),
                    suggestion = appString(R.string.download_invalid_url_suggestion),
                )
            }

            val call = client.newCall(Request.Builder().url(url).get().build())
            check(activeCall.compareAndSet(null, call)) { "A download is already running" }
            val cancellationWatcher =
                CoroutineScope(currentCoroutineContext() + Dispatchers.Default).launch(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
            try {
                operationCatching {
                    call.awaitResponse().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext DownloadResult.Failure(
                                message = appString(R.string.download_failed),
                                suggestion =
                                    appString(
                                        R.string.download_failed_server_code_suggestion,
                                        response.code,
                                    ),
                            )
                        }

                        val body = response.body

                        val fileName =
                            FileNamePolicy.sanitize(
                                preferredFileName?.takeIf { it.isNotBlank() }
                                    ?: response
                                        .header("Content-Disposition")
                                        ?.let(::fileNameFromContentDisposition)
                                    ?: fileNameFromUrl(url)
                                    ?: "download-${System.currentTimeMillis()}"
                            )

                        val downloadDir = downloadDir()
                        if (persistentDirectory == null) cleanupDownloadDir(downloadDir)
                        val taskDir =
                            File(downloadDir, java.util.UUID.randomUUID().toString()).apply {
                                mkdirs()
                            }
                        val targetFile = File(taskDir, fileName)
                        targetFile.parentFile?.mkdirs()

                        val totalBytes = body.contentLength().takeIf { it > 0L } ?: -1L
                        var downloadedBytes = 0L
                        var lastProgressAt = 0L

                        try {
                            body.byteStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        ensureActive()
                                        if (call.isCanceled()) {
                                            targetFile.delete()
                                            taskDir.delete()
                                            return@withContext DownloadResult.Canceled
                                        }
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        output.write(buffer, 0, read)
                                        downloadedBytes += read

                                        val progress =
                                            if (totalBytes > 0L) {
                                                (downloadedBytes.toFloat() / totalBytes).coerceIn(
                                                    0f,
                                                    1f,
                                                )
                                            } else {
                                                0f
                                            }
                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressAt >= ProgressUpdateIntervalMillis) {
                                            lastProgressAt = now
                                            onProgress(
                                                DownloadTask(
                                                    url = url,
                                                    fileName = fileName,
                                                    targetPath = "",
                                                    localPath = targetFile.absolutePath,
                                                    progress = progress,
                                                    state = DownloadState.Downloading,
                                                    message =
                                                        if (totalBytes > 0L) {
                                                            appString(
                                                                R.string.download_progress_percent,
                                                                (progress * 100).toInt(),
                                                            )
                                                        } else {
                                                            appString(
                                                                R.string.download_progress_bytes,
                                                                formatBytes(downloadedBytes),
                                                            )
                                                        },
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (error: Throwable) {
                            targetFile.delete()
                            taskDir.delete()
                            throw error
                        }
                        onProgress(
                            DownloadTask(
                                url = url,
                                fileName = fileName,
                                targetPath = "",
                                localPath = targetFile.absolutePath,
                                progress = 1f,
                                state = DownloadState.Downloading,
                                message = appString(R.string.download_complete),
                            )
                        )

                        DownloadResult.Success(
                            fileName = fileName,
                            localPath = targetFile.absolutePath,
                        )
                    }
                }
                    .getOrElse { error ->
                        if (error is CancellationException) throw error
                        currentCoroutineContext().ensureActive()
                        if (call.isCanceled()) return@withContext DownloadResult.Canceled
                        DownloadResult.Failure(
                            message = appString(R.string.download_failed),
                            suggestion = appString(R.string.download_failed_generic_suggestion),
                            cause = error,
                        )
                    }
            } finally {
                activeCall.compareAndSet(call, null)
                cancellationWatcher.cancel()
                call.cancel()
            }
        }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { _, value, _ -> value.close() }
                    }
                }
            )
        }

    private fun downloadDir(): File {
        persistentDirectory?.let { return it }
        val baseDir = context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        return File(baseDir, "downloads")
    }

    private fun fileNameFromUrl(url: String): String? {
        return url.substringBefore("?")
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
    }

    private fun fileNameFromContentDisposition(value: String): String? {
        return value
            .split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("filename=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun cleanupDownloadDir(directory: File) {
        directory
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MaxCachedDownloads)
            ?.forEach { file -> runCatching { file.deleteRecursively() } }
    }

    private companion object {
        const val ProgressUpdateIntervalMillis = 150L
        const val MaxCachedDownloads = 3
    }
}
