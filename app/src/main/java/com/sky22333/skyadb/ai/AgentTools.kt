package com.sky22333.skyadb.ai

import android.net.Uri
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.discovery.AndroidAdbMdnsDiscovery
import com.sky22333.skyadb.model.AdbEndpoint
import com.sky22333.skyadb.model.AdbOperationResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Generic operations refer to explicit environments and use existing ADB sessions. */
internal object AgentTools {
    internal data class DeviceHandle(val manager: KadbManager, val token: Any, val endpoint: String)
    private fun documents(config: AiConfig): AiDocuments = AiDocuments(
        AppServices.context,
        Uri.parse(config.documentTree.takeIf(String::isNotBlank) ?: error("No local directory authorized")),
    )
    suspend fun environments(config: AiConfig): String {
        val devices = AppServices.agentSessions.list()
        val recent = AppServices.recentDeviceStore.devices.first()
        return formatEnvironments(config, devices, recent)
    }
    internal fun formatEnvironments(config: AiConfig, devices: List<AgentSessions.Device>, recent: List<com.sky22333.skyadb.model.AdbDevice>): String {
        return buildString {
            appendLine("app: Android app UID, system shell and accessible files")
            if (config.documentTree.isNotBlank()) appendLine("authorized: user-selected document directory, available through file transfer")
            if (devices.isEmpty()) appendLine("No ADB devices are connected")
            devices.forEach { appendLine("${it.id}: ADB ${it.endpoint} (${if (it.connected) "connected" else "disconnected; use agent_connect to reconnect"})") }
            recent.forEach { device ->
                val endpoint = AdbEndpoint.format(device.host, device.port)
                val connected = devices.firstOrNull { it.connected && AdbEndpoint.sameAuthority(it.endpoint, endpoint) }
                appendLine("Recent device: name=${Json.encodeToString(device.name)}, host=${Json.encodeToString(device.host)}, port=${device.port}, ${if (connected != null) "connected ID=${connected.id}" else "not connected"}")
            }
            append("Use exact connected environment IDs. To use a recent device that is not connected, call agent_connect with its host and port, then use the ID returned. If the target is ambiguous, ask the user. Discover capabilities with commands on the selected environment.")
        }
    }
    suspend fun discover(): String {
        val discovery = AndroidAdbMdnsDiscovery(AppServices.context)
        return try {
            discovery.start()
            delay(3000)
            val state = discovery.state.value
            state.error?.let { error(it) }
            state.endpoints.joinToString("\n") { "${it.type}: ${it.endpoint}" }.ifEmpty { "No ADB services found" }
        } finally { discovery.stop() }
    }
    suspend fun capture(id: String, activeToken: Any?): DeviceHandle {
        val manager = AppServices.agentSessions.resolve(id)
        val token = requireNotNull(manager.sessionToken()) { "ADB device is not connected: $id" }
        if (id == "active") check(token === activeToken) { "ADB session changed" }
        return DeviceHandle(manager, token, manager.currentEndpoint() ?: id)
    }
    private suspend fun <T> onDevice(id: String, handles: Map<String, DeviceHandle>, block: suspend (KadbManager) -> T): T {
        val handle = requireNotNull(handles[id]) { "Unknown device: $id" }
        return handle.manager.withSession(handle.token) { block(handle.manager) }
    }
    suspend fun command(id: String, command: String, handles: Map<String, DeviceHandle>): String {
        require(command.isNotBlank()) { "Command is empty" }
        if (id == "app") return runAppShell(command)
        return onDevice(id, handles) { manager ->
            when (val result = manager.shell(command, maxOutputBytes = 20_000, timeoutMillis = null)) {
                is AdbOperationResult.Success -> "exit=${if (result.data.exitCode < 0) "unknown" else result.data.exitCode}\nstdout=${result.data.output}\nstderr=${result.data.errorOutput}"
                is AdbOperationResult.Failure -> error("${result.message}: ${result.suggestion}")
            }
        }
    }
    suspend fun inspect(id: String, handles: Map<String, DeviceHandle>): String {
        require(id != "app" && id != "authorized") { "Screen inspection requires an ADB environment" }
        return onDevice(id, handles) { manager ->
            val path = "/data/local/tmp/skyadb-ui-${java.util.UUID.randomUUID()}.xml"
            try {
                when (val result = manager.shell("uiautomator dump '$path' >/dev/null && cat '$path'", maxOutputBytes = 100_000)) {
                    is AdbOperationResult.Success -> {
                        check(result.data.exitCode == 0) { result.data.errorOutput.ifBlank { result.data.output } }
                        result.data.output
                    }
                    is AdbOperationResult.Failure -> error("${result.message}: ${result.suggestion}")
                }
            } finally {
                withContext(NonCancellable) { manager.shell("rm -f '$path'", maxOutputBytes = 1024, timeoutMillis = 5000) }
            }
        }
    }
    suspend fun transfer(config: AiConfig, source: String, sourcePath: String, destination: String, destinationPath: String, handles: Map<String, DeviceHandle>, selected: Uri?): String {
        require((source == "selected" || sourcePath.isNotBlank()) && destinationPath.isNotBlank()) { "Source and destination paths are required" }
        require(destination != "selected") { "Selected attachment is read-only" }
        require(source != destination || sourcePath != destinationPath) { "Source and destination are identical" }
        val temp = withContext(Dispatchers.IO) { File.createTempFile("agent-transfer-", ".tmp", AppServices.context.cacheDir) }
        try {
            when (source) {
                "selected" -> withContext(Dispatchers.IO) { requireNotNull(AppServices.context.contentResolver.openInputStream(requireNotNull(selected) { "No local file selected" })).use { input -> temp.outputStream().use { output -> copy(input, output) } } }
                "app" -> withContext(Dispatchers.IO) { File(sourcePath).inputStream().use { input -> temp.outputStream().use { output -> copy(input, output) } } }
                "authorized" -> withContext(Dispatchers.IO) { documents(config).read(sourcePath).use { input -> temp.outputStream().use { output -> copy(input, output) } } }
                else -> onDevice(source, handles) { manager ->
                    when (val result = manager.pull(sourcePath, temp)) {
                        is AdbOperationResult.Success -> Unit
                        is AdbOperationResult.Failure -> error("${result.message}: ${result.suggestion}")
                    }
                }
            }
            when (destination) {
                "app" -> withContext(Dispatchers.IO) {
                    val target = File(destinationPath).absoluteFile
                    val staging = File.createTempFile(".skyadb-transfer-", ".tmp", requireNotNull(target.parentFile))
                    try {
                        temp.inputStream().use { input -> staging.outputStream().use { output -> copy(input, output) } }
                        currentCoroutineContext().ensureActive()
                        check(staging.renameTo(target)) { "Cannot save destination file" }
                    } finally { staging.delete() }
                }
                "authorized" -> withContext(Dispatchers.IO) { temp.inputStream().use { input -> documents(config).replace(destinationPath) { output -> copy(input, output) } } }
                else -> onDevice(destination, handles) { manager ->
                    when (val result = manager.push(temp, destinationPath)) {
                        is AdbOperationResult.Success -> Unit
                        is AdbOperationResult.Failure -> error("${result.message}: ${result.suggestion}")
                    }
                }
            }
            return "Transferred ${temp.length()} bytes from ${Json.encodeToString("$source:$sourcePath")} to ${Json.encodeToString("$destination:$destinationPath")}."
        } finally { withContext(Dispatchers.IO + NonCancellable) { temp.delete() } }
    }
    private suspend fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }
}
