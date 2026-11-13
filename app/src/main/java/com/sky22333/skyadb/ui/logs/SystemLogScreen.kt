package com.sky22333.skyadb.ui.logs

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
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
import com.sky22333.skyadb.ui.components.FlatTabRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.launch

@Composable
fun SystemLogScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: SystemLogViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.syslog_clip_label)
    val copiedToast = stringResource(R.string.syslog_copied_toast)

    SystemLogContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::loadLogs,
        onClearClick = viewModel::clearLogs,
        onCopyClick = {
            coroutineScope.launch {
                val clipData = ClipData.newPlainText(clipLabel, buildLogCopyText(uiState))
                clipboard.setClipEntry(ClipEntry(clipData))
                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
            }
        },
        onQueryChanged = viewModel::onQueryChanged,
        onLevelSelected = viewModel::onLevelSelected,
        onLineLimitSelected = viewModel::onLineLimitSelected,
    )
}

@Composable
internal fun SystemLogContent(
    bottomPadding: Dp = 0.dp,
    uiState: SystemLogUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onClearClick: () -> Unit,
    onCopyClick: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onLevelSelected: (String) -> Unit,
    onLineLimitSelected: (Int) -> Unit,
) {
    val filteredLogs = remember(uiState.logs, uiState.level, uiState.query) { uiState.filteredLogs }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.syslog_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(
                    onClick = onCopyClick,
                    enabled = filteredLogs.isNotEmpty(),
                ) {
                    Icon(MiuixIcons.Copy, contentDescription = stringResource(R.string.syslog_copy_desc))
                }
                IconButton(onClick = onClearClick, enabled = uiState.logs.isNotEmpty()) {
                    Icon(MiuixIcons.Clear, contentDescription = stringResource(R.string.syslog_clear_desc))
                }
                IconButton(onClick = onRefreshClick, enabled = !uiState.loading) {
                    Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.syslog_refresh_desc))
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.CompactGap,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap)) {
                    LogFilters(
                        uiState = uiState,
                        onQueryChanged = onQueryChanged,
                        onLevelSelected = onLevelSelected,
                        onLineLimitSelected = onLineLimitSelected,
                    )
                    if (uiState.status !is OperationStatus.Idle) {
                        LogStatus(uiState.status, uiState.loading)
                    }
                    SectionHeader(
                        title = stringResource(R.string.syslog_content_title),
                        trailing = {
                            Text(
                                text = stringResource(R.string.syslog_content_desc_format, filteredLogs.size, uiState.logs.size),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        },
                    )
                }
            }
            if (filteredLogs.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.syslog_empty_title),
                        message = stringResource(R.string.syslog_empty_message),
                    )
                }
            } else {
                items(filteredLogs.takeLast(MaxDisplayLogs)) { line ->
                    LogLine(line = line)
                }
            }
        }
    }
}

@Composable
private fun LogFilters(
    uiState: SystemLogUiState,
    onQueryChanged: (String) -> Unit,
    onLevelSelected: (String) -> Unit,
    onLineLimitSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = uiState.query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.syslog_keyword_label),
            singleLine = true,
        )

        FlatTabRow(
            tabs = LogLineLimits.map(Int::toString),
            selectedTabIndex = LogLineLimits.indexOf(uiState.lineLimit),
            onTabSelected = { onLineLimitSelected(LogLineLimits[it]) },
        )
        FlatTabRow(
            tabs = LogLevels.map { if (it == LogLevelAll) stringResource(R.string.logs_level_all) else it },
            selectedTabIndex = LogLevels.indexOf(uiState.level),
            onTabSelected = { onLevelSelected(LogLevels[it]) },
            minWidth = 48.dp,
        )
    }
}

@Composable
private fun LogLine(line: String) {
    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        color = MiuixTheme.colorScheme.onSurface,
        style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LogStatus(status: OperationStatus, loading: Boolean) {
    when (status) {
        OperationStatus.Idle -> Unit

        is OperationStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        }

        is OperationStatus.Success -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)

        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

private fun buildLogCopyText(uiState: SystemLogUiState): String {
    return uiState.filteredLogs
        .takeLast(MaxDisplayLogs)
        .joinToString(separator = "\n")
}

private const val MaxDisplayLogs = 1000

@Preview(name = "系统日志", showBackground = true, widthDp = 390)
@Composable
private fun SystemLogContentPreview() {
    AdbManagerTheme() {
        SystemLogContent(
            uiState = SystemLogUiState(
                logs = listOf(
                    "05-29 17:32:08.000 1234 1234 I ActivityTaskManager: Displayed com.example/.MainActivity",
                    "05-29 17:32:09.000 1234 1234 W PackageManager: Slow operation",
                ),
            ),
            onBackClick = {},
            onRefreshClick = {},
            onClearClick = {},
            onCopyClick = {},
            onQueryChanged = {},
            onLevelSelected = {},
            onLineLimitSelected = {},
        )
    }
}
