package io.github.rhythmcache.fastboot.sparse

import java.io.EOFException
import org.junit.Assert.assertThrows
import org.junit.Test

class SparseValidationTest {
    @Test fun rejectsMissingDeclaredBlocks() {
        val source = ByteArraySource(SparseFormat.fileHeader(4096, 4, 0))
        assertThrows(SparseFormatException::class.java) { ImageAnalyzer.analyze(source) }
    }

    @Test fun rejectsReadWithoutProgress() {
        val source = object : RandomAccessSource {
            override val size = 8L
            override fun read(position: Long, dest: ByteArray, offset: Int, length: Int) = 0
        }
        assertThrows(EOFException::class.java) { source.readFully(0, ByteArray(8), 0, 8) }
    }
}
