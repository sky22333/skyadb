package com.sky22333.skyadb.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OperationProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = progress?.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth(),
    )
}
