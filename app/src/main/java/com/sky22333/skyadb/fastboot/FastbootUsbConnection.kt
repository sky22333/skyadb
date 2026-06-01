package com.sky22333.skyadb.fastboot

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FastbootUsbConnection private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val inputEndpoint: UsbEndpoint,
    private val outputEndpoint: UsbEndpoint,
) {
    suspend fun execute(command: String, downloadFile: File? = null): FastbootCommandResult = withContext(Dispatchers.IO) {
        val normalized = FastbootProtocol.validateCommand(command)
        val output = StringBuilder()
        if (FastbootProtocol.shouldDownloadBeforeCommand(normalized, downloadFile)) {
            requireNotNull(downloadFile)
            download(downloadFile, output)
            if (!FastbootProtocol.shouldExecuteAfterDownload(normalized)) {
                return@withContext FastbootCommandResult(command = normalized, output = output.toString().trim())
            }
        }
        sendCommand(normalized, output)
        FastbootCommandResult(command = normalized, output = output.toString().trim())
    }

    fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    private fun download(file: File, output: StringBuilder) {
        require(file.isFile) { "请选择有效的镜像文件" }
        sendPacket(FastbootProtocol.downloadCommand(file).encodeToByteArray())
        when (val response = readTerminalResponse(output)) {
            is FastbootResponse.Data -> {
                if (response.size != file.length()) {
                    error("设备请求的数据大小与文件大小不一致")
                }
            }
            is FastbootResponse.Fail -> error(response.message.ifBlank { "设备拒绝下载镜像" })
            is FastbootResponse.Okay -> error("设备未进入 DATA 传输状态")
            is FastbootResponse.Info -> error(response.message)
        }

        file.inputStream().use { input ->
            val buffer = ByteArray(TransferChunkSize)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sendPacket(buffer, read)
            }
        }

        when (val response = readTerminalResponse(output)) {
            is FastbootResponse.Okay -> appendOutput(output, response.message.ifBlank { "download OKAY" })
            is FastbootResponse.Fail -> error(response.message.ifBlank { "镜像下载失败" })
            is FastbootResponse.Data -> error("镜像下载后收到异常 DATA 响应")
            is FastbootResponse.Info -> error(response.message)
        }
    }

    private fun sendCommand(command: String, output: StringBuilder) {
        sendPacket(command.encodeToByteArray())
        when (val response = readTerminalResponse(output)) {
            is FastbootResponse.Okay -> appendOutput(output, response.message.ifBlank { "OKAY" })
            is FastbootResponse.Fail -> error(response.message.ifBlank { "命令执行失败" })
            is FastbootResponse.Data -> appendOutput(output, "DATA ${response.size}")
            is FastbootResponse.Info -> appendOutput(output, response.message)
        }
    }

    private fun readTerminalResponse(output: StringBuilder): FastbootResponse {
        while (true) {
            val response = readResponse()
            if (response is FastbootResponse.Info) {
                appendOutput(output, response.message)
            } else {
                return response
            }
        }
    }

    private fun readResponse(): FastbootResponse {
        val buffer = ByteArray(ResponseBufferSize)
        val read = connection.bulkTransfer(inputEndpoint, buffer, buffer.size, TransferTimeoutMillis)
        if (read <= 0) error("读取 Fastboot 响应超时")
        return FastbootProtocol.parseResponse(buffer, read)
    }

    private fun sendPacket(bytes: ByteArray, length: Int = bytes.size) {
        var offset = 0
        while (offset < length) {
            val count = minOf(outputEndpoint.maxPacketSize.coerceAtLeast(1) * 64, length - offset)
            val written = connection.bulkTransfer(outputEndpoint, bytes, offset, count, TransferTimeoutMillis)
            if (written <= 0) error("写入 Fastboot 数据超时")
            offset += written
        }
    }

    private fun appendOutput(output: StringBuilder, value: String) {
        if (value.isBlank()) return
        if (output.isNotEmpty()) output.appendLine()
        output.append(value)
    }

    companion object {
        fun open(usbManager: UsbManager, device: UsbDevice): FastbootUsbConnection {
            val usbInterface = device.fastbootInterface() ?: error("未找到 Fastboot USB 接口")
            val input = usbInterface.bulkEndpoint(direction = UsbConstants.USB_DIR_IN)
                ?: error("未找到 Fastboot 输入端点")
            val output = usbInterface.bulkEndpoint(direction = UsbConstants.USB_DIR_OUT)
                ?: error("未找到 Fastboot 输出端点")
            val connection = usbManager.openDevice(device) ?: error("无法打开 USB 设备")
            if (!connection.claimInterface(usbInterface, true)) {
                connection.close()
                error("无法声明 Fastboot USB 接口")
            }
            return FastbootUsbConnection(connection, usbInterface, input, output)
        }

        fun isFastbootDevice(device: UsbDevice): Boolean = device.fastbootInterface() != null

        private fun UsbDevice.fastbootInterface(): UsbInterface? {
            return (0 until interfaceCount)
                .asSequence()
                .map { getInterface(it) }
                .firstOrNull { usbInterface ->
                    usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                        usbInterface.interfaceSubclass == AndroidUsbSubclass &&
                        usbInterface.interfaceProtocol == FastbootProtocolId &&
                        usbInterface.bulkEndpoint(UsbConstants.USB_DIR_IN) != null &&
                        usbInterface.bulkEndpoint(UsbConstants.USB_DIR_OUT) != null
                }
        }

        private fun UsbInterface.bulkEndpoint(direction: Int): UsbEndpoint? {
            return (0 until endpointCount)
                .asSequence()
                .map { getEndpoint(it) }
                .firstOrNull { endpoint ->
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        endpoint.direction == direction
                }
        }

        private const val AndroidUsbSubclass = 0x42
        private const val FastbootProtocolId = 0x03
        private const val TransferChunkSize = 64 * 1024
        private const val ResponseBufferSize = 4096
        private const val TransferTimeoutMillis = 10_000
    }
}
