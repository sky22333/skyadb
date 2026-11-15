package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.usb.KadbUsb
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.ShellCommandResult
import com.sky22333.skyadb.model.operationCatching
import java.io.File
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineStart
import okio.source

enum class AdbSessionKind {
    None,
    Wifi,
    UsbAdb,
    UsbFastboot,
}

internal fun parseDeviceProperties(output: String): Map<String, String> =
    output.lineSequence().mapNotNull { line ->
        val separator = line.indexOf("]: [")
        if (!line.startsWith('[') || separator < 1 || !line.endsWith(']')) return@mapNotNull null
        line.substring(1, separator) to line.substring(separator + 4, line.lastIndex).trim()
    }.toMap()

class KadbManager {
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    @Volatile private var activeKadb: Kadb? = null
    @Volatile private var activeEndpoint: String? = null

    @Volatile private var sessionKind: AdbSessionKind = AdbSessionKind.None

    internal fun sessionToken(): Any? = activeKadb

    internal fun isConnected(): Boolean = activeKadb?.connectionCheck() == true

    internal suspend fun <T> withSession(token: Any, action: suspend () -> T): T =
        sessionMutex.withLock {
            check(activeKadb === token) { "ADB session changed" }
            action()
        }

    suspend fun connect(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = 10_000,
        socketTimeoutMillis: Int = 30_000,
    ): AdbOperationResult<String> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                disconnectAdbOnlyLocked()
                openKadbSessionLocked(
                    host = host,
                    port = port,
                    connectTimeoutMillis = connectTimeoutMillis,
                    socketTimeoutMillis = socketTimeoutMillis,
                    sessionKind = AdbSessionKind.Wifi,
                    endpoint = "$host:$port",
                )
            }
        }

    private suspend fun openKadbSessionLocked(
        host: String,
        port: Int,
        connectTimeoutMillis: Int,
        socketTimeoutMillis: Int,
        sessionKind: AdbSessionKind,
        endpoint: String,
    ): AdbOperationResult<String> {
        activeKadb?.close()
        activeKadb = null
        return operationCatching {
            val kadb = Kadb.create(host, port, connectTimeoutMillis, socketTimeoutMillis)
            try {
                probeConnection(kadb, maxOf(connectTimeoutMillis, socketTimeoutMillis))
                activeKadb = kadb
                activeEndpoint = endpoint
                this.sessionKind = sessionKind
                AdbOperationResult.Success(endpoint)
            } catch (error: Throwable) {
                kadb.close()
                throw error
            }
        }
            .getOrElse { error ->
                AdbOperationResult.Failure(
                    message = appString(R.string.kadb_cannot_connect_device),
                    suggestion = appString(R.string.kadb_cannot_connect_suggestion),
                    cause = error,
                )
            }
    }

    suspend fun connectUsb(
        usbManager: UsbManager,
        device: UsbDevice,
        connectTimeoutMillis: Int = 10_000,
        socketTimeoutMillis: Int = 30_000,
    ): AdbOperationResult<String> =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                disconnectLocked()
                operationCatching {
                    check(usbManager.hasPermission(device)) {
                        appString(R.string.repo_usb_permission_missing)
                    }
                    val client = KadbUsb.create(AppServices.context, device)
                    try {
                        probeConnection(client, maxOf(connectTimeoutMillis, socketTimeoutMillis))
                        activeKadb = client
                        activeEndpoint = "usb-otg:${device.deviceName}"
                        sessionKind = AdbSessionKind.UsbAdb
                        AdbOperationResult.Success(activeEndpoint!!)
                    } catch (error: Throwable) {
                        client.close()
                        throw error
                    }
                }
                    .getOrElse { error ->
                        AdbOperationResult.Failure(
                            message = appString(R.string.kadb_usb_otg_connect_failed),
                            suggestion = appString(R.string.usb_connection_hint),
                            cause = error,
                        )
                    }
            }
        }

    /** Closing Kadb aborts its pending USB request/socket; cancelling a Job alone cannot. */
    private suspend fun probeConnection(client: Kadb, timeoutMillis: Int) {
        try {
            withTimeout(timeoutMillis.toLong()) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { client.close() }
                    connectionScope.launch {
                        val result = operationCatching {
                            check(client.shell("echo kadb_ready").exitCode == 0) {
                                "ADB probe failed"
                            }
                        }
                        if (continuation.isActive) continuation.resumeWith(result)
                        else client.close()
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw SocketTimeoutException("ADB authorization timed out after ${timeoutMillis}ms")
                .apply { initCause(error) }
        }
    }

    suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        name: String = "sky adb",
    ): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            operationCatching {
                Kadb.pair(host, port, pairingCode, name)
                AdbOperationResult.Success(Unit)
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_pair_failed),
                        suggestion = appString(R.string.kadb_pair_failed_suggestion),
                        cause = error,
                    )
                }
        }

    /** Reads shell output in bounded chunks; never buffers the complete device response. */
    suspend fun shell(command: String, maxOutputBytes: Int = 1_000_000, timeoutMillis: Long? = 30_000): AdbOperationResult<ShellCommandResult> =
        withContext(Dispatchers.IO) {
            val client = activeKadb ?: return@withContext AdbOperationResult.Failure(
                appString(R.string.kadb_not_connected), appString(R.string.kadb_connect_before_shell_suggestion),
            )
            operationCatching {
                withTimeoutOrNull(timeoutMillis ?: Long.MAX_VALUE) {
                    val v2 = client.supportsFeature("shell_v2")
                    val exitMarker = "\n__skyadb_exit_${java.util.UUID.randomUUID()}__"
                    val legacyCommand = "sh -c ${shellQuote(command)}; printf '${exitMarker.replace("\n", "\\n")}%d\\n' \$?"
                    val stream = client.open(if (v2) "shell,v2,raw:$command" else "shell:$legacyCommand")
                    coroutineScope {
                        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { runCatching { stream.close() } }
                        }
                        try {
                            val out = java.io.ByteArrayOutputStream()
                            val err = java.io.ByteArrayOutputStream()
                            var truncated = false
                            var exit = -1
                            val buffer = ByteArray(8192)
                            val readContext = currentCoroutineContext()
                            fun consume(length: Long, destination: java.io.ByteArrayOutputStream) {
                                var left = length
                                while (left > 0) {
                                    readContext.ensureActive()
                                    val n = stream.source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                                    if (n < 0) throw java.io.EOFException()
                                    val keep = minOf(n, (maxOutputBytes - destination.size()).coerceAtLeast(0))
                                    if (keep > 0) destination.write(buffer, 0, keep)
                                    if (keep < n) truncated = true
                                    left -= n
                                }
                            }
                            if (v2) {
                                while (true) {
                                    val id = stream.source.readByte().toInt() and 0xff
                                    val length = stream.source.readIntLe()
                                    require(length >= 0 && (id == 1 || id == 2 || id == 3)) { "Invalid shell packet" }
                                    if (id == 3) { require(length == 1); exit = stream.source.readByte().toInt() and 0xff; break }
                                    consume(length.toLong(), if (id == 1) out else err)
                                }
                            } else {
                                var tail = ByteArray(0)
                                fun keepOutput(bytes: ByteArray, length: Int) {
                                    val keep = minOf(length, (maxOutputBytes - out.size()).coerceAtLeast(0))
                                    if (keep > 0) out.write(bytes, 0, keep)
                                    if (keep < length) truncated = true
                                }
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = stream.source.read(buffer, 0, buffer.size)
                                    if (n < 0) break
                                    val combined = tail + buffer.copyOf(n)
                                    val emit = (combined.size - 128).coerceAtLeast(0)
                                    keepOutput(combined, emit)
                                    tail = combined.copyOfRange(emit, combined.size)
                                }
                                val text = tail.toString(Charsets.ISO_8859_1)
                                val marker = text.lastIndexOf(exitMarker)
                                val code = if (marker >= 0) text.substring(marker + exitMarker.length).trim().toIntOrNull() else null
                                if (code != null && code in 0..255) {
                                    exit = code
                                    keepOutput(tail, marker)
                                } else keepOutput(tail, tail.size)
                            }
                            val suffix = if (truncated) "\n[output truncated]" else ""
                            AdbOperationResult.Success(ShellCommandResult(command, out.toString("UTF-8") + suffix, err.toString("UTF-8"), exit))
                        } finally { watcher.cancel(); stream.close() }
                    }
                } ?: throw SocketTimeoutException("Shell command timed out")
            }.getOrElse { error -> AdbOperationResult.Failure(appString(R.string.kadb_command_failed), appString(R.string.kadb_command_failed_suggestion), error) }
        }

    suspend fun reboot(target: String): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val client = activeKadb ?: return@withLock AdbOperationResult.Failure(
                appString(R.string.kadb_not_connected), appString(R.string.kadb_connect_before_shell_suggestion),
            )
            operationCatching {
                withTimeoutOrNull(10_000) {
                    runInterruptible {
                        val stream = client.open("reboot:$target")
                        runCatching { stream.close() }
                    }
                    true
                }
                    ?: throw SocketTimeoutException("Reboot request timed out")
                AdbOperationResult.Success("")
            }.getOrElse { error ->
                AdbOperationResult.Failure(appString(R.string.kadb_command_failed), error.message ?: appString(R.string.kadb_command_failed_suggestion), error)
            }
        }
    }

    suspend fun fetchDeviceInfo(): AdbOperationResult<DeviceInfo> =
        withContext(Dispatchers.IO) {
            val kadb =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_not_connected),
                        suggestion = appString(R.string.kadb_connect_before_device_info_suggestion),
                    )

            operationCatching {
                val unknown = appString(R.string.unknown)
                val properties = parseDeviceProperties(kadb.shell("getprop").output)
                fun property(key: String) = properties[key]?.takeIf { it.isNotBlank() } ?: unknown
                val resolution = parseResolution(kadb.shell("wm size").output)
                val battery = parseBatteryLevel(kadb.shell("dumpsys battery").output)

                AdbOperationResult.Success(
                    DeviceInfo(
                        brand = property("ro.product.brand"),
                        manufacturer = property("ro.product.manufacturer"),
                        model = property("ro.product.model"),
                        device = property("ro.product.device"),
                        androidVersion = property("ro.build.version.release"),
                        sdk = property("ro.build.version.sdk"),
                        abi = property("ro.product.cpu.abi"),
                        buildId = property("ro.build.id"),
                        securityPatch = property("ro.build.version.security_patch"),
                        resolution = resolution,
                        battery = battery,
                    )
                )
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_read_device_info_failed),
                        suggestion = appString(R.string.kadb_read_device_info_suggestion),
                        cause = error,
                    )
                }
        }

    suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val kadb =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_not_connected),
                        suggestion = appString(R.string.kadb_connect_before_install_suggestion),
                    )

            operationCatching {
                val total = apkFile.length()
                apkFile.source().use { raw ->
                    val source =
                        if (onProgress == null) {
                            raw
                        } else {
                            CountingSource(raw, total, onProgress)
                        }
                    kadb.install(source, total)
                }
                AdbOperationResult.Success(Unit)
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_apk_install_failed),
                        suggestion =
                            adbFailureSuggestion(
                                error = error,
                                fallback = appString(R.string.kadb_apk_install_fallback_suggestion),
                            ),
                        cause = error,
                    )
                }
        }

    suspend fun uninstall(packageName: String): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val kadb =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_not_connected),
                        suggestion = appString(R.string.kadb_connect_before_uninstall_suggestion),
                    )

            operationCatching {
                kadb.uninstall(packageName)
                AdbOperationResult.Success(Unit)
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_app_uninstall_failed),
                        suggestion = appString(R.string.kadb_app_uninstall_suggestion),
                        cause = error,
                    )
                }
        }

    suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val result = shell("am force-stop ${shellQuote(packageName)}")
            when (result) {
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message = appString(R.string.kadb_app_stop_failed),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_app_stop_suggestion)
                                },
                        )
                    }
                }
                is AdbOperationResult.Failure -> result
            }
        }

    suspend fun launchApp(packageName: String): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val result = shell("monkey -p ${shellQuote(packageName)} 1")
            when (result) {
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message = appString(R.string.kadb_app_launch_failed),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_app_launch_suggestion)
                                },
                        )
                    }
                }
                is AdbOperationResult.Failure -> result
            }
        }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val command =
                if (enabled) {
                    "pm enable ${shellQuote(packageName)}"
                } else {
                    "pm disable-user --user 0 ${shellQuote(packageName)}"
                }
            when (val result = shell(command)) {
                is AdbOperationResult.Failure -> result
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message =
                                appString(
                                    if (enabled) R.string.kadb_app_enable_failed
                                    else R.string.kadb_app_disable_failed
                                ),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_app_toggle_suggestion)
                                },
                        )
                    }
                }
            }
        }

    suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File> =
        withContext(Dispatchers.IO) {
            val pathResult =
                when (val result = shell("pm path ${shellQuote(packageName)}")) {
                    is AdbOperationResult.Failure -> return@withContext result
                    is AdbOperationResult.Success -> result.data
                }
            if (pathResult.exitCode != 0) {
                return@withContext AdbOperationResult.Failure(
                    message = appString(R.string.kadb_apk_export_failed),
                    suggestion =
                        pathResult.errorOutput.ifBlank {
                            appString(R.string.kadb_apk_export_check_exist_suggestion)
                        },
                )
            }

            val apkPath =
                parseApkPaths(pathResult.output).singleOrNull()
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_apk_export_failed),
                        suggestion = appString(R.string.kadb_apk_export_single_only_suggestion),
                    )

            localFile.parentFile?.mkdirs()
            when (val pullResult = pull(apkPath, localFile)) {
                is AdbOperationResult.Success -> AdbOperationResult.Success(localFile)
                is AdbOperationResult.Failure ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_apk_export_failed),
                        suggestion = pullResult.suggestion,
                        cause = pullResult.cause,
                    )
            }
        }

    /** Preserve package management when a target cannot start the metadata probe. */
    suspend fun listPackages(): AdbOperationResult<List<AppInfo>> = withContext(Dispatchers.IO) {
        val client = activeKadb ?: return@withContext AdbOperationResult.Failure(
            appString(R.string.kadb_not_connected), appString(R.string.kadb_connect_before_list_apps_suggestion))
        operationCatching {
            fun names(command: String): List<String> {
                val result = client.shell(command)
                check(result.exitCode == 0) { "Package listing failed" }
                return result.output.lineSequence().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toList()
            }
            val disabled = names("pm list packages -d").toSet()
            val users = names("pm list packages -3").map { AppInfo(it, it, false, it !in disabled) }
            val system = names("pm list packages -s").map { AppInfo(it, it, true, it !in disabled) }
            AdbOperationResult.Success((users + system).sortedBy { it.packageName })
        }.getOrElse { error ->
            AdbOperationResult.Failure(appString(R.string.kadb_list_apps_failed), appString(R.string.kadb_list_apps_suggestion), error)
        }
    }

    suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>> =
        withContext(Dispatchers.IO) {
            val client =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        appString(R.string.kadb_not_connected),
                        appString(R.string.kadb_connect_before_list_apps_suggestion),
                    )
            val parent = remotePath.trimEnd('/').ifBlank { "/" }
            operationCatching {
                client.openSync().use { sync ->
                    val entries = sync.list(parent).filter { it.name != "." && it.name != ".." }
                    val result =
                        entries
                            .map { entry ->
                                check(entry.errorCode == null || entry.errorCode == 0) {
                                    "Cannot stat ${entry.name}: ${entry.errorCode}"
                                }
                                val path =
                                    if (parent == "/") "/${entry.name}" else "$parent/${entry.name}"
                                val mode =
                                    if (
                                        entry.mode and 0xF000 == 0xA000 &&
                                            client.supportsFeature("stat_v2")
                                    ) {
                                        sync.statV2(path).mode
                                    } else entry.mode
                                RemoteFileEntry(
                                    entry.name,
                                    path,
                                    mode and 0xF000 == 0x4000,
                                    entry.size,
                                )
                            }
                            .sortedWith(
                                compareBy<RemoteFileEntry> { !it.isDirectory }
                                    .thenBy { it.name.lowercase() }
                            )
                    AdbOperationResult.Success(result)
                }
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        appString(R.string.kadb_list_dir_failed),
                        error.message ?: appString(R.string.kadb_list_dir_suggestion),
                        error,
                    )
                }
        }

    suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            when (val result = shell("mkdir ${shellQuote(remotePath)}")) {
                is AdbOperationResult.Failure -> result
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message = appString(R.string.kadb_mkdir_failed),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_mkdir_suggestion)
                                },
                        )
                    }
                }
            }
        }

    suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val command =
                if (isDirectory) "rmdir ${shellQuote(remotePath)}"
                else "rm -f ${shellQuote(remotePath)}"
            when (val result = shell(command)) {
                is AdbOperationResult.Failure -> result
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message = appString(R.string.error_delete_failed),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_delete_suggestion)
                                },
                        )
                    }
                }
            }
        }

    suspend fun renameFile(remotePath: String, newName: String): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val parent =
                remotePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "/")
            val target = if (parent == "/") "/$newName" else "$parent/$newName"
            when (val result = shell("mv ${shellQuote(remotePath)} ${shellQuote(target)}")) {
                is AdbOperationResult.Failure -> result
                is AdbOperationResult.Success -> {
                    if (result.data.exitCode == 0) {
                        AdbOperationResult.Success(Unit)
                    } else {
                        AdbOperationResult.Failure(
                            message = appString(R.string.error_rename_failed),
                            suggestion =
                                result.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_rename_suggestion)
                                },
                        )
                    }
                }
            }
        }

    suspend fun push(
        localFile: File,
        remotePath: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val kadb =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_not_connected),
                        suggestion = appString(R.string.kadb_connect_before_push_suggestion),
                    )

            operationCatching {
                val total = localFile.length()
                localFile.source().use { raw ->
                    val source =
                        if (onProgress == null) {
                            raw
                        } else {
                            CountingSource(raw, total, onProgress)
                        }
                    kadb.push(
                        source = source,
                        remotePath = remotePath,
                        mode = DefaultPushMode,
                        lastModifiedMs = localFile.lastModified(),
                    )
                }
                AdbOperationResult.Success(Unit)
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_push_failed),
                        suggestion =
                            adbFailureSuggestion(
                                error = error,
                                fallback = appString(R.string.kadb_push_fallback_suggestion),
                            ),
                        cause = error,
                    )
                }
        }

    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val kadb =
                activeKadb
                    ?: return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_not_connected),
                        suggestion = appString(R.string.kadb_connect_before_pull_suggestion),
                    )

            operationCatching {
                kadb.pull(localFile, remotePath)
                AdbOperationResult.Success(Unit)
            }
                .getOrElse { error ->
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_pull_failed),
                        suggestion = appString(R.string.kadb_pull_suggestion),
                        cause = error,
                    )
                }
        }

    suspend fun captureScreenshot(localFile: File): AdbOperationResult<File> =
        withContext(Dispatchers.IO) {
            val remotePath =
                "/sdcard/Download/adb-manager-screenshot-${System.currentTimeMillis()}.png"
            when (val captureResult = shell("screencap -p $remotePath")) {
                is AdbOperationResult.Failure -> captureResult
                is AdbOperationResult.Success -> {
                    if (captureResult.data.exitCode != 0) {
                        return@withContext AdbOperationResult.Failure(
                            message = appString(R.string.kadb_screenshot_failed),
                            suggestion =
                                captureResult.data.errorOutput.ifBlank {
                                    appString(R.string.kadb_screenshot_suggestion)
                                },
                        )
                    }
                    localFile.parentFile?.mkdirs()
                    val pullResult = pull(remotePath, localFile)
                    shell("rm ${shellQuote(remotePath)}")
                    when (pullResult) {
                        is AdbOperationResult.Success -> AdbOperationResult.Success(localFile)
                        is AdbOperationResult.Failure -> pullResult
                    }
                }
            }
        }

    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock { disconnectLocked() }
        }

    private fun disconnectLocked() {
        activeKadb?.close()
        activeKadb = null
        activeEndpoint = null
        sessionKind = AdbSessionKind.None
    }

    private fun disconnectAdbOnlyLocked() {
        activeKadb?.close()
        activeKadb = null
        activeEndpoint = null
        if (sessionKind == AdbSessionKind.Wifi || sessionKind == AdbSessionKind.UsbAdb) {
            sessionKind = AdbSessionKind.None
        }
    }

    fun sessionKind(): AdbSessionKind = sessionKind

    fun isActiveUsbDevice(deviceName: String): Boolean {
        return when (sessionKind) {
            AdbSessionKind.UsbAdb -> activeEndpoint == "usb-otg:$deviceName"
            AdbSessionKind.UsbFastboot -> activeEndpoint == "fastboot:$deviceName"
            AdbSessionKind.None,
            AdbSessionKind.Wifi -> false
        }
    }

    suspend fun markUsbFastbootSession(deviceName: String) =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                disconnectAdbOnlyLocked()
                activeEndpoint = "fastboot:$deviceName"
                sessionKind = AdbSessionKind.UsbFastboot
            }
        }

    suspend fun clearUsbFastbootSession() =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                if (sessionKind == AdbSessionKind.UsbFastboot) {
                    activeEndpoint = null
                    sessionKind = AdbSessionKind.None
                }
            }
        }

    fun currentEndpoint(): String? = activeEndpoint

    /** Android 11+（API 30）才支持官方音频转发。 */
    suspend fun currentDeviceSdkInt(): Int? =
        withContext(Dispatchers.IO) {
            when (val result = shell("getprop ro.build.version.sdk")) {
                is AdbOperationResult.Failure -> null
                is AdbOperationResult.Success -> {
                    result.data.output
                        .lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?.toIntOrNull()
                }
            }
        }

    /** Kadb 2.1.5 multiplexes video/audio/control on the existing WiFi or USB transport. */
    suspend fun mirrorClient(): AdbOperationResult<Kadb> = sessionMutex.withLock {
        val client = activeKadb
        if (client == null) {
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_not_connected),
                suggestion = appString(R.string.kadb_connect_before_mirror_suggestion),
            )
        } else {
            AdbOperationResult.Success(client)
        }
    }

    private fun parseResolution(output: String): String {
        return output
            .lineSequence()
            .firstOrNull { it.contains("Physical size") || it.contains("Override size") }
            ?.substringAfter(":")
            ?.trim()
            ?.ifBlank { null } ?: appString(R.string.unknown)
    }

    private fun parseBatteryLevel(output: String): String {
        val level =
            output
                .lineSequence()
                .firstOrNull { it.trim().startsWith("level:") }
                ?.substringAfter(":")
                ?.trim()

        return if (level.isNullOrBlank()) appString(R.string.unknown) else "$level%"
    }

    private fun parseApkPaths(output: String): List<String> {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.endsWith(".apk", ignoreCase = true) }
            .toList()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private companion object {
        const val DefaultPushMode = 420
    }
}
