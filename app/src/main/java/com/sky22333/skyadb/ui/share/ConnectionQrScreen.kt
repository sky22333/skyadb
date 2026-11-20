package com.sky22333.skyadb.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AdbEndpoint
import com.sky22333.skyadb.qr.ConnectionQrPayload
import com.sky22333.skyadb.qr.QrCodeGenerator
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConnectionQrScreen(
    endpoint: String,
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
) {
    val parsed = remember(endpoint) { AdbEndpoint.parseAuthority(endpoint) }
    val payload = remember(parsed) { parsed?.let(ConnectionQrPayload::encode) }
    val bitmap by produceState<Bitmap?>(initialValue = null, payload) {
        value = payload?.let { content ->
            withContext(Dispatchers.Default) { runCatching { QrCodeGenerator.generate(content) }.getOrNull() }
        }
    }
    val context = LocalContext.current
    val qrTitle = stringResource(R.string.connection_qr_title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(bottom = bottomPadding),
    ) {
        SmallTopAppBar(
            title = stringResource(R.string.connection_qr_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            SectionHeader(
                title = stringResource(R.string.connection_qr_scan_title),
                description = stringResource(R.string.connection_qr_scan_description),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = AppDimens.ControlRadius,
                colors = CardDefaults.defaultColors(color = Color.White, contentColor = Color.Black),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val qr = bitmap
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = stringResource(R.string.connection_qr_content_description),
                            modifier = Modifier.size(260.dp),
                        )
                    } else {
                        Text(stringResource(R.string.connection_qr_generating))
                    }
                }
            }
            Text(
                text = parsed?.authority ?: endpoint,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.connection_qr_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val authority = parsed?.authority ?: endpoint
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            qrTitle,
                            authority,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.connection_qr_copy_address))
            }
        }
    }
}
