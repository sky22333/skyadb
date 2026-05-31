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

import okio.sink
import okio.source
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.jvm.Throws


internal class DadbImpl @Throws(IllegalArgumentException::class) constructor(
        private val description: String,
        private val transportFactory: AdbTransportFactory,
        private val keyPair: AdbKeyPair? = null,
        private val features: Set<String> = Constants.CONNECT_FEATURES.toSet(),
) : Dadb {

    internal constructor(
        transportFactory: AdbTransportFactory,
        keyPair: AdbKeyPair? = null,
        features: Set<String> = Constants.CONNECT_FEATURES.toSet(),
    ) : this(
        description = transportFactory.description,
        transportFactory = transportFactory,
        keyPair = keyPair,
        features = features,
    )

    internal constructor(
        host: String,
        port: Int,
        keyPair: AdbKeyPair? = null,
        connectTimeout: Int = 0,
        socketTimeout: Int = 0,
        keepAlive: Boolean = false,
    ) : this(
        description = "$host:$port",
        transportFactory = SocketAdbTransportFactory(host, port, connectTimeout, socketTimeout, keepAlive),
        keyPair = keyPair,
    ) {
        if (port < 0) {
            throw IllegalArgumentException("port must be >= 0")
        }

        if (connectTimeout < 0) {
            throw IllegalArgumentException("connectTimeout must be >= 0")
        }

        if (socketTimeout < 0) {
            throw IllegalArgumentException("socketTimeout must be >= 0")
        }
    }

    private var connection: Pair<AdbConnection, AdbTransport>? = null

    override fun open(destination: String) = openWithRetry(destination)

    override fun supportsFeature(feature: String): Boolean {
        return connection().supportsFeature(feature)
    }

    override fun isTlsConnection(): Boolean {
        return connection().isTlsConnection()
    }

    override fun close() {
        connection?.first?.close()
        connection = null
    }
    override fun toString() = description

    fun closeConnection() {
        connection?.second?.close()
        connection = null
    }

    @Synchronized
    private fun connection(): AdbConnection {
        var connection = connection
        if (connection == null || connection.second.isClosed) {
            connection = newConnection()
            this.connection = connection
        }
        return connection.first
    }

    private fun newConnection(): Pair<AdbConnection, AdbTransport> {
        val transport = transportFactory.connect()
        val adbConnection = AdbConnection.connect(transport, keyPair, features)
        return adbConnection to transport
    }

    private fun openWithRetry(
        destination: String,
        retryOnStaleConnection: Boolean = true,
    ): AdbStream {
        val connection = connectionPair()
        return try {
            connection.first.open(destination)
        } catch (t: Throwable) {
            if (!retryOnStaleConnection || !isRecoverableOpenFailure(t)) {
                throw t
            }
            discardConnection(connection.first)
            openWithRetry(destination, retryOnStaleConnection = false)
        }
    }

    @Synchronized
    private fun connectionPair(): Pair<AdbConnection, AdbTransport> {
        var connection = connection
        if (connection == null || connection.second.isClosed) {
            connection = newConnection()
            this.connection = connection
        }
        return connection
    }

    @Synchronized
    private fun discardConnection(failedConnection: AdbConnection) {
        val current = connection
        if (current?.first === failedConnection) {
            runCatching { current.first.close() }
            connection = null
        } else {
            runCatching { failedConnection.close() }
        }
    }

    private fun isRecoverableOpenFailure(error: Throwable): Boolean {
        return when (error) {
            is EOFException -> true
            is IOException, is IllegalStateException -> {
                val message = error.message.orEmpty().lowercase()
                OPEN_FAILURE_MARKERS.any(message::contains) ||
                    error.cause?.let(::isRecoverableOpenFailure) == true
            }
            else -> error.cause?.let(::isRecoverableOpenFailure) == true
        }
    }

    private companion object {
        private val OPEN_FAILURE_MARKERS =
            listOf(
                "closed",
                "broken pipe",
                "connection reset",
                "connection abort",
                "unexpected end",
                "eof",
            )
    }

    private class SocketAdbTransportFactory(
        private val host: String,
        private val port: Int,
        private val connectTimeout: Int,
        private val socketTimeout: Int,
        private val keepAlive: Boolean,
    ) : AdbTransportFactory {
        override val description: String = "$host:$port"

        override fun connect(): AdbTransport {
            val socketAddress = InetSocketAddress(host, port)
            val socket = Socket()
            socket.soTimeout = socketTimeout
            socket.connect(socketAddress, connectTimeout)
            if (keepAlive) {
                socket.keepAlive = true
            }
            return SocketAdbTransport(socket, description)
        }
    }

    private class SocketAdbTransport(
        private val socket: Socket,
        override val description: String,
    ) : AdbTransport {
        override val source = socket.source()
        override val sink = socket.sink()
        override val isClosed: Boolean
            get() = socket.isClosed

        override fun close() {
            socket.close()
        }
    }

}
