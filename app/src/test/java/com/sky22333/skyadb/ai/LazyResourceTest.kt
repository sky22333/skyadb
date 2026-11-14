package com.sky22333.skyadb.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class LazyResourceTest {
    private class Resource : AutoCloseable {
        val closes = AtomicInteger()
        override fun close() { closes.incrementAndGet() }
    }

    @Test fun createsOnlyOnDemandAndReusesUntilClosed() {
        val creates = AtomicInteger()
        val owner = LazyResource { creates.incrementAndGet(); Resource() }
        assertEquals(0, creates.get())
        val resource = owner.get()
        assertSame(resource, owner.get())
        assertEquals(1, creates.get())
        owner.close()
        owner.close()
        assertEquals(1, resource.closes.get())
        assertTrue(runCatching { owner.get() }.isFailure)
    }

    @Test fun closingWithoutUseDoesNotCreateResource() {
        val creates = AtomicInteger()
        val owner = LazyResource { creates.incrementAndGet(); Resource() }
        owner.close()
        assertEquals(0, creates.get())
    }

    @Test fun resourceFinishingAfterCloseIsReleased() {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val resource = Resource()
        val owner = LazyResource { started.countDown(); check(finish.await(5, TimeUnit.SECONDS)); resource }
        val worker = thread { runCatching { owner.get() } }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        owner.close()
        finish.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertEquals(1, resource.closes.get())
    }
}
