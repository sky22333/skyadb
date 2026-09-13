package com.qwadb.app.model

sealed interface AdbOperationResult<out T> {
    data class Success<T>(val data: T) : AdbOperationResult<T>
    data class Failure(
        val message: String,
        val suggestion: String,
        val cause: Throwable? = null,
    ) : AdbOperationResult<Nothing>
}

data class ShellCommandResult(
    val command: String,
    val output: String,
    val errorOutput: String,
    val exitCode: Int,
)
