package com.sky22333.skyadb.adb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.rv882.fastbootjava.FastbootCommand
import com.rv882.fastbootjava.FastbootDeviceContext
import com.rv882.fastbootjava.FastbootResponse
import com.rv882.fastbootjava.transport.UsbTransport
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.usb.AndroidUsbInterface
import java.io.IOException

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
                message = appString(R.string.fastboot_interface_not_found),
                suggestion = appString(R.string.fastboot_interface_suggestion),
            )
        val connection = usbManager.openDevice(device)
            ?: return AdbOperationResult.Failure(
                message = appString(R.string.usb_cannot_open_device),
                suggestion = appString(R.string.usb_replug_allow_access_suggestion),
            )
        return runCatching {
            val transport = UsbTransport(usbInterface, connection)
            val context = FastbootDeviceContext(transport)
            context.sendCommand(FastbootCommand.getVar(ProbeVariable), ProbeTimeoutMs, true)
            val status = FastbootResponse.getStatus()
            if (!isAliveResponse(status)) {
                context.close()
                throw IOException(appString(R.string.fastboot_invalid_response_error, status))
            }
            deviceContext = context
            activeDeviceName = device.deviceName
            AdbOperationResult.Success("fastboot:${device.deviceName}")
        }.getOrElse { error ->
            runCatching { connection.close() }
            AdbOperationResult.Failure(
                message = appString(R.string.fastboot_connect_failed),
                suggestion = appString(R.string.fastboot_connect_failed_suggestion),
                cause = error,
            )
        }
    }

    fun sendCommand(command: String): AdbOperationResult<String> {
        val context = deviceContext
            ?: return AdbOperationResult.Failure(
                message = appString(R.string.fastboot_not_connected),
                suggestion = appString(R.string.fastboot_connect_via_usb_otg_suggestion),
            )
        val normalized = command.trim()
        if (normalized.isEmpty()) {
            return AdbOperationResult.Failure(
                message = appString(R.string.fastboot_command_empty),
                suggestion = appString(R.string.fastboot_command_empty_suggestion),
            )
        }
        return runCatching {
            context.sendCommand(
                normalized.toByteArray(Charsets.UTF_8),
                CommandTimeoutMs,
                true,
            )
            val status = FastbootResponse.getStatus()
            if (!isAliveResponse(status)) {
                throw IOException(appString(R.string.fastboot_invalid_response_error, status))
            }
            val data = FastbootResponse.getData().trim { it <= ' ' || it == '\u0000' }
            AdbOperationResult.Success("$status: $data")
        }.getOrElse { error ->
            AdbOperationResult.Failure(
                message = appString(R.string.fastboot_command_failed),
                suggestion = appString(R.string.fastboot_command_failed_suggestion),
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

    companion object {
        private const val ProbeVariable = "product"
        private const val ProbeTimeoutMs = 3_000
        private const val CommandTimeoutMs = 5_000

        fun isAliveResponse(status: FastbootResponse.ResponseStatus): Boolean {
            return status == FastbootResponse.ResponseStatus.OKAY ||
                status == FastbootResponse.ResponseStatus.FAIL ||
                status == FastbootResponse.ResponseStatus.INFO ||
                status == FastbootResponse.ResponseStatus.DATA
        }
    }
}
