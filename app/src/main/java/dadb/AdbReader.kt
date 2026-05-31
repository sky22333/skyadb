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

import okio.Source
import okio.buffer
import java.io.IOException

internal class AdbReader(
    source: Source,
    maxPayloadSize: Int = Constants.CONNECT_MAXDATA,
) : AutoCloseable {

    private val bufferedSource = source.buffer()
    @Volatile
    private var maxPayloadSize = maxPayloadSize.coerceAtLeast(0).coerceAtMost(Constants.CONNECT_MAXDATA)

    fun setMaxPayloadSize(maxPayloadSize: Int) {
        this.maxPayloadSize = maxPayloadSize.coerceAtLeast(0).coerceAtMost(Constants.CONNECT_MAXDATA)
    }

    fun readMessage(): AdbMessage {
        synchronized(bufferedSource) {
            bufferedSource.apply {
                val command = readIntLe()
                val arg0 = readIntLe()
                val arg1 = readIntLe()
                val payloadLength = readIntLe()
                if (payloadLength < 0 || payloadLength > maxPayloadSize) {
                    throw IOException("Invalid ADB payload length: $payloadLength (max=$maxPayloadSize)")
                }
                val checksum = readIntLe()
                val magic = readIntLe()
                val expectedMagic = command.inv()
                if (magic != expectedMagic) {
                    throw IOException("Invalid ADB magic: expected=$expectedMagic actual=$magic command=$command")
                }
                val payload = readByteArray(payloadLength.toLong())
                return AdbMessage(command, arg0, arg1, payloadLength, checksum, magic, payload).also {
                    log { "(${Thread.currentThread().name}) < $it" }
                }
            }
        }
    }

    override fun close() {
        bufferedSource.close()
    }
}
