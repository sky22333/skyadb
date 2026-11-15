package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.usb.AndroidUsbInterface
import io.github.rhythmcache.fastboot.FastbootConnection
import io.github.rhythmcache.fastboot.FastbootClient
import io.github.rhythmcache.fastboot.FastbootException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream

class FastbootOtgManager {
    private val mutex = Mutex()
    private var channel: FastbootUsbChannel? = null
    private var connection: FastbootConnection? = null
    @Volatile private var activeDeviceName: String? = null
    @Volatile private var sessionId: String? = null

    suspend fun connect(usbManager: UsbManager, device: UsbDevice): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectLocked()
            val iface = AndroidUsbInterface.findFastbootInterface(device)
                ?: return@withLock AdbOperationResult.Failure(appString(R.string.fastboot_interface_not_found), appString(R.string.fastboot_interface_suggestion))
            val usb = usbManager.openDevice(device)
                ?: return@withLock AdbOperationResult.Failure(appString(R.string.usb_cannot_open_device), appString(R.string.usb_replug_allow_access_suggestion))
            try {
                val opened = FastbootUsbChannel(usb, iface) { usbManager.deviceList.containsKey(device.deviceName) }
                channel = opened
                connection = FastbootConnection(opened)
                operationContext = currentCoroutineContext()
                opened.checkActive = { currentContextCheck() }
                opened.deadlineNanos = System.nanoTime() + 3_000_000_000L
                try { connection!!.rawCommand("getvar:product") } catch (_: FastbootException.DeviceFail) { }
                opened.deadlineNanos = Long.MAX_VALUE
                operationContext = null
                activeDeviceName = device.deviceName
                sessionId = java.util.UUID.randomUUID().toString()
                AdbOperationResult.Success("fastboot:${device.deviceName}")
            } catch (e: Exception) {
                operationContext = null
                disconnectLocked(); usb.close()
                if (e is CancellationException) throw e
                AdbOperationResult.Failure(appString(R.string.fastboot_connect_failed), appString(R.string.fastboot_connect_failed_suggestion), e)
            }
        }
    }

    // Captured per operation so blocking USB polls observe cancellation.
    private var operationContext: kotlin.coroutines.CoroutineContext? = null
    private fun currentContextCheck() { operationContext?.ensureActive() }

    suspend fun sendCommand(command: String): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: return@withLock notConnected()
            operationContext = currentCoroutineContext()
            channel?.deadlineNanos = System.nanoTime() + 10_000_000_000L
            val output = StringBuilder()
            var truncated = false
            fun append(s: String) {
                val remaining = 64 * 1024 - 512 - output.length
                if (s.length > remaining) truncated = true
                if (remaining > 0) output.append(s.take(remaining))
            }
            fun terminal(s: String) {
                if (truncated) output.append("\n…")
                if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                output.append(s.take(480))
            }
            try {
                val result = conn.rawCommand(command.trim(), onInfo = { append("(bootloader) $it\n") }, onText = { append(it) })
                terminal("OKAY" + if (result.response.isEmpty()) "" else ": ${result.response}")
                if (command == "reboot" || command.startsWith("reboot-") || (command == "continue" || command == "shutdown")) disconnectLocked()
                AdbOperationResult.Success(output.toString())
            } catch (e: CancellationException) { disconnectLocked(); throw e
            } catch (e: Exception) {
                if (e !is FastbootException.DeviceFail) disconnectLocked() else terminal("FAIL: ${e.message}")
                AdbOperationResult.Failure(appString(R.string.fastboot_command_failed), e.message ?: appString(R.string.fastboot_command_failed_suggestion), e, output.toString())
            } finally { operationContext = null; channel?.deadlineNanos = Long.MAX_VALUE }
        }
    }

    suspend fun readSummary(): AdbOperationResult<FastbootDeviceSummary> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: return@withLock notConnected()
            operationContext = currentCoroutineContext()
            try {
                val values = FastbootDeviceSummary.Variables.associateWith { variable ->
                    channel?.deadlineNanos = System.nanoTime() + 3_000_000_000L
                    try { conn.rawCommand("getvar:$variable").response.takeIf { it.isNotBlank() } }
                    catch (_: FastbootException.DeviceFail) { null }
                }
                AdbOperationResult.Success(FastbootDeviceSummary(values))
            } catch (e: CancellationException) { disconnectLocked(); throw e
            } catch (e: Exception) {
                disconnectLocked()
                AdbOperationResult.Failure(appString(R.string.fastboot_summary_failed), e.message ?: appString(R.string.fastboot_connect_failed_suggestion), e)
            } finally { operationContext = null; channel?.deadlineNanos = Long.MAX_VALUE }
        }
    }

    suspend fun image(
        action: String, partition: String, stream: FileInputStream, size: Long, maxChunkSize: Long?,
        expectedSession: String, onProgress: (Long, Long) -> Unit,
    ): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: return@withLock notConnected()
            check(sessionId == expectedSession) { "Fastboot device changed" }
            require(action in setOf("flash", "boot", "stage")) { "Invalid image action" }
            require(action != "flash" || partition.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid partition" }
            operationContext = currentCoroutineContext()
            val log = StringBuilder()
            fun info(line: String) {
                log.append(line.take(512)).append('\n')
                if (log.length > 4096) log.delete(0, log.length - 4096)
            }
            try {
                val client = FastbootClient(conn)
                val progress: (io.github.rhythmcache.fastboot.Progress) -> Unit = { onProgress(it.current, it.total) }
                val result = when (action) {
                    "flash" -> client.flash(partition, stream, size, ::info, progress, maxChunkSize)
                    "boot" -> client.boot(stream, size, progress, ::info)
                    else -> client.stage(stream, size, progress)
                }
                if (action == "boot") disconnectLocked()
                AdbOperationResult.Success("${log}OKAY: ${result.response}")
            } catch (e: CancellationException) { disconnectLocked(); throw e
            } catch (e: Exception) {
                if (e !is FastbootException.DeviceFail) disconnectLocked()
                AdbOperationResult.Failure(appString(R.string.fastboot_command_failed), e.message ?: "Image operation failed", e, log.toString())
            } finally { operationContext = null; channel?.deadlineNanos = Long.MAX_VALUE }
        }
    }

    suspend fun pull(action: String, partition: String, file: File, expectedSession: String, onProgress: (Long, Long) -> Unit): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: return@withLock notConnected()
            check(sessionId == expectedSession) { "Fastboot device changed" }
            require(action == "upload" || action == "fetch") { "Invalid transfer action" }
            require(action != "fetch" || partition.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid partition" }
            operationContext = currentCoroutineContext()
            try {
                val client = FastbootClient(conn)
                file.outputStream().use { output ->
                    val sink: (ByteArray, Int) -> Unit = { data, length -> output.write(data, 0, length) }
                    val progress: (io.github.rhythmcache.fastboot.Progress) -> Unit = { onProgress(it.current, it.total) }
                    if (action == "fetch") client.fetch(partition, sink, onProgress = progress)
                    else client.upload(sink, progress)
                }
                AdbOperationResult.Success(file.absolutePath)
            } catch (e: CancellationException) { disconnectLocked(); throw e
            } catch (e: Exception) {
                if (e !is FastbootException.DeviceFail) disconnectLocked()
                AdbOperationResult.Failure(appString(R.string.fastboot_command_failed), e.message ?: "Transfer failed", e)
            } finally { operationContext = null; channel?.deadlineNanos = Long.MAX_VALUE }
        }
    }

    private fun notConnected() = AdbOperationResult.Failure(appString(R.string.fastboot_not_connected), appString(R.string.fastboot_connect_via_usb_otg_suggestion))
    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { disconnectLocked() } }
    private fun disconnectLocked() { runCatching { connection?.close() }; connection = null; channel = null; activeDeviceName = null; sessionId = null }
    fun currentDeviceName(): String? = activeDeviceName
    fun currentSessionId(): String? = sessionId
}
