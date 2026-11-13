package com.sky22333.skyadb.ui.shell

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Play
import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.sky22333.skyadb.ui.components.FlatButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens
import kotlinx.coroutines.launch

@Composable
fun ShellScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.shell_output_title)
    val copied = stringResource(R.string.command_result_copied)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    ShellContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onCommandChanged = viewModel::onCommandChanged,
        onExecuteClick = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.onExecuteClick()
        },
        onHistoryCommandClick = viewModel::onHistoryCommandClick,
        onRefreshSummary = viewModel::refreshSummary,
        onReboot = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.onQuickCommand(it)
        },
        onCopy = { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text)))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun ShellContent(
    bottomPadding: Dp = 0.dp,
    uiState: ShellUiState,
    onBackClick: () -> Unit,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
    onHistoryCommandClick: (String) -> Unit,
    onRefreshSummary: () -> Unit = {},
    onReboot: (String) -> Unit = {},
    onCopy: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(if (uiState.fastboot) R.string.fastboot_console_title else R.string.shell_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            if (uiState.fastboot)
                item {
                    FastbootControls(uiState, onRefreshSummary, onReboot, onCopy)
                }
            item {
                ShellCommandSection(
                    uiState = uiState,
                    onCommandChanged = onCommandChanged,
                    onExecuteClick = onExecuteClick,
                )
            }
            item { ShellOutputSection(output = uiState.output, onCopy = { onCopy(uiState.output) }) }
            item {
                ShellHistorySection(
                    history = uiState.history,
                    onHistoryCommandClick = onHistoryCommandClick,
                )
            }
        }
    }
}

@Composable
private fun ShellCommandSection(
    uiState: ShellUiState,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(title = stringResource(R.string.shell_command_input_title))
        TextField(
            value = uiState.command,
            enabled = uiState.operationStatus !is OperationStatus.Running && !uiState.summaryLoading,
            onValueChange = onCommandChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(if (uiState.fastboot) R.string.fastboot_console_title else R.string.shell_command_label),
            minLines = 1,
            maxLines = 4,
        )
        if (uiState.command.isBlank()) {
            Text(stringResource(if (uiState.fastboot) R.string.fastboot_command_placeholder else R.string.shell_command_placeholder), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Text(stringResource(if (uiState.fastboot) R.string.fastboot_command_input_hint else R.string.adb_command_input_hint), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        ShellStatusMessage(status = uiState.operationStatus)
        FlatButton(
            onClick = onExecuteClick,
            primary = true,
            enabled = uiState.executeEnabled && !uiState.summaryLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = MiuixIcons.Play, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.action_execute))
        }
    }
}

@Composable
private fun ShellOutputSection(output: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.shell_output_title),
            trailing = {
                IconButton(onClick = onCopy, enabled = output.isNotBlank()) {
                    Icon(
                        MiuixIcons.Copy,
                        stringResource(R.string.command_copy_result),
                    )
                }
            },
        )
        if (output.isBlank()) {
            Text(stringResource(R.string.shell_no_output), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        } else {
            Text(
                text = output,
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
internal fun ShellHistorySection(
    history: List<String>,
    onHistoryCommandClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.shell_history_title))
        if (history.isEmpty()) {
            Text(stringResource(R.string.shell_no_history), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { command ->
                    Surface(
                        onClick = { onHistoryCommandClick(command) },
                        shape = RoundedCornerShape(AppDimens.ControlRadius),
                        color = Color.Transparent,
                    ) {
                        Text(command, modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        is OperationStatus.Success ->
            Text(text = status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Failed ->
            Text(
                text =
                    stringResource(
                        R.string.device_status_error_format,
                        status.text,
                        status.suggestion,
                    ),
                color = MiuixTheme.colorScheme.error,
            )
    }
}

@Preview(name = "Shell - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentEmptyPreview() {
    AdbManagerTheme() {
        ShellContent(
            uiState = ShellUiState(),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}

@Preview(name = "Shell - 输出态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentOutputPreview() {
    AdbManagerTheme() {
        ShellContent(
            uiState =
                ShellUiState(
                    command = "getprop ro.product.model",
                    output = "Android TV",
                    history = listOf("getprop ro.product.model", "wm size", "dumpsys battery"),
                    operationStatus = OperationStatus.Success("命令执行完成，退出码 0"),
                    executeEnabled = true,
                ),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}
