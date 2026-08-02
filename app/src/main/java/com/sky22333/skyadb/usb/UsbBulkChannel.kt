package com.sky22333.skyadb.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import java.io.Closeable
import java.io.IOException

class UsbBulkChannel(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val ioTimeoutMs: Int,
) : Closeable {
    val endpointIn: UsbEndpoint
    val endpointOut: UsbEndpoint
    val pollTimeoutMs: Int = ioTimeoutMs.coerceIn(MinPollTimeoutMs, MaxPollTimeoutMs)

    init {
        if (!connection.claimInterface(usbInterface, true)) {
            throw IOException(appString(R.string.usb_claim_interface_failed))
        }
        var input: UsbEndpoint? = null
        var output: UsbEndpoint? = null
        for (index in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(index)
            if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                input = endpoint
            } else {
                output = endpoint
            }
        }
        if (input == null || output == null) {
            connection.releaseInterface(usbInterface)
            throw IOException(appString(R.string.usb_bulk_endpoint_not_found))
        }
        endpointIn = input
        endpointOut = output
    }

    /** `null` 表示超时，可继续轮询。 */
    fun readChunkOrTimeout(buffer: ByteArray): Int? {
        val transferred = connection.bulkTransfer(
            endpointIn,
            buffer,
            buffer.size,
            pollTimeoutMs,
        )
        return if (transferred < 0) null else transferred
    }

    fun writeChunk(source: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val chunk = minOf(length - written, MaxChunkBytes)
            val transferred = connection.bulkTransfer(
                endpointOut,
                source,
                offset + written,
                chunk,
                ioTimeoutMs,
            )
            if (transferred <= 0) {
                throw IOException(appString(R.string.usb_write_failed, transferred))
            }
            written += transferred
        }
    }

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
    }

    private companion object {
        const val MaxChunkBytes = 16 * 1024
        const val MinPollTimeoutMs = 250
        const val MaxPollTimeoutMs = 1_000
    }
}
