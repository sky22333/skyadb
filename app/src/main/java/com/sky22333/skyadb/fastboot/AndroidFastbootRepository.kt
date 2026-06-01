package com.sky22333.skyadb.fastboot

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidFastbootRepository(
    context: Context,
) : FastbootRepository {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private var connection: FastbootUsbConnection? = null
    private var connectedDeviceId: String? = null

    override fun listDevices(): List<FastbootUsbDevice> {
        return usbManager.deviceList.values
            .filter(FastbootUsbConnection::isFastbootDevice)
            .map(::toModel)
            .sortedBy { it.name }
    }

    override suspend fun requestPermission(deviceId: String): FastbootOperationResult<Unit> {
        val device = findDevice(deviceId) ?: return FastbootOperationResult.Failure(
            message = "未找到 Fastboot 设备",
            suggestion = "请确认目标设备已进入 Fastboot 模式，并通过 OTG 连接。",
        )
        if (usbManager.hasPermission(device)) return FastbootOperationResult.Success(Unit)

        return runCatching {
            awaitPermission(device)
            if (usbManager.hasPermission(device)) {
                FastbootOperationResult.Success(Unit)
            } else {
                FastbootOperationResult.Failure(
                    message = "USB 授权被拒绝",
                    suggestion = "请在系统 USB 授权弹窗中允许 sky adb 访问该设备。",
                )
            }
        }.getOrElse { error ->
            FastbootOperationResult.Failure(
                message = "请求 USB 授权失败",
                suggestion = error.message ?: "请重新插拔设备后再试。",
                cause = error,
            )
        }
    }

    override suspend fun connect(deviceId: String): FastbootOperationResult<FastbootUsbDevice> = withContext(Dispatchers.IO) {
        val device = findDevice(deviceId) ?: return@withContext FastbootOperationResult.Failure(
            message = "未找到 Fastboot 设备",
            suggestion = "请确认目标设备已进入 Fastboot 模式，并通过 OTG 连接。",
        )
        if (!usbManager.hasPermission(device)) {
            return@withContext FastbootOperationResult.Failure(
                message = "缺少 USB 授权",
                suggestion = "请先授权 USB 设备，再连接 Fastboot。",
            )
        }
        runCatching {
            disconnect()
            connection = FastbootUsbConnection.open(usbManager, device)
            connectedDeviceId = device.deviceName
            FastbootOperationResult.Success(toModel(device))
        }.getOrElse { error ->
            FastbootOperationResult.Failure(
                message = "Fastboot 连接失败",
                suggestion = error.message ?: "请确认设备处于 Fastboot 模式。",
                cause = error,
            )
        }
    }

    override suspend fun execute(command: String, downloadFile: java.io.File?): FastbootOperationResult<FastbootCommandResult> {
        val activeConnection = connection ?: return FastbootOperationResult.Failure(
            message = "未连接 Fastboot 设备",
            suggestion = "请先授权并连接 USB Fastboot 设备。",
        )
        return withContext(Dispatchers.IO) {
            runCatching {
                FastbootOperationResult.Success(activeConnection.execute(command, downloadFile))
            }.getOrElse { error ->
                FastbootOperationResult.Failure(
                    message = "Fastboot 命令执行失败",
                    suggestion = error.message ?: "请确认命令、分区名、镜像文件和设备状态正确。",
                    cause = error,
                )
            }
        }
    }

    override fun disconnect() {
        connection?.close()
        connection = null
        connectedDeviceId = null
    }

    private fun findDevice(deviceId: String): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { it.deviceName == deviceId }
    }

    private fun toModel(device: UsbDevice): FastbootUsbDevice {
        val usbName = runCatching {
            listOfNotNull(device.manufacturerName, device.productName).joinToString(" ")
        }.getOrNull().orEmpty()
        return FastbootUsbDevice(
            id = device.deviceName,
            name = usbName.ifBlank { "Fastboot ${device.vendorId.toString(16)}:${device.productId.toString(16)}" },
            vendorId = device.vendorId,
            productId = device.productId,
            hasPermission = usbManager.hasPermission(device),
        )
    }

    private suspend fun awaitPermission(device: UsbDevice) = suspendCancellableCoroutine<Unit> { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UsbPermissionAction) {
                    runCatching { appContext.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        val filter = IntentFilter(UsbPermissionAction)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        continuation.invokeOnCancellation {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(UsbPermissionAction).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private companion object {
        const val UsbPermissionAction = "com.sky22333.skyadb.fastboot.USB_PERMISSION"
    }
}
