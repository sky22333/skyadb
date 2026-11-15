package io.github.rhythmcache.fastboot.sparse

import java.io.ByteArrayOutputStream

/**
 * One self-contained sparse image: file header, an optional leading DONT_CARE (skip to this piece's
 * first block), the data chunks, and an optional trailing DONT_CARE. Every piece declares the *full*
 * image block count, exactly as libsparse's `sparse_file_resparse` does, so the device writes each piece
 * at the right offset without touching other blocks.
 *
 * A piece is an immutable plan: [read] is stateless, so a failed transfer can simply be restarted.
 */
class SparsePiece internal constructor(
    private val segments: Array<Segment>,
) {
    internal class Segment(
        val length: Long,
        // literal bytes (headers, fill values)
        val data: ByteArray?,
        // >= 0: bytes come from the source; -1: zeros (when data == null)
        val fileOffset: Long,
    )

    private val starts = LongArray(segments.size)

    /** Exact number of bytes this piece occupies on the wire. */
    val size: Long

    init {
        var p = 0L
        for (i in segments.indices) {
            starts[i] = p
            p += segments[i].length
        }
        size = p
    }

    /** Fills [dest] with up to [length] bytes of this piece starting at [position]. Returns bytes produced. */
    fun read(
        src: RandomAccessSource,
        position: Long,
        dest: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (position < 0 || position >= size) return 0
        var idx = segmentIndexFor(position)
        var pos = position
        var out = 0
        while (out < length && idx < segments.size) {
            val seg = segments[idx]
            val inSeg = pos - starts[idx]
            val n = minOf(seg.length - inSeg, (length - out).toLong()).toInt()
            when {
                seg.data != null -> System.arraycopy(seg.data, inSeg.toInt(), dest, offset + out, n)
                seg.fileOffset >= 0 -> src.readFully(seg.fileOffset + inSeg, dest, offset + out, n)
                else -> java.util.Arrays.fill(dest, offset + out, offset + out + n, 0.toByte())
            }
            out += n
            pos += n
            if (pos >= starts[idx] + seg.length) idx++
        }
        return out
    }

    private fun segmentIndexFor(position: Long): Int {
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= position) lo = mid else hi = mid - 1
        }
        return lo
    }
}

object SparsePlanner {
    /** Smallest limit that can hold a file header, three chunk headers and one block. */
    fun minimumLimit(blockSize: Int): Long = SparseFormat.FILE_HEADER_SIZE + 4L * SparseFormat.CHUNK_HEADER_SIZE + blockSize

    /**
     * Packs [layout] into pieces of at most [limit] bytes each (exact accounting, no estimate):
     * 28-byte file header + 12 bytes per chunk header (+4 for FILL) + payload, reserving 12 bytes
     * for a trailing DONT_CARE in every piece. RAW extents are split on block boundaries; FILL and
     * DONT_CARE are never split.
     */
    fun plan(
        layout: ImageLayout,
        limit: Long,
    ): List<SparsePiece> {
        val bs = layout.blockSize.toLong()
        require(limit >= minimumLimit(layout.blockSize)) {
            "Chunk limit $limit is too small for block size ${layout.blockSize} (need at least ${minimumLimit(layout.blockSize)})"
        }
        require(layout.totalBlocks <= 0xFFFFFFFFL) { "Image has too many blocks for the sparse format" }

        val pieces = ArrayList<SparsePiece>()
        var b = Builder(layout.totalBlocks, layout.blockSize)

        for (ext in layout.extents) {
            var start = ext.startBlock
            var remaining = ext.blockCount
            var consumed = 0L // blocks of ext already emitted (for RAW data offsets)
            while (remaining > 0) {
                val gap = if (start > b.cursor) SparseFormat.CHUNK_HEADER_SIZE else 0
                when (ext) {
                    is FillExtent -> {
                        val need = gap + SparseFormat.CHUNK_HEADER_SIZE + 4L
                        if (b.used + need > limit) {
                            check(b.chunkCount > 0) { "Fill chunk does not fit an empty piece" }
                            pieces += b.finish()
                            b = Builder(layout.totalBlocks, layout.blockSize)
                            continue
                        }
                        b.skipTo(start)
                        b.addFill(remaining, ext.value)
                        start += remaining
                        remaining = 0
                    }
                    is RawExtent -> {
                        val avail = limit - b.used - gap - SparseFormat.CHUNK_HEADER_SIZE
                        val fit = if (avail <= 0) 0L else avail / bs
                        if (fit <= 0) {
                            check(b.chunkCount > 0) { "Raw chunk does not fit an empty piece" }
                            pieces += b.finish()
                            b = Builder(layout.totalBlocks, layout.blockSize)
                            continue
                        }
                        val take = minOf(fit, remaining, 0xFFFFFFFFL)
                        b.skipTo(start)
                        val dataStart = consumed * bs
                        val dataLen = (ext.dataLength - dataStart).coerceIn(0L, take * bs)
                        b.addRaw(take, bs, ext.fileOffset + dataStart, dataLen)
                        start += take
                        consumed += take
                        remaining -= take
                    }
                }
            }
        }
        if (b.chunkCount > 0 || pieces.isEmpty()) pieces += b.finish()

        for (p in pieces) check(p.size <= limit) { "Internal error: piece of ${p.size} bytes exceeds limit $limit" }
        return pieces
    }

