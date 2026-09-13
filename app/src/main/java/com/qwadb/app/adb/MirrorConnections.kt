package com.qwadb.app.adb

import com.flyfishxu.kadb.Kadb

data class MirrorConnections(
    val control: Kadb,
    val video: Kadb,
    val audio: Kadb? = null,
) : AutoCloseable {
    override fun close() {
        runCatching { control.close() }
        runCatching { video.close() }
        runCatching { audio?.close() }
    }
}
