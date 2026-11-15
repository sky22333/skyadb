package com.sky22333.skyadb.ai

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.usb.UsbPermissionEvent
import com.sky22333.skyadb.validation.CommandInputParser
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Fastboot tools operate on the same USB session as the rest of the app. */
internal object AiFastboot {
    suspend fun devices(mode: String): String {
        require(mode in setOf("adb", "fastboot", "any")) { "Mode must be adb, fastboot, or any" }
        val host = AppServices.usbOtgHost
        host.refresh()
        fun matching() = host.attachments.value.filter { mode == "any" || it.mode.name.equals(mode, ignoreCase = true) }
        var attempts = 0
        while (matching().isEmpty() && attempts++ < 30) {
            delay(500)
            host.refresh()
        }
        val devices = matching()
        return if (devices.isEmpty()) "No ADB or Fastboot USB device found." else devices.joinToString("\n") {
            "${it.deviceName} mode=${it.mode} vendor=%04x product=%04x permission=${it.hasPermission} connected=${AppServices.adbRepository.isActiveUsbDevice(it.deviceName)}".format(it.vendorId, it.productId)
        }
    }

    suspend fun connect(deviceName: String): String {
        val host = AppServices.usbOtgHost
        host.refresh()
        val attachment = host.attachments.value.firstOrNull { it.deviceName == deviceName }
            ?: error("USB device not found; discover devices again")
        if (AppServices.adbRepository.isActiveUsbDevice(deviceName)) return "Already connected: $deviceName"
        if (!attachment.hasPermission) {
            val actions = AppServices.usbOtgActions
            actions.askPermission(deviceName)
            val event = withTimeoutOrNull(60_000) {
                actions.latestPermissionResult.first {
                    when (it) {
                        is UsbPermissionEvent.Granted -> it.deviceName == deviceName
                        is UsbPermissionEvent.Denied -> it.deviceName == deviceName
                        is UsbPermissionEvent.Detached -> it.deviceName == deviceName
                        null -> false
                    }
                }
            }
            check(event is UsbPermissionEvent.Granted) { "USB permission not granted or device disconnected" }
        }
        host.refresh()
        return result(AppServices.adbRepository.connectUsbOtg(deviceName))
    }

    suspend fun command(command: String, session: String): String {
        val wire = fastbootWireCommand(command)
        return result(AppServices.adbRepository.fastbootCommand(wire, session))
    }

    suspend fun image(action: String, partition: String, source: String, path: String, selected: Uri?, tree: String, session: String, downloads: File, maxChunkSize: Long?, onProgress: (Long, Long) -> Unit): String = withContext(Dispatchers.IO) {
        require(action in setOf("flash", "boot", "stage")) { "Use flash, boot, or stage" }
        require(action != "flash" || partition.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid partition" }
        suspend fun run(stream: FileInputStream, size: Long): String {
            require(size > 0) { "Image is empty" }
            return result(AppServices.adbRepository.fastbootImage(action, partition, stream, size, maxChunkSize, session, onProgress))
        }
        when (source) {
            "local" -> {
                val file = localFile(path, downloads)
                require(file.isFile && file.canRead()) { "Image is unavailable" }
                FileInputStream(file).use { run(it, file.length()) }
            }
            "selected", "authorized" -> {
                val uri = documentUri(source, path, selected, tree)
                val descriptor = requireNotNull(AppServices.context.contentResolver.openFileDescriptor(uri, "r")) { "Cannot open image" }
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                    val size = descriptor.statSize
                    val seekable = runCatching { stream.channel.position(); true }.getOrDefault(false)
                    if (size > 0 && seekable) run(stream, size) else {
                        // Some document providers expose a pipe or omit the size; spool once for random access.
                        val temp = File.createTempFile("fastboot-image-", ".tmp", AppServices.context.cacheDir)
                        try {
                            temp.outputStream().use { output -> copyFirmwareData(stream, output) }
                            FileInputStream(temp).use { run(it, temp.length()) }
                        } finally { temp.delete() }
                    }
                }
            }
            else -> error("Source must be local, selected, or authorized")
        }
    }

    suspend fun archive(action: String, source: String, path: String, entryName: String, selected: Uri?, tree: String, downloads: File): String = withContext(Dispatchers.IO) {
        require(action == "list" || action == "extract") { "Use list or extract" }
        var temporary: File? = null
        try {
            val archive = if (source == "local") {
                localFile(path, downloads)
            } else {
                val uri = documentUri(source, path, selected, tree)
                val temp = File.createTempFile("fastboot-archive-", ".zip", AppServices.context.cacheDir)
                temporary = temp
                requireNotNull(AppServices.context.contentResolver.openInputStream(uri)).use { input ->
                    temp.outputStream().use { dest -> copyFirmwareData(input, dest) }
                }
                temp
            }
            processFirmwareArchive(archive, action, entryName, downloads)
        } finally {
            temporary?.delete()
        }
    }

    private fun localFile(path: String, downloads: File): File {
        val file = File(path).canonicalFile
        val shared = android.os.Environment.getExternalStorageDirectory().canonicalFile
        val saved = downloads.canonicalFile
        require(file.path.startsWith(shared.path + File.separator) || file.path.startsWith(saved.path + File.separator)) { "File must be in shared storage or app downloads" }
        return file
    }

    private fun documentUri(source: String, path: String, selected: Uri?, tree: String): Uri = when (source) {
        "selected" -> requireNotNull(selected) { "No file selected" }
        "authorized" -> AiDocuments(AppServices.context, Uri.parse(tree.takeIf { it.isNotBlank() } ?: error("No local directory authorized"))).uri(path)
        else -> error("Source must be local, selected, or authorized")
    }

    suspend fun pull(action: String, partition: String, session: String, downloads: File, onProgress: (Long, Long) -> Unit): String = withContext(Dispatchers.IO) {
        require(action in setOf("upload", "fetch")) { "Use upload or fetch" }
        require(action != "fetch" || partition.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid partition" }
        val dir = File(downloads, java.util.UUID.randomUUID().toString())
        check(dir.mkdirs()) { "Cannot create download directory" }
        val file = File(dir, if (action == "fetch") "$partition.img" else "staged.img")
        try {
            val path = result(AppServices.adbRepository.fastbootPull(action, partition, file, session, onProgress))
            "Saved $path (${file.length()} bytes)"
        } catch (e: Exception) {
            file.delete()
            dir.delete()
            throw e
        }
    }

    private fun result(result: AdbOperationResult<String>): String = when (result) {
        is AdbOperationResult.Success -> result.data
        is AdbOperationResult.Failure -> error(listOfNotNull(result.output?.takeLast(350), "${result.message}: ${result.suggestion}").joinToString("\n"))
    }
}

internal fun fastbootWireCommand(command: String): String {
    val parsed = CommandInputParser.parse(command, fastboot = true)
    require(parsed is CommandInputParser.Result.Ready) { "Invalid Fastboot command; use the image tool for file operations" }
    val verb = parsed.command.substringBefore(':').substringBefore(' ')
    require(verb !in setOf("download", "upload", "fetch")) { "Use an image transfer tool for data commands" }
    return parsed.command
}
