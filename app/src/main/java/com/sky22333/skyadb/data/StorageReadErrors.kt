package com.sky22333.skyadb.data

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** A failed DataStore read waits for an explicit retry instead of producing invented data. */
internal object StorageReadErrors {
    private val failures = MutableStateFlow<Set<String>>(emptySet())
    val state = failures.asStateFlow()
    private val retries = MutableStateFlow(0L)

    suspend fun awaitRetry(key: String, error: Throwable): Boolean {
        if (error !is IOException) return false
        val generation = retries.value
        failures.update { it + key }
        retries.first { it != generation }
        return true
    }

    fun recovered(key: String) { failures.update { it - key } }
    fun retry() { retries.update { it + 1 } }
}
