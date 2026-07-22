package com.sky22333.skyadb.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.sky22333.skyadb.model.RemoteFileEntry
import java.io.File

class LocalFileManager(
    private val context: Context,
) {
    fun defaultBrowsePath(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

    fun listDirectory(path: String): Result<List<RemoteFileEntry>> = runCatching {
        val dir = File(path)
        require(dir.exists()) { "目录不存在" }
        require(dir.isDirectory) { "不是目录" }
        val children = dir.listFiles()
            ?: error("无法读取目录，请授予「所有文件访问」权限")
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
        val target = File(parentPath, name)
        require(!target.exists()) { "已存在同名项目" }
        require(target.mkdir()) { "创建文件夹失败" }
    }

    fun delete(path: String, isDirectory: Boolean): Result<Unit> = runCatching {
        val target = File(path)
        require(target.exists()) { "目标不存在" }
        val ok = if (isDirectory) target.deleteRecursively() else target.delete()
        require(ok) { "删除失败" }
    }

    fun rename(path: String, newName: String): Result<Unit> = runCatching {
        val safeName = newName.trim()
        require(safeName.isNotEmpty()) { "名称不能为空" }
        require(!safeName.contains('/') && !safeName.contains('\\')) { "名称不能包含路径分隔符" }
        val source = File(path)
        require(source.exists()) { "目标不存在" }
        val target = File(source.parentFile, safeName)
        require(!target.exists()) { "已存在同名项目" }
        require(source.renameTo(target)) { "重命名失败" }
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
        val safeName = preferredName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val target = File(context.cacheDir, "picked/$safeName")
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取选择的文件" }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
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
            requireNotNull(output) { "无法写入选择的保存位置" }
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
