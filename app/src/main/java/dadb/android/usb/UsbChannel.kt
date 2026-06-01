/*
 * Copyright (c) 2021 mobile.dev inc.
 * Android USB transport support by github.com/XRSec/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dadb.android.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

internal class UsbChannel(
    usbManager: UsbManager,
    private val usbDevice: UsbDevice,
    private val maxPacketSize: Int,
    private val writeTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val readIdleTimeoutMs: Int,
) : AutoCloseable {
    private companion object {
        private const val TAG = "DadbUsbChannel"
        private const val CMD_CNXN = 0x4e584e43
    }

    private val connection: UsbDeviceConnection =
        usbManager.openDevice(usbDevice)
            ?: throw IOException("Failed to open USB device: ${usbDevice.deviceName}")
    private val usbInterface: UsbInterface
    private val endpointIn: UsbEndpoint
    private val endpointOut: UsbEndpoint
    private val incomingChunks = LinkedBlockingQueue<ByteArray>()
    private val readerThread = Thread(::readMessages, "dadb-usb-reader")

    @Volatile
    private var closed = false

    @Volatile
    private var readError: Throwable? = null

    @Volatile
    private var handshakeComplete = false

    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0

    init {
        usbInterface = findAdbInterface()
            ?: throw IOException("ADB interface not found on device: ${usbDevice.deviceName}")

        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw IOException("Failed to claim USB interface")
        }

        val endpoints = findBulkEndpoints()
        endpointIn = endpoints.first ?: throw IOException("Bulk IN endpoint not found")
        endpointOut = endpoints.second ?: throw IOException("Bulk OUT endpoint not found")
        Log.d(
            TAG,
            "UsbChannel init device=${usbDevice.deviceName} in=${endpointIn.address}/${endpointIn.maxPacketSize} " +
                "out=${endpointOut.address}/${endpointOut.maxPacketSize}",
        )

        readerThread.start()
    }

    fun write(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data)
        while (buffer.remaining() > 0) {
            if (buffer.remaining() < AdbPacketCodec.HEADER_LENGTH) {
                throw IOException("Incomplete ADB packet header: remaining=${buffer.remaining()}")
            }

            val header = ByteArray(AdbPacketCodec.HEADER_LENGTH)
            buffer.get(header)
            val packetHeader = AdbPacketCodec.parseHeader(header)
            transferOut(header)

            if (buffer.remaining() < packetHeader.payloadLength) {
                throw IOException(
                    "Incomplete ADB packet payload: required=${packetHeader.payloadLength}, remaining=${buffer.remaining()}",
                )
            }

            if (packetHeader.payloadLength > 0) {
                val payload = ByteArray(packetHeader.payloadLength)
                buffer.get(payload)
                transferOut(payload)
            }
        }
    }

    fun readAtMost(size: Int): ByteArray {
        require(size > 0) { "size must be > 0" }

        while (true) {
            val chunk = currentChunk
            if (chunk != null && currentChunkOffset < chunk.size) {
                val copyLength = minOf(size, chunk.size - currentChunkOffset)
                val result = chunk.copyOfRange(currentChunkOffset, currentChunkOffset + copyLength)
                currentChunkOffset += copyLength
                if (currentChunkOffset >= chunk.size) {
                    currentChunk = null
                    currentChunkOffset = 0
                }
                return result
            }

            val nextChunk = incomingChunks.take()
            if (nextChunk.isEmpty()) {
                val error = readError
                if (error != null) {
                    throw IOException("USB read failed: ${error.message}", error)
                }
                throw IOException("USB channel closed: ${usbDevice.deviceName}")
            }

            currentChunk = nextChunk
            currentChunkOffset = 0
        }
    }

    fun flush() = Unit

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        Log.d(TAG, "UsbChannel close start device=${usbDevice.deviceName}")
        incomingChunks.offer(ByteArray(0))

        try {
            readerThread.interrupt()
        } catch (_: Exception) {
        }

        try {
            connection.releaseInterface(usbInterface)
        } catch (_: Exception) {
        }

        try {
            connection.close()
        } catch (_: Exception) {
        }
        Log.d(TAG, "UsbChannel close complete device=${usbDevice.deviceName}")
    }

    private fun findAdbInterface(): UsbInterface? {
        for (index in 0 until usbDevice.interfaceCount) {
            val usbInterface = usbDevice.getInterface(index)
            if (UsbConstants.isAdb(usbInterface)) {
                return usbInterface
            }
        }
        return null
    }

    private fun findBulkEndpoints(): Pair<UsbEndpoint?, UsbEndpoint?> {
        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null

        for (index in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(index)
            if (endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                when (endpoint.direction) {
                    android.hardware.usb.UsbConstants.USB_DIR_IN -> endpointIn = endpoint
                    android.hardware.usb.UsbConstants.USB_DIR_OUT -> endpointOut = endpoint
                }
            }
        }

        return endpointIn to endpointOut
    }

    private fun transferOut(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(maxPacketSize, bytes.size - offset)
            val transferred = connection.bulkTransfer(endpointOut, bytes, offset, chunkSize, writeTimeoutMs)
            if (transferred < 0) {
                throw IOException("USB bulk transfer failed: $transferred")
            }
            if (transferred == 0) {
                throw IOException("USB bulk transfer made no progress for ${usbDevice.deviceName}")
            }
            offset += transferred
        }
    }

    private fun readMessages() {
        try {
            while (!closed && !Thread.currentThread().isInterrupted) {
                val header = readExact(AdbPacketCodec.HEADER_LENGTH)
                val packetHeader = AdbPacketCodec.parseHeader(header)
                if (packetHeader.command == CMD_CNXN) {
                    handshakeComplete = true
                }
                incomingChunks.put(header)
                if (packetHeader.payloadLength > 0) {
                    incomingChunks.put(readExact(packetHeader.payloadLength))
                }
            }
        } catch (t: Throwable) {
            if (!closed) {
                readError = t
                Log.w(TAG, "UsbChannel reader stopped device=${usbDevice.deviceName}: ${t.javaClass.simpleName}: ${t.message}", t)
            }
        } finally {
            incomingChunks.offer(ByteArray(0))
        }
    }

    private fun readExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        var lastProgressAt = System.currentTimeMillis()

        while (offset < length) {
            if (closed || Thread.currentThread().isInterrupted) {
                throw IOException("USB channel closed while reading: ${usbDevice.deviceName}")
            }

            val transferred = connection.bulkTransfer(endpointIn, buffer, offset, length - offset, readTimeoutMs)
            if (transferred <= 0) {
                val idleMs = System.currentTimeMillis() - lastProgressAt
                val effectiveIdleTimeoutMs = if (handshakeComplete) 0 else readIdleTimeoutMs
                if (effectiveIdleTimeoutMs > 0 && idleMs >= effectiveIdleTimeoutMs) {
                    throw IOException("USB read timed out waiting for ADB data from ${usbDevice.deviceName}")
                }
                continue
            }

            offset += transferred
            lastProgressAt = System.currentTimeMillis()
        }

        return buffer
    }
}
