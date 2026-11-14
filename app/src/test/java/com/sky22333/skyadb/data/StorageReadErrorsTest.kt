package com.sky22333.skyadb.data

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageReadErrorsTest {
    @Test fun failedReadWaitsForRetryWithoutEmittingDefaults() = runBlocking {
        var attempts = 0
        val result = async {
            flow { if (attempts++ == 0) throw IOException("test"); emit("saved") }
                .retryWhen { error, _ -> StorageReadErrors.awaitRetry("test", error) }
                .onEach { StorageReadErrors.recovered("test") }
                .first()
        }
        withTimeout(5000) { StorageReadErrors.state.first { "test" in it } }
        assertFalse(result.isCompleted)
        StorageReadErrors.retry()
        assertEquals("saved", withTimeout(5000) { result.await() })
        assertFalse("test" in StorageReadErrors.state.value)
        assertFalse(StorageReadErrors.awaitRetry("test", IllegalArgumentException()))
    }
}
