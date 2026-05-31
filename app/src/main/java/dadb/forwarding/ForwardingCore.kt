package dadb.forwarding

import dadb.Dadb
import dadb.PortForwarder
import dadb.log
import okio.BufferedSink
import okio.Source
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

internal interface ForwardingClient : AutoCloseable {
    val source: Source
    val sink: BufferedSink
}

internal interface ForwardingServer : AutoCloseable {
    fun accept(): ForwardingClient
}

internal interface ForwardingDuplex : AutoCloseable {
    val source: Source
    val sink: BufferedSink
}

internal object StreamForwarder {
    fun transfer(source: Source, sink: BufferedSink) {
        try {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    if (source.read(sink.buffer, 256) >= 0) {
                        sink.flush()
                    } else {
                        return
                    }
                } catch (_: IOException) {
                    return
                }
            }
        } catch (_: InterruptedException) {
            // Do nothing.
        } catch (_: InterruptedIOException) {
            // Do nothing.
        }
    }
}

internal abstract class BaseForwarder(
    private val dadb: Dadb,
    private val remoteDestination: String,
    private val endpointDescription: String,
    private val forwardingType: String,
    private val remoteChannelFactory: (String) -> ForwardingDuplex = { destination ->
        AdbForwardingDuplex(dadb.open(destination))
    },
) : PortForwarder {

    @Volatile
    private var startupFailure: Throwable? = null

    private var serverThread: Thread? = null
    private var server: ForwardingServer? = null
    private var clientExecutor: ExecutorService? = null
    private val stateController = ForwarderStateController(endpointDescription)

    fun start() {
        stateController.prepareStart()
        startupFailure = null

        clientExecutor = createClientExecutor()
        serverThread = thread {
            try {
                handleForwarding()
            } catch (_: SocketException) {
                // Expected when the server is closed while stopping.
            } catch (t: Throwable) {
                if (stateController.isStarting()) {
                    startupFailure = t
                }
                log { "could not start $forwardingType forwarding: ${t.message}" }
            } finally {
                stateController.markStopped()
            }
        }

        stateController.awaitStarted()
        startupFailure?.let { throwStartFailure(it) }
    }

    override fun isRunning(): Boolean = stateController.isStarted()

    private fun handleForwarding() {
        val serverRef = createServer()
        server = serverRef

        stateController.markStarted()

        while (!Thread.currentThread().isInterrupted) {
            val client = serverRef.accept()
            clientExecutor?.execute {
                try {
                    handleClient(client)
                } catch (t: Throwable) {
                    log { "forwarder client failed for $endpointDescription -> $remoteDestination: ${t.message}" }
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun handleClient(client: ForwardingClient) {
        val remoteChannel = remoteChannelFactory(remoteDestination)

        val readerThread = thread {
            StreamForwarder.transfer(client.source, remoteChannel.sink)
        }

        try {
            StreamForwarder.transfer(remoteChannel.source, client.sink)
        } finally {
            runCatching { remoteChannel.close() }
            runCatching { client.close() }
            readerThread.interrupt()
        }
    }

    protected abstract fun createServer(): ForwardingServer

    override fun close() {
        if (!stateController.shouldStop()) {
            return
        }

        stateController.awaitStartedOrStopped()
        if (!stateController.shouldStop()) {
            return
        }

        stateController.markStopping()

        server?.close()
        server = null
        serverThread?.interrupt()
        serverThread = null
        clientExecutor?.shutdownNow()
        clientExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        clientExecutor = null

        stateController.awaitStopped()
    }

    private fun createClientExecutor(): ExecutorService {
        return ThreadPoolExecutor(
            MAX_CLIENT_THREADS,
            MAX_CLIENT_THREADS,
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(MAX_CLIENT_QUEUE_SIZE),
            ThreadPoolExecutor.CallerRunsPolicy(),
        ).apply {
            // Multi-socket protocols like scrcpy expect all channels to be connected promptly.
            // If accepted clients are queued behind a single active worker, the device only sees
            // the first socket and waits forever for the remaining ones.
            allowCoreThreadTimeOut(true)
        }
    }

    private fun throwStartFailure(failure: Throwable): Nothing {
        when (failure) {
            is IOException -> throw failure
            is RuntimeException -> throw failure
            is Error -> throw failure
            else -> throw IOException("Failed to start $forwardingType forwarding at $endpointDescription", failure)
        }
    }

    private companion object {
        const val MAX_CLIENT_THREADS = 32
        const val MAX_CLIENT_QUEUE_SIZE = 256
    }
}

private class AdbForwardingDuplex(
    private val stream: dadb.AdbStream,
) : ForwardingDuplex {
    override val source: Source = stream.source
    override val sink: BufferedSink = stream.sink

    override fun close() {
        stream.close()
    }
}

private class ForwarderStateController(
    private val endpointDescription: String,
) {
    @Volatile
    private var state: State = State.STOPPED
    private val lock = Any()
    private var startedSignal = CountDownLatch(0)
    private var stoppedSignal = CountDownLatch(0)

    fun prepareStart() {
        synchronized(lock) {
            check(state == State.STOPPED) { "Forwarder is already started at $endpointDescription" }
            startedSignal = CountDownLatch(1)
            stoppedSignal = CountDownLatch(1)
            state = State.STARTING
        }
    }

    fun shouldStop(): Boolean = synchronized(lock) {
        state != State.STOPPED && state != State.STOPPING
    }

    fun isStarting(): Boolean = state == State.STARTING

    fun isStarted(): Boolean = state == State.STARTED

    fun markStarted() {
        synchronized(lock) {
            state = State.STARTED
            startedSignal.countDown()
        }
    }

    fun markStopping() {
        synchronized(lock) {
            if (state != State.STOPPED) {
                state = State.STOPPING
            }
        }
    }

    fun markStopped() {
        synchronized(lock) {
            if (state == State.STARTING) {
                startedSignal.countDown()
            }
            state = State.STOPPED
            stoppedSignal.countDown()
        }
    }

    fun awaitStarted() {
        awaitStateTransition(startedSignal, "start")
    }

    fun awaitStartedOrStopped() {
        if (state == State.STOPPED) {
            return
        }
        awaitStarted()
    }

    fun awaitStopped() {
        awaitStateTransition(stoppedSignal, "stop")
    }

    private fun awaitStateTransition(signal: CountDownLatch, action: String) {
        if (!signal.await(5, TimeUnit.SECONDS)) {
            throw TimeoutException("Timed out waiting for forwarder to $action")
        }
    }

    private enum class State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
    }
}
