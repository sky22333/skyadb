package com.sky22333.skyadb.ai

/** Creates a resource on demand and closes it even if the owner is cleared during creation. */
internal class LazyResource<T : AutoCloseable>(private val create: () -> T) : AutoCloseable {
    private val lock = Any()
    private var value: T? = null
    private var closed = false

    fun get(): T {
        synchronized(lock) {
            check(!closed) { "Resource is closed" }
            value?.let { return it }
        }
        val created = create()
        synchronized(lock) {
            if (closed) {
                created.close()
                error("Resource is closed")
            }
            value?.let { created.close(); return it }
            value = created
            return created
        }
    }

    override fun close() {
        val current = synchronized(lock) {
            closed = true
            value.also { value = null }
        }
        current?.close()
    }
}
