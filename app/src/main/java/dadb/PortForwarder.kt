package dadb

interface PortForwarder : AutoCloseable {
    fun isRunning(): Boolean
}
