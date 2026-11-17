package com.sky22333.skyadb.ui.mirror

import kotlinx.coroutines.channels.Channel

/** FIFO control queue that only sacrifices queued moves when the backlog is excessive. */
internal class MirrorControlQueue<T>(
    private val capacity: Int,
    private val isDroppable: (T) -> Boolean,
) {
    private val lock = Any()
    private val commands = ArrayDeque<T>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var closed = false
    private var droppedCommands = 0L

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun offer(command: T): Boolean {
        synchronized(lock) {
            if (closed) return false
            if (commands.size >= capacity) {
                dropOldestMovesLocked()
            }
            if (commands.size >= capacity && isDroppable(command)) {
                droppedCommands++
                return false
            }
            commands.addLast(command)
        }
        signal.trySend(Unit)
        return true
    }

    suspend fun take(): T? {
        while (true) {
            synchronized(lock) {
                commands.removeFirstOrNull()?.let { return it }
                if (closed) return null
            }
            signal.receiveCatching().getOrNull() ?: return null
        }
    }

    fun clear() {
        synchronized(lock) { commands.clear() }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            commands.clear()
        }
        signal.close()
    }

    fun droppedCount(): Long = synchronized(lock) { droppedCommands }

    fun size(): Int = synchronized(lock) { commands.size }

    private fun dropOldestMovesLocked() {
        val targetSize = capacity / 2
        while (commands.size > targetSize) {
            val index = commands.indexOfFirst(isDroppable)
            if (index < 0) return
            commands.removeAt(index)
            droppedCommands++
        }
    }
}
