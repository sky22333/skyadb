package com.sky22333.skyadb.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.Build
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import io.github.rhythmcache.fastboot.FastbootTransport
import java.net.SocketTimeoutException

/** USB bulk transport preserving response packet lengths. */
internal class FastbootUsbChannel(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val isPresent: () -> Boolean,
) : FastbootTransport {
    var checkActive: () -> Unit = {}
    var deadlineNanos: Long = Long.MAX_VALUE
    private val closed = AtomicBoolean()
    private val input = endpoint(UsbConstants.USB_DIR_IN)
    private val output = endpoint(UsbConstants.USB_DIR_OUT)

    init {
        check(connection.claimInterface(usbInterface, true)) {
            "Cannot claim Fastboot USB interface"
        }
    }

    private fun endpoint(direction: Int): UsbEndpoint =
        (0 until usbInterface.endpointCount)
            .asSequence()
            .map(usbInterface::getEndpoint)
            .firstOrNull {
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == direction
            } ?: error("Missing Fastboot bulk endpoint")

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        checkActive()
        check(!closed.get()) { "Fastboot USB closed" }
        var sent = 0
        while (sent < length) {
            checkActive()
            val limit = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) 16 * 1024 else 256 * 1024
            val amount = minOf(length - sent, limit)
            val written = connection.bulkTransfer(output, buffer, offset + sent, amount, 10_000)
            if (written != amount) throw IOException("Fastboot USB short write: $written/$amount")
            sent += written
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val deadline = System.nanoTime() + 300_000_000_000L
        while (true) {
            checkActive()
            if (System.nanoTime() >= minOf(deadline, deadlineNanos)) throw SocketTimeoutException("Fastboot USB read timed out")
            check(!closed.get()) { "Fastboot USB closed" }
            val count = connection.bulkTransfer(input, buffer, offset, length, 250)
            if (count > 0) return count
            if (!isPresent()) throw IOException("Fastboot USB disconnected")
            if (System.nanoTime() >= minOf(deadline, deadlineNanos)) throw SocketTimeoutException("Fastboot USB read timed out")
            if (count < 0) Thread.sleep(10)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { connection.releaseInterface(usbInterface) }
            connection.close()
        }
    }
}
