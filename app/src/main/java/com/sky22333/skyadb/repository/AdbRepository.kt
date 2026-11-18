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
import com.sky22333.skyadb.model.AdbEndpoint
import com.sky22333.skyadb.model.AdbLinkKind
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.model.RebootMode
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.ShellCommandResult
import com.sky22333.skyadb.usb.UsbOtgHost
import com.sky22333.skyadb.usb.UsbOtgMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AdbRepository {
    val recentDevices: Flow<List<AdbDevice>>
    val selectedDeviceInfo: Flow<DeviceInfo>
    val activeDeviceId: Flow<String?>

    suspend fun connect(host: String, port: Int): AdbOperationResult<String>

    suspend fun connectUsbOtg(deviceName: String): AdbOperationResult<String>

    suspend fun runFastbootCommand(command: String): AdbOperationResult<String>

    suspend fun reboot(mode: RebootMode, deviceId: String): AdbOperationResult<String>

    suspend fun fastbootSummary(): AdbOperationResult<FastbootDeviceSummary>

    suspend fun pair(host: String, port: Int, pairingCode: String): AdbOperationResult<Unit>

    suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo>

    suspend fun runShell(
        command: String,
        timeoutMillis: Long? = 30_000,
        expectedSessionToken: Any? = null,
    ): AdbOperationResult<ShellCommandResult>

    suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    ): AdbOperationResult<Unit>

    suspend fun listPackages(): AdbOperationResult<List<AppInfo>>

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

    suspend fun selectDevice(deviceId: String): AdbOperationResult<Unit>

    suspend fun disconnectDevice(deviceId: String): AdbOperationResult<Unit>

    suspend fun renameDevice(deviceId: String, name: String): AdbOperationResult<Unit>

    suspend fun removeDevice(deviceId: String): AdbOperationResult<Unit>

    fun sessionKind(): AdbSessionKind

    fun sessionToken(): Any?

    fun isActiveUsbDevice(deviceName: String): Boolean

    fun deviceDisplayName(deviceId: String): String?
}

