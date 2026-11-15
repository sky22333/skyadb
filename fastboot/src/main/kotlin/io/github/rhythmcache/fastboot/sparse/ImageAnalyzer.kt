package io.github.rhythmcache.fastboot.sparse

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A run of blocks in the *expanded* (partition) image. Blocks not covered by any extent are DONT_CARE. */
sealed class Extent(val startBlock: Long, val blockCount: Long)

/**
 * [blockCount] blocks whose bytes live in the source at [fileOffset]. Only [dataLength] bytes exist
 * there; any remainder up to `blockCount * blockSize` is zero padding (unaligned end of a raw file).
 */
class RawExtent(
    startBlock: Long,
    blockCount: Long,
    val fileOffset: Long,
    val dataLength: Long,
) : Extent(startBlock, blockCount)

/** [blockCount] blocks, each filled with the 4-byte little-endian pattern [value]. */
class FillExtent(
    startBlock: Long,
    blockCount: Long,
    val value: Int,
) : Extent(startBlock, blockCount)

/** Block-level description of an image, independent of whether the input was raw or sparse. */
class ImageLayout(
    val blockSize: Int,
    val totalBlocks: Long,
    val extents: List<Extent>,
)

object ImageAnalyzer {
    /** Detects sparse vs raw by magic and returns the block layout either way. */
    fun analyze(
        src: RandomAccessSource,
        rawBlockSize: Int = SparseFormat.DEFAULT_BLOCK_SIZE,
    ): ImageLayout {
        val head = ByteArray(SparseFormat.FILE_HEADER_SIZE)
        val n = if (src.size >= head.size) readUpTo(src, 0, head) else 0
        val header = if (n == head.size) SparseHeader.parseOrNull(head) else null
        return if (header != null) parseSparse(src, header) else scanRaw(src, rawBlockSize)
    }

    private fun readUpTo(
        src: RandomAccessSource,
        position: Long,
        dest: ByteArray,
    ): Int {
        var done = 0
        while (done < dest.size) {
            val n = src.read(position + done, dest, done, dest.size - done)
            if (n <= 0) break
            done += n
        }
        return done
    }

    /** Parses an existing sparse image into extents. CRC32 chunks are skipped, DONT_CARE becomes a gap. */
    fun parseSparse(
        src: RandomAccessSource,
        header: SparseHeader,
    ): ImageLayout {
        val bs = header.blockSize.toLong()
        val extents = ArrayList<Extent>()
        val chunkHdr = ByteArray(header.chunkHeaderSize)
        val word = ByteArray(4)
        var pos = header.fileHeaderSize.toLong()
        var block = 0L

        for (i in 0 until header.totalChunks) {
            if (pos + header.chunkHeaderSize > src.size) {
                throw SparseFormatException("Truncated sparse image: chunk header $i past end of file")
            }
            src.readFully(pos, chunkHdr, 0, chunkHdr.size)
            val type = SparseFormat.u16(chunkHdr, 0)
            val blocks = SparseFormat.u32(chunkHdr, 4)
            val total = SparseFormat.u32(chunkHdr, 8)
            val payload = total - header.chunkHeaderSize
            val payloadPos = pos + header.chunkHeaderSize

            if (pos + total > src.size) {
                throw SparseFormatException("Truncated sparse image: chunk $i extends past end of file")
            }
            when (type) {
                SparseFormat.CHUNK_RAW -> {
                    if (payload != blocks * bs) throw SparseFormatException("RAW chunk $i size mismatch")
                    if (blocks > 0) extents += RawExtent(block, blocks, payloadPos, payload)
                }
                SparseFormat.CHUNK_FILL -> {
                    if (payload != 4L) throw SparseFormatException("FILL chunk $i size mismatch")
                    src.readFully(payloadPos, word, 0, 4)
                    if (blocks > 0) extents += FillExtent(block, blocks, SparseFormat.i32(word, 0))
                }
                SparseFormat.CHUNK_DONT_CARE -> {
                    if (payload != 0L) throw SparseFormatException("DONT_CARE chunk $i has payload")
                }
                SparseFormat.CHUNK_CRC32 -> {
                    if (payload != 4L) throw SparseFormatException("CRC32 chunk $i size mismatch")
                    // libsparse's resparse drops CRC chunks too; pieces are written without one.
                }
                else -> throw SparseFormatException("Unknown chunk type 0x${type.toString(16)} in chunk $i")
            }
            if (type != SparseFormat.CHUNK_CRC32) block += blocks
            if (block > header.totalBlocks) throw SparseFormatException("Chunks cover more blocks than the header declares")
            pos += total
        }
        if (block != header.totalBlocks) throw SparseFormatException("Chunks cover fewer blocks than the header declares")
        return ImageLayout(header.blockSize, header.totalBlocks, extents)
    }

    /**
     * Streams a raw image once and classifies each block like libsparse's `sparse_file_read_normal`:
     * a block whose 4-byte words are all identical becomes FILL, everything else RAW. Adjacent blocks of
     * the same kind (and fill value) are merged. The final partial block, if any, is RAW and zero padded.
     */
    fun scanRaw(
        src: RandomAccessSource,
        blockSize: Int = SparseFormat.DEFAULT_BLOCK_SIZE,
    ): ImageLayout {
        require(blockSize > 0 && blockSize % 4 == 0) { "blockSize must be a positive multiple of 4" }
        val size = src.size
        val bs = blockSize.toLong()
        val totalBlocks = (size + bs - 1) / bs
        val extents = ArrayList<Extent>()

        val bufBlocks = 256
        val buf = ByteArray(blockSize * bufBlocks)
        val words = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val wordsPerBlock = blockSize / 4

        // open run state
        var kind = 0 // 0 none, 1 raw, 2 fill
        var runStart = 0L
        var runBlocks = 0L
        var runData = 0L
        var runFill = 0

        fun flush() {
            when (kind) {
                1 -> extents += RawExtent(runStart, runBlocks, runStart * bs, runData)
                2 -> extents += FillExtent(runStart, runBlocks, runFill)
            }
            kind = 0
        }

        var block = 0L
        while (block < totalBlocks) {
            val inBuf = minOf(bufBlocks.toLong(), totalBlocks - block).toInt()
            val byteStart = block * bs
            val want = minOf(inBuf * bs, size - byteStart).toInt()
            src.readFully(byteStart, buf, 0, want)

            for (k in 0 until inBuf) {
                val blockStart = byteStart + k * bs
                val avail = minOf(bs, size - blockStart)
                var newKind = 1
                var fill = 0
                if (avail == bs) {
                    val base = k * wordsPerBlock
                    val first = words.get(base)
                    var uniform = true
                    var w = 1
                    while (w < wordsPerBlock) {
                        if (words.get(base + w) != first) {
                            uniform = false
                            break
                        }
                        w++
                    }
                    if (uniform) {
                        newKind = 2
                        fill = first
                    }
                }
                val b = block + k
                if (newKind == kind && (kind == 1 || fill == runFill)) {
                    runBlocks++
                    if (kind == 1) runData += avail
                } else {
                    flush()
                    kind = newKind
                    runStart = b
                    runBlocks = 1
                    runData = if (newKind == 1) avail else 0
                    runFill = fill
                }
            }
            block += inBuf
        }
        flush()
        return ImageLayout(blockSize, totalBlocks, extents)
    }
}
