package com.sky22333.skyadb.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.scrcpy.ScrcpyConstants
import java.io.EOFException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.source

/** One target-side process for the lifetime of the apps screen. */
internal class DeviceAppsProbe private constructor(private val stream: AdbStream) {
    private val commands = Mutex()
    private val icons = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private var closed = false

    suspend fun list(): List<AppInfo> = withContext(Dispatchers.IO + NonCancellable) {
        commands.withLock {
            send("LIST")
            buildList {
                while (true) {
                    val line = readLine()
                    if (line == "END") break
                    val fields = line.split('\t')
                    check(fields.size == 5 && fields[0] == "APP") { "Invalid probe response" }
                    add(AppInfo(fields[1], String(Base64.decode(fields[2], Base64.DEFAULT), Charsets.UTF_8), fields[3] == "1", fields[4] == "1"))
                }
            }.sortedWith(compareBy<AppInfo> { !it.enabled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    suspend fun icon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        commands.withLock {
            withContext(NonCancellable) iconRequest@{
                if (closed) return@iconRequest null
                icons.get(packageName)?.let { return@iconRequest it }
                send("ICON\t$packageName")
                val line = readLine()
                if (!line.startsWith("ICON\t")) return@iconRequest null
                val bytes = Base64.decode(line.substringAfter('\t'), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { icons.put(packageName, it) }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        commands.withLock {
            if (!closed) {
                closed = true
                try {
                    send("QUIT")
                    while (stream.source.readUtf8Line() != null) { }
                } finally {
                    try { stream.close() } finally {
                        icons.evictAll()
                        owner.unlock()
                    }
                }
            }
        }
    }

    private fun send(command: String) {
        stream.sink.writeUtf8(command).writeByte('\n'.code).flush()
    }

    private fun readLine(): String = stream.source.readUtf8Line() ?: throw EOFException("Probe closed")

    companion object {
        private const val DEX = "/data/local/tmp/skyadb-app-probe.dex"
        private const val SERVER = "/data/local/tmp/skyadb-app-probe.jar"
        private val owner = Mutex()

        suspend fun open(context: Context, manager: KadbManager): DeviceAppsProbe =
            withContext(Dispatchers.IO + NonCancellable) {
                owner.lock()
                var client: Kadb? = null
                var stream: AdbStream? = null
                try {
                    client = when (val result = manager.mirrorClient()) {
                        is AdbOperationResult.Success -> result.data
                        is AdbOperationResult.Failure -> error(result.message)
                    }
                    context.assets.open("probe/classes.dex").use { client.push(it.source(), DEX, 420, System.currentTimeMillis()) }
                    context.assets.open(ScrcpyConstants.ServerAssetPath).use { client.push(it.source(), SERVER, 420, System.currentTimeMillis()) }
                    val command = "trap 'rm -f $DEX $SERVER' EXIT; CLASSPATH=$DEX:$SERVER app_process / SkyAppsProbe"
                    stream = client.open("shell:$command")
                    check(stream.source.readUtf8Line() == "READY") { "Probe failed to start" }
                    DeviceAppsProbe(stream)
                } catch (error: Throwable) {
                    runCatching { stream?.close() }
                    if (client != null) runCatching { client.shell("rm -f $DEX $SERVER") }
                    owner.unlock()
                    throw error
                }
            }
    }
}
