package com.sky22333.skyadb.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextPageTest {
    @Test fun pagesKeepUtf8CharactersAcrossByteBoundary() {
        val text = "a".repeat(15999) + "中文结束"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val first = readTextPage(bytes.inputStream(), 0)
        assertTrue(first.endsWith("\n[next_offset=15999]"))
        val second = readTextPage(bytes.inputStream(), 15999)
        assertEquals("中文结束\n[end of file]", second)
    }
    @Test fun offsetPastEndReportsEnd() {
        assertEquals("End of file", readTextPage("abc".byteInputStream(), 10))
    }
}
