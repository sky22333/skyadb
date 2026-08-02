package com.sky22333.skyadb.diagnostics

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticFormatter {
    fun format(logs: List<DiagnosticLog>): String {
        return if (logs.isEmpty()) {
            appString(R.string.diagnostic_format_empty)
        } else {
            logs.joinToString(separator = "\n\n") { format(it) }
        }
    }

    fun format(log: DiagnosticLog): String {
        return buildString {
            appendLine(appString(R.string.diagnostic_format_time, formatTime(log.timeMillis)))
            appendLine(appString(R.string.diagnostic_format_module, appString(log.module.labelRes)))
            appendLine(appString(R.string.diagnostic_format_operation, log.operation))
            log.target?.let { appendLine(appString(R.string.diagnostic_format_target, it)) }
            appendLine(appString(R.string.diagnostic_format_reason, log.message))
            appendLine(appString(R.string.diagnostic_format_suggestion, log.suggestion))
            if (log.errorClass != null || log.errorMessage != null) {
                append(appString(R.string.diagnostic_format_error))
                append(log.errorClass.orEmpty())
                log.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
        }
    }

    fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
    }
}
