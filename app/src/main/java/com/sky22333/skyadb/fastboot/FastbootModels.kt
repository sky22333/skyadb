package com.sky22333.skyadb.fastboot

import java.io.File

data class FastbootUsbDevice(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
)

data class FastbootCommandResult(
    val command: String,
    val output: String,
)

sealed interface FastbootOperationResult<out T> {
    data class Success<T>(val data: T) : FastbootOperationResult<T>
    data class Failure(
        val message: String,
        val suggestion: String,
        val cause: Throwable? = null,
    ) : FastbootOperationResult<Nothing>
}

data class FastbootPreparedCommand(
    val command: String,
    val requiresConfirmation: Boolean,
    val normalizedCommand: String = command.trim(),
)

object FastbootCommandPolicy {
    private val DangerousPrefixes = listOf(
        "flash:",
        "erase:",
        "format:",
        "oem ",
        "flashing ",
        "set_active:",
        "update:",
        "boot",
    )

    fun prepare(command: String): FastbootPreparedCommand {
        val normalized = command.trim()
        val requiresConfirmation = DangerousPrefixes.any {
            normalized.startsWith(it, ignoreCase = true)
        }
        return FastbootPreparedCommand(
            command = command,
            normalizedCommand = normalized,
            requiresConfirmation = requiresConfirmation,
        )
    }
}

interface FastbootRepository {
    fun listDevices(): List<FastbootUsbDevice>
    suspend fun requestPermission(deviceId: String): FastbootOperationResult<Unit>
    suspend fun connect(deviceId: String): FastbootOperationResult<FastbootUsbDevice>
    suspend fun execute(command: String, downloadFile: File? = null): FastbootOperationResult<FastbootCommandResult>
    fun disconnect()
}
