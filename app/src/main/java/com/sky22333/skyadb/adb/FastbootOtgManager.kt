package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.rv882.fastbootjava.FastbootCommand
import com.rv882.fastbootjava.FastbootDeviceContext
import com.rv882.fastbootjava.FastbootResponse
import com.rv882.fastbootjava.transport.UsbTransport
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.usb.AndroidUsbInterface
import java.io.IOException
import java.nio.charset.StandardCharsets

class FastbootOtgManager {
    private var deviceContext: FastbootDeviceContext? = null
    private var activeDeviceName: String? = null

    fun connect(
        usbManager: UsbManager,
        device: UsbDevice,
    ): AdbOperationResult<String> {
        disconnect()
        val usbInterface = AndroidUsbInterface.findFastbootInterface(device)
            ?: return AdbOperationResult.Failure(
                message = "未找到 Fastboot 接口",
                suggestion = "请确认目标设备已进入 Bootloader / Fastboot 模式。",
            )
        val connection = usbManager.openDevice(device)
            ?: return AdbOperationResult.Failure(
                message = "无法打开 USB 设备",
                suggestion = "请重新插拔 OTG 线，并在系统弹窗中允许 USB 访问。",
            )
        return runCatching {
            val transport = UsbTransport(usbInterface, connection)
            val context = FastbootDeviceContext(transport)
            context.sendCommand(
                FastbootCommand.getVar("version").toString().toByteArray(StandardCharsets.UTF_8),
                ProbeTimeoutMs,
                true,
            )
            if (FastbootResponse.getStatus() == FastbootResponse.ResponseStatus.FAIL) {
                context.close()
                throw IOException(FastbootResponse.getData().ifBlank { "Fastboot 探测返回 FAIL" })
            }
            deviceContext = context
            activeDeviceName = device.deviceName
            AdbOperationResult.Success("fastboot:${device.deviceName}")
        }.getOrElse { error ->
            runCatching { connection.close() }
            AdbOperationResult.Failure(
                message = "Fastboot 连接失败",
                suggestion = "请确认目标设备处于 Fastboot 模式，并重新授权 USB 访问。",
                cause = error,
            )
        }
    }

    fun sendCommand(command: String): AdbOperationResult<String> {
        val context = deviceContext
            ?: return AdbOperationResult.Failure(
                message = "未连接 Fastboot 设备",
                suggestion = "请先在首页通过 USB OTG 连接处于 Fastboot 模式的设备。",
            )
        val normalized = command.trim()
        if (normalized.isEmpty()) {
            return AdbOperationResult.Failure(
                message = "Fastboot 命令为空",
                suggestion = "请输入有效的 Fastboot 命令，例如 getvar:version。",
            )
        }
        return runCatching {
            context.sendCommand(
                normalized.toByteArray(StandardCharsets.UTF_8),
                CommandTimeoutMs,
                true,
            )
            val status = FastbootResponse.getStatus().name
            val data = FastbootResponse.getData().trim { it <= ' ' || it == '\u0000' }
            AdbOperationResult.Success("$status: $data")
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = "Fastboot 命令执行失败",
                suggestion = "请确认命令格式正确，且设备仍处于 Fastboot 模式。",
                cause = error,
            )
        }
    }

    fun disconnect() {
        runCatching { deviceContext?.close() }
        deviceContext = null
        activeDeviceName = null
    }

    fun currentDeviceName(): String? = activeDeviceName

    private companion object {
        const val ProbeTimeoutMs = 3_000
        const val CommandTimeoutMs = 5_000
    }
}
