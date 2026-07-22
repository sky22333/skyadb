package com.sky22333.skyadb.apps

import android.content.Context
import com.sky22333.skyadb.model.AppInfo
import java.util.Locale

/**
 * 设备应用展示名：优先用控制机本机 PackageManager（同包名零流量），
 * 再合并远程文本探测结果；避免 pull APK。
 */
object AppDisplayEnricher {
    private val labelCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 256
    }

    fun cachedLabel(packageName: String): String? = synchronized(labelCache) { labelCache[packageName] }

    fun rememberLabel(packageName: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || isWeakLabel(trimmed, packageName)) return
        synchronized(labelCache) { labelCache[packageName] = trimmed }
    }

    fun localLabel(context: Context, packageName: String): String? {
        cachedLabel(packageName)?.let { return it }
        val pm = context.packageManager
        val label = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().trim()
        }.getOrNull()
        if (label.isNullOrEmpty() || isWeakLabel(label, packageName)) return null
        rememberLabel(packageName, label)
        return label
    }

    fun enrichWithLocal(context: Context, apps: List<AppInfo>): List<AppInfo> {
        return apps
            .map { app ->
                val label = localLabel(context, app.packageName) ?: app.label
                if (label == app.label) app else app.copy(label = label)
            }
            .sortedWith(compareBy<AppInfo> { !it.enabled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun mergeRemoteLabels(apps: List<AppInfo>, remoteLabels: Map<String, String>): List<AppInfo> {
        if (remoteLabels.isEmpty()) return apps
        return apps
            .map { app ->
                val remote = remoteLabels[app.packageName]?.trim().orEmpty()
                when {
                    remote.isEmpty() || isWeakLabel(remote, app.packageName) -> app
                    !isWeakLabel(app.label, app.packageName) -> app
                    else -> {
                        rememberLabel(app.packageName, remote)
                        app.copy(label = remote)
                    }
                }
            }
            .sortedWith(compareBy<AppInfo> { !it.enabled }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun needsRemoteLabel(app: AppInfo): Boolean = isWeakLabel(app.label, app.packageName)

    fun isWeakLabel(label: String, packageName: String): Boolean {
        val normalized = label.trim()
        if (normalized.isEmpty()) return true
        if (normalized == packageName) return true
        if (normalized == packageName.substringAfterLast('.')) return true
        return false
    }

    fun fallbackLabel(packageName: String): String {
        cachedLabel(packageName)?.let { return it }
        val last = packageName.substringAfterLast('.')
        return last.ifBlank { packageName }.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
