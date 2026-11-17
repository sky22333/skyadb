package com.sky22333.skyadb.ui.mirror

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorControlQueueTest {
    @Test
    fun preservesEveryMoveInFifoOrder() = runBlocking {
        val queue = MirrorControlQueue<String>(capacity = 8, isDroppable = ::isMove)
        val commands = listOf("down", "move-1", "move-2", "move-3", "up")

        commands.forEach { assertTrue(queue.offer(it)) }

        assertEquals(commands, drain(queue, commands.size))
        assertEquals(0L, queue.droppedCount())
    }

    @Test
    fun dropsOldMovesButKeepsDownLatestMoveAndUpWhenBacklogged() = runBlocking {
        val queue = MirrorControlQueue<String>(capacity = 3, isDroppable = ::isMove)

        listOf("down", "move-1", "move-2").forEach { assertTrue(queue.offer(it)) }
        assertTrue(queue.offer("move-3"))
        assertTrue(queue.offer("up"))

        assertEquals(listOf("down", "move-3", "up"), drain(queue, 3))
        assertEquals(2L, queue.droppedCount())
    }

    @Test
    fun neverDropsQueuedDownOrUpToMakeRoom() = runBlocking {
        val queue = MirrorControlQueue<String>(capacity = 1, isDroppable = ::isMove)

        assertTrue(queue.offer("down"))
        assertTrue(queue.offer("up"))

        assertEquals(2, queue.size())
        assertEquals(listOf("down", "up"), drain(queue, 2))
    }

    @Test
    fun rejectsNewMoveWhenQueueContainsNoDroppableCommands() {
        val queue = MirrorControlQueue<String>(capacity = 1, isDroppable = ::isMove)

        assertTrue(queue.offer("down"))
        assertFalse(queue.offer("move-1"))
        assertEquals(1L, queue.droppedCount())
        assertEquals(1, queue.size())
    }

    @Test
    fun closeWakesBlockedConsumerAndDropsPendingCommands() = runBlocking {
        val queue = MirrorControlQueue<String>(capacity = 4, isDroppable = ::isMove)

        val pending = async(start = CoroutineStart.UNDISPATCHED) { queue.take() }

        queue.close()

        assertNull(pending.await())
        assertNull(queue.take())
        assertEquals(0, queue.size())
    }

    private suspend fun <T> drain(queue: MirrorControlQueue<T>, count: Int): List<T> {
        val commands = ArrayList<T>(count)
        repeat(count) { commands += queue.take() ?: error("queue closed") }
        return commands
    }

    private fun isMove(command: String): Boolean = command.startsWith("move-")
}
