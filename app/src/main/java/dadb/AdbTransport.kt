/*
 * Copyright (c) 2021 mobile.dev inc.
 * Additional transport support by github.com/XRSec/
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

import okio.Sink
import okio.Source
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A low-level transport carrying raw ADB packets.
 *
 * Socket is the default implementation, but callers can provide transports backed by
 * platform-specific APIs, tunnels, in-process bridges, or any other bidirectional channel.
 */
interface AdbTransport : AutoCloseable {

    val source: Source

    val sink: Sink

    val connectMaxData: Int
        get() = Constants.CONNECT_MAXDATA

    val isClosed: Boolean

    val description: String
        get() = javaClass.name
}

/**
 * A transport that can upgrade the current raw socket/channel to TLS in-place after `A_STLS`.
 *
 * Wireless Debugging on modern Android starts as plain ADB, negotiates `A_STLS`, then upgrades
 * the same underlying connection to TLS before continuing with normal ADB auth/CNXN messages.
 */
interface TlsUpgradableAdbTransport : AdbTransport {
    @Throws(IOException::class)
    fun upgradeToTls(version: Int = Constants.STLS_VERSION)
}

fun interface AdbTransportFactory {

    val description: String
        get() = javaClass.name

    @Throws(IOException::class)
    fun connect(): AdbTransport
}

/**
 * A transport backed by an existing okio [Source] and [Sink].
 */
class SourceSinkAdbTransport(
    override val source: Source,
    override val sink: Sink,
    override val description: String,
    override val connectMaxData: Int = Constants.CONNECT_MAXDATA,
    private val closeable: AutoCloseable? = null,
) : AdbTransport {
    private val closed = AtomicBoolean(false)

    override val isClosed: Boolean
        get() = closed.get()

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        if (closeable != null) {
            closeable.close()
            return
        }
        sink.close()
        source.close()
    }
}
