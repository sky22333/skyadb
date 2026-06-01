package com.sky22333.skyadb.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.sky22333.skyadb.diagnostics.DiagnosticFormatter
import com.sky22333.skyadb.diagnostics.DiagnosticLog
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import kotlinx.coroutines.flow.StateFlow

class DiagnosticLogViewModel : ViewModel() {
    val logs: StateFlow<List<DiagnosticLog>> = DiagnosticLogger.logs

    fun clear() {
        DiagnosticLogger.clear()
    }

    fun copyText(logs: List<DiagnosticLog>): String {
        return DiagnosticFormatter.format(logs)
    }
}
