package com.sky22333.skyadb.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.download.DownloadResult
import com.sky22333.skyadb.download.NetworkDownloadManager
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDownloadIntegrationTest {
    @Test fun downloadsIntoPersistentDirectory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "test-ai-downloads")
        val server = ServerSocket(0)
        val worker = thread {
            server.accept().use { socket ->
                socket.getInputStream().bufferedReader().apply { while (!readLine().isNullOrEmpty()) {} }
                val body = "download content".toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body)
                }
            }
        }
        try {
            val progress = mutableListOf<Float>()
            val manager = NetworkDownloadManager(context, persistentDirectory = directory)
            val result = manager.download("http://127.0.0.1:${server.localPort}/example.txt") { progress.add(it.progress) }
            assertTrue(result is DownloadResult.Success)
            val file = File((result as DownloadResult.Success).localPath)
            assertTrue(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
            assertEquals("example.txt", result.fileName)
            assertEquals("download content", file.readText())
            assertEquals(1f, progress.last(), 0f)
        } finally {
            server.close()
            worker.join(1000)
            directory.deleteRecursively()
        }
    }
}
