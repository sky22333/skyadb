package com.qwadb.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.qwadb.app.diagnostics.DiagnosticFormatter
import com.qwadb.app.diagnostics.DiagnosticLog
import com.qwadb.app.diagnostics.DiagnosticLogger
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
