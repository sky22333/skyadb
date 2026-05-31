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

import okio.Buffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object AdbPacketCodec {

    const val HEADER_LENGTH = 24

    private const val CMD_AUTH = 0x48545541
    private const val CMD_CNXN = 0x4e584e43
    private const val CMD_OPEN = 0x4e45504f
    private const val CMD_OKAY = 0x59414b4f
    private const val CMD_CLSE = 0x45534c43
    private const val CMD_WRTE = 0x45545257
    private const val MAX_PAYLOAD_LENGTH = 1024 * 1024

    private val validCommands = setOf(
        CMD_AUTH,
        CMD_CNXN,
        CMD_OPEN,
        CMD_OKAY,
        CMD_CLSE,
        CMD_WRTE,
    )

    data class Header(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payloadLength: Int,
        val checksum: Int,
        val magic: Int,
    )

    @Throws(IOException::class)
    fun parseHeader(headerBytes: ByteArray): Header {
        require(headerBytes.size == HEADER_LENGTH) {
            "ADB header must be $HEADER_LENGTH bytes, was ${headerBytes.size}"
        }

        val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val packetHeader = Header(
            command = header.getInt(),
            arg0 = header.getInt(),
            arg1 = header.getInt(),
            payloadLength = header.getInt(),
            checksum = header.getInt(),
            magic = header.getInt(),
        )

        validateHeader(packetHeader)
        return packetHeader
    }

    @Throws(IOException::class)
    fun completePacketLength(buffer: Buffer): Long? {
        if (buffer.size < HEADER_LENGTH) {
            return null
        }

        val headerBytes = buffer.copy().readByteArray(HEADER_LENGTH.toLong())
        val header = parseHeader(headerBytes)
        val packetLength = HEADER_LENGTH + header.payloadLength.toLong()
        return if (buffer.size >= packetLength) packetLength else null
    }

    @Throws(IOException::class)
    private fun validateHeader(header: Header) {
        if (header.command !in validCommands) {
            throw IOException("Unknown ADB command: 0x${header.command.toUInt().toString(16)}")
        }
        if (header.magic != (header.command xor -0x1)) {
            throw IOException("Invalid ADB magic for command 0x${header.command.toUInt().toString(16)}: ${header.magic}")
        }
        if (header.payloadLength < 0 || header.payloadLength > MAX_PAYLOAD_LENGTH) {
            throw IOException("Invalid ADB payload length: ${header.payloadLength}")
        }
    }
}
