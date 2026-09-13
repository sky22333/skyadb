package com.qwadb.app.diagnostics

import com.qwadb.app.model.AdbOperationResult

fun AdbOperationResult.Failure.alsoLog(
    module: DiagnosticModule,
    operation: String,
    target: String? = null,
): AdbOperationResult.Failure {
    DiagnosticLogger.record(
        module = module,
        operation = operation,
        target = target,
        message = message,
        suggestion = suggestion,
        cause = cause,
    )
    return this
}
