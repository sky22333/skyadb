package com.sky22333.skyadb.ai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs the system shell under the app UID and drains both streams without buffering large output. */
internal suspend fun runAppShell(command: String): String = withContext(Dispatchers.IO) {
    require(command.isNotBlank()) { "Command is empty" }
    val process = ProcessBuilder("/system/bin/sh", "-c", command).start()
    coroutineScope {
        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                process.destroy()
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                runCatching { process.outputStream.close() }
            }
        }
        suspend fun read(input: InputStream): String = input.use {
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var truncated = false
            while (true) {
                val count = try { it.read(buffer) } catch (error: java.io.IOException) {
                    currentCoroutineContext().ensureActive()
                    throw error
                }
                if (count < 0) break
                val keep = minOf(count, (20_000 - result.size()).coerceAtLeast(0))
                if (keep > 0) result.write(buffer, 0, keep)
                if (keep < count) truncated = true
            }
            result.toString("UTF-8") + if (truncated) "\n[truncated]" else ""
        }
        try {
            val stdout = async(Dispatchers.IO) { read(process.inputStream) }
            val stderr = async(Dispatchers.IO) { read(process.errorStream) }
            val exit = process.waitFor()
            "exit=$exit\nstdout=${stdout.await()}\nstderr=${stderr.await()}"
        } finally { watcher.cancel(); process.destroy() }
    }
}

