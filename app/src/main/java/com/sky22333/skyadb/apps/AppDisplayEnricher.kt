package com.sky22333.skyadb.apps

import android.content.Context
import com.sky22333.skyadb.model.AppInfo
import java.util.Locale

/** 设备应用展示名：控制机同包 PackageManager，零流量。 */
object AppDisplayEnricher {
    private val labelCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 256
    }

    private fun cachedLabel(packageName: String): String? = synchronized(labelCache) { labelCache[packageName] }

    private fun rememberLabel(packageName: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || isWeakLabel(trimmed, packageName)) return
        synchronized(labelCache) { labelCache[packageName] = trimmed }
    }

    fun localLabel(context: Context, packageName: String): String? {
        cachedLabel(packageName)?.let { return it }
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().trim()
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
