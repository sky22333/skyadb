package com.sky22333.skyadb.ui.diagnostics

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.diagnostics.DiagnosticFormatter
import com.sky22333.skyadb.diagnostics.DiagnosticLog
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.launch

@Composable
fun DiagnosticLogScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: DiagnosticLogViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsState()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.diagnostics_clip_label)
    val copiedToast = stringResource(R.string.diagnostics_copied_toast)

    DiagnosticLogContent(
        bottomPadding = bottomPadding,
        logs = logs,
        onBackClick = onBackClick,
        onClearClick = viewModel::clear,
        onCopyClick = {
            coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText(clipLabel, viewModel.copyText(logs))),
                )
                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun DiagnosticLogContent(
    bottomPadding: Dp = 0.dp,
    logs: List<DiagnosticLog>,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.settings_diagnostics_title))
                    Text(
                        stringResource(R.string.diagnostics_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onCopyClick, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.syslog_copy_desc))
                }
                IconButton(onClick = onClearClick, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.syslog_clear_desc))
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.diagnostics_recent_errors_title),
                    description = stringResource(R.string.diagnostics_count_format, logs.size),
                )
            }
            if (logs.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.diagnostics_empty_title),
                        message = stringResource(R.string.diagnostics_empty_message),
                    )
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    DiagnosticLogCard(log)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogCard(log: DiagnosticLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${stringResource(log.module.labelRes)} · ${log.operation}",
                style = MaterialTheme.typography.titleSmall,
            )
            log.target?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Text(
                text = log.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = log.suggestion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            log.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Text(
                text = DiagnosticFormatter.formatTime(log.timeMillis),
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Preview(name = "诊断日志", showBackground = true, widthDp = 390)
@Composable
private fun DiagnosticLogContentPreview() {
    AdbManagerTheme(dynamicColor = false) {
        DiagnosticLogContent(
            logs = listOf(
                DiagnosticLog(
                    id = 1,
                    timeMillis = System.currentTimeMillis(),
                    module = DiagnosticModule.WifiAdb,
                    operation = "连接设备",
                    target = "192.168.1.20:5555",
                    message = "无法连接到设备",
                    suggestion = "请确认设备与本机处于同一网络，并在目标设备上允许调试授权。",
                ),
            ),
            onBackClick = {},
            onClearClick = {},
            onCopyClick = {},
        )
    }
}
