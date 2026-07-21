package com.sky22333.skyadb.model

sealed interface OperationStatus {
    data object Idle : OperationStatus
    data class Running(
        val text: String,
        /** `null` 表示不确定进度；`0f..1f` 为确定进度。 */
        val progress: Float? = null,
    ) : OperationStatus
    data class Success(val text: String) : OperationStatus
    data class Failed(val text: String, val suggestion: String) : OperationStatus
}
