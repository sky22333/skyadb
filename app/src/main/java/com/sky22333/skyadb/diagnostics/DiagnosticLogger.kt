package com.sky22333.skyadb.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DiagnosticLogger {
    private const val MaxLogs = 500
    private const val MaxTargetLength = 160
    private const val MaxTextLength = 500
    private const val MaxErrorMessageLength = 300

    private val logsState = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    private val lock = Any()
    private var nextId = 1L

    val logs: StateFlow<List<DiagnosticLog>> = logsState.asStateFlow()

    fun record(
        module: DiagnosticModule,
        operation: String,
        target: String? = null,
        message: String,
        suggestion: String,
        cause: Throwable? = null,
    ) {
        synchronized(lock) {
            val normalizedTarget = target?.trim()?.take(MaxTargetLength)?.takeIf { it.isNotBlank() }
            val normalizedMessage = message.trim().take(MaxTextLength)
            val normalizedSuggestion = suggestion.trim().take(MaxTextLength)
            val previous = logsState.value.firstOrNull()
            if (
                previous != null &&
                System.currentTimeMillis() - previous.timeMillis < 1_000 &&
                previous.module == module &&
                previous.operation == operation &&
                previous.target == normalizedTarget &&
                previous.message == normalizedMessage
            ) {
                return
            }
            val log = DiagnosticLog(
                id = nextId++,
                timeMillis = System.currentTimeMillis(),
                module = module,
                operation = operation,
                target = normalizedTarget,
                message = normalizedMessage,
                suggestion = normalizedSuggestion,
                errorClass = cause?.javaClass?.name,
                errorMessage = cause?.message?.trim()?.take(MaxErrorMessageLength),
            )
            logsState.value = (listOf(log) + logsState.value).take(MaxLogs)
        }
    }

    fun clear() {
        synchronized(lock) {
            logsState.value = emptyList()
        }
    }
}
