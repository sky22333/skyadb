package com.qwadb.app.discovery

import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdbProtocolProbe {
    suspend fun probe(host: String, port: Int, timeoutMillis: Int): AdbScanResult = withContext(Dispatchers.IO) {
        var state = AdbProbeState.Failed
        val latency = measureTimeMillis {
            state = probeState(host = host, port = port, timeoutMillis = timeoutMillis)
        }
        AdbScanResult(host = host, port = port, state = state, latencyMs = latency)
    }

    private fun probeState(host: String, port: Int, timeoutMillis: Int): AdbProbeState {
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
            socket.soTimeout = timeoutMillis

            val payload = "host::skyadb\u0000".toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write(createPacket(command = A_CNXN, arg0 = A_VERSION, arg1 = MaxPayload, payload))
            socket.getOutputStream().flush()

            val header = readExactly(socket, HeaderLength)
            val command = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
            when (command) {
                A_CNXN -> AdbProbeState.AdbAvailable
                A_AUTH -> AdbProbeState.AdbUnauthorized
                A_STLS -> AdbProbeState.AdbSecure
                else -> AdbProbeState.NotAdb
            }
        } catch (_: SocketTimeoutException) {
            if (socket.isConnected) AdbProbeState.PortOpen else AdbProbeState.PortClosed
        } catch (_: EOFException) {
            if (socket.isConnected) AdbProbeState.PortOpen else AdbProbeState.PortClosed
        } catch (_: IOException) {
            if (socket.isConnected) AdbProbeState.PortOpen else AdbProbeState.PortClosed
        } catch (_: Throwable) {
            AdbProbeState.Failed
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun createPacket(command: Int, arg0: Int, arg1: Int, payload: ByteArray): ByteArray {
        val checksum = payload.sumOf { it.toInt() and 0xff }
        return ByteBuffer
            .allocate(HeaderLength + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(payload.size)
            .putInt(checksum)
            .putInt(command xor -1)
            .put(payload)
            .array()
    }

    private fun readExactly(socket: Socket, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        val input = socket.getInputStream()
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read == -1) throw EOFException()
            offset += read
        }
        return buffer
    }

    private companion object {
        const val HeaderLength = 24
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_STLS = 0x534c5453
        const val A_VERSION = 0x01000000
        const val MaxPayload = 4096
    }
}
