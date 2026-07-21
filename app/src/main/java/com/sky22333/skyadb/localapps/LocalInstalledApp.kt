package com.sky22333.skyadb.localapps

data class LocalInstalledApp(
    val packageName: String,
    val label: String,
    val sourcePath: String,
    val versionName: String,
    val isSingleApk: Boolean,
    val apkSizeBytes: Long,
) {
    val installable: Boolean = isSingleApk && apkSizeBytes > 0L
}
