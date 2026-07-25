package com.sky22333.skyadb.validation

import com.sky22333.skyadb.R

object DownloadInputValidator {
    fun urlError(value: String, requireApk: Boolean = false): Int? {
        val normalized = value.trim()
        return when {
            normalized.isBlank() -> null
            !isHttpUrl(normalized) -> R.string.error_url_scheme
            requireApk && !normalized.substringBefore("?").endsWith(".apk", ignoreCase = true) -> {
                R.string.error_url_apk_suffix
            }
            else -> null
        }
    }

    fun isHttpUrl(value: String): Boolean {
        return value.startsWith("https://") || value.startsWith("http://")
    }
}
