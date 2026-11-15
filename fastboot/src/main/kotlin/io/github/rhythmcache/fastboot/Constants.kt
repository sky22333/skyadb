package io.github.rhythmcache.fastboot

internal object FastbootConst {
    const val MAX_COMMAND_SIZE = 4096
    const val RESPONSE_MAX_SIZE = 256
    const val RESPONSE_TIMEOUT_MS = 300_000L
    const val MAX_DOWNLOAD_SIZE = 0xFFFFFFFFL
}

internal object Commands {
    fun getVar(key: String) = "getvar:$key"

    fun download(size: Long) = "download:%08x".format(size)

    fun flash(partition: String) = "flash:$partition"

    fun erase(partition: String) = "erase:$partition"

    fun setActive(slot: String) = "set_active:$slot"

    fun createLogicalPartition(
        partition: String,
        size: Long,
    ) = "create-logical-partition:$partition:$size"

    fun deleteLogicalPartition(partition: String) = "delete-logical-partition:$partition"

    fun resizeLogicalPartition(
        partition: String,
        size: Long,
    ) = "resize-logical-partition:$partition:$size"

    fun updateSuper(
        partition: String,
        wipe: Boolean,
    ) = if (wipe) "update-super:$partition:wipe" else "update-super:$partition"

    fun snapshotUpdate(cmd: String) = "snapshot-update:$cmd"

    fun oem(cmd: String) = "oem $cmd"

    fun gsi(cmd: String) = "gsi $cmd"

    fun rebootTo(target: String) = "reboot-$target"

    fun fetch(
        partition: String,
        offset: Long?,
        size: Long?,
    ): String {
        var cmd = "fetch:$partition"
        if (offset != null) {
            cmd += ":0x%08x".format(offset)
            if (size != null) cmd += ":0x%08x".format(size)
        }
        return cmd
    }

    const val BOOT = "boot"
    const val CONTINUE = "continue"
    const val REBOOT = "reboot"
    const val SHUTDOWN = "shutdown"
    const val UPLOAD = "upload"
    const val FLASHING_UNLOCK = "flashing unlock"
    const val FLASHING_LOCK = "flashing lock"
    const val FLASHING_UNLOCK_CRITICAL = "flashing unlock_critical"
    const val FLASHING_LOCK_CRITICAL = "flashing lock_critical"
    const val FLASHING_GET_UNLOCK_ABILITY = "flashing get_unlock_ability"
}
