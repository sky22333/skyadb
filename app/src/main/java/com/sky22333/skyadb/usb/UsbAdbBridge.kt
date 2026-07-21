package com.sky22333.skyadb.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB ADB bulk ↔ 本机 TCP，供 Kadb 接入。
 *
 * 阻塞读通过关闭 Socket / ServerSocket 解除（Socket InputStream 通常不可被 Thread.interrupt 打断），
 * 符合 Kotlin 对阻塞 IO 的官方建议：关闭资源以强制退出阻塞调用。
 */
class UsbAdbBridge(
    private val connection: UsbDeviceConnection,
    adbInterface: UsbInterface,
    ioTimeoutMs: Int,
) : Closeable {
    private val channel = UsbBulkChannel(connection, adbInterface, ioTimeoutMs)
    private val server = ServerSocket()
    private val running = AtomicBoolean(true)
    private val activeClient = AtomicReference<Socket?>(null)
    private var acceptJob: Job? = null

    val localPort: Int

    init {
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress("127.0.0.1", 0))
            localPort = server.localPort
        } catch (error: Throwable) {
            runCatching { channel.close() }
            runCatching { connection.close() }
            throw error
        }
    }

    fun start(scope: CoroutineScope) {
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && running.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!running.get()) {
                    runCatching { client.close() }
                    break
                }
                client.tcpNoDelay = true
                activeClient.set(client)
                try {
                    relay(client)
                } finally {
                    activeClient.compareAndSet(client, null)
                    runCatching { client.close() }
                }
            }
        }
    }

    private suspend fun relay(client: Socket) = coroutineScope {
        val input = client.getInputStream()
        val output = client.getOutputStream()
        val toUsb = launch(Dispatchers.IO) {
            runCatching { pumpTcpToUsb(input) }
        }
        val fromUsb = launch(Dispatchers.IO) {
            runCatching { pumpUsbToTcp(output) }
        }
        // 任一侧结束时关闭 socket，解除另一侧阻塞读/写
        toUsb.invokeOnCompletion { runCatching { client.close() } }
        fromUsb.invokeOnCompletion { runCatching { client.close() } }
        toUsb.join()
        fromUsb.join()
    }

    private fun pumpTcpToUsb(input: InputStream) {
        val buffer = ByteArray(16 * 1024)
        while (running.get()) {
            val read = input.read(buffer)
            if (read <= 0) break
            channel.writeChunk(buffer, 0, read)
        }
    }

    private fun pumpUsbToTcp(output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (running.get()) {
            val read = channel.readChunkOrTimeout(buffer) ?: continue
            if (read == 0) continue
            output.write(buffer, 0, read)
            output.flush()
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        // 先关客户端与监听口，解除 accept()/InputStream.read() 阻塞
        runCatching { activeClient.getAndSet(null)?.close() }
        runCatching { server.close() }
        acceptJob?.cancel()
        runCatching { channel.close() }
        runCatching { connection.close() }
    }
}
