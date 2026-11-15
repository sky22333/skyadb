package com.sky22333.skyadb.ui.device

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.ai.AiFastboot
import com.sky22333.skyadb.ai.copyFirmwareData
import com.sky22333.skyadb.model.AdbOperationResult
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class FastbootPanel { Flash, Boot, Archive, Slot }
internal data class ArchiveEntry(val name: String, val size: Long)
internal data class FastbootActionsState(
    val panel: FastbootPanel? = null,
    val session: String? = null,
    val image: Uri? = null,
    val name: String = "",
    val size: Long = -1,
    val partition: String = "",
    val slot: String = "",
    val entries: List<ArchiveEntry> = emptyList(),
    val busy: Boolean = false,
    val progress: Float? = null,
    val bytesPerSecond: Long = 0,
    val status: String = "",
    val failed: Boolean = false,
    val completed: Boolean = false,
)

internal class FastbootActionsViewModel : ViewModel() {
    private val state = MutableStateFlow(FastbootActionsState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var archive: File? = null
    private var extracted: File? = null
    private val results = mutableMapOf<Pair<FastbootPanel, String>, Pair<String, Boolean>>()

    fun open(panel: FastbootPanel) {
        if (state.value.busy) return
        clearFiles()
        val session = AppServices.fastbootOtgManager.currentSessionId()
        val previous = session?.let { results[panel to it] }
        state.value = FastbootActionsState(panel = panel, session = session, status = previous?.first.orEmpty(), failed = previous?.second ?: false)
    }
    fun close() {
        if (state.value.busy) return
        clearFiles()
        state.value = FastbootActionsState()
    }
    fun partition(value: String) { state.update { it.copy(partition = value, status = "", failed = false, completed = false) } }
    fun slot(value: String) { state.update { it.copy(slot = value, status = "", failed = false, completed = false) } }
    fun cancel() { job?.cancel() }

    fun select(uri: Uri?) {
        if (uri == null || state.value.busy) return
        val panel = state.value.panel
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, progress = null, status = "", failed = false, completed = false, image = null, name = "", entries = emptyList()) }
            var pendingArchive: File? = null
            try {
                val (name, size) = withContext(Dispatchers.IO) {
                    val name = runCatching { AppServices.localFileManager.displayName(uri) }.getOrElse { uri.lastPathSegment.orEmpty() }
                    val size = runCatching {
                        AppServices.context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
                        } ?: -1L
                    }.getOrDefault(-1L)
                    name to size
                }
                if (panel == FastbootPanel.Archive) {
                    val temp = withContext(Dispatchers.IO) {
                        File.createTempFile("fastboot-zip-", ".zip", AppServices.context.cacheDir).also { pendingArchive = it }
                    }
                    archive?.delete(); archive = null
                    val entries = withContext(Dispatchers.IO) {
                        requireNotNull(AppServices.context.contentResolver.openInputStream(uri)).use { input ->
                            temp.outputStream().use { output -> copyFirmwareData(input, output) }
                        }
                        ZipFile(temp).use { zip -> zip.entries().asSequence().filterNot { it.isDirectory }.map { ArchiveEntry(it.name, it.size) }.toList() }
                    }
                    archive = temp
                    pendingArchive = null
                    state.update { it.copy(name = name, size = size, entries = entries, busy = false) }
                } else {
                    extracted?.delete(); extracted = null
                    state.update { it.copy(image = uri, name = name, size = size, busy = false) }
                }
            } catch (e: CancellationException) { state.update { it.copy(busy = false, status = appString(R.string.fb_action_cancelled)) }; throw e }
            catch (e: Exception) { state.update { it.copy(busy = false, status = e.message.orEmpty(), failed = true) } }
            finally { pendingArchive?.delete() }
        }
    }

    fun selectEntry(entry: ArchiveEntry, panel: FastbootPanel) {
        val source = archive ?: return
        if (state.value.busy) return
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, status = "", failed = false) }
            var temp: File? = null
            try {
                val image = withContext(Dispatchers.IO) {
                    File.createTempFile("fastboot-image-", ".img", AppServices.context.cacheDir).also { temp = it }
                }
                withContext(Dispatchers.IO) {
                    ZipFile(source).use { zip ->
                        val item = requireNotNull(zip.getEntry(entry.name))
                        zip.getInputStream(item).use { input -> image.outputStream().use { output -> copyFirmwareData(input, output) } }
                    }
                }
                extracted?.delete(); extracted = image
                state.update { it.copy(panel = panel, image = Uri.fromFile(image), name = entry.name.substringAfterLast('/'), size = image.length(), busy = false, entries = emptyList()) }
                archive?.delete(); archive = null
            } catch (e: CancellationException) { temp?.delete(); state.update { it.copy(busy = false, status = appString(R.string.fb_action_cancelled)) }; throw e }
            catch (e: Exception) { temp?.delete(); state.update { it.copy(busy = false, status = e.message.orEmpty(), failed = true) } }
        }
    }

    fun exportEntry(entry: ArchiveEntry, destination: Uri?) {
        val source = archive ?: return
        if (destination == null || state.value.busy) return
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, status = "", failed = false) }
            try {
                withContext(Dispatchers.IO) {
                    ZipFile(source).use { zip ->
                        val item = requireNotNull(zip.getEntry(entry.name))
                        zip.getInputStream(item).use { input ->
                            requireNotNull(AppServices.context.contentResolver.openOutputStream(destination, "wt")).use { output -> copyFirmwareData(input, output) }
                        }
                    }
                }
                state.update { it.copy(busy = false, status = appString(R.string.fb_exported)) }
            } catch (e: CancellationException) { state.update { it.copy(busy = false, status = appString(R.string.fb_export_incomplete), failed = true) }; throw e }
            catch (e: Exception) { state.update { it.copy(busy = false, status = appString(R.string.fb_export_incomplete) + "\n" + e.message.orEmpty(), failed = true) } }
        }
    }

    fun start() {
        val snapshot = state.value
        val session = snapshot.session ?: run {
            state.update { it.copy(status = appString(R.string.fastboot_not_connected), failed = true) }
            return
        }
        if (snapshot.busy || snapshot.image == null) return
        val target = if (snapshot.panel == FastbootPanel.Flash) fastbootPartitionTarget(snapshot.partition, snapshot.slot) ?: return else ""
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, progress = null, status = "", failed = false, completed = false) }
            try {
                var last = 0L
                val started = android.os.SystemClock.elapsedRealtime()
                val result = AiFastboot.image(
                    if (snapshot.panel == FastbootPanel.Flash) "flash" else "boot", target, "selected", "", snapshot.image,
                    "", session, AppServices.context.cacheDir, null,
                ) { current, total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - last >= 200 || current == total) {
                        last = now
                        state.update { it.copy(progress = if (total > 0) current.toFloat() / total else null, bytesPerSecond = current * 1000 / (now - started).coerceAtLeast(1)) }
                    }
                }
                results[requireNotNull(snapshot.panel) to session] = result to false
                state.update { it.copy(busy = false, progress = null, status = result, completed = true) }
            } catch (e: CancellationException) {
                val message = appString(if (snapshot.panel == FastbootPanel.Flash) R.string.fb_cancelled_partial else R.string.fb_action_cancelled)
                results[requireNotNull(snapshot.panel) to session] = message to true
                state.update { it.copy(busy = false, progress = null, status = message, failed = true) }
                throw e
            }
            catch (e: Exception) {
                val message = e.message.orEmpty()
                results[requireNotNull(snapshot.panel) to session] = message to true
                state.update { it.copy(busy = false, progress = null, status = message, failed = true) }
            }
        }
    }

    fun switchSlot(slot: String) {
        val session = state.value.session ?: run {
            state.update { it.copy(status = appString(R.string.fastboot_not_connected), failed = true) }
            return
        }
        if (state.value.busy) return
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, status = "", failed = false) }
            try {
                when (val result = AppServices.adbRepository.fastbootCommand("set_active:$slot", session)) {
                    is AdbOperationResult.Success -> { results[FastbootPanel.Slot to session] = result.data to false; state.update { it.copy(busy = false, status = result.data, completed = true) } }
                    is AdbOperationResult.Failure -> {
                        val message = result.output.orEmpty() + "\n" + result.suggestion
                        results[FastbootPanel.Slot to session] = message to true
                        state.update { it.copy(busy = false, status = message, failed = true) }
                    }
                }
            } catch (e: CancellationException) { state.update { it.copy(busy = false, status = appString(R.string.fb_action_cancelled)) }; throw e }
            catch (e: Exception) { state.update { it.copy(busy = false, status = e.message.orEmpty(), failed = true) } }
        }
    }
    private fun clearFiles() { archive?.delete(); archive = null; extracted?.delete(); extracted = null }
    override fun onCleared() { job?.cancel(); clearFiles() }
}

internal fun fastbootPartitionTarget(partition: String, slot: String): String? {
    if (!partition.matches(Regex("[A-Za-z0-9_.-]+")) || slot !in setOf("", "a", "b")) return null
    if (slot.isNotEmpty() && Regex("_[ab]$").containsMatchIn(partition)) return null
    return partition + if (slot.isEmpty()) "" else "_$slot"
}
