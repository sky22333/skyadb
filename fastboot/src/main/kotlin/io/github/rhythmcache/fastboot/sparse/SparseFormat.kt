package io.github.rhythmcache.fastboot.sparse

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thrown when a file claims to be an Android sparse image but is malformed or unsupported. */
class SparseFormatException(message: String) : IOException(message)

/**
 * Constants and (de)serialisation helpers for the Android sparse image format
 * (libsparse `sparse_format.h`). All multi-byte fields are little-endian.
 *
 * Sizes that can exceed 2 GiB are `Long`; header fields that are u32 on the wire are
 * read into `Long` so values >= 0x80000000 stay positive.
 */
object SparseFormat {
    const val MAGIC: Int = 0xED26FF3A.toInt()
    const val MAJOR_VERSION = 1
    const val MINOR_VERSION = 0
    const val FILE_HEADER_SIZE = 28
    const val CHUNK_HEADER_SIZE = 12
    const val DEFAULT_BLOCK_SIZE = 4096

    const val CHUNK_RAW = 0xCAC1
    const val CHUNK_FILL = 0xCAC2
    const val CHUNK_DONT_CARE = 0xCAC3
    const val CHUNK_CRC32 = 0xCAC4

    internal fun fileHeader(
        blockSize: Int,
        totalBlocks: Long,
        totalChunks: Long,
    ): ByteArray {
        val b = ByteBuffer.allocate(FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(MAGIC)
        b.putShort(MAJOR_VERSION.toShort())
        b.putShort(MINOR_VERSION.toShort())
        b.putShort(FILE_HEADER_SIZE.toShort())
        b.putShort(CHUNK_HEADER_SIZE.toShort())
        b.putInt(blockSize)
        b.putInt(totalBlocks.toInt())
        b.putInt(totalChunks.toInt())
        b.putInt(0) // image checksum: libsparse writes 0 unless CRC mode is on, devices ignore it
        return b.array()
    }

    internal fun chunkHeader(
        type: Int,
        blocks: Long,
        totalSize: Long,
    ): ByteArray {
        val b = ByteBuffer.allocate(CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(type.toShort())
        b.putShort(0)
        b.putInt(blocks.toInt())
        b.putInt(totalSize.toInt())
        return b.array()
    }

    internal fun u16(
        b: ByteArray,
        o: Int,
    ): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    internal fun i32(
        b: ByteArray,
        o: Int,
    ): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    internal fun u32(
        b: ByteArray,
        o: Int,
    ): Long = i32(b, o).toLong() and 0xFFFFFFFFL
}

/** Parsed sparse file header. */
class SparseHeader(
    val majorVersion: Int,
    val minorVersion: Int,
    val fileHeaderSize: Int,
    val chunkHeaderSize: Int,
    val blockSize: Int,
    val totalBlocks: Long,
    val totalChunks: Long,
) {
    companion object {
        /** Returns null when [b] does not start with the sparse magic; throws if the header is invalid. */
        fun parseOrNull(b: ByteArray): SparseHeader? {
            if (b.size < SparseFormat.FILE_HEADER_SIZE) return null
            if (SparseFormat.i32(b, 0) != SparseFormat.MAGIC) return null
            val h =
                SparseHeader(
                    majorVersion = SparseFormat.u16(b, 4),
                    minorVersion = SparseFormat.u16(b, 6),
                    fileHeaderSize = SparseFormat.u16(b, 8),
                    chunkHeaderSize = SparseFormat.u16(b, 10),
                    blockSize = SparseFormat.i32(b, 12),
                    totalBlocks = SparseFormat.u32(b, 16),
                    totalChunks = SparseFormat.u32(b, 20),
                )
            if (h.majorVersion != SparseFormat.MAJOR_VERSION) {
                throw SparseFormatException("Unsupported sparse major version ${h.majorVersion}")
            }
            if (h.fileHeaderSize < SparseFormat.FILE_HEADER_SIZE || h.chunkHeaderSize < SparseFormat.CHUNK_HEADER_SIZE) {
                throw SparseFormatException("Bad sparse header sizes (${h.fileHeaderSize}/${h.chunkHeaderSize})")
            }
            if (h.blockSize <= 0 || h.blockSize % 4 != 0) {
                throw SparseFormatException("Bad sparse block size ${h.blockSize}")
            }
            return h
        }
    }
}
