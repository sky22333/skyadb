package com.sky22333.skyadb.ui.ai

import com.sky22333.skyadb.ai.AiMessage
import org.junit.Assert.*
import org.junit.Test

class AiTimelineTest {
    @Test fun groupsOnlyConsecutiveToolsAndKeepsStableKeys() {
        val user = AiMessage(id = "u", role = "user", text = "request")
        val first = AiMessage(id = "a", role = "tool", text = "one")
        val second = AiMessage(id = "b", role = "tool", text = "two")
        val answer = AiMessage(id = "r", role = "assistant", text = "answer")
        val before = listOf(user, first).toTimeline()
        val after = listOf(user, first.copy(text = "updated"), second, answer).toTimeline()
        assertEquals(listOf("message-u", "work-a", "message-r"), after.map { it.key })
        assertEquals(before.last().key, after[1].key)
        assertEquals(listOf("a", "b"), (after[1] as AiTimelineEntry.Work).messages.map { it.id })
        assertEquals(4, listOf(first, answer, second, user).toTimeline().size)
    }
}
