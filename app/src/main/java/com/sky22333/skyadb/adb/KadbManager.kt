package com.sky22333.skyadb.adb

import com.flyfishxu.kadb.Kadb
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.diagnostics.alsoLog
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.RemoteFileListParser
import com.sky22333.skyadb.model.ShellCommandResult
import dadb.Dadb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KadbManager {
    private var activeClient: AdbClient? = null
    private var activeEndpoint: String? = null

    suspend fun connect(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = 10_000,
        socketTimeoutMillis: Int = 30_000,
    ): AdbOperationResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            val kadb = Kadb.create(host, port, connectTimeoutMillis, socketTimeoutMillis)
            val probe = kadb.shell("echo kadb_ready")
            if (probe.exitCode != 0) {
                return@withContext AdbOperationResult.Failure(
                    message = "ADB 已响应但授权未完成",
                    suggestion = "已连接到 $host:$port，但测试命令执行失败。请在电视或目标设备上确认 sky adb 的调试授权弹窗后重试。",
                ).alsoLog(DiagnosticModule.WifiAdb, "连接设备", "$host:$port")
            }
            activeClient?.close()
            activeClient = KadbClient(kadb)
            activeEndpoint = "$host:$port"
            AdbOperationResult.Success(activeEndpoint.orEmpty())
        }.getOrElse { error ->
            if (AdbIdentityManager.isRsaCrtError(error)) {
                AdbIdentityManager.repairIdentity()
                return@withContext AdbOperationResult.Failure(
                    message = "ADB 身份密钥异常",
                    suggestion = "当前系统的 RSA 私钥兼容性异常，已尝试重建 ADB 身份。请重新连接，并在目标设备上重新允许调试授权。",
                    cause = error,
                )
            }
            AdbOperationResult.Failure(
                message = "无法连接 USB ADB 设备",
                suggestion = "请确认 USB 线支持数据传输、目标设备已开启 USB 调试，并在授权弹窗中允许此电脑调试。",
                cause = error,
            ).alsoLog(DiagnosticModule.UsbAdb, "连接设备", endpoint)
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
            if (AdbIdentityManager.isRsaCrtError(error)) {
                AdbIdentityManager.repairIdentity()
                return@withContext AdbOperationResult.Failure(
                    message = "ADB 身份密钥异常",
                    suggestion = "当前系统的 RSA 私钥兼容性异常，已尝试重建 ADB 身份。请重新配对，并在目标设备上重新允许调试授权。",
                    cause = error,
                )
            }
            AdbOperationResult.Failure(
                message = "无线调试配对失败",
                suggestion = "请确认配对码未过期，配对 IP 和配对端口来自目标设备的配对窗口。",
                cause = error,
            ).alsoLog(DiagnosticModule.WifiAdb, "无线配对", "$host:$port")
        }
    }

    suspend fun shell(command: String): AdbOperationResult<ShellCommandResult> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先在首页连接设备，再执行 Shell 命令。",
        ).alsoLog(DiagnosticModule.Shell, "执行 Shell", command.take(80))

        runCatching {
            val response = adbClient.shell(command)
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
            ).alsoLog(DiagnosticModule.Shell, "执行 Shell", command.take(80))
        }
    }

    suspend fun fetchDeviceInfo(): AdbOperationResult<DeviceInfo> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再查看设备详情。",
        ).alsoLog(DiagnosticModule.WifiAdb, "读取设备信息")

        runCatching {
            val brand = adbClient.shell("getprop ro.product.brand").output.trim().ifBlank { "未知" }
            val model = adbClient.shell("getprop ro.product.model").output.trim().ifBlank { "未知" }
            val androidVersion = adbClient.shell("getprop ro.build.version.release").output.trim().ifBlank { "未知" }
            val sdk = adbClient.shell("getprop ro.build.version.sdk").output.trim().ifBlank { "未知" }
            val abi = adbClient.shell("getprop ro.product.cpu.abi").output.trim().ifBlank { "未知" }
            val resolution = parseResolution(adbClient.shell("wm size").output)
            val battery = parseBatteryLevel(adbClient.shell("dumpsys battery").output)

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
            ).alsoLog(DiagnosticModule.WifiAdb, "读取设备信息", activeEndpoint)
        }
    }

    suspend fun install(apkFile: File): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再安装 APK。",
        ).alsoLog(DiagnosticModule.Install, "安装 APK", apkFile.name)

        runCatching {
            adbClient.install(apkFile)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "APK 安装失败",
                suggestion = "请确认 APK 文件完整、设备存储空间充足，并允许安装该应用。",
                cause = error,
            ).alsoLog(DiagnosticModule.Install, "安装 APK", apkFile.name)
        }
    }

    suspend fun uninstall(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再卸载应用。",
        ).alsoLog(DiagnosticModule.Apps, "卸载应用", packageName)

        runCatching {
            adbClient.uninstall(packageName)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "应用卸载失败",
                suggestion = "请确认包名正确，且该应用允许被当前用户卸载。",
                cause = error,
            ).alsoLog(DiagnosticModule.Apps, "卸载应用", packageName)
        }
    }

    suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val result = shell("am force-stop $packageName")
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
        val result = shell("monkey -p $packageName 1")
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
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再查看应用列表。",
        )

        runCatching {
            val disabledPackages = parsePackageNames(adbClient.shell("pm list packages -d").output).toSet()
            val userPackages = parsePackageList(adbClient.shell("pm list packages -3").output, isSystem = false)
            val systemPackages = parsePackageList(adbClient.shell("pm list packages -s").output, isSystem = true)
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

    suspend fun push(localFile: File, remotePath: String): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再推送文件。",
        )

        runCatching {
            adbClient.push(localFile, remotePath)
            AdbOperationResult.Success(Unit)
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "文件推送失败",
                suggestion = "请确认本地文件存在，目标路径可写，并保持设备连接。",
                cause = error,
            )
        }
    }

    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> = withContext(Dispatchers.IO) {
        val adbClient = activeClient ?: return@withContext AdbOperationResult.Failure(
            message = "未连接设备",
            suggestion = "请先连接设备，再拉取文件。",
        )

        runCatching {
            adbClient.pull(localFile, remotePath)
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

    fun disconnect() {
        activeClient?.close()
        activeClient = null
        activeEndpoint = null
    }

    fun currentEndpoint(): String? = activeEndpoint

    suspend fun checkRuntimeReady(): Boolean = true

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

    private fun classifyConnectFailure(error: Throwable, host: String, port: Int): AdbOperationResult.Failure {
        val text = buildString {
            var current: Throwable? = error
            while (current != null) {
                append(current::class.java.name)
                append(' ')
                append(current.message.orEmpty())
                append(' ')
                current = current.cause
            }
        }.lowercase()

        return (when {
            listOf("auth", "authenticate", "unauthorized", "public key", "adbkey", "signature").any(text::contains) ->
                AdbOperationResult.Failure(
                    message = "ADB 授权未完成",
                    suggestion = "已找到 $host:$port，但目标设备没有信任 sky adb 的调试密钥。请在电视或目标设备上确认“允许 USB 调试/网络调试”的授权弹窗，必要时撤销调试授权后重新连接。",
                    cause = error,
                )

            listOf("timed out", "timeout", "time out").any(text::contains) ->
                AdbOperationResult.Failure(
                    message = "连接超时",
                    suggestion = "已尝试连接 $host:$port，但目标设备未及时响应。请确认 ADB TCP 端口仍开启，电视没有休眠，并且手机和电视在同一网络。",
                    cause = error,
                )

            listOf("connection refused", "refused", "econnrefused").any(text::contains) ->
                AdbOperationResult.Failure(
                    message = "ADB 端口拒绝连接",
                    suggestion = "$host:$port 可达但没有可用的 adbd 在监听。请在目标设备上重新开启网络 ADB，或确认端口不是扫描误判。",
                    cause = error,
                )

            listOf("no route", "unreachable", "enetunreach", "ehostunreach", "network is unreachable").any(text::contains) ->
                AdbOperationResult.Failure(
                    message = "网络不可达",
                    suggestion = "手机无法到达 $host。请确认手机和目标设备在同一局域网，且路由器没有开启 AP 隔离或访客网络隔离。",
                    cause = error,
                )

            listOf("reset", "broken pipe", "closed", "eof", "unexpected end").any(text::contains) ->
                AdbOperationResult.Failure(
                    message = "ADB 连接被目标设备断开",
                    suggestion = "目标设备主动关闭了连接。常见原因是未确认 ADB 授权、adbd 重启、电视系统限制网络调试；请在目标设备上确认授权后重试。",
                    cause = error,
                )

            else ->
                AdbOperationResult.Failure(
                    message = "无法连接到 ADB 设备",
                    suggestion = "已尝试连接 $host:$port。请确认网络 ADB 已开启、端口正确，并在目标设备上允许 sky adb 的调试授权。",
                    cause = error,
                )
        }).alsoLog(DiagnosticModule.WifiAdb, "连接设备", "$host:$port")
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}

private interface AdbClient : AutoCloseable {
    fun shell(command: String): ShellCommandResult
    fun install(apkFile: File)
    fun uninstall(packageName: String)
    fun push(localFile: File, remotePath: String)
    fun pull(localFile: File, remotePath: String)
}

private class KadbClient(
    private val kadb: Kadb,
) : AdbClient {
    override fun shell(command: String): ShellCommandResult {
        val response = kadb.shell(command)
        return ShellCommandResult(
            command = command,
            output = response.output,
            errorOutput = response.errorOutput,
            exitCode = response.exitCode,
        )
    }

    override fun install(apkFile: File) = kadb.install(apkFile)
    override fun uninstall(packageName: String) = kadb.uninstall(packageName)
    override fun push(localFile: File, remotePath: String) = kadb.push(localFile, remotePath)
    override fun pull(localFile: File, remotePath: String) = kadb.pull(localFile, remotePath)
    override fun close() = kadb.close()
}

private class DadbClient(
    private val dadb: Dadb,
) : AdbClient {
    override fun shell(command: String): ShellCommandResult {
        val response = dadb.shell(command)
        return ShellCommandResult(
            command = command,
            output = response.output,
            errorOutput = response.errorOutput,
            exitCode = response.exitCode,
        )
    }

    override fun install(apkFile: File) = dadb.install(apkFile)
    override fun uninstall(packageName: String) = dadb.uninstall(packageName)
    override fun push(localFile: File, remotePath: String) = dadb.push(localFile, remotePath)
    override fun pull(localFile: File, remotePath: String) = dadb.pull(localFile, remotePath)
    override fun close() = dadb.close()
}
