package com.sky22333.skyadb.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface

enum class UsbOtgMode {
    Adb,
    Fastboot,
}

object AndroidUsbInterface {
    private const val ClassVendorSpecific = 0xFF
    private const val SubclassAndroid = 0x42
    private const val ProtocolAdb = 0x01
    private const val ProtocolFastboot = 0x03

    fun modeOf(device: UsbDevice): UsbOtgMode? {
        return when {
            findInterface(device, ProtocolAdb) != null -> UsbOtgMode.Adb
            findInterface(device, ProtocolFastboot) != null -> UsbOtgMode.Fastboot
            else -> null
        }
    }

    fun findAdbInterface(device: UsbDevice): UsbInterface? = findInterface(device, ProtocolAdb)

    fun findFastbootInterface(device: UsbDevice): UsbInterface? = findInterface(device, ProtocolFastboot)

    fun isAndroidDebugDevice(device: UsbDevice): Boolean = modeOf(device) != null

    fun isAndroidDebugInterface(
        interfaceClass: Int,
        interfaceSubclass: Int,
        interfaceProtocol: Int,
    ): UsbOtgMode? {
        if (interfaceClass != ClassVendorSpecific || interfaceSubclass != SubclassAndroid) return null
        return when (interfaceProtocol) {
            ProtocolAdb -> UsbOtgMode.Adb
            ProtocolFastboot -> UsbOtgMode.Fastboot
            else -> null
        }
    }

    private fun findInterface(device: UsbDevice, protocol: Int): UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            val mode = isAndroidDebugInterface(
                usbInterface.interfaceClass,
                usbInterface.interfaceSubclass,
                usbInterface.interfaceProtocol,
            ) ?: continue
            if ((protocol == ProtocolAdb && mode == UsbOtgMode.Adb) ||
                (protocol == ProtocolFastboot && mode == UsbOtgMode.Fastboot)
            ) {
                return usbInterface
            }
        }
        return null
    }
}
