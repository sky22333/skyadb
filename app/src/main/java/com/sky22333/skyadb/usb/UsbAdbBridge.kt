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
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", 0))
        localPort = server.localPort
    }

    fun start(scope: CoroutineScope) {
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && running.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.soTimeout = channel.ioTimeoutMs
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
            val read = channel.readChunk(buffer)
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
