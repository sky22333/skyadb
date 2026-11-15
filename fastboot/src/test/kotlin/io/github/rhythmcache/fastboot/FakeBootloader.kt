package io.github.rhythmcache.fastboot

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * In-memory stand-in for a bootloader, used as a [FastbootTransport] in tests.
 *
 * It enforces max-download-size exactly like real bootloaders (FAIL on `download:` above the limit) and
 * decodes whatever is flashed with its own sparse decoder, so tests can compare the resulting
 * partition with the source image.
 *
 * NOTE: the FastbootTransport interface (read/write/waitForDisconnect/close) was reconstructed from how
 * FastbootConnection uses it; adapt the overrides if the real one differs.
 */
class FakeBootloader(
    private val maxDownload: Long,
    /** What `getvar:max-download-size` answers after OKAY (e.g. "0x100000", "1048576"); null makes it FAIL. */
    private val maxDownloadReply: String?,
    partitionSize: Int,
    private val failFlashAt: Int? = null,
    private val variables: Map<String, String> = emptyMap(),
) : FastbootTransport {
    val partition = ByteArray(partitionSize) { SENTINEL }
    val commands = ArrayList<String>()
    val downloadSizes = ArrayList<Long>()
    val stagedWasSparse = ArrayList<Boolean>()

    private val replies = ArrayDeque<ByteArray>()
    private var receiving = 0L
    private val staged = ByteArrayOutputStream()

    override fun write(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (receiving > 0) {
            check(length <= receiving) { "host sent $length bytes but only $receiving were announced" }
            staged.write(buffer, offset, length)
            receiving -= length
            if (receiving == 0L) reply("OKAY")
            return
        }
        val cmd = String(buffer, offset, length, Charsets.UTF_8)
        commands += cmd
        when {
            cmd.startsWith("getvar:") && cmd.substring(7) in variables -> reply("OKAY${variables.getValue(cmd.substring(7))}")
            cmd == "getvar:max-download-size" ->
                if (maxDownloadReply != null) reply("OKAY$maxDownloadReply") else reply("FAILunknown variable")
            cmd.startsWith("download:") -> {
                val n = cmd.substring("download:".length).toLong(16)
                downloadSizes += n
                if (n > maxDownload) {
                    reply("FAILRequested download size is more than max allowed")
                } else {
                    staged.reset()
                    receiving = n
                    reply("DATA%08x".format(n))
                }
            }
            cmd.startsWith("flash:") -> {
                if (commands.count { it.startsWith("flash:") } == failFlashAt) {
                    reply("FAILwrite failed")
                    return
                }
                applyStaged()
                reply("OKAY")
            }
            cmd.startsWith("fetch:") -> {
                val parts = cmd.split(':')
                val start = parts[2].removePrefix("0x").toLong(16).toInt()
                val size = parts[3].removePrefix("0x").toLong(16).toInt()
                reply("DATA%08x".format(size))
                replies.addLast(partition.copyOfRange(start, start + size))
                reply("OKAY")
            }
            else -> reply("FAILunknown command")
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val r = replies.removeFirstOrNull() ?: throw IOException("fake bootloader has no reply queued")
        System.arraycopy(r, 0, buffer, offset, r.size)
        return r.size
    }

    override fun waitForDisconnect() {}

    override fun close() {}

    private fun reply(s: String) {
        replies.addLast(s.toByteArray(Charsets.UTF_8))
    }

    private fun applyStaged() {
        val img = staged.toByteArray()
        val sparse = img.size >= 4 && le32(img, 0) == 0xED26FF3AL
        stagedWasSparse += sparse
        if (!sparse) {
            System.arraycopy(img, 0, partition, 0, img.size)
            return
        }
        require(le16(img, 4) == 1 && le16(img, 8) == 28 && le16(img, 10) == 12) { "bad sparse header" }
        val blk = le32(img, 12).toInt()
        val totalBlocks = le32(img, 16)
        val chunks = le32(img, 20)
        require(totalBlocks * blk <= partition.size) { "sparse image larger than partition" }
        var pos = 28
        var block = 0L
        for (c in 0 until chunks) {
            val type = le16(img, pos)
            val blocks = le32(img, pos + 4)
            val total = le32(img, pos + 8).toInt()
            when (type) {
                0xCAC1 -> System.arraycopy(img, pos + 12, partition, (block * blk).toInt(), (blocks * blk).toInt())
                0xCAC2 -> for (i in 0 until blocks * blk) partition[(block * blk + i).toInt()] = img[pos + 12 + (i % 4).toInt()]
                0xCAC3 -> {}
                0xCAC4 -> {}
                else -> error("bad chunk type $type")
            }
            if (type != 0xCAC4) block += blocks
            pos += total
        }
        require(pos == img.size) { "sparse chunks do not add up to the downloaded size" }
        require(block == totalBlocks) { "sparse chunks cover $block blocks, header says $totalBlocks" }
    }

    private fun le16(
        b: ByteArray,
        o: Int,
    ) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun le32(
        b: ByteArray,
        o: Int,
    ) = le16(b, o).toLong() or (le16(b, o + 2).toLong() shl 16)

    companion object {
        const val SENTINEL: Byte = 0x5A
    }
}
