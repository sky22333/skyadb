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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 将 USB ADB bulk 通道桥接到本机 TCP，供 Kadb 以 TCP ADB 客户端接入。
 * 不设置 Socket soTimeout，USB 读超时视为空闲轮询而非断线。
 */
class UsbAdbBridge(
    private val connection: UsbDeviceConnection,
    adbInterface: UsbInterface,
    ioTimeoutMs: Int,
) : Closeable {
    private val channel = UsbBulkChannel(connection, adbInterface, ioTimeoutMs)
    private val server = ServerSocket()
    private val running = AtomicBoolean(true)
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
                client.tcpNoDelay = true
                runCatching { relay(client) }
                runCatching { client.close() }
            }
        }
    }

    private fun relay(client: Socket) {
        val input = client.getInputStream()
        val output = client.getOutputStream()
        val toUsb = Thread { runCatching { pumpTcpToUsb(input) } }
        val fromUsb = Thread { runCatching { pumpUsbToTcp(output) } }
        toUsb.start()
        fromUsb.start()
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
        acceptJob?.cancel()
        runCatching { server.close() }
        runCatching { channel.close() }
        runCatching { connection.close() }
    }
}