class DefaultAdbRepository(
    private val kadbManager: KadbManager,
    private val fastbootOtgManager: FastbootOtgManager,
    private val usbOtgHost: UsbOtgHost,
    private val recentDeviceStore: RecentDeviceStore,
    private val settingsStore: AppSettingsStore,
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectionMutex = Mutex()
    private val recentDeviceState = MutableStateFlow(emptyList<AdbDevice>())
    private val deviceInfoState = MutableStateFlow(DeviceInfo())
    private val activeDeviceState = MutableStateFlow<String?>(null)
    private val deletedDeviceIds = mutableSetOf<String>()

    override val recentDevices: Flow<List<AdbDevice>> = recentDeviceState.asStateFlow()
    override val selectedDeviceInfo: Flow<DeviceInfo> = deviceInfoState.asStateFlow()
    override val activeDeviceId: Flow<String?> = activeDeviceState.asStateFlow()

    init {
        scope.launch {
            recentDeviceStore.devices.collect { devices ->
                connectionMutex.withLock {
                    val liveConnected =
                        recentDeviceState.value
                            .filter {
                                it.connectionState == ConnectionState.Connected &&
                                    it.id !in deletedDeviceIds
                            }
                    val liveIds = liveConnected.mapTo(mutableSetOf()) { it.id }
                    recentDeviceState.value =
                        (liveConnected + devices.filterNot { it.id in deletedDeviceIds || it.id in liveIds })
                            .distinctBy { it.id }
                }
            }
        }
        activeDeviceState.value = kadbManager.currentEndpoint()
    }

    override suspend fun connect(host: String, port: Int): AdbOperationResult<String> =
        connectionMutex.withLock {
            val socketHost = AdbEndpoint.hostForSocket(host)
            val endpoint = AdbEndpoint.format(socketHost, port)
            val settings = settingsStore.settings.first()
            val result =
                kadbManager.connect(
                    host = socketHost,
                    port = port,
                    connectTimeoutMillis = settings.connectionTimeoutSeconds * 1_000,
                    socketTimeoutMillis = settings.commandTimeoutSeconds * 1_000,
                )
            if (result is AdbOperationResult.Success) {
                val existingName = recentDeviceState.value.firstOrNull { it.id == endpoint }?.name
                persistConnectedDevice(
                    AdbDevice(
                        id = endpoint,
                        name = existingName ?: appString(R.string.repo_device_name_android),
                        host = socketHost,
                        port = port,

                        connectionState = ConnectionState.Connected,

                        linkKind = AdbLinkKind.Wifi,
                    ),
                    preserveName = existingName != null,
                )
            }
            syncConnectionState()
            return@withLock result.logFailure(DiagnosticModule.WifiAdb, "连接设备", endpoint)
        }

    override suspend fun connectUsbOtg(deviceName: String): AdbOperationResult<String> =
        connectionMutex.withLock {
            val device =
                usbOtgHost.getDevice(deviceName)
                    ?: return@withLock AdbOperationResult.Failure(
                        message = appString(R.string.repo_usb_device_disconnected),
                        suggestion = appString(R.string.repo_usb_device_disconnected_suggestion),
                    )
            if (!usbOtgHost.hasPermission(device)) {
                return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.repo_usb_permission_missing),
                    suggestion = appString(R.string.repo_usb_permission_missing_suggestion),
                )
            }

            val settings = settingsStore.settings.first()
            val mode =
                usbOtgHost.attachments.value.firstOrNull { it.deviceName == deviceName }?.mode
                    ?: return@withLock AdbOperationResult.Failure(
                        message = appString(R.string.repo_usb_mode_unknown),
                        suggestion = appString(R.string.repo_usb_mode_unknown_suggestion),
                    )

            val result =
                when (mode) {
                    UsbOtgMode.Adb -> {
                        if (fastbootOtgManager.currentDeviceName() != null) {
                            fastbootOtgManager.disconnect()
                        }
                        kadbManager.disconnectFastbootSessions()
                        kadbManager.connectUsb(
                            usbManager = usbOtgHost.usbManager(),
                            device = device,
                            connectTimeoutMillis = settings.connectionTimeoutSeconds * 1_000,
                            socketTimeoutMillis = settings.commandTimeoutSeconds * 1_000,
                        )
                    }
                    UsbOtgMode.Fastboot ->
                        fastbootOtgManager.connect(
                            usbManager = usbOtgHost.usbManager(),
                            device = device,
                        )
                }

            if (result is AdbOperationResult.Success) {
                when (mode) {
                    UsbOtgMode.Adb -> {
                        val endpoint = "usb-otg:$deviceName"
                        val existingName = recentDeviceState.value.firstOrNull { it.id == endpoint }?.name
                        persistConnectedDevice(
                            AdbDevice(
                                id = endpoint,
                                name = existingName ?: appString(R.string.repo_device_name_usb),
                                host = "usb-otg",
                                port = 0,

                                connectionState = ConnectionState.Connected,

                                linkKind = AdbLinkKind.UsbOtg,
                            ),
                            preserveName = existingName != null,
                        )
                    }
                    UsbOtgMode.Fastboot -> {
                        kadbManager.markUsbFastbootSession(deviceName)
                        val endpoint = "fastboot:$deviceName"
                        val existingName = recentDeviceState.value.firstOrNull { it.id == endpoint }?.name
                        persistConnectedDevice(
                            AdbDevice(
                                id = endpoint,
                                name = existingName ?: appString(R.string.repo_device_name_fastboot),
                                host = "fastboot",
                                port = 0,

                                connectionState = ConnectionState.Connected,

                                linkKind = AdbLinkKind.UsbOtg,
                            ),
                            preserveName = existingName != null,
                        )
                    }
                }
            }
            syncConnectionState()

            return@withLock result.logFailure(DiagnosticModule.UsbOtg, "USB OTG 连接", deviceName)
        }

    override suspend fun runFastbootCommand(command: String): AdbOperationResult<String> =
        connectionMutex.withLock {
            if (kadbManager.sessionKind() != AdbSessionKind.UsbFastboot) {
                return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.repo_not_fastboot),
                    suggestion = appString(R.string.fastboot_connect_via_usb_otg_suggestion),
                )
            }
            try {
                fastbootOtgManager
                    .sendCommand(command)
                    .logFailure(DiagnosticModule.Fastboot, "Fastboot 命令", command.take(80))
            } finally {
                clearClosedFastbootSession()
            }
        }

    suspend fun fastbootCommand(command: String, expectedSession: String): AdbOperationResult<String> =
        connectionMutex.withLock {
            if (kadbManager.sessionKind() != AdbSessionKind.UsbFastboot || fastbootOtgManager.currentSessionId() != expectedSession) {
                return@withLock AdbOperationResult.Failure(appString(R.string.repo_not_fastboot), appString(R.string.fastboot_connect_via_usb_otg_suggestion))
            }
            try { fastbootOtgManager.sendCommand(command) } finally { clearClosedFastbootSession() }
        }

    suspend fun fastbootImage(action: String, partition: String, stream: java.io.FileInputStream, size: Long, maxChunkSize: Long?, expectedSession: String, onProgress: (Long, Long) -> Unit): AdbOperationResult<String> =
        connectionMutex.withLock {
            if (kadbManager.sessionKind() != AdbSessionKind.UsbFastboot || fastbootOtgManager.currentSessionId() != expectedSession) {
                return@withLock AdbOperationResult.Failure(appString(R.string.repo_not_fastboot), appString(R.string.fastboot_connect_via_usb_otg_suggestion))
            }
            try { fastbootOtgManager.image(action, partition, stream, size, maxChunkSize, expectedSession, onProgress) }
            finally { clearClosedFastbootSession() }
        }

    suspend fun fastbootPull(action: String, partition: String, file: File, expectedSession: String, onProgress: (Long, Long) -> Unit): AdbOperationResult<String> =
        connectionMutex.withLock {
            if (kadbManager.sessionKind() != AdbSessionKind.UsbFastboot || fastbootOtgManager.currentSessionId() != expectedSession) {
                return@withLock AdbOperationResult.Failure(appString(R.string.repo_not_fastboot), appString(R.string.fastboot_connect_via_usb_otg_suggestion))
            }
            try { fastbootOtgManager.pull(action, partition, file, expectedSession, onProgress) }
            finally { clearClosedFastbootSession() }
        }

    override suspend fun reboot(mode: RebootMode, deviceId: String): AdbOperationResult<String> = connectionMutex.withLock {
        if (kadbManager.currentEndpoint() != deviceId) {
            return@withLock AdbOperationResult.Failure(appString(R.string.home_device_unavailable), appString(R.string.home_device_unavailable_hint))
        }
        if (kadbManager.sessions().none { it.id == deviceId }) {
            return@withLock AdbOperationResult.Failure(appString(R.string.kadb_not_connected), appString(R.string.kadb_connect_before_shell_suggestion))
        }
        val result = when (kadbManager.sessionKind()) {
            AdbSessionKind.UsbFastboot -> fastbootOtgManager.sendCommand(mode.fastbootCommand)
            AdbSessionKind.Wifi, AdbSessionKind.UsbAdb -> kadbManager.reboot(mode.adbTarget)
            AdbSessionKind.None -> return@withLock AdbOperationResult.Failure(appString(R.string.kadb_not_connected), appString(R.string.kadb_connect_before_shell_suggestion))
        }
        if (result is AdbOperationResult.Success) withContext(NonCancellable) { disconnectActiveLocked() }
        else clearClosedFastbootSession()
        result
    }

    override suspend fun fastbootSummary(): AdbOperationResult<FastbootDeviceSummary> =
        connectionMutex.withLock {
            try {
                fastbootOtgManager.readSummary().logFailure(DiagnosticModule.Fastboot, "读取设备摘要")
            } finally {
                clearClosedFastbootSession()
            }
        }

    private suspend fun clearClosedFastbootSession() {
        if (
            kadbManager.sessionKind() == AdbSessionKind.UsbFastboot &&
                fastbootOtgManager.currentDeviceName() == null
        ) {
            withContext(NonCancellable) { disconnectActiveLocked() }
        }
    }

    override suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
    ): AdbOperationResult<Unit> {
        val socketHost = AdbEndpoint.hostForSocket(host)
        return kadbManager
            .pair(socketHost, port, pairingCode)
            .logFailure(DiagnosticModule.Pairing, "无线配对", AdbEndpoint.format(socketHost, port))
    }

    override suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo> =
        connectionMutex.withLock {
            refreshDeviceInfoLocked()
        }

    private suspend fun refreshDeviceInfoLocked(): AdbOperationResult<DeviceInfo> {
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
            val activeId = kadbManager.currentEndpoint()
            recentDeviceState.value = recentDeviceState.value.map { device ->
                if (device.id == activeId) device.copy(connectionState = ConnectionState.Offline) else device
            }
        }
        return result.logFailure(DiagnosticModule.WifiAdb, "刷新设备信息")
    }

    override suspend fun runShell(
        command: String,
        timeoutMillis: Long?,
        expectedSessionToken: Any?,
    ): AdbOperationResult<ShellCommandResult> {
        if (kadbManager.sessionKind() == AdbSessionKind.UsbFastboot) {
            return AdbOperationResult.Failure(
                message = appString(R.string.repo_fastboot_no_shell),
                suggestion = appString(R.string.repo_fastboot_no_shell_suggestion),
            )
        }
        return kadbManager
            .shell(command, timeoutMillis = timeoutMillis, expectedToken = expectedSessionToken)
            .logFailure(DiagnosticModule.Shell, "执行 Shell", command.take(80))
    }

    override fun sessionToken(): Any? = kadbManager.sessionToken()

    override suspend fun install(
        apkFile: File,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
    ): AdbOperationResult<Unit> {
        return kadbManager
            .install(apkFile, onProgress)
            .logFailure(DiagnosticModule.Install, "安装 APK", apkFile.name)
    }

    override suspend fun listPackages(): AdbOperationResult<List<AppInfo>> = kadbManager.listPackages()

    override suspend fun launchApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager
            .launchApp(packageName)
            .logFailure(DiagnosticModule.Apps, "启动应用", packageName)
    }

    override suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager
            .forceStopApp(packageName)
            .logFailure(DiagnosticModule.Apps, "停止应用", packageName)
    }

    override suspend fun setAppEnabled(
        packageName: String,
        enabled: Boolean,
    ): AdbOperationResult<Unit> {
        return kadbManager
            .setAppEnabled(packageName, enabled)
            .logFailure(DiagnosticModule.Apps, if (enabled) "启用应用" else "冻结应用", packageName)
    }

    override suspend fun exportAppApk(
        packageName: String,
        localFile: File,
    ): AdbOperationResult<File> {
        return kadbManager
            .exportAppApk(packageName, localFile)
            .logFailure(DiagnosticModule.Apps, "导出 APK", packageName)
    }

    override suspend fun uninstall(packageName: String): AdbOperationResult<Unit> {
        return kadbManager
            .uninstall(packageName)
            .logFailure(DiagnosticModule.Apps, "卸载应用", packageName)
    }

    override suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>> {
        return kadbManager
            .listFiles(remotePath)
            .logFailure(DiagnosticModule.Files, "读取目录", remotePath)
    }

    override suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit> {
        return kadbManager
            .makeDirectory(remotePath)
            .logFailure(DiagnosticModule.Files, "新建文件夹", remotePath)
    }

    override suspend fun deleteFile(
        remotePath: String,
        isDirectory: Boolean,
    ): AdbOperationResult<Unit> {
        return kadbManager
            .deleteFile(remotePath, isDirectory)
            .logFailure(DiagnosticModule.Files, if (isDirectory) "删除目录" else "删除文件", remotePath)
    }

    override suspend fun renameFile(remotePath: String, newName: String): AdbOperationResult<Unit> {
        return kadbManager
            .renameFile(remotePath, newName)
            .logFailure(DiagnosticModule.Files, "重命名", remotePath)
    }

    override suspend fun push(
        localFile: File,
        remotePath: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)?,
    ): AdbOperationResult<Unit> {
        return kadbManager
            .push(localFile, remotePath, onProgress)
            .logFailure(DiagnosticModule.Files, "推送文件", remotePath)
    }

    override suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> {
        return kadbManager
            .pull(remotePath, localFile)
            .logFailure(DiagnosticModule.Files, "拉取文件", remotePath)
    }

    override suspend fun captureScreenshot(localFile: File): AdbOperationResult<File> {
        return kadbManager
            .captureScreenshot(localFile)
            .logFailure(DiagnosticModule.Screenshot, "截图")
    }

    override suspend fun disconnect() = connectionMutex.withLock { disconnectActiveLocked() }

    private suspend fun disconnectActiveLocked() {
        val activeId = kadbManager.currentEndpoint()
        if (activeId != null && kadbManager.sessionKind() == AdbSessionKind.UsbFastboot) {
            fastbootOtgManager.disconnect()
            kadbManager.clearUsbFastbootSession()
        }
        if (activeId != null) kadbManager.disconnectSession(activeId)
        syncConnectionState()
        deviceInfoState.value = DeviceInfo()
        refreshPromotedDeviceIfNeeded()
    }

    override suspend fun selectDevice(deviceId: String): AdbOperationResult<Unit> =
        connectionMutex.withLock {
            if (!kadbManager.selectSession(deviceId)) {
                syncConnectionState()
                return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.home_device_unavailable),
                    suggestion = appString(R.string.home_device_unavailable_hint),
                )
            }
            syncConnectionState()
            deviceInfoState.value = DeviceInfo()
            refreshPromotedDeviceIfNeeded()
            AdbOperationResult.Success(Unit)
        }

    override suspend fun disconnectDevice(deviceId: String): AdbOperationResult<Unit> =
        connectionMutex.withLock {
            disconnectDeviceLocked(deviceId)
            AdbOperationResult.Success(Unit)
        }

    private suspend fun disconnectDeviceLocked(deviceId: String) {
        val wasActive = kadbManager.currentEndpoint() == deviceId
        val sessionKind = kadbManager.sessions().firstOrNull { it.id == deviceId }?.kind
        if (sessionKind == AdbSessionKind.UsbFastboot) {
            fastbootOtgManager.disconnect()
            kadbManager.clearUsbFastbootSession()
        }
        val disconnected = kadbManager.disconnectSession(deviceId)
        if (!disconnected && wasActive && kadbManager.currentEndpoint() == deviceId) kadbManager.disconnect()
        syncConnectionState()
        if (wasActive) {
            deviceInfoState.value = DeviceInfo()
            refreshPromotedDeviceIfNeeded()
        }
    }

    private fun refreshPromotedDeviceIfNeeded() {
        if (kadbManager.currentEndpoint() == null) return
        scope.launch {
            connectionMutex.withLock { refreshDeviceInfoLocked() }
        }
    }

    override suspend fun renameDevice(deviceId: String, name: String): AdbOperationResult<Unit> =
        connectionMutex.withLock {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MaxDeviceNameLength) {
                return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.home_device_name_invalid),
                    suggestion = appString(R.string.home_device_name_invalid_hint),
                )
            }
            val device = recentDeviceState.value.firstOrNull { it.id == deviceId }
                ?: return@withLock AdbOperationResult.Failure(
                    message = appString(R.string.home_device_unavailable),
                    suggestion = appString(R.string.home_device_unavailable_hint),
                )
            val renamed = device.copy(name = trimmed)
            recentDeviceState.value = recentDeviceState.value.map {
                if (it.id == deviceId) renamed else it
            }
            if (renamed.linkKind == AdbLinkKind.Wifi) {
                recentDeviceStore.upsert(renamed.copy(connectionState = ConnectionState.Disconnected))
            }
            AdbOperationResult.Success(Unit)
        }

    override suspend fun removeDevice(deviceId: String): AdbOperationResult<Unit> =
        connectionMutex.withLock {
            val matchingIds = (recentDeviceState.value.map { it.id } + deviceId)
                .filter { it == deviceId || AdbEndpoint.sameAuthority(it, deviceId) }
                .toSet()
            deletedDeviceIds += matchingIds
            val connectedIds = kadbManager.sessions()
                .map { it.id }
                .filter { it in matchingIds || AdbEndpoint.sameAuthority(it, deviceId) }
            connectedIds.forEach { disconnectDeviceLocked(it) }
            if (connectedIds.isEmpty() && kadbManager.currentEndpoint() == deviceId) {
                disconnectDeviceLocked(deviceId)
            }
            recentDeviceState.value = recentDeviceState.value.filterNot {
                it.id in matchingIds || AdbEndpoint.sameAuthority(it.id, deviceId)
            }
            recentDeviceStore.remove(matchingIds)
            syncConnectionState()
            AdbOperationResult.Success(Unit)
        }

    override fun sessionKind(): AdbSessionKind = kadbManager.sessionKind()

    override fun isActiveUsbDevice(deviceName: String): Boolean {
        return kadbManager.isActiveUsbDevice(deviceName) ||
            fastbootOtgManager.currentDeviceName() == deviceName
    }

    override fun deviceDisplayName(deviceId: String): String? =
        recentDeviceState.value.firstOrNull { it.id == deviceId }?.name

    private suspend fun persistConnectedDevice(connectedDevice: AdbDevice, preserveName: Boolean = false) {
        deletedDeviceIds -= connectedDevice.id
        recentDeviceState.value = upsertRecentDevice(connectedDevice)
        if (connectedDevice.linkKind == AdbLinkKind.Wifi) {
            recentDeviceStore.upsert(
                connectedDevice.copy(connectionState = ConnectionState.Disconnected)
            )
        }

        val unknown = appString(R.string.unknown)
        if (connectedDevice.linkKind == AdbLinkKind.Wifi || connectedDevice.host == "usb-otg") {
            val infoResult = refreshDeviceInfoLocked()
            if (infoResult is AdbOperationResult.Success) {
                val deviceName =
                    listOf(infoResult.data.brand, infoResult.data.model)
                        .filter { it != unknown }
                        .joinToString(" ")
                        .ifBlank { appString(R.string.repo_device_name_android) }
                val namedDevice = connectedDevice.copy(name = if (preserveName) connectedDevice.name else deviceName)
                recentDeviceState.value = upsertRecentDevice(namedDevice)
                if (namedDevice.linkKind == AdbLinkKind.Wifi) {
                    recentDeviceStore.upsert(
                        namedDevice.copy(connectionState = ConnectionState.Disconnected)
                    )
                }
            }
        }
    }

    private fun upsertRecentDevice(device: AdbDevice): List<AdbDevice> {
        val others = recentDeviceState.value.filter { it.id != device.id }
        val connected = others.filter { it.connectionState == ConnectionState.Connected }
        val disconnected = others
            .filterNot { it.connectionState == ConnectionState.Connected }
            .take((MaxRecentDevices - connected.size - 1).coerceAtLeast(0))
        return listOf(device) + connected + disconnected
    }

    private fun syncConnectionState() {
        val connectedIds = kadbManager.sessions().map { it.id }.toSet()
        recentDeviceState.value = recentDeviceState.value.map { device ->
            when {
                device.id in connectedIds -> device.copy(connectionState = ConnectionState.Connected)
                device.connectionState == ConnectionState.Connected ->
                    device.copy(connectionState = ConnectionState.Disconnected)
                else -> device
            }
        }
        activeDeviceState.value = kadbManager.currentEndpoint()
    }

    private companion object {
        const val MaxRecentDevices = 8
        const val MaxDeviceNameLength = 80
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