    private class Builder(
        private val totalBlocks: Long,
        private val blockSize: Int,
    ) {
        private val segments = ArrayList<SparsePiece.Segment>()
        private val pending = ByteArrayOutputStream()

        var chunkCount = 0
            private set

        /** Bytes committed so far, including the file header and the reserved trailing DONT_CARE. */
        var used = SparseFormat.FILE_HEADER_SIZE.toLong() + SparseFormat.CHUNK_HEADER_SIZE
            private set

        /** Next block (in the expanded image) this piece has accounted for. */
        var cursor = 0L
            private set

        fun skipTo(block: Long) {
            if (block > cursor) {
                val hdr =
                    SparseFormat.chunkHeader(
                        SparseFormat.CHUNK_DONT_CARE,
                        block - cursor,
                        SparseFormat.CHUNK_HEADER_SIZE.toLong(),
                    )
                pending.write(hdr)
                chunkCount++
                used += SparseFormat.CHUNK_HEADER_SIZE
                cursor = block
            }
        }

        fun addFill(
            blocks: Long,
            value: Int,
        ) {
            pending.write(SparseFormat.chunkHeader(SparseFormat.CHUNK_FILL, blocks, SparseFormat.CHUNK_HEADER_SIZE + 4L))
            val v = ByteArray(4)
            for (i in 0 until 4) v[i] = (value ushr (8 * i)).toByte()
            pending.write(v)
            chunkCount++
            used += SparseFormat.CHUNK_HEADER_SIZE + 4L
            cursor += blocks
        }

        fun addRaw(
            blocks: Long,
            blockSize: Long,
            fileOffset: Long,
            dataLength: Long,
        ) {
            val payload = blocks * blockSize
            pending.write(SparseFormat.chunkHeader(SparseFormat.CHUNK_RAW, blocks, SparseFormat.CHUNK_HEADER_SIZE + payload))
            flushPending()
            if (dataLength > 0) segments += SparsePiece.Segment(dataLength, null, fileOffset)
            if (payload > dataLength) segments += SparsePiece.Segment(payload - dataLength, null, -1)
            chunkCount++
            used += SparseFormat.CHUNK_HEADER_SIZE + payload
            cursor += blocks
        }

        fun finish(): SparsePiece {
            if (cursor < totalBlocks) {
                val hdr =
                    SparseFormat.chunkHeader(
                        SparseFormat.CHUNK_DONT_CARE,
                        totalBlocks - cursor,
                        SparseFormat.CHUNK_HEADER_SIZE.toLong(),
                    )
                pending.write(hdr)
                chunkCount++
                cursor = totalBlocks
            }
            flushPending()
            val header = SparseFormat.fileHeader(blockSize, totalBlocks, chunkCount.toLong())
            segments.add(0, SparsePiece.Segment(header.size.toLong(), header, -1))
            return SparsePiece(segments.toTypedArray())
        }

        private fun flushPending() {
            if (pending.size() > 0) {
                val bytes = pending.toByteArray()
                segments += SparsePiece.Segment(bytes.size.toLong(), bytes, -1)
                pending.reset()
            }
        }
    }
}
