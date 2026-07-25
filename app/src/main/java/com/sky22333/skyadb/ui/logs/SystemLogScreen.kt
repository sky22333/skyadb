package com.sky22333.skyadb.ui.logs

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    val uiState by viewModel.uiState.collectAsState()
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
private fun SystemLogContent(
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
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.syslog_title))
                    Text(
                        stringResource(R.string.syslog_subtitle),
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
                IconButton(
                    onClick = onCopyClick,
                    enabled = uiState.filteredLogs.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.syslog_copy_desc))
                }
                IconButton(onClick = onClearClick, enabled = uiState.logs.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.syslog_clear_desc))
                }
                IconButton(onClick = onRefreshClick, enabled = !uiState.loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.syslog_refresh_desc))
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
                LogFilterCard(
                    uiState = uiState,
                    onQueryChanged = onQueryChanged,
                    onLevelSelected = onLevelSelected,
                    onLineLimitSelected = onLineLimitSelected,
                )
            }
            item { LogStatus(uiState.status, uiState.loading) }
            item {
                SectionHeader(
                    title = stringResource(R.string.syslog_content_title),
                    description = stringResource(
                        R.string.syslog_content_desc_format,
                        uiState.filteredLogs.size,
                        uiState.logs.size,
                    ),
                )
            }
            if (uiState.filteredLogs.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.syslog_empty_title),
                        message = stringResource(R.string.syslog_empty_message),
                    )
                }
            } else {
                items(uiState.filteredLogs.takeLast(MaxDisplayLogs)) { line ->
                    LogLine(line = line)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LogFilterCard(
    uiState: SystemLogUiState,
    onQueryChanged: (String) -> Unit,
    onLevelSelected: (String) -> Unit,
    onLineLimitSelected: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.syslog_keyword_label)) },
                singleLine = true,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogLineLimits.forEach { limit ->
                    FilterChip(
                        selected = uiState.lineLimit == limit,
                        onClick = { onLineLimitSelected(limit) },
                        label = { Text("$limit") },
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogLevels.forEach { level ->
                    FilterChip(
                        selected = uiState.level == level,
                        onClick = { onLevelSelected(level) },
                        label = {
                            Text(
                                if (level == LogLevelAll) {
                                    stringResource(R.string.logs_level_all)
                                } else {
                                    level
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
            Text(status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is OperationStatus.Success -> Text(status.text, color = MaterialTheme.colorScheme.primary)

        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
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
    AdbManagerTheme(dynamicColor = false) {
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