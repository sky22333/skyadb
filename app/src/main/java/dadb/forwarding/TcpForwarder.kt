package dadb.forwarding

import dadb.Dadb
import okio.buffer
import okio.sink
import okio.source
import java.net.ServerSocket

internal class TcpForwarder(
    dadb: Dadb,
    private val hostPort: Int,
    remoteDestination: String,
) : BaseForwarder(
    dadb = dadb,
    remoteDestination = remoteDestination,
    endpointDescription = "port $hostPort",
    forwardingType = "TCP",
) {

    override fun createServer(): ForwardingServer = TcpServerAdapter(hostPort)

    private class TcpServerAdapter(port: Int) : ForwardingServer {
        private val delegate = ServerSocket(port)

        override fun accept(): ForwardingClient = TcpClientAdapter(delegate.accept())

        override fun close() {
            delegate.close()
        }
    }

    private class TcpClientAdapter(
        private val delegate: java.net.Socket,
    ) : ForwardingClient {
        override val source = delegate.getInputStream().source()
        override val sink = delegate.sink().buffer()

        override fun close() {
            delegate.close()
        }
    }
}
