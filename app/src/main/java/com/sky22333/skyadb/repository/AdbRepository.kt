package com.sky22333.skyadb.repository

import com.sky22333.skyadb.R
import com.sky22333.skyadb.adb.AdbSessionKind
import com.sky22333.skyadb.adb.FastbootOtgManager
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.data.RecentDeviceStore
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.diagnostics.alsoLog
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.DeviceType
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.ShellCommandResult
import com.sky22333.skyadb.usb.UsbOtgHost
import com.sky22333.skyadb.usb.UsbOtgMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface AdbRepository {
    val recentDevices: Flow<List<AdbDevice>>
    val selectedDeviceInfo: Flow<DeviceInfo>
    suspend fun connect(host: String, port: Int): AdbOperationResult<String>
    suspend fun connectUsbOtg(deviceName: String): AdbOperationResult<String>
    suspend fun runFastbootCommand(command: String): AdbOperationResult<String>
    suspend fun pair(host: String, port: Int, pairingCode: String): AdbOperationResult<Unit>
    suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo>
    suspend fun runShell(command: String): AdbOperationResult<ShellCommandResult>
    suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit>
    suspend fun listApps(): AdbOperationResult<List<AppInfo>>
    suspend fun launchApp(packageName: String): AdbOperationResult<Unit>
    suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit>
    suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit>
    suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File>
    suspend fun uninstall(packageName: String): AdbOperationResult<Unit>
    suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>>
    suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit>
    suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit>
    suspend fun renameFile(remotePath: String, newName: String): AdbOperationResult<Unit>
    suspend fun push(
        localFile: File,
        remotePath: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit>
    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit>
    suspend fun captureScreenshot(localFile: File): AdbOperationResult<File>
    suspend fun disconnect()
    fun sessionKind(): AdbSessionKind
    fun isActiveUsbDevice(deviceName: String): Boolean
}

class DefaultAdbRepository(
    private val kadbManager: KadbManager,
    private val fastbootOtgManager: FastbootOtgManager,
    private val usbOtgHost: UsbOtgHost,
    private val recentDeviceStore: RecentDeviceStore,
    private val settingsStore: AppSettingsStore,
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val recentDeviceState = MutableStateFlow(emptyList<AdbDevice>())
    private val deviceInfoState = MutableStateFlow(DeviceInfo())

    override val recentDevices: Flow<List<AdbDevice>> = recentDeviceState.asStateFlow()
    override val selectedDeviceInfo: Flow<DeviceInfo> = deviceInfoState.asStateFlow()

    init {
        scope.launch {
            recentDeviceStore.devices.collect { devices ->
                val connectedIds = recentDeviceState.value
                    .filter { it.connectionState == ConnectionState.Connected }
                    .map { it.id }
                    .toSet()
                recentDeviceState.value = devices.map { device ->
                    if (device.id in connectedIds) {
                        device.copy(
                            connectionState = ConnectionState.Connected,
                            lastConnectedText = appString(R.string.repo_status_just_connected),
                        )
                    } else {
                        device
                    }
                }
            }
        }
    }

    override suspend fun connect(host: String, port: Int): AdbOperationResult<String> {
        disconnect()
        val settings = settingsStore.settings.first()
        val result = kadbManager.connect(
            host = host,
            port = port,
            connectTimeoutMillis = settings.connectionTimeoutSeconds * 1_000,
            socketTimeoutMillis = settings.commandTimeoutSeconds * 1_000,
        )
        if (result is AdbOperationResult.Success) {
            persistConnectedDevice(
                AdbDevice(
                    id = "$host:$port",
                    name = appString(R.string.repo_device_name_android),
                    host = host,
                    port = port,
                    type = DeviceType.Unknown,
                    connectionState = ConnectionState.Connected,
                    lastConnectedText = appString(R.string.repo_status_just_connected),
                    linkKind = AdbLinkKind.Wifi,
                ),
            )
        }
        return result.logFailure(DiagnosticModule.WifiAdb, "连接设备", "$host:$port")
    }

    override suspend fun connectUsbOtg(deviceName: String): AdbOperationResult<String> {
        val device = usbOtgHost.getDevice(deviceName)
            ?: return AdbOperationResult.Failure(
                message = appString(R.string.repo_usb_device_disconnected),
                suggestion = appString(R.string.repo_usb_device_disconnected_suggestion),
            )
        if (!usbOtgHost.hasPermission(device)) {
            return AdbOperationResult.Failure(
                message = appString(R.string.repo_usb_permission_missing),
                suggestion = appString(R.string.repo_usb_permission_missing_suggestion),
            )
        }

        disconnect()
        val settings = settingsStore.settings.first()
        val mode = usbOtgHost.attachments.value.firstOrNull { it.deviceName == deviceName }?.mode
            ?: return AdbOperationResult.Failure(
                message = appString(R.string.repo_usb_mode_unknown),
                suggestion = appString(R.string.repo_usb_mode_unknown_suggestion),
            )

        val result = when (mode) {
            UsbOtgMode.Adb -> kadbManager.connectUsb(
                usbManager = usbOtgHost.usbManager(),
                device = device,
                connectTimeoutMillis = settings.connectionTimeoutSeconds * 1_000,
                socketTimeoutMillis = settings.commandTimeoutSeconds * 1_000,
            )
            UsbOtgMode.Fastboot -> fastbootOtgManager.connect(
                usbManager = usbOtgHost.usbManager(),
                device = device,
            )
        }

        if (result is AdbOperationResult.Success) {
            when (mode) {
                UsbOtgMode.Adb -> {
                    persistConnectedDevice(
                        AdbDevice(
                            id = "usb-otg:$deviceName",
                            name = appString(R.string.repo_device_name_usb),
                            host = "usb-otg",
                            port = 0,
                            type = DeviceType.Unknown,
                            connectionState = ConnectionState.Connected,
                            lastConnectedText = appString(R.string.repo_status_just_connected),
                            linkKind = AdbLinkKind.UsbOtg,
                        ),
                    )
                }
                UsbOtgMode.Fastboot -> {
                    kadbManager.markUsbFastbootSession(deviceName)
                    persistConnectedDevice(
                        AdbDevice(
                            id = "fastboot:$deviceName",
                            name = appString(R.string.repo_device_name_fastboot),
                            host = "fastboot",
                            port = 0,
                            type = DeviceType.Unknown,
                            connectionState = ConnectionState.Connected,
                            lastConnectedText = appString(R.string.repo_status_fastboot_connected),
                            linkKind = AdbLinkKind.UsbOtg,
                        ),
                    )
                }
            }
        }

        return result.logFailure(DiagnosticModule.UsbOtg, "USB OTG 连接", deviceName)
    }

    override suspend fun runFastbootCommand(command: String): AdbOperationResult<String> {
        if (kadbManager.sessionKind() != AdbSessionKind.UsbFastboot) {
            return AdbOperationResult.Failure(
                message = appString(R.string.repo_not_fastboot),
                suggestion = appString(R.string.fastboot_connect_via_usb_otg_suggestion),
            )
        }
        return fastbootOtgManager.sendCommand(command)
            .logFailure(DiagnosticModule.Fastboot, "Fastboot 命令", command.take(80))
    }

    override suspend fun pair(host: String, port: Int, pairingCode: String): AdbOperationResult<Unit> {
        return kadbManager.pair(host, port, pairingCode)
            .logFailure(DiagnosticModule.Pairing, "无线配对", "$host:$port")
    }

    override suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo> {
        if (kadbManager.sessionKind() == AdbSessionKind.UsbFastboot) {
            return AdbOperationResult.Failure(
                message = appString(R.string.repo_fastboot_no_device_info),
                suggestion = appString(R.string.repo_fastboot_no_device_info_suggestion),
            )
        }
        val result = kadbManager.fetchDeviceInfo()
        if (result is AdbOperationResult.Success) {
            deviceInfoState.value = result.data
        } else {
            deviceInfoState.value = DeviceInfo()
            markConnectedDevices(ConnectionState.Offline)
        }
        return result.logFailure(DiagnosticModule.WifiAdb, "刷新设备信息")
    }

    override suspend fun runShell(command: String): AdbOperationResult<ShellCommandResult> {
        if (kadbManager.sessionKind() == AdbSessionKind.UsbFastboot) {
            return AdbOperationResult.Failure(
                message = appString(R.string.repo_fastboot_no_shell),
                suggestion = appString(R.string.repo_fastboot_no_shell_suggestion),
            )
        }
        return kadbManager.shell(command)
            .logFailure(DiagnosticModule.Shell, "执行 Shell", command.take(80))
    }

    override suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
    ): AdbOperationResult<Unit> {
        return kadbManager.install(apkFile, onProgress)
            .logFailure(DiagnosticModule.Install, "安装 APK", apkFile.name)
    }

    override suspend fun listApps(): AdbOperationResult<List<AppInfo>> {
        return kadbManager.listApps()
            .logFailure(DiagnosticModule.Apps, "读取应用列表")
    }

    override suspend fun launchApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.launchApp(packageName)
            .logFailure(DiagnosticModule.Apps, "启动应用", packageName)
    }

    override suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.forceStopApp(packageName)
            .logFailure(DiagnosticModule.Apps, "停止应用", packageName)
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit> {
        return kadbManager.setAppEnabled(packageName, enabled)
            .logFailure(DiagnosticModule.Apps, if (enabled) "启用应用" else "冻结应用", packageName)
    }

    override suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File> {
        return kadbManager.exportAppApk(packageName, localFile)
            .logFailure(DiagnosticModule.Apps, "导出 APK", packageName)
    }

    override suspend fun uninstall(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.uninstall(packageName)
            .logFailure(DiagnosticModule.Apps, "卸载应用", packageName)
    }

    override suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>> {
        return kadbManager.listFiles(remotePath)
            .logFailure(DiagnosticModule.Files, "读取目录", remotePath)
    }

    override suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit> {
        return kadbManager.makeDirectory(remotePath)
            .logFailure(DiagnosticModule.Files, "新建文件夹", remotePath)
    }

    override suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit> {
        return kadbManager.deleteFile(remotePath, isDirectory)
            .logFailure(DiagnosticModule.Files, if (isDirectory) "删除目录" else "删除文件", remotePath)
    }

    override suspend fun renameFile(remotePath: String, newName: String): AdbOperationResult<Unit> {
        return kadbManager.renameFile(remotePath, newName)
            .logFailure(DiagnosticModule.Files, "重命名", remotePath)
    }

    override suspend fun push(
        localFile: File,
        remotePath: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
    ): AdbOperationResult<Unit> {
        return kadbManager.push(localFile, remotePath, onProgress)
            .logFailure(DiagnosticModule.Files, "推送文件", remotePath)
    }

    override suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> {
        return kadbManager.pull(remotePath, localFile)
            .logFailure(DiagnosticModule.Files, "拉取文件", remotePath)
    }

    override suspend fun captureScreenshot(localFile: File): AdbOperationResult<File> {
        return kadbManager.captureScreenshot(localFile)
            .logFailure(DiagnosticModule.Screenshot, "截图")
    }

    override suspend fun disconnect() {
        kadbManager.disconnect()
        fastbootOtgManager.disconnect()
        kadbManager.clearUsbFastbootSession()
        val next = recentDeviceState.value.map {
            it.copy(connectionState = ConnectionState.Disconnected)
        }
        recentDeviceState.value = next
        deviceInfoState.value = DeviceInfo()
        scope.launch {
            recentDeviceStore.saveDevices(next)
        }
    }

    override fun sessionKind(): AdbSessionKind = kadbManager.sessionKind()

    override fun isActiveUsbDevice(deviceName: String): Boolean {
        return kadbManager.isActiveUsbDevice(deviceName) ||
            fastbootOtgManager.currentDeviceName() == deviceName
    }
    private suspend fun persistConnectedDevice(connectedDevice: AdbDevice) {
        recentDeviceState.value = upsertRecentDevice(connectedDevice)
        recentDeviceStore.upsert(connectedDevice.copy(connectionState = ConnectionState.Disconnected))

        val unknown = appString(R.string.unknown)
        if (connectedDevice.linkKind == AdbLinkKind.Wifi) {
            val infoResult = refreshDeviceInfo()
            if (infoResult is AdbOperationResult.Success) {
                val deviceName = listOf(infoResult.data.brand, infoResult.data.model)
                    .filter { it != unknown }
                    .joinToString(" ")
                    .ifBlank { appString(R.string.repo_device_name_android) }
                val namedDevice = connectedDevice.copy(name = deviceName)
                recentDeviceState.value = upsertRecentDevice(namedDevice)
                recentDeviceStore.upsert(namedDevice.copy(connectionState = ConnectionState.Disconnected))
            }
        } else if (connectedDevice.host == "usb-otg") {
            val infoResult = refreshDeviceInfo()
            if (infoResult is AdbOperationResult.Success) {
                val deviceName = listOf(infoResult.data.brand, infoResult.data.model)
                    .filter { it != unknown }
                    .joinToString(" ")
                    .ifBlank { appString(R.string.repo_device_name_usb) }
                val namedDevice = connectedDevice.copy(name = deviceName)
                recentDeviceState.value = upsertRecentDevice(namedDevice)
                recentDeviceStore.upsert(namedDevice.copy(connectionState = ConnectionState.Disconnected))
            }
        }
    }

    private fun upsertRecentDevice(device: AdbDevice): List<AdbDevice> {
        val others = recentDeviceState.value.filterNot { it.id == device.id }
        return (listOf(device) + others).take(MaxRecentDevices)
    }

    private fun markConnectedDevices(connectionState: ConnectionState) {
        val next = recentDeviceState.value.map { device ->
            if (device.connectionState == ConnectionState.Connected) {
                device.copy(connectionState = connectionState)
            } else {
                device
            }
        }
        recentDeviceState.value = next
    }

    private companion object {
        const val MaxRecentDevices = 8
    }
}

private fun <T> AdbOperationResult<T>.logFailure(
    module: DiagnosticModule,
    operation: String,
    target: String? = null,
): AdbOperationResult<T> {
    if (this is AdbOperationResult.Failure) {
        alsoLog(module, operation, target)
    }
    return this
}
