package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.flyfishxu.kadb.Kadb
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
                    message = "连接失败",
                    suggestion = "设备已响应但命令执行失败，请确认目标设备已允许无线调试授权。",
                )
            }
            activeKadb = kadb
            activeEndpoint = endpoint
            this.sessionKind = sessionKind
            AdbOperationResult.Success(endpoint)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "无法连接到设备",
                suggestion = "请确认设备与本机处于同一网络、ADB 端口正确，并已允许调试授权。",
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
                    message = "未找到 ADB 接口",
                    suggestion = "请确认目标设备已开启 USB 调试，且当前不是 Fastboot 模式。",
                )
            val connection = usbManager.openDevice(device)
                ?: return@withLock AdbOperationResult.Failure(
                    message = "无法打开 USB 设备",
                    suggestion = "请重新插拔 OTG 线，并在系统弹窗中允许 USB 访问。",
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
                    message = "USB OTG 连接失败",
                    suggestion = "请确认目标设备已允许 USB 调试授权，并重新插拔 OTG 线。",
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
                message = "无线调试配对失败",
                suggestion = "请确认配对码未过期，配对 IP 和配对端口来自目标设备的配对窗口。",
                cause = error,
            )
        }
    }

    suspend fun shell(command: String): AdbOperationResult<ShellCommandResult> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先在首页连接设备，再执行 Shell 命令。",
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
                message = "命令执行失败",
                suggestion = "请检查命令是否正确，或确认设备仍保持连接。",
                cause = error,
            )
        }
    }

    suspend fun fetchDeviceInfo(): AdbOperationResult<DeviceInfo> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再查看设备详情。",
        )

        runCatching {
            val brand = kadb.shell("getprop ro.product.brand").output.trim().ifBlank { "未知" }
            val model = kadb.shell("getprop ro.product.model").output.trim().ifBlank { "未知" }
            val androidVersion = kadb.shell("getprop ro.build.version.release").output.trim().ifBlank { "未知" }
            val sdk = kadb.shell("getprop ro.build.version.sdk").output.trim().ifBlank { "未知" }
            val abi = kadb.shell("getprop ro.product.cpu.abi").output.trim().ifBlank { "未知" }
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
                message = "读取设备信息失败",
                suggestion = "请确认设备仍在线，并允许执行 ADB 命令。",
                cause = error,
            )
        }
    }

    suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再安装 APK。",
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
                message = "APK 安装失败",
                suggestion = adbFailureSuggestion(
                    error = error,
                    fallback = "请确认 APK 文件完整、设备存储空间充足，并允许安装该应用。",
                ),
                cause = error,
            )
        }
    }

    suspend fun uninstall(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再卸载应用。",
        )

        runCatching {
            kadb.uninstall(packageName)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "应用卸载失败",
                suggestion = "请确认包名正确，且该应用允许被当前用户卸载。",
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
                        message = "停止应用失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认包名正确，且设备允许停止该应用。" },
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
                        message = "启动应用失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认包名正确，且目标应用存在可启动入口。" },
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
                        message = if (enabled) "启用应用失败" else "冻结应用失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认包名正确，且设备允许修改该应用状态。" },
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
                message = "导出 APK 失败",
                suggestion = pathResult.errorOutput.ifBlank { "请确认应用存在，并保持设备连接。" },
            )
        }

        val apkPath = parseApkPaths(pathResult.output).singleOrNull() ?: return@withContext AdbOperationResult.Failure(
            message = "导出 APK 失败",
            suggestion = "仅支持导出单 APK 应用，请确认应用存在且不是拆分安装包。",
        )

        localFile.parentFile?.mkdirs()
        when (val pullResult = pull(apkPath, localFile)) {
            is AdbOperationResult.Success -> AdbOperationResult.Success(localFile)
            is AdbOperationResult.Failure -> AdbOperationResult.Failure(
                message = "导出 APK 失败",
                suggestion = pullResult.suggestion,
                cause = pullResult.cause,
            )
        }
    }

    suspend fun listApps(): AdbOperationResult<List<AppInfo>> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再查看应用列表。",
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
                message = "读取应用列表失败",
                suggestion = "请确认设备仍在线，并允许执行 pm list packages 命令。",
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
                        message = "读取目录失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认设备路径存在，并且当前用户有权限读取。" },
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
                        message = "新建文件夹失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认目标路径可写，且文件夹名称未被占用。" },
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
                        message = "删除失败",
                        suggestion = result.data.errorOutput.ifBlank { "请确认路径存在，目录为空，并且当前用户有权限删除。" },
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
            message = "未连接设备",
            suggestion = "请先连接设备，再推送文件。",
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
                message = "文件推送失败",
                suggestion = adbFailureSuggestion(
                    error = error,
                    fallback = "请确认本地文件存在，目标路径可写，并保持设备连接。",
                ),
                cause = error,
            )
        }
    }

    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val kadb = activeKadb ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再拉取文件。",
        )

        runCatching {
            kadb.pull(localFile, remotePath)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "文件拉取失败",
                suggestion = "请确认设备路径存在，本机保存位置可写，并保持设备连接。",
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
                        message = "截图失败",
                        suggestion = captureResult.data.errorOutput.ifBlank { "请确认设备允许执行 screencap 命令。" },
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

    /** 镜像专用 video / control 双连接。 */
    suspend fun beginMirrorSession(): AdbOperationResult<MirrorConnections> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (sessionKind == AdbSessionKind.UsbAdb) {
                return@withLock AdbOperationResult.Failure(
                    message = "USB OTG 暂不支持屏幕镜像",
                    suggestion = "屏幕镜像需要两条并行 ADB 连接，当前 USB OTG 仅支持单通道。请改用 Wi-Fi ADB 连接后再试。",
                )
            }
            val endpoint = parseWifiEndpoint(activeEndpoint.orEmpty())
                ?: return@withLock AdbOperationResult.Failure(
                    message = "未连接设备",
                    suggestion = "请先连接设备，再启动屏幕镜像。",
                )

            activeKadb?.close()
            activeKadb = null

            runCatching {
                val control = Kadb.create(endpoint.host, endpoint.port, lastConnectTimeoutMillis, 0)
                val video = try {
                    Kadb.create(endpoint.host, endpoint.port, lastConnectTimeoutMillis, 0)
                } catch (error: Throwable) {
                    control.close()
                    throw error
                }
                AdbOperationResult.Success(MirrorConnections(control = control, video = video))
            }.getOrElse { error ->
                val restored = restoreActiveConnectionLocked()
                if (!restored) {
                    disconnectLocked()
                }
                AdbOperationResult.Failure(
                    message = "屏幕镜像连接失败",
                    suggestion = if (restored) {
                        "请确认设备仍在线，并重新尝试进入屏幕镜像。"
                    } else {
                        "镜像连接失败，且无法恢复原会话，请重新连接设备。"
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
            ?: "未知"
    }

    private fun parseBatteryLevel(output: String): String {
        val level = output
            .lineSequence()
            .firstOrNull { it.trim().startsWith("level:") }
            ?.substringAfter(":")
            ?.trim()

        return if (level.isNullOrBlank()) "未知" else "$level%"
    }

    private fun parsePackageList(output: String, isSystem: Boolean): List<AppInfo> {
        return parsePackageNames(output)
            .map { packageName ->
                AppInfo(
                    packageName = packageName,
                    label = packageName.substringAfterLast('.'),
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
        /** 0644，与 Kadb 默认推送权限一致。 */
        const val DefaultPushMode = 420
    }
}
