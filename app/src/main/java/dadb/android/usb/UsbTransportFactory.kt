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
import android.hardware.usb.UsbManager
import dadb.AdbTransport
import dadb.AdbTransportFactory
import dadb.SourceSinkAdbTransport
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout

/**
 * Android USB host transport for direct ADB connections.
 *
 * This implementation depends on the Android USB host APIs and is not available on
 * desktop JVM runtimes such as Windows, macOS, or Linux.
 */
class UsbTransportFactory(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice,
    override val description: String = usbDevice.deviceName,
    private val connectMaxData: Int = UsbConstants.CONNECT_MAX_DATA,
    private val maxPacketSize: Int = UsbConstants.MAX_PACKET_SIZE,
    private val writeTimeoutMs: Int = UsbConstants.WRITE_TIMEOUT_MS,
    private val readTimeoutMs: Int = UsbConstants.READ_TIMEOUT_MS,
    private val readIdleTimeoutMs: Int = UsbConstants.READ_IDLE_TIMEOUT_MS,
) : AdbTransportFactory {

    override fun connect(): AdbTransport {
        val channel = UsbChannel(
            usbManager = usbManager,
            usbDevice = usbDevice,
            maxPacketSize = maxPacketSize,
            writeTimeoutMs = writeTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            readIdleTimeoutMs = readIdleTimeoutMs,
        )
        return SourceSinkAdbTransport(
            source = UsbSource(channel, maxPacketSize),
            sink = UsbSink(channel),
            description = description,
            connectMaxData = connectMaxData,
            closeable = channel,
        )
    }
}

private class UsbSource(
    private val channel: UsbChannel,
    private val maxPacketSize: Int,
) : Source {

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) {
            return 0L
        }

        val chunk = channel.readAtMost(minOf(byteCount, maxPacketSize.toLong()).toInt())
        sink.write(chunk)
        return chunk.size.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private class UsbSink(
    private val channel: UsbChannel,
) : Sink {

    private val pending = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        require(byteCount <= Int.MAX_VALUE) { "byteCount too large: $byteCount" }
        pending.write(source, byteCount)
        emitCompletePackets()
    }

    override fun flush() {
        emitCompletePackets()
        channel.flush()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit

    private fun emitCompletePackets() {
        while (true) {
            val packetLength = AdbPacketCodec.completePacketLength(pending) ?: return
            if (pending.size < packetLength) {
                return
            }

            channel.write(pending.readByteArray(packetLength))
        }
    }
}
