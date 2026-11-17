package com.sky22333.skyadb.ai

import org.junit.Assert.*
import org.junit.Test

class AiAttachmentTest {
    @Test fun selectsByIdWithoutGuessingWhenNamesRepeat() {
        val one = AiAttachment(id = "one", uri = "content://one", name = "same.txt")
        val two = AiAttachment(id = "two", uri = "content://two", name = "same.txt")
        assertEquals(two, resolveAttachment(listOf(one, two), "two"))
        assertEquals(one, resolveAttachment(listOf(one), ""))
        assertTrue(runCatching { resolveAttachment(listOf(one, two), "") }.isFailure)
        assertTrue(runCatching { resolveAttachment(listOf(one, two), "missing") }.isFailure)
        assertTrue(runCatching { resolveAttachment(emptyList(), "") }.isFailure)
    }
}
