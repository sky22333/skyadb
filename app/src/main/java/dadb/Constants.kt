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

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object Constants {

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    const val CMD_AUTH = 0x48545541
    const val CMD_CNXN = 0x4e584e43
    const val CMD_STLS = 0x534c5453
    const val CMD_OPEN = 0x4e45504f
    const val CMD_OKAY = 0x59414b4f
    const val CMD_CLSE = 0x45534c43
    const val CMD_WRTE = 0x45545257

    const val A_VERSION_MIN = 0x01000000
    const val A_VERSION_SKIP_CHECKSUM = 0x01000001
    const val CONNECT_VERSION = A_VERSION_SKIP_CHECKSUM
    const val MAX_PAYLOAD_V1 = 4 * 1024
    const val CONNECT_MAXDATA = 1024 * 1024
    const val INITIAL_DELAYED_ACK_BYTES = 32 * 1024 * 1024
    const val STLS_VERSION = 0x01000000

    val CONNECT_FEATURES = listOf(
        "shell_v2",
        "cmd",
        "abb_exec",
        FEATURE_DELAYED_ACK,
    )

    const val FEATURE_DELAYED_ACK = "delayed_ack"

    val CONNECT_PAYLOAD = "host::features=${CONNECT_FEATURES.joinToString(",")}".toByteArray().also {
        require(it.size <= MAX_PAYLOAD_V1) {
            "ADB connect banner is too long: ${it.size} > $MAX_PAYLOAD_V1"
        }
    }

    fun decodeOkayAckBytes(message: AdbMessage, delayedAckEnabled: Boolean): Int {
        require(message.command == CMD_OKAY) { "Expected OKAY message, got ${message.command}" }
        return when (message.payloadLength) {
            0 -> {
                if (delayedAckEnabled) {
                    throw java.io.IOException("Delayed ACK stream missing OKAY payload for localId: ${message.arg1.toString(16)}")
                }
                0
            }

            Int.SIZE_BYTES -> {
                if (!delayedAckEnabled) {
                    throw java.io.IOException("Unexpected OKAY payload for non-delayed-ack stream: ${message.payloadLength}")
                }
                ByteBuffer.wrap(message.payload, 0, Int.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
            }

            else -> throw java.io.IOException("Invalid OKAY payload size: ${message.payloadLength}")
        }
    }
}
