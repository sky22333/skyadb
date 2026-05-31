package com.sky22333.skyadb.repository

import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.data.RecentDeviceStore
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AdbDevice
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.model.DeviceInfo
import com.sky22333.skyadb.model.DeviceType
import com.sky22333.skyadb.model.RemoteFileEntry
import com.sky22333.skyadb.model.ShellCommandResult
import dadb.Dadb
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
    suspend fun connectUsb(dadb: Dadb, endpoint: String, name: String): AdbOperationResult<String>
    suspend fun pair(host: String, port: Int, pairingCode: String): AdbOperationResult<Unit>
    suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo>
    suspend fun runShell(command: String): AdbOperationResult<ShellCommandResult>
    suspend fun install(apkFile: File): AdbOperationResult<Unit>
    suspend fun listApps(): AdbOperationResult<List<AppInfo>>
    suspend fun launchApp(packageName: String): AdbOperationResult<Unit>
    suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit>
    suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit>
    suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File>
    suspend fun uninstall(packageName: String): AdbOperationResult<Unit>
    suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>>
    suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit>
    suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit>
    suspend fun push(localFile: File, remotePath: String): AdbOperationResult<Unit>
    suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit>
    suspend fun captureScreenshot(localFile: File): AdbOperationResult<File>
    fun disconnect()
}

class DefaultAdbRepository(
    private val kadbManager: KadbManager,
    private val recentDeviceStore: RecentDeviceStore,
    private val settingsStore: AppSettingsStore,
) : AdbRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val recentDeviceState = MutableStateFlow(
        emptyList<AdbDevice>(),
    )

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
                        device.copy(connectionState = ConnectionState.Connected, lastConnectedText = "刚刚连接")
                    } else {
                        device
                    }
                }
            }
        }
    }

    override suspend fun connect(host: String, port: Int): AdbOperationResult<String> {
        val settings = settingsStore.settings.first()
        val result = kadbManager.connect(
            host = host,
            port = port,
            connectTimeoutMillis = settings.connectionTimeoutSeconds * 1_000,
            socketTimeoutMillis = settings.commandTimeoutSeconds * 1_000,
        )
        if (result is AdbOperationResult.Success) {
            val connectedDevice = AdbDevice(
                id = "$host:$port",
                name = "Android 设备",
                host = host,
                port = port,
                type = DeviceType.Unknown,
                connectionState = ConnectionState.Connected,
                lastConnectedText = "刚刚连接",
            )
            recentDeviceState.value = upsertRecentDevice(connectedDevice)
            recentDeviceStore.upsert(connectedDevice.copy(connectionState = ConnectionState.Disconnected))

            val infoResult = refreshDeviceInfo()
            if (infoResult is AdbOperationResult.Success) {
                val deviceName = listOf(infoResult.data.brand, infoResult.data.model)
                    .filter { it != "未知" }
                    .joinToString(" ")
                    .ifBlank { "Android 设备" }
                val namedDevice = connectedDevice.copy(name = deviceName)
                recentDeviceState.value = upsertRecentDevice(namedDevice)
                recentDeviceStore.upsert(namedDevice.copy(connectionState = ConnectionState.Disconnected))
            }
        }
        return result
    }

    override suspend fun connectUsb(dadb: Dadb, endpoint: String, name: String): AdbOperationResult<String> {
        val result = kadbManager.connectUsb(dadb, endpoint)
        if (result is AdbOperationResult.Success) {
            val connectedDevice = AdbDevice(
                id = endpoint,
                name = name.ifBlank { "USB ADB 设备" },
                host = "USB",
                port = 0,
                type = DeviceType.Unknown,
                connectionState = ConnectionState.Connected,
                lastConnectedText = "刚刚连接",
            )
            recentDeviceState.value = upsertRecentDevice(connectedDevice)
            recentDeviceStore.upsert(connectedDevice.copy(connectionState = ConnectionState.Disconnected))
            val infoResult = refreshDeviceInfo()
            if (infoResult is AdbOperationResult.Success) {
                val deviceName = listOf(infoResult.data.brand, infoResult.data.model)
                    .filter { it != "未知" && it != "鏈煡" }
                    .joinToString(" ")
                    .ifBlank { connectedDevice.name }
                val namedDevice = connectedDevice.copy(name = deviceName)
                recentDeviceState.value = upsertRecentDevice(namedDevice)
                recentDeviceStore.upsert(namedDevice.copy(connectionState = ConnectionState.Disconnected))
            }
        }
        return result
    }

    override suspend fun pair(host: String, port: Int, pairingCode: String): AdbOperationResult<Unit> {
        return kadbManager.pair(host, port, pairingCode)
    }

    override suspend fun refreshDeviceInfo(): AdbOperationResult<DeviceInfo> {
        val result = kadbManager.fetchDeviceInfo()
        if (result is AdbOperationResult.Success) {
            deviceInfoState.value = result.data
        } else {
            deviceInfoState.value = DeviceInfo()
            markConnectedDevices(ConnectionState.Offline)
        }
        return result
    }

    override suspend fun runShell(command: String): AdbOperationResult<ShellCommandResult> {
        return kadbManager.shell(command)
    }

    override suspend fun install(apkFile: File): AdbOperationResult<Unit> {
        return kadbManager.install(apkFile)
    }

    override suspend fun listApps(): AdbOperationResult<List<AppInfo>> {
        return kadbManager.listApps()
    }

    override suspend fun launchApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.launchApp(packageName)
    }

    override suspend fun forceStopApp(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.forceStopApp(packageName)
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): AdbOperationResult<Unit> {
        return kadbManager.setAppEnabled(packageName, enabled)
    }

    override suspend fun exportAppApk(packageName: String, localFile: File): AdbOperationResult<File> {
        return kadbManager.exportAppApk(packageName, localFile)
    }

    override suspend fun uninstall(packageName: String): AdbOperationResult<Unit> {
        return kadbManager.uninstall(packageName)
    }

    override suspend fun listFiles(remotePath: String): AdbOperationResult<List<RemoteFileEntry>> {
        return kadbManager.listFiles(remotePath)
    }

    override suspend fun makeDirectory(remotePath: String): AdbOperationResult<Unit> {
        return kadbManager.makeDirectory(remotePath)
    }

    override suspend fun deleteFile(remotePath: String, isDirectory: Boolean): AdbOperationResult<Unit> {
        return kadbManager.deleteFile(remotePath, isDirectory)
    }

    override suspend fun push(localFile: File, remotePath: String): AdbOperationResult<Unit> {
        return kadbManager.push(localFile, remotePath)
    }

    override suspend fun pull(remotePath: String, localFile: File): AdbOperationResult<Unit> {
        return kadbManager.pull(remotePath, localFile)
    }

    override suspend fun captureScreenshot(localFile: File): AdbOperationResult<File> {
        return kadbManager.captureScreenshot(localFile)
    }

    override fun disconnect() {
        kadbManager.disconnect()
        val next = recentDeviceState.value.map {
            it.copy(connectionState = ConnectionState.Disconnected)
        }
        recentDeviceState.value = next
        deviceInfoState.value = DeviceInfo()
        scope.launch {
            recentDeviceStore.saveDevices(next)
        }
    }

    suspend fun isReady(): Boolean = kadbManager.checkRuntimeReady()

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
