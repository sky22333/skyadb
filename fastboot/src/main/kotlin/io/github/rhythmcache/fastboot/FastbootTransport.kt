package io.github.rhythmcache.fastboot

import java.io.Closeable

interface FastbootTransport : Closeable {
    /** Writes exactly [length] bytes from [buffer] starting at [offset]. */
    fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    )

    /** Reads up to [length] bytes into [buffer] starting at [offset]. Returns actual bytes read. */
    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    /** Resets the transport connection. Default is a no-op. */
    fun reset() {}

    /**
     * Blocks until the transport disconnects.
     * Transports that don't support this return immediately.
     * Default is a no-op.
     */
    fun waitForDisconnect() {}

    override fun close()
}
