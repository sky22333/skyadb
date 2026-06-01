package com.sky22333.skyadb.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sky22333.skyadb.repository.AdbRepository
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.android.usb.UsbConstants
import dadb.android.usb.UsbTransportFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidUsbAdbRepository(
    context: Context,
    private val adbRepository: AdbRepository,
) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val keyDir = File(appContext.filesDir, "adb_keys")
    private var activeDeviceId: String? = null

    init {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
                    val detachedDevice = intent.usbDeviceExtra() ?: return
                    if (detachedDevice.deviceName == activeDeviceId) {
                        activeDeviceId = null
                        adbRepository.disconnect()
                    }
            }
        }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun listDevices(): List<UsbAdbDevice> {
        return usbManager.deviceList.values
            .filter(UsbConstants::isAdb)
            .map(::toModel)
            .sortedBy { it.name }
    }

    suspend fun connect(deviceId: String): UsbAdbConnectResult = withContext(Dispatchers.IO) {
        val device = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceId }
            ?: return@withContext UsbAdbConnectResult.Failed(
                message = "未找到 USB ADB 设备",
                suggestion = "请确认设备仍然通过 USB 连接，并已开启 USB 调试。",
            )

        if (!usbManager.hasPermission(device) && !requestPermission(device)) {
            return@withContext UsbAdbConnectResult.Failed(
                message = "USB 权限未授予",
                suggestion = "请在系统授权弹窗中允许 sky adb 访问此 USB 设备。",
            )
        }

        runCatching {
            val dadb = createDadb(device)
            when (val result = adbRepository.connectUsb(dadb, "usb:${device.deviceName}", toModel(device).name)) {
                is com.sky22333.skyadb.model.AdbOperationResult.Success ->
                    UsbAdbConnectResult.Connected(result.data).also {
                        activeDeviceId = device.deviceName
                    }
                is com.sky22333.skyadb.model.AdbOperationResult.Failure ->
                    UsbAdbConnectResult.Failed(result.message, result.suggestion, result.cause)
            }
        }.getOrElse { error ->
            UsbAdbConnectResult.Failed(
                message = "USB ADB 连接失败",
                suggestion = "请确认目标设备已允许 USB 调试；如果刚授权，请重新点击连接。",
                cause = error,
            )
        }
    }

    private fun createDadb(device: UsbDevice): Dadb {
        val keyPair = loadOrCreateKeyPair()
        return Dadb.create(
            UsbTransportFactory(
                usbManager = usbManager,
                usbDevice = device,
                description = "usb:${device.deviceName}",
            ),
            keyPair,
        )
    }

    private fun loadOrCreateKeyPair(): AdbKeyPair {
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey, publicKeyOwner = "skyadb@android")
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action != UsbPermissionAction) return
                        val grantedDevice = intent.usbDeviceExtra()
                        if (grantedDevice?.deviceName != device.deviceName) return
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        runCatching { appContext.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(granted)
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

            val intent = PendingIntent.getBroadcast(
                appContext,
                device.deviceId,
                Intent(UsbPermissionAction).setPackage(appContext.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            usbManager.requestPermission(device, intent)
        }

    private fun toModel(device: UsbDevice): UsbAdbDevice {
        val productName = runCatching { device.productName }.getOrNull()
        val manufacturer = runCatching { device.manufacturerName }.getOrNull()
        val name = listOfNotNull(manufacturer, productName)
            .joinToString(" ")
            .ifBlank { device.deviceName }
        return UsbAdbDevice(
            id = device.deviceName,
            name = name,
            vendorId = device.vendorId,
            productId = device.productId,
            hasPermission = usbManager.hasPermission(device),
        )
    }

    private companion object {
        const val UsbPermissionAction = "com.sky22333.skyadb.USB_ADB_PERMISSION"
    }
}

private fun Intent.usbDeviceExtra(): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }
}
