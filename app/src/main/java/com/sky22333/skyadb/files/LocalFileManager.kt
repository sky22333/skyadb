package com.sky22333.skyadb.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.RemoteFileEntry
import java.io.File
import com.sky22333.skyadb.validation.FileNamePolicy

class LocalFileManager(
    private val context: Context,
) {
    fun defaultBrowsePath(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

    fun listDirectory(path: String): Result<List<RemoteFileEntry>> = runCatching {
        val dir = File(path)
        require(dir.exists()) { appString(R.string.files_dir_not_exist) }
        require(dir.isDirectory) { appString(R.string.files_not_a_directory) }
        val children = dir.listFiles()
            ?: error(appString(R.string.files_list_dir_permission))
        children
            .asSequence()
            .filter { it.name != "." && it.name != ".." }
            .map { file ->
                RemoteFileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                )
            }
            .sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun createDirectory(parentPath: String, name: String): Result<Unit> = runCatching {
        require(FileNamePolicy.isValid(name)) { appString(R.string.files_name_no_separator) }
        val target = File(parentPath, name)
        require(!target.exists()) { appString(R.string.files_name_exists) }
        require(target.mkdir()) { appString(R.string.files_mkdir_failed) }
    }

    fun delete(path: String, isDirectory: Boolean): Result<Unit> = runCatching {
        val target = File(path)
        require(target.exists()) { appString(R.string.files_target_not_exist) }
        val ok = if (isDirectory) target.deleteRecursively() else target.delete()
        require(ok) { appString(R.string.error_delete_failed) }
    }

    fun rename(path: String, newName: String): Result<Unit> = runCatching {
        val safeName = newName.trim()
        require(safeName.isNotEmpty()) { appString(R.string.files_rename_name_empty) }
        require(FileNamePolicy.isValid(safeName)) { appString(R.string.files_name_no_separator) }
        val source = File(path)
        require(source.exists()) { appString(R.string.files_target_not_exist) }
        val target = File(source.parentFile, safeName)
        require(!target.exists()) { appString(R.string.files_name_exists) }
        require(source.renameTo(target)) { appString(R.string.error_rename_failed) }
    }

    fun displayName(uri: Uri): String {
        val fromCursor = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }

        return fromCursor
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "selected-${System.currentTimeMillis()}"
    }

    fun copyToCache(uri: Uri, preferredName: String = displayName(uri)): File {
        val taskDir = File(context.cacheDir, "picked/${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val target = File(taskDir, FileNamePolicy.sanitize(preferredName))
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { appString(R.string.files_cannot_read_picked) }
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: Throwable) {
            taskDir.deleteRecursively()
            throw error
        }
        return target
    }

    fun createExportApkFile(packageName: String): File {
        val safeName = packageName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "app" }
        val targetDir = File(context.cacheDir, "exported-apps")
        targetDir.mkdirs()
        cleanupApkFiles(targetDir)
        return File(targetDir, "$safeName.apk")
    }

    fun copyToUri(file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { appString(R.string.files_cannot_write_save_location) }
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun cleanupApkFiles(targetDir: File) {
        targetDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
