/*
 * Copyright (c) 2021 mobile.dev inc.
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

package dadb

import okio.*
import java.lang.Integer.min
import java.nio.ByteBuffer

interface AdbStream : AutoCloseable {

    val source: BufferedSource

    val sink: BufferedSink
}

internal class AdbStreamImpl internal constructor(
        private val messageQueue: AdbMessageQueue,
        private val adbWriter: AdbWriter,
        private val maxPayloadSize: Int,
        private val localId: Int,
        private val remoteId: Int,
        private val delayedAckEnabled: Boolean = false,
        private val initialAvailableSendBytes: Long = 0,
) : AdbStream {

    private var isClosed = false

    override val source = object : Source {

        private var message: AdbMessage? = null
        private var bytesRead = 0

        override fun read(sink: Buffer, byteCount: Long): Long {
            val message = message() ?: return -1

            val bytesRemaining = message.payloadLength - bytesRead
            val bytesToRead = byteCount
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtMost(bytesRemaining)

            sink.write(message.payload, bytesRead, bytesToRead)

            bytesRead += bytesToRead

            if (delayedAckEnabled && bytesToRead > 0) {
                adbWriter.writeOkay(localId, remoteId, bytesToRead)
            }

            check(bytesRead <= message.payloadLength)

            if (bytesRead == message.payloadLength) {
                this.message = null
                if (!delayedAckEnabled) {
                    adbWriter.writeOkay(localId, remoteId)
                }
            }

            return bytesToRead.toLong()
        }

        private fun message(): AdbMessage? {
            message?.let { return it }
            val nextMessage = nextMessage(Constants.CMD_WRTE)
            message = nextMessage
            bytesRead = 0
            return nextMessage
        }

        override fun close() {}

        override fun timeout() = Timeout.NONE
    }.buffer()

    override val sink = object : Sink {

        private val buffer = ByteBuffer.allocate(maxPayloadSize)
        private var availableSendBytes = initialAvailableSendBytes

        override fun write(source: Buffer, byteCount: Long) {
            var remainingBytes = byteCount
            while (true) {
                remainingBytes -= writeToBuffer(source, remainingBytes)
                if (remainingBytes == 0L) return
                check(remainingBytes > 0L)
            }
        }

        private fun writeToBuffer(source: BufferedSource, byteCount: Long): Int {
            val bytesToWrite = min(buffer.remaining(), byteCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val bytesWritten = source.read(buffer.array(), buffer.position(), bytesToWrite)
            if (bytesWritten < 0) throw java.io.EOFException("EOF while writing ADB stream")

            // Cast to prevent NoSuchMethodError when mixing Java versions
            // Learn more: https://www.morling.dev/blog/bytebuffer-and-the-dreaded-nosuchmethoderror
            (buffer as java.nio.Buffer).position(buffer.position() + bytesWritten)
            if (buffer.remaining() == 0) flush()

            return bytesWritten
        }

        override fun flush() {
            val payloadLength = buffer.position()
            if (payloadLength == 0) return
            if (delayedAckEnabled) {
                var offset = 0
                while (offset < payloadLength) {
                    while (availableSendBytes <= 0) {
                        availableSendBytes += awaitAckBytes().toLong()
                    }
                    val chunkLength = min(
                        payloadLength - offset,
                        availableSendBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
                    adbWriter.writeWrite(localId, remoteId, buffer.array(), offset, chunkLength)
                    availableSendBytes -= chunkLength.toLong()
                    offset += chunkLength
                }
            } else {
                adbWriter.writeWrite(localId, remoteId, buffer.array(), 0, payloadLength)
            }

            // Cast to prevent NoSuchMethodError when mixing Java versions
            // Learn more: https://www.morling.dev/blog/bytebuffer-and-the-dreaded-nosuchmethoderror
            (buffer as java.nio.Buffer).clear()
            if (!delayedAckEnabled && nextMessage(Constants.CMD_OKAY) == null) {
                throw IOException("ADB stream closed before OKAY for localId: ${localId.toString(16)}")
            }
        }

        override fun close() {}

        override fun timeout() = Timeout.NONE
    }.buffer()

    private fun awaitAckBytes(): Int {
        val message = nextMessage(Constants.CMD_OKAY)
            ?: throw IOException("ADB stream closed before delayed ACK for localId: ${localId.toString(16)}")
        val ackBytes = Constants.decodeOkayAckBytes(message, delayedAckEnabled = true)
        require(ackBytes >= 0) { "Delayed ACK bytes must be >= 0: $ackBytes" }
        return ackBytes
    }

    private fun nextMessage(command: Int): AdbMessage? {
        return try {
            messageQueue.take(localId, command)
        } catch (e: IOException) {
            close(sendClose = false)
            return null
        }
    }

    override fun close() {
        close(sendClose = true)
    }

    private fun close(sendClose: Boolean) {
        if (isClosed) return
        isClosed = true
        try {
            if (sendClose) {
                adbWriter.writeClose(localId, remoteId)
            }
        } finally {
            messageQueue.stopListening(localId)
        }
    }
}
