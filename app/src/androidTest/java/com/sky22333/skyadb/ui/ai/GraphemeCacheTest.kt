package com.sky22333.skyadb.ui.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphemeCacheTest {
    @Test fun appendsAndReplacesWithoutSplittingEmojiOrCombiningMarks() {
        val cache = GraphemeCache()
        assertArrayEquals(intArrayOf(0, 1), cache.update("a"))
        assertArrayEquals(intArrayOf(0, 2), cache.update("a\u0301"))
        val family = "👨‍👩‍👧‍👦"
        assertArrayEquals(intArrayOf(0, 2, 2 + family.length), cache.update("a\u0301$family"))
        assertArrayEquals(intArrayOf(0, family.length), cache.update(family))
        assertArrayEquals(intArrayOf(0), cache.update(""))
    }
}
