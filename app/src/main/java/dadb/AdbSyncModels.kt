package dadb

/**
 * Compat stat result aligned with AOSP v1 fields.
 */
class AdbSyncStat(
    val mode: Int,
    val size: Long,
    val mtimeSec: Long,
)

/**
 * Full stat_v2/lstat_v2 result aligned with AOSP sync_stat_v2.
 */
class AdbSyncStatV2(
    val errorCode: Int,
    val dev: Long,
    val ino: Long,
    val mode: Int,
    val nlink: Int,
    val uid: Int,
    val gid: Int,
    val size: Long,
    val atimeSec: Long,
    val mtimeSec: Long,
    val ctimeSec: Long,
)

/**
 * Compat list entry aligned with AOSP v1 dent fields, plus optional v2 error code.
 */
class AdbSyncDirEntry(
    val name: String,
    val mode: Int,
    val size: Long,
    val mtimeSec: Long,
    val errorCode: Int?,
)

/**
 * Full list_v2 entry aligned with AOSP sync_dent_v2.
 */
class AdbSyncDirEntryV2(
    val name: String,
    val errorCode: Int,
    val dev: Long,
    val ino: Long,
    val mode: Int,
    val nlink: Int,
    val uid: Int,
    val gid: Int,
    val size: Long,
    val atimeSec: Long,
    val mtimeSec: Long,
    val ctimeSec: Long,
)
