package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.flyfishxu.kadb.Kadb
import com.sky22333.skyadb.R
import com.sky22333.skyadb.apps.AppDisplayEnricher
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.RemoteFileListParser
import com.sky22333.skyadb.model.ShellCommandResult
import com.sky22333.skyadb.usb.AndroidUsbInterface
import com.sky22333.skyadb.usb.UsbAdbBridge
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.source

enum class AdbSessionKind {
    None,
    Wifi,
    UsbAdb,
    UsbFastboot,
}

class KadbManager {
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    @Volatile private var activeKadb: Kadb? = null
    @Volatile private var activeEndpoint: String? = null
    @Volatile private var usbBridge: UsbAdbBridge? = null
    @Volatile private var sessionKind: AdbSessionKind = AdbSessionKind.None
    private var lastConnectTimeoutMillis = 10_000
    private var lastSocketTimeoutMillis = 30_000

    suspend fun connect(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = 10_000,
        socketTimeoutMillis: Int = 30_000,
    ): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            lastConnectTimeoutMillis = connectTimeoutMillis
            lastSocketTimeoutMillis = socketTimeoutMillis
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

    private fun openKadbSessionLocked(
        host: String,
        port: Int,
        connectTimeoutMillis: Int,
        socketTimeoutMillis: Int,
        sessionKind: AdbSessionKind,
        endpoint: String,
    ): AdbOperationResult<String> {
        activeKadb?.close()
        activeKadb = null
        return runCatching {
            val kadb = Kadb.create(host, port, connectTimeoutMillis, socketTimeoutMillis)
            val probe = kadb.shell("echo kadb_ready")
            if (probe.exitCode != 0) {
                return AdbOperationResult.Failure(
                    message = appString(R.string.kadb_connect_failed),
                    suggestion = appString(R.string.kadb_connect_failed_probe_suggestion),
                )
            }
            activeKadb = kadb
            activeEndpoint = endpoint
            this.sessionKind = sessionKind
            AdbOperationResult.Success(endpoint)
        }.getOrElse { error ->
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
    ): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            disconnectAdbOnlyLocked()
            val adbInterface = AndroidUsbInterface.findAdbInterface(device)
                ?: return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.kadb_adb_interface_not_found),
                    suggestion = appString(R.string.kadb_adb_interface_suggestion),
                )
            val connection = usbManager.openDevice(device)
                ?: return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.usb_cannot_open_device),
                    suggestion = appString(R.string.usb_replug_allow_access_suggestion),
                )

            var bridgeOwned = false
            runCatching {
                val bridge = UsbAdbBridge(connection, adbInterface, socketTimeoutMillis)
                usbBridge = bridge
                bridgeOwned = true
                bridge.start(bridgeScope)
                when (
                    val result = openKadbSessionLocked(
                        host = "127.0.0.1",
                        port = bridge.localPort,
                        connectTimeoutMillis = connectTimeoutMillis,
                        socketTimeoutMillis = socketTimeoutMillis,
                        sessionKind = AdbSessionKind.UsbAdb,
                        endpoint = "usb-otg:${device.deviceName}",
                    )
                ) {
                    is AdbOperationResult.Success -> result
                    is AdbOperationResult.Failure -> {
                        disconnectLocked()
                        result
                    }
                }
            }.getOrElse { error ->
                if (bridgeOwned) {
                    disconnectLocked()
                } else {
                    runCatching { connection.close() }
                }
                AdbOperationResult.Failure(
                    message = appString(R.string.kadb_usb_otg_connect_failed),
                    suggestion = appString(R.string.kadb_usb_otg_connect_suggestion),
                    cause = error,
                )
            }
        }
    }

    suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        name: String = "sky adb",
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Kadb.pair(host, port, pairingCode, name)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_pair_failed),
                suggestion = appString(R.string.kadb_pair_failed_suggestion),
                cause = error,
            )
        }
    }

    suspend fun shell(command: String): AdbOperationResult<ShellCommandResult> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_shell_suggestion),
        )

        runCatching {
            val response = kadb.shell(command)
            AdbOperationResult.Success(
                ShellCommandResult(
                    command = command,
                    output = response.output,
                    errorOutput = response.errorOutput,
                    exitCode = response.exitCode,
                ),
            )
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_command_failed),
                suggestion = appString(R.string.kadb_command_failed_suggestion),
                cause = error,
            )
        }
    }

    suspend fun fetchDeviceInfo(): AdbOperationResult<DeviceInfo> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_device_info_suggestion),
        )

        runCatching {
            val unknown = appString(R.string.unknown)
            val brand = kadb.shell("getprop ro.product.brand").output.trim().ifBlank { unknown }
            val model = kadb.shell("getprop ro.product.model").output.trim().ifBlank { unknown }
            val androidVersion = kadb.shell("getprop ro.build.version.release").output.trim().ifBlank { unknown }
            val sdk = kadb.shell("getprop ro.build.version.sdk").output.trim().ifBlank { unknown }
            val abi = kadb.shell("getprop ro.product.cpu.abi").output.trim().ifBlank { unknown }
            val resolution = parseResolution(kadb.shell("wm size").output)
            val battery = parseBatteryLevel(kadb.shell("dumpsys battery").output)

            AdbOperationResult.Success(
                DeviceInfo(
                    brand = brand,
                    model = model,
                    androidVersion = androidVersion,
                    sdk = sdk,
                    abi = abi,
                    resolution = resolution,
                    battery = battery,
                ),
            )
        }.getOrElse { error ->
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
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_install_suggestion),
        )

        runCatching {
            val total = apkFile.length()
            apkFile.source().use { raw ->
                val source = if (onProgress == null) {
                    raw
                } else {
                    CountingSource(raw, total, onProgress)
                }
                kadb.install(source, total)
            }
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_apk_install_failed),
                suggestion = adbFailureSuggestion(
                    error = error,
                    fallback = appString(R.string.kadb_apk_install_fallback_suggestion),
                ),
                cause = error,
            )
        }
    }

    suspend fun uninstall(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_uninstall_suggestion),
        )

        runCatching {
            kadb.uninstall(packageName)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_app_uninstall_failed),
                suggestion = appString(R.string.kadb_app_uninstall_suggestion),
                cause = error,
            )
        }
    }

    suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val result = shell("am force-stop ${shellQuote(packageName)}")
        when (result) {
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(Unit)
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_app_stop_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_app_stop_suggestion) },
                    )
                }
            }
            is AdbOperationResult.Failure -> result
        }
    }

    suspend fun launchApp(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val result = shell("monkey -p ${shellQuote(packageName)} 1")
        when (result) {
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(Unit)
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_app_launch_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_app_launch_suggestion) },
                    )
                }
            }
            is AdbOperationResult.Failure -> result
        }
    }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val command = if (enabled) {
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
                        message = appString(if (enabled) R.string.kadb_app_enable_failed else R.string.kadb_app_disable_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_app_toggle_suggestion) },
                    )
                }
            }
        }
    }

    suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File> = withContext(Dispatchers.IO) {
        val pathResult = when (val result = shell("pm path ${shellQuote(packageName)}")) {
            is AdbOperationResult.Failure -> return@withContext result
            is AdbOperationResult.Success -> result.data
        }
        if (pathResult.exitCode != 0) {
            return@withContext AdbOperationResult.Failure(
                message = appString(R.string.kadb_apk_export_failed),
                suggestion = pathResult.errorOutput.ifBlank { appString(R.string.kadb_apk_export_check_exist_suggestion) },
            )
        }

        val apkPath = parseApkPaths(pathResult.output).singleOrNull() ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_apk_export_failed),
            suggestion = appString(R.string.kadb_apk_export_single_only_suggestion),
        )

        localFile.parentFile?.mkdirs()
        when (val pullResult = pull(apkPath, localFile)) {
            is AdbOperationResult.Success -> AdbOperationResult.Success(localFile)
            is AdbOperationResult.Failure -> AdbOperationResult.Failure(
                message = appString(R.string.kadb_apk_export_failed),
                suggestion = pullResult.suggestion,
                cause = pullResult.cause,
            )
        }
    }

    suspend fun listApps(): AdbOperationResult<List<AppInfo>> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_list_apps_suggestion),
        )

        runCatching {
            val disabledPackages = parsePackageNames(kadb.shell("pm list packages -d").output).toSet()
            val userPackages = parsePackageList(kadb.shell("pm list packages -3").output, isSystem = false)
            val systemPackages = parsePackageList(kadb.shell("pm list packages -s").output, isSystem = true)
            AdbOperationResult.Success(
                (userPackages + systemPackages)
                    .map { app -> app.copy(enabled = app.packageName !in disabledPackages) }
                    .sortedWith(compareBy<AppInfo> { !it.enabled }.thenBy { it.packageName }),
            )
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_list_apps_failed),
                suggestion = appString(R.string.kadb_list_apps_suggestion),
                cause = error,
            )
        }
    }

    suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>> = withContext(Dispatchers.IO) {
        val path = remotePath.ifBlank { "/" }
        val command = buildListFilesCommand(path)
        when (val result = shell(command)) {
            is AdbOperationResult.Failure -> result
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(RemoteFileListParser.parse(result.data.output, path))
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_list_dir_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_list_dir_suggestion) },
                    )
                }
            }
        }
    }

    suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        when (val result = shell("mkdir ${shellQuote(remotePath)}")) {
            is AdbOperationResult.Failure -> result
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(Unit)
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.kadb_mkdir_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_mkdir_suggestion) },
                    )
                }
            }
        }
    }

    suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val command = if (isDirectory) "rmdir ${shellQuote(remotePath)}" else "rm -f ${shellQuote(remotePath)}"
        when (val result = shell(command)) {
            is AdbOperationResult.Failure -> result
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(Unit)
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.error_delete_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_delete_suggestion) },
                    )
                }
            }
        }
    }

    suspend fun renameFile(remotePath: String, newName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val parent = remotePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "/")
        val target = if (parent == "/") "/$newName" else "$parent/$newName"
        when (val result = shell("mv ${shellQuote(remotePath)} ${shellQuote(target)}")) {
            is AdbOperationResult.Failure -> result
            is AdbOperationResult.Success -> {
                if (result.data.exitCode == 0) {
                    AdbOperationResult.Success(Unit)
                } else {
                    AdbOperationResult.Failure(
                        message = appString(R.string.error_rename_failed),
                        suggestion = result.data.errorOutput.ifBlank { appString(R.string.kadb_rename_suggestion) },
                    )
                }
            }
        }
    }

    suspend fun push(
        localFile: File,
        remotePath: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_push_suggestion),
        )

        runCatching {
            val total = localFile.length()
            localFile.source().use { raw ->
                val source = if (onProgress == null) {
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
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_push_failed),
                suggestion = adbFailureSuggestion(
                    error = error,
                    fallback = appString(R.string.kadb_push_fallback_suggestion),
                ),
                cause = error,
            )
        }
    }

    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = appString(R.string.kadb_not_connected),
            suggestion = appString(R.string.kadb_connect_before_pull_suggestion),
        )

        runCatching {
            kadb.pull(localFile, remotePath)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.kadb_pull_failed),
                suggestion = appString(R.string.kadb_pull_suggestion),
                cause = error,
            )
        }
    }

    suspend fun captureScreenshot(localFile: File): AdbOperationResult<File> = withContext(Dispatchers.IO) {
        val remotePath = "/sdcard/Download/adb-manager-screenshot-${System.currentTimeMillis()}.png"
        when (val captureResult = shell("screencap -p $remotePath")) {
            is AdbOperationResult.Failure -> captureResult
            is AdbOperationResult.Success -> {
                if (captureResult.data.exitCode != 0) {
                    return@withContext AdbOperationResult.Failure(
                        message = appString(R.string.kadb_screenshot_failed),
                        suggestion = captureResult.data.errorOutput.ifBlank { appString(R.string.kadb_screenshot_suggestion) },
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

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        sessionMutex.withLock { disconnectLocked() }
    }

    private fun disconnectLocked() {
        activeKadb?.close()
        activeKadb = null
        usbBridge?.close()
        usbBridge = null
        activeEndpoint = null
        sessionKind = AdbSessionKind.None
    }

    private fun disconnectAdbOnlyLocked() {
        activeKadb?.close()
        activeKadb = null
        usbBridge?.close()
        usbBridge = null
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
            AdbSessionKind.None, AdbSessionKind.Wifi -> false
        }
    }

    suspend fun markUsbFastbootSession(deviceName: String) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            disconnectAdbOnlyLocked()
            activeEndpoint = "fastboot:$deviceName"
            sessionKind = AdbSessionKind.UsbFastboot
        }
    }

    suspend fun clearUsbFastbootSession() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (sessionKind == AdbSessionKind.UsbFastboot) {
                activeEndpoint = null
                sessionKind = AdbSessionKind.None
            }
        }
    }

    fun currentEndpoint(): String? = activeEndpoint

    /** Android 11+（API 30）才支持官方音频转发。 */
    suspend fun currentDeviceSdkInt(): Int? = withContext(Dispatchers.IO) {
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

    /** 镜像专用连接：video / [audio] / control。 */
    suspend fun beginMirrorSession(audioEnabled: Boolean): AdbOperationResult<MirrorConnections> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (sessionKind == AdbSessionKind.UsbAdb) {
                return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.kadb_mirror_usb_unsupported),
                    suggestion = appString(R.string.kadb_mirror_usb_unsupported_suggestion),
                )
            }
            val endpoint = parseWifiEndpoint(activeEndpoint.orEmpty())
                ?: return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.kadb_not_connected),
                    suggestion = appString(R.string.kadb_connect_before_mirror_suggestion),
                )

            activeKadb?.close()
            activeKadb = null

            runCatching {
                coroutineScope {
                    val controlDeferred = async {
                        Kadb.create(endpoint.host, endpoint.port, lastConnectTimeoutMillis, 0)
                    }
                    val videoDeferred = async {
                        Kadb.create(endpoint.host, endpoint.port, lastConnectTimeoutMillis, 0)
                    }
                    val audioDeferred = if (audioEnabled) {
                        async { Kadb.create(endpoint.host, endpoint.port, lastConnectTimeoutMillis, 0) }
                    } else {
                        null
                    }
                    val control = controlDeferred.await()
                    val video = try {
                        videoDeferred.await()
                    } catch (error: Throwable) {
                        control.close()
                        audioDeferred?.cancel()
                        throw error
                    }
                    val audio = try {
                        audioDeferred?.await()
                    } catch (error: Throwable) {
                        video.close()
                        control.close()
                        throw error
                    }
                    AdbOperationResult.Success(
                        MirrorConnections(control = control, video = video, audio = audio),
                    )
                }
            }.getOrElse { error ->
                val restored = restoreActiveConnectionLocked()
                if (!restored) {
                    disconnectLocked()
                }
                AdbOperationResult.Failure(
                    message = appString(R.string.kadb_mirror_connect_failed),
                    suggestion = if (restored) {
                        appString(R.string.kadb_mirror_restored_suggestion)
                    } else {
                        appString(R.string.kadb_mirror_not_restored_suggestion)
                    },
                    cause = error,
                )
            }
        }
    }

    suspend fun endMirrorSession(connections: MirrorConnections?) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            runCatching { connections?.close() }
            if (!restoreActiveConnectionLocked()) {
                disconnectLocked()
            }
        }
    }

    /** @return true 表示会话已恢复；false 表示需由调用方视为已断开。 */
    private fun restoreActiveConnectionLocked(): Boolean {
        val endpoint = parseWifiEndpoint(activeEndpoint.orEmpty()) ?: return false
        return runCatching {
            activeKadb = Kadb.create(
                endpoint.host,
                endpoint.port,
                lastConnectTimeoutMillis,
                lastSocketTimeoutMillis,
            )
            true
        }.getOrDefault(false)
    }

    private fun parseWifiEndpoint(endpoint: String): Endpoint? {
        val host = endpoint.substringBeforeLast(':', missingDelimiterValue = "")
        val port = endpoint.substringAfterLast(':').toIntOrNull()
        if (host.isBlank() || port == null) return null
        return Endpoint(host, port)
    }

    private data class Endpoint(val host: String, val port: Int)

    private fun parseResolution(output: String): String {
        return output
            .lineSequence()
            .firstOrNull { it.contains("Physical size") || it.contains("Override size") }
            ?.substringAfter(":")
            ?.trim()
            ?.ifBlank { null }
            ?: appString(R.string.unknown)
    }

    private fun parseBatteryLevel(output: String): String {
        val level = output
            .lineSequence()
            .firstOrNull { it.trim().startsWith("level:") }
            ?.substringAfter(":")
            ?.trim()

        return if (level.isNullOrBlank()) appString(R.string.unknown) else "$level%"
    }

    private fun parsePackageList(output: String, isSystem: Boolean): List<AppInfo> {
        return parsePackageNames(output)
            .map { packageName ->
                AppInfo(
                    packageName = packageName,
                    label = AppDisplayEnricher.fallbackLabel(packageName),
                    isSystem = isSystem,
                )
            }
    }

    private fun parsePackageNames(output: String): List<String> {
        return output
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toList()
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

    private fun buildListFilesCommand(remotePath: String): String {
        val path = shellQuote(remotePath)
        return """
            dir=$path
            [ -d "${'$'}dir" ] || exit 2
            for f in "${'$'}dir"/* "${'$'}dir"/.*; do
              [ -e "${'$'}f" ] || continue
              name="${'$'}{f##*/}"
              [ "${'$'}name" = "." ] && continue
              [ "${'$'}name" = ".." ] && continue
              if [ -d "${'$'}f" ]; then
                printf 'D\t%s\t0\n' "${'$'}name"
              else
                size=${'$'}(stat -c %s "${'$'}f" 2>/dev/null || echo 0)
                printf 'F\t%s\t%s\n' "${'$'}name" "${'$'}size"
              fi
            done
        """.trimIndent()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private companion object {
        const val DefaultPushMode = 420
    }
}
