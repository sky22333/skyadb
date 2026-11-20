package com.sky22333.skyadb.adb

import com.flyfishxu.kadb.Kadb
import com.sky22333.skyadb.model.MirrorConnectionMode
import com.sky22333.skyadb.model.MirrorTransport

/** A mirror-scoped Kadb connection. Shared leases deliberately do not close the main session. */
class MirrorClient internal constructor(
    val kadb: Kadb,
    val transport: MirrorTransport,
    val mode: MirrorConnectionMode,
    private val release: () -> Unit,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        release()
    }
}
