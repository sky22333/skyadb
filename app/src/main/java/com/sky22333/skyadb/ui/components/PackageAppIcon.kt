package com.sky22333.skyadb.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sky22333.skyadb.localapps.LocalAppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PackageAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(
        initialValue = LocalAppIcons.peek(packageName)?.asImageBitmap(),
        key1 = packageName,
    ) {
        if (value == null) value = LocalAppIcons.load(context, packageName)?.asImageBitmap()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val icon = bitmap
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = MiuixIcons.GridView,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
