package com.sky22333.skyadb.ai

import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Additional ADB sessions are independent from the application's selected device. */
class AgentSessions(private val primary: KadbManager) {
    data class Device(val id: String, val endpoint: String, val connected: Boolean = true)
    private val mutex = Mutex()
    private val managers = linkedMapOf<String, KadbManager>()
    private val devices = linkedMapOf<String, Device>()

    suspend fun connect(id: String, host: String, port: Int): AdbOperationResult<Device> = mutex.withLock {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,32}")) && id !in setOf("active", "app", "authorized")) { "Invalid or reserved device ID" }
        require(host.isNotBlank() && host.length <= 255 && port in 1..65535) { "Invalid ADB endpoint" }
        val endpoint = "$host:$port"
        if (primary.currentEndpoint() == endpoint && primary.isConnected()) return@withLock AdbOperationResult.Success(Device("active", endpoint))
        devices.values.firstOrNull { it.endpoint == endpoint }?.let { existing ->
            val manager = requireNotNull(managers[existing.id])
            if (manager.isConnected()) return@withLock AdbOperationResult.Success(existing)
            return@withLock when (val result = manager.connect(host, port)) {
                is AdbOperationResult.Success -> AdbOperationResult.Success(existing)
                is AdbOperationResult.Failure -> result
            }
        }
        require(id !in managers) { "Device ID already exists: $id" }
        val manager = KadbManager()
        var registered = false
        try {
            val result = manager.connect(host, port)
            if (result is AdbOperationResult.Success) {
                val device = Device(id, result.data)
                managers[id] = manager
                devices[id] = device
                registered = true
                AdbOperationResult.Success(device)
            } else {
                result as AdbOperationResult.Failure
            }
        } finally {
            if (!registered) withContext(NonCancellable) { manager.disconnect() }
        }
    }
    suspend fun disconnect(id: String) {
        val manager = mutex.withLock {
            requireNotNull(managers.remove(id)) { "Unknown additional device: $id" }.also { devices.remove(id) }
        }
        withContext(NonCancellable) { manager.disconnect() }
    }
    suspend fun resolve(id: String): KadbManager = if (id == "active") primary else mutex.withLock {
        requireNotNull(managers[id]) { "Unknown device: $id" }
    }
    suspend fun list(): List<Device> = mutex.withLock { buildList {
        if (primary.sessionKind() != com.sky22333.skyadb.adb.AdbSessionKind.UsbFastboot) {
            primary.currentEndpoint()?.let { add(Device("active", it, primary.isConnected())) }
        }
        devices.values.forEach { add(it.copy(connected = managers[it.id]?.isConnected() == true)) }
    } }
}
