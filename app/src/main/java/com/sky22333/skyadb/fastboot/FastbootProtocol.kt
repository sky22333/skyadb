package com.sky22333.skyadb.fastboot

import java.io.File

internal sealed interface FastbootResponse {
    data class Okay(val message: String) : FastbootResponse
    data class Fail(val message: String) : FastbootResponse
    data class Data(val size: Long) : FastbootResponse
    data class Info(val message: String) : FastbootResponse
}

internal object FastbootProtocol {
    const val MaxCommandBytes = 64
    private const val StatusLength = 4

    fun validateCommand(command: String): String {
        val normalized = command.trim()
        require(normalized.isNotBlank()) { "Fastboot 命令不能为空" }
        require(normalized.encodeToByteArray().size <= MaxCommandBytes) {
            "Fastboot 命令不能超过 $MaxCommandBytes 字节"
        }
        return normalized
    }

    fun parseResponse(packet: ByteArray, length: Int): FastbootResponse {
        require(length >= StatusLength) { "Fastboot 响应长度不足" }
        val status = packet.decodeToString(endIndex = StatusLength)
        val message = packet.decodeToString(startIndex = StatusLength, endIndex = length)
        return when (status) {
            "OKAY" -> FastbootResponse.Okay(message)
            "FAIL" -> FastbootResponse.Fail(message)
            "INFO" -> FastbootResponse.Info(message)
            "DATA" -> FastbootResponse.Data(message.trim().toLong(radix = 16))
            else -> error("未知 Fastboot 响应：$status")
        }
    }

    fun downloadCommand(file: File): String {
        return "download:%08x".format(file.length())
    }

    fun shouldDownloadBeforeCommand(command: String, file: File?): Boolean {
        if (file == null) return false
        val normalized = command.trim()
        return normalized.startsWith("flash:", ignoreCase = true) ||
            normalized.equals("boot", ignoreCase = true) ||
            normalized.startsWith("update:", ignoreCase = true) ||
            normalized.startsWith("download:", ignoreCase = true)
    }

    fun shouldExecuteAfterDownload(command: String): Boolean {
        return !command.trim().startsWith("download:", ignoreCase = true)
    }
}
