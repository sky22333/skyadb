package io.github.rhythmcache.fastboot.sparse

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Positional (pread-style) read access to an image. Implementations must be stateless with respect
 * to position so any piece can be re-read after a failed transfer.
 */
interface RandomAccessSource {
    /** Total size in bytes. */
    val size: Long

    /** Reads up to [length] bytes at [position]; returns the count read, or -1 at EOF. */
    fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

/** Reads exactly [length] bytes at [position] or throws [EOFException]. */
fun RandomAccessSource.readFully(
    position: Long,
    dest: ByteArray,
    offset: Int,
    length: Int,
) {
    var done = 0
    while (done < length) {
        val n = read(position + done, dest, offset + done, length - done)
        if (n <= 0) {
            throw EOFException("Unexpected EOF at ${position + done} (wanted $length bytes at $position)")
        }
        done += n
    }
}

class ByteArraySource(private val bytes: ByteArray) : RandomAccessSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position >= bytes.size) return -1
        val n = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), dest, offset, n)
        return n
    }
}

/**
 * Positional reads over a [FileChannel]. [size] is passed in because some document providers report
 * a bogus channel size; the caller already resolved the real one.
 */
class FileChannelSource(
    private val channel: FileChannel,
    override val size: Long,
) : RandomAccessSource {
    override fun read(
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position >= size) return -1
        val want = minOf(length.toLong(), size - position).toInt()
        return channel.read(ByteBuffer.wrap(dest, offset, want), position)
    }

    companion object {
        /** Throws [IOException] if [channel] cannot do positional reads (pipes, sockets). */
        fun requireSeekable(channel: FileChannel) {
            channel.position() // "Illegal seek" on non-seekable descriptors
        }
    }
}
