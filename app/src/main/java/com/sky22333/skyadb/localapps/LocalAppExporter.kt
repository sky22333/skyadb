package com.sky22333.skyadb.localapps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAppExporter(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    suspend fun listUserApps(): List<LocalInstalledApp> = withContext(Dispatchers.IO) {
        launchableApplications()
            .asSequence()
            .filter { it.isUserApp() }
            .map { appInfo ->
                val source = appInfo.sourceDir.orEmpty()
                val sourceFile = File(source)
                val splitCount = appInfo.splitSourceDirs?.size ?: 0
                LocalInstalledApp(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(packageManager).toString().ifBlank { appInfo.packageName },
                    sourcePath = source,
                    versionName = versionName(appInfo.packageName),
                    isSingleApk = splitCount == 0 && sourceFile.extension.equals("apk", ignoreCase = true),
                    apkSizeBytes = sourceFile.takeIf { it.isFile && it.canRead() }?.length() ?: 0L,
                )
            }
            .sortedWith(compareBy<LocalInstalledApp> { !it.installable }.thenBy { it.label.lowercase(Locale.ROOT) })
            .toList()
    }

    suspend fun exportSingleApk(app: LocalInstalledApp): File = withContext(Dispatchers.IO) {
        require(app.installable) { "该应用不是可导出的单 APK 安装包" }
        val source = File(app.sourcePath)
        require(source.isFile && source.canRead()) { "无法读取该应用安装包" }

        val targetDir = File(context.cacheDir, "exported-apps")
        targetDir.mkdirs()
        cleanupExportCache(targetDir)
        val target = File(targetDir, "${app.packageName}-${System.currentTimeMillis()}.apk")
        FileInputStream(source).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target
    }

    private fun launchableApplications(): List<ApplicationInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { packageName -> applicationInfo(packageName) }
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
    }

    private fun versionName(packageName: String): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        }.getOrNull().orEmpty()
    }

    private fun ApplicationInfo.isUserApp(): Boolean {
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return flags and systemFlags == 0 && packageName != context.packageName
    }

    private fun cleanupExportCache(targetDir: File) {
        targetDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
