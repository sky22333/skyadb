package com.sky22333.skyadb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun AdbManagerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = (if (darkTheme) darkColorScheme() else lightColorScheme())
        .copy(windowDimming = Color.Black.copy(alpha = if (darkTheme) 0.2f else 0.08f))
    MiuixTheme(colors = colors, content = content)
}
