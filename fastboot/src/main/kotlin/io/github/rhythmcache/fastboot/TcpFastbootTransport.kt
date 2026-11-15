package io.github.rhythmcache.fastboot

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Transport for Fastboot over TCP (`fastboot -s tcp:<host>[:<port>]`),
 * fully compatible with the official AOSP fastboot TCP protocol specification.
 *
 * Wire protocol:
 * 1. Initial handshake: Client sends "FB01" (4 bytes ASCII). Device responds with "FBxx"
 *    where `xx` is a 2-digit protocol version (must be >= 01).
 * 2. Message framing: Every message sent or received is prefixed by an 8-byte big-endian
 *    64-bit integer length header, followed by the message payload bytes.
 */
class TcpFastbootTransport(
    val socket: Socket,
    private val socketTimeoutMs: Int = 0,
    private val handshakeTimeoutMs: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS,
    performHandshake: Boolean = true,
) : FastbootTransport {

    private val inputStream: InputStream = socket.getInputStream()
    private val outputStream: OutputStream = socket.getOutputStream()

    private val writeLock = Any()
    private val readLock = Any()

    private val headerBuf = ByteArray(8)
    private var messageBytesLeft = 0L

    private val closed = AtomicBoolean(false)

    init {
        if (performHandshake) {
            handshake()
        }
    }

    private fun handshake() {
        try {
            socket.soTimeout = handshakeTimeoutMs

            // 1. Send client handshake message "FB01"
            val handshakeBytes = HANDSHAKE_MESSAGE.toByteArray(Charsets.US_ASCII)
            try {
                outputStream.write(handshakeBytes)
                outputStream.flush()
            } catch (e: Exception) {
                throw FastbootException.Io("Failed to send initialization message: ${e.message}", e)
            }

            // 2. Receive 4-byte response
            val response = ByteArray(HANDSHAKE_LENGTH)
            var totalRead = 0
            while (totalRead < HANDSHAKE_LENGTH) {
                val n = try {
                    inputStream.read(response, totalRead, HANDSHAKE_LENGTH - totalRead)
                } catch (e: SocketTimeoutException) {
                    throw FastbootException.Io(
                        "No initialization message received (timeout). Target may not support TCP fastboot",
                        e,
                    )
                } catch (e: Exception) {
                    throw FastbootException.Io("Failed to receive initialization message: ${e.message}", e)
                }
                if (n < 0) {
                    throw FastbootException.Io(
                        "No initialization message received (EOF). Target may not support TCP fastboot",
                    )
                }
                totalRead += n
            }

            // 3. Verify magic "FB"
            if (response[0] != 'F'.code.toByte() || response[1] != 'B'.code.toByte()) {
                val receivedStr = response.decodeToString(0, HANDSHAKE_LENGTH)
                throw FastbootException.Protocol(
                    "Unrecognized initialization message '$receivedStr'. Target may not support TCP fastboot",
                )
            }

            // 4. Verify version >= 1
            val versionStr = response.decodeToString(2, 4)
            val version = versionStr.toIntOrNull()
                ?: throw FastbootException.Protocol("Unknown TCP protocol version $versionStr (host version 01)")
            if (version < PROTOCOL_VERSION) {
                val hostVer = "%02d".format(Locale.US, PROTOCOL_VERSION)
                throw FastbootException.Protocol("Unknown TCP protocol version $versionStr (host version $hostVer)")
            }
        } catch (e: FastbootException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            throw FastbootException.Io("TCP handshake failed: ${e.message}", e)
        } finally {
            try {
                if (!closed.get() && socket.soTimeout != socketTimeoutMs) {
                    socket.soTimeout = socketTimeoutMs
                }
            } catch (_: Exception) {}
        }
    }

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (closed.get() || socket.isClosed) {
            throw FastbootException.Io("Cannot write to closed TCP transport")
        }
        if (offset < 0 || length < 0 || length > buffer.size - offset) {
            throw FastbootException.BadArgument("Invalid buffer slice: offset=$offset length=$length size=${buffer.size}")
        }

        synchronized(writeLock) {
            try {
                // For small writes (like commands), coalesce header and payload into a single packet
                if (length <= 4096) {
                    val combined = ByteArray(8 + length)
                    encodeLength(length.toLong(), combined, 0)
                    System.arraycopy(buffer, offset, combined, 8, length)
                    outputStream.write(combined)
                } else {
                    val header = ByteArray(8)
                    encodeLength(length.toLong(), header, 0)
                    outputStream.write(header)
                    outputStream.write(buffer, offset, length)
                }
                outputStream.flush()
            } catch (e: Exception) {
                close()
                throw FastbootException.Io("TCP write failed: ${e.message}", e)
            }
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (closed.get() || socket.isClosed) return -1
        if (length <= 0) return 0

        synchronized(readLock) {
            try {
                // If previous message was fully read, fetch next message length header
                while (messageBytesLeft == 0L) {
                    var headerRead = 0
                    while (headerRead < 8) {
                        val n = inputStream.read(headerBuf, headerRead, 8 - headerRead)
                        if (n < 0) {
                            close()
                            return -1
                        }
                        headerRead += n
                    }
                    val extracted = extractLength(headerBuf, 0)
                    if (extracted < 0L) {
                        close()
                        throw FastbootException.Protocol("Invalid negative message length in TCP header: $extracted")
                    }
                    messageBytesLeft = extracted
                }

                // Read up to min(length, messageBytesLeft)
                val toRead = minOf(length.toLong(), messageBytesLeft).toInt()
                var totalRead = 0
                while (totalRead < toRead) {
                    val n = inputStream.read(buffer, offset + totalRead, toRead - totalRead)
                    if (n < 0) {
                        close()
                        if (totalRead == 0) return -1
                        break
                    }
                    totalRead += n
                }

                messageBytesLeft -= totalRead
                return totalRead
            } catch (e: SocketException) {
                close()
                if (closed.get()) return -1
                throw FastbootException.Io("TCP read failed: ${e.message}", e)
            } catch (e: SocketTimeoutException) {
                // A timeout mid-frame desyncs the stream framing, so teardown is fatal (matching AOSP tcp.cpp)
                close()
                throw FastbootException.Timeout("TCP read timed out: ${e.message}")
            } catch (e: FastbootException) {
                close()
                throw e
            } catch (e: Exception) {
                close()
                throw FastbootException.Io("TCP read failed: ${e.message}", e)
            }
        }
    }

    override fun waitForDisconnect() {
        if (closed.get() || socket.isClosed) return
        synchronized(readLock) {
            try {
                val buf = ByteArray(1)
                while (!closed.get() && !socket.isClosed) {
                    val n = inputStream.read(buf)
                    if (n < 0) break
                }
            } catch (_: Exception) {
                // Expected on peer disconnect or reboot
            } finally {
                close()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            socket.close()
        } catch (_: Exception) {}
    }

    companion object {
        const val DEFAULT_PORT = 5554
        const val PROTOCOL_VERSION = 1
        const val HANDSHAKE_LENGTH = 4
        const val HANDSHAKE_MESSAGE = "FB01"
        const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 2000

        internal fun encodeLength(length: Long, out: ByteArray, offset: Int = 0) {
            for (i in 0..7) {
                out[offset + i] = ((length ushr (56 - i * 8)) and 0xFF).toByte()
            }
        }

        internal fun extractLength(buffer: ByteArray, offset: Int = 0): Long {
            var ret = 0L
            for (i in 0..7) {
                ret = (ret shl 8) or (buffer[offset + i].toLong() and 0xFFL)
            }
            return ret
        }

        /**
         * Connects to a Fastboot over TCP daemon at [host]:[port].
         *
         * @param host Device IP address or hostname.
         * @param port TCP port (defaults to official fastboot port 5554).
         * @param connectTimeoutMs Socket connection timeout in milliseconds.
         * @param handshakeTimeoutMs Timeout for handshake exchange in milliseconds.
         * @param socketTimeoutMs Socket SO_TIMEOUT in milliseconds (0 for infinite/blocking).
         */
        fun connect(
            host: String,
            port: Int = DEFAULT_PORT,
            connectTimeoutMs: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS,
            handshakeTimeoutMs: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS,
            socketTimeoutMs: Int = 0,
        ): TcpFastbootTransport {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                return TcpFastbootTransport(
                    socket = socket,
                    socketTimeoutMs = socketTimeoutMs,
                    handshakeTimeoutMs = handshakeTimeoutMs,
                    performHandshake = true,
                )
            } catch (e: FastbootException) {
                try { socket.close() } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) {}
                throw FastbootException.Io("Failed to connect to TCP fastboot at $host:$port: ${e.message}", e)
            }
        }
    }
}
