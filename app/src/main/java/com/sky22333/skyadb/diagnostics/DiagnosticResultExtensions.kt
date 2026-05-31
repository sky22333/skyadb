package com.sky22333.skyadb.diagnostics

import com.sky22333.skyadb.model.AdbOperationResult

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
